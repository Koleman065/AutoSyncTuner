package com.edk.autosynctuner.data

import io.ktor.http.encodeURLParameter

/**
 * Builds the link behind "Send feedback": GitHub's feedback form for this project, with
 * the app version and phone type already filled in. Nothing personal (email, VIN,
 * setting values) is ever put in the link, because the form is public.
 */
object Feedback {
    private const val FORM = "https://github.com/Koleman065/AutoSyncTuner/issues/new"

    fun url(app: AppInfo, demo: Boolean, setting: String? = null): String {
        val params = buildList {
            add("template" to "feedback.yml")
            add("app_version" to "${app.versionName} (build ${app.versionCode}) · ${app.platform}" + if (demo) " · demo" else "")
            if (setting != null) {
                add("setting" to setting)
                add("title" to "Problem with: $setting")
            }
        }
        return FORM + "?" + params.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
    }
}
