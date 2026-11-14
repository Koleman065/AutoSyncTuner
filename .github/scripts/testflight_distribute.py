#!/usr/bin/env python3
"""
Sends a freshly uploaded iOS build to every external TestFlight group, the way you
would by hand in App Store Connect:

  1. wait until Apple has finished processing the build
  2. set its "What to Test" notes
  3. add it to each external tester group (the public-link group included)
  4. submit it for Beta App Review (Apple often approves later builds automatically)

Uses the App Store Connect API with the same key the upload uses
(ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8). Exits quietly when those aren't set.

Usage: testflight_distribute.py --bundle-id com.edk.autosynctuner --build 16 --notes "..."
"""
import argparse
import base64
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

import jwt  # PyJWT

API = "https://api.appstoreconnect.apple.com/v1"


def log(msg):
    print(msg, flush=True)


def normalize_p8(raw: str) -> str:
    """Rebuilds the .p8 key from however it was pasted into the GitHub secret."""
    raw = raw.replace("\r", "")
    if "PRIVATE KEY" not in raw:
        try:
            decoded = base64.b64decode(re.sub(r"\s", "", raw)).decode("utf-8", "ignore")
            if "PRIVATE KEY" in decoded:
                raw = decoded
        except Exception:
            pass
    body = re.sub(r"-----(BEGIN|END) [A-Z ]*-----", "", raw)
    body = re.sub(r"\s", "", body)
    lines = [body[i:i + 64] for i in range(0, len(body), 64)]
    return "-----BEGIN PRIVATE KEY-----\n" + "\n".join(lines) + "\n-----END PRIVATE KEY-----\n"


class Api:
    def __init__(self, key_id, issuer_id, p8):
        self.key_id, self.issuer_id, self.p8 = key_id, issuer_id, normalize_p8(p8)

    def _token(self):
        now = int(time.time())
        return jwt.encode(
            {"iss": self.issuer_id, "iat": now, "exp": now + 15 * 60, "aud": "appstoreconnect-v1"},
            self.p8, algorithm="ES256", headers={"kid": self.key_id, "typ": "JWT"},
        )

    def call(self, method, path, body=None, ok_errors=()):
        url = path if path.startswith("http") else API + path
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": "Bearer " + self._token(),
            "Content-Type": "application/json",
        })
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                text = resp.read().decode()
                return json.loads(text) if text else {}
        except urllib.error.HTTPError as e:
            text = e.read().decode(errors="replace")
            if e.code in ok_errors:
                return {"_status": e.code, "_body": text}
            raise SystemExit(f"App Store Connect API {method} {path} failed: HTTP {e.code}\n{text}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bundle-id", required=True)
    ap.add_argument("--build", required=True, help="CFBundleVersion (the GitHub run number)")
    ap.add_argument("--notes", default="")
    ap.add_argument("--timeout-min", type=int, default=60)
    args = ap.parse_args()

    key_id = os.environ.get("ASC_KEY_ID", "").strip()
    issuer = os.environ.get("ASC_ISSUER_ID", "").strip()
    p8 = os.environ.get("ASC_KEY_P8", "")
    if not (key_id and issuer and p8):
        log("App Store Connect key not configured; skipping TestFlight distribution.")
        return

    api = Api(key_id, issuer, p8)

    apps = api.call("GET", f"/apps?filter[bundleId]={args.bundle_id}&limit=1")["data"]
    if not apps:
        raise SystemExit(f"No App Store Connect app with bundle ID {args.bundle_id}")
    app_id = apps[0]["id"]

    # 1. Wait for Apple to finish processing the upload.
    deadline = time.time() + args.timeout_min * 60
    build = None
    while True:
        found = api.call("GET", f"/builds?filter[app]={app_id}&filter[version]={args.build}&limit=1")["data"]
        state = found[0]["attributes"].get("processingState") if found else "NOT_FOUND"
        log(f"Build {args.build}: {state}")
        if state == "VALID":
            build = found[0]
            break
        if state in ("FAILED", "INVALID"):
            raise SystemExit(f"Apple rejected build {args.build} during processing ({state}).")
        if time.time() > deadline:
            raise SystemExit(f"Build {args.build} was still {state} after {args.timeout_min} minutes.")
        time.sleep(30)
    build_id = build["id"]

    # 2. "What to Test" notes.
    if args.notes:
        locs = api.call("GET", f"/builds/{build_id}/betaBuildLocalizations")["data"]
        en = next((l for l in locs if l["attributes"].get("locale", "").startswith("en")), None)
        if en:
            api.call("PATCH", f"/betaBuildLocalizations/{en['id']}", {
                "data": {"type": "betaBuildLocalizations", "id": en["id"], "attributes": {"whatsNew": args.notes}}})
        else:
            api.call("POST", "/betaBuildLocalizations", {"data": {
                "type": "betaBuildLocalizations",
                "attributes": {"locale": "en-US", "whatsNew": args.notes},
                "relationships": {"build": {"data": {"type": "builds", "id": build_id}}}}})
        log("Set What to Test notes.")

    # 3. Add to every external group.
    groups = api.call("GET", f"/betaGroups?filter[app]={app_id}&limit=50")["data"]
    external = [g for g in groups if not g["attributes"].get("isInternalGroup")]
    if not external:
        log("No external TestFlight groups yet; nothing to distribute to.")
        return
    for g in external:
        api.call("POST", f"/betaGroups/{g['id']}/relationships/builds",
                 {"data": [{"type": "builds", "id": build_id}]}, ok_errors=(409,))
        log(f"Added build {args.build} to external group \"{g['attributes'].get('name')}\".")

    # 4. Submit for Beta App Review. A 409/422 here usually means it's already
    #    submitted or approved, which is fine.
    r = api.call("POST", "/betaAppReviewSubmissions", {"data": {
        "type": "betaAppReviewSubmissions",
        "relationships": {"build": {"data": {"type": "builds", "id": build_id}}}}}, ok_errors=(409, 422))
    if "_status" in r:
        log(f"Beta review submission returned HTTP {r['_status']} (usually: already submitted or approved).")
        log(r["_body"][:500])
    else:
        log("Submitted for Beta App Review.")

    detail = api.call("GET", f"/builds/{build_id}/buildBetaDetail")["data"]["attributes"]
    log(f"External build state: {detail.get('externalBuildState')}")


if __name__ == "__main__":
    main()
