# AutoSync Tuner

A native Android app for the BT DieselWorks **AutoSync Manager** website
(autosync.btdieselworks.com). It shows your truck's tune-customization settings with
phone-friendly controls and saves them back to your AutoSync account.

## What it does

- Sign in with your AutoSync Manager email and password. The password isn't stored;
  the app keeps the website's sign-in cookie, so you stay signed in until you sign out.
- Lists the vehicles on your profile, then every setting grouped by module (BCM, IPC,
  EBCM, HVAC, Radio, HMI) and category, with search across all settings.
- Each setting page shows the description, stock value, range and status (stock or
  customized), plus a control that fits the setting:
  - **Yes/No and Enabled/Disabled** become a tappable list.
  - **Dropdowns** (e.g. DRL Style) become a tappable list.
  - **Numbers** become a box with − / + buttons, limited to the allowed range.
  - **0/1 tables** (e.g. Approach Lighting Theme) become one labeled switch per lamp.
  - **Number tables** (dimming curves, gauge scaling, wiper delays) become one labeled
    box per cell, grouped by row.
- **Save** shows a confirmation with the current, new and stock values. After saving, the
  app reads the setting back from the website and tells you whether the new value stuck.
- **Fill in stock value** puts the stock value back in the editor, and you still press Save.
  **Remove customization** is the website's Delete button.
- Values outside the range the website allows are blocked before anything is sent.

## Building it

1. Install **Android Studio** (any 2025 or newer release).
2. **File → Open** and pick this `AutoSyncTuner` folder. Let Gradle sync; the first sync
   downloads the Android build tools.
3. Plug in your phone with USB debugging on (or use **Build → Generate App Bundles or
   APKs → Generate APKs**) and press **Run ▶**.

The release build is signed with the debug key so it can be sideloaded directly.

Run the parser tests with `gradlew test` (or right-click `ParserTest` → Run).

## How it talks to the website

There's no official API. The app loads the same HTML pages as a browser and submits the
same form fields the website's own Create / Update / Delete buttons send:

| Setting type | Field sent |
|---|---|
| Yes/No style | `custom_value=0` / `1` |
| Number | `custom_value=<number>` |
| Dropdown | `custom_type_enum=<option id>` |
| Table | `custom_map=[[...],[...]]` |

Every save also sends `enabled` (the "Setting Enabled" box), the page's
`csrfmiddlewaretoken`, and `update=Create|Update` (or `delete=Delete`).

If BT DieselWorks changes the site's layout, **`app/src/main/java/.../data/Parser.kt`** is
the file to update. A setting whose page the app doesn't recognise is shown read-only
and can't be saved.

## Code map

```
data/Model.kt               data classes (vehicle, modules, setting page, editor types)
data/Parser.kt              HTML → data (Jsoup); unit-tested in ParserTest
data/AutoSyncClient.kt      sign-in, loading pages, saving / deleting (OkHttp)
data/PersistentCookieJar.kt keeps you signed in
ui/Draft.kt                 edit state, validation, read-back check
ui/Editors.kt               the four control types
ui/*Screen.kt               sign-in, vehicles, settings list, setting detail
```
