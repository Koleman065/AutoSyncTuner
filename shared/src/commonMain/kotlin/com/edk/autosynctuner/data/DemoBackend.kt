package com.edk.autosynctuner.data

import kotlinx.coroutines.delay

/**
 * "Try demo" mode: a pretend AutoSync account that lives only in the app.
 * Nothing is sent anywhere. The settings are modeled on real AutoSync Manager
 * settings (one of each control type, licensed and unlicensed modules) so the whole
 * app can be tried, and screenshots taken, without an account. Changes are kept in
 * memory until the demo is closed.
 */
internal class DemoBackend {

    private data class Saved(val value: NewValue, val enabled: Boolean)

    /** Custom values, keyed by "VIN/settingId". */
    private val saved = mutableMapOf<String, Saved>()

    init {
        // A couple of settings start out customized so both states can be seen.
        val vin = VEHICLES.first().vin
        saved["$vin/359"] = Saved(NewValue.Grid(listOf(listOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0))), true)
        saved["$vin/13"] = Saved(NewValue.Number(60.0), true)
    }

    suspend fun profile(): Profile {
        pause()
        return Profile(VEHICLES, DEVICES)
    }

    suspend fun vehicle(vin: String): VehicleSettings {
        pause()
        val modules = SETTINGS.groupBy { it.module }.map { (code, list) ->
            Module(code, list.groupBy { it.category }.map { (cat, s) -> Category(cat, s.map { SettingRef(it.id, it.name) }) })
        }
        return VehicleSettings(modules, LICENSES.associateBy { it.code })
    }

    suspend fun setting(vin: String, id: String): SettingPage {
        pause()
        val def = SETTINGS.firstOrNull { it.id == id } ?: throw SiteChangedException("Demo setting $id not found")
        val custom = saved["$vin/$id"]
        val editor = custom?.let { applyValue(def.editor, it.value) } ?: def.editor
        return SettingPage(
            id = id,
            title = "${def.category} - ${def.name}",
            description = def.description,
            stockValue = def.stock,
            range = def.range,
            editor = editor,
            enabled = custom?.enabled ?: true,
            enabledIsFixed = def.editor is Editor.Radio, // the real site hides the checkbox for Yes/No settings
            submitLabel = if (custom != null) "Update" else "Create",
            isCustomized = custom != null,
            csrfToken = "demo",
            messages = emptyList(),
        )
    }

    suspend fun save(vin: String, page: SettingPage, value: NewValue, enabled: Boolean): SettingPage {
        saved["$vin/${page.id}"] = Saved(value, enabled)
        return setting(vin, page.id)
    }

    suspend fun delete(vin: String, page: SettingPage): SettingPage {
        saved.remove("$vin/${page.id}")
        return setting(vin, page.id)
    }

    private suspend fun pause() = delay(250) // feels like a network round trip

    private fun applyValue(stock: Editor, value: NewValue): Editor = when {
        stock is Editor.Radio && value is NewValue.Choice -> stock.copy(current = value.value)
        stock is Editor.Number && value is NewValue.Number -> stock.copy(current = value.value)
        stock is Editor.Enum && value is NewValue.EnumChoice -> stock.copy(current = value.value)
        stock is Editor.Grid && value is NewValue.Grid -> stock.copy(values = value.values)
        else -> stock
    }

    private class Def(
        val module: String,
        val category: String,
        val id: String,
        val name: String,
        val description: List<String>,
        val stock: String?,
        val range: String?,
        /** The stock state of the control (no custom value). */
        val editor: Editor,
    )

    companion object {
        val VEHICLES = listOf(
            Vehicle("1GC1KXEY0JF000001", "2018 CHEVROLET Silverado 2500 4WD 4D 6.6 Diesel"),
            Vehicle("1GT12UEY0LF000002", "2020 GMC Sierra 2500 HD 4WD 6.6 Diesel"),
        )

        val DEVICES = listOf(
            Device("DEMO01", "AutoSync-v4.10", null),
            Device("DEMO02", "AutoSync-v3.95", "Update required"),
        )

        private val LICENSES = listOf(
            ModuleLicense("BCM", "Body Control Module", "Licensed - Ready", true, null),
            ModuleLicense("IPC", "Instrument cluster", "Licensed - Ready", true, null),
            ModuleLicense("EBCM", "Electronic Brake Control Module (ABS)", "Get License", false, "https://www.btdieselworks.com/products/custom-abs-ebcm-tuning"),
            ModuleLicense("Radio", "", "Get License", false, "https://www.btdieselworks.com"),
            ModuleLicense("HMI", "Human Machine Interface module", "Get License", false, "https://www.btdieselworks.com"),
        )

        private fun yesNo(no: String = "No", yes: String = "Yes", current: String? = null) =
            Editor.Radio(listOf(Choice("0", no), Choice("1", yes)), current)

        private fun row(vararg v: Number) = v.map { it.toDouble() }

        private val SETTINGS = listOf(
            // ---- BCM (licensed) ------------------------------------------------------
            Def(
                "BCM", "Approach Lighting", "359", "Approach Lighting Theme",
                listOf(
                    "Approach lighting turns on lights around the truck when you press Unlock on the key fob in the dark.",
                    "Pick which lights are part of it. Switch a light on to include it, off to leave it out.",
                ),
                "[[0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0]]", "0 – 1 ___ 0 = No, 1 = Yes",
                Editor.Grid(
                    "Lamps", null,
                    listOf("Cornering", "DRL", "Fog", "High Beam", "Low Beam", "Park", "Puddle", "Reverse", "Turn"),
                    emptyList(), 0.0, 1.0, listOf(row(0, 0, 0, 0, 1, 1, 1, 1, 0)),
                ),
            ),
            Def(
                "BCM", "Approach Lighting", "13", "Approach Lights Timeout",
                listOf("How long approach lighting stays on before turning itself off. Pressing Unlock again restarts the timer."),
                "30.00 Seconds", "3.00 – 2500.00 Seconds",
                Editor.Number(3.0, 2500.0, 0.01, null),
            ),
            Def(
                "BCM", "Approach Lighting", "235", "Approach Lights Ambient Light Check",
                listOf(
                    "When Yes (factory), approach lighting only comes on when it's dark outside.",
                    "Set to No to have it come on day or night.",
                ),
                "Yes", null, yesNo(),
            ),
            Def(
                "BCM", "Daytime Running Lights", "6", "DRL Style",
                listOf("Chooses which lights act as the daytime running lights."),
                "Low Beam (reduced intensity)", null,
                Editor.Enum(
                    listOf(
                        Choice("1", "Turn Signals"), Choice("3", "Low Beam (reduced intensity)"),
                        Choice("4", "Low Beam (full intensity)"), Choice("2", "Dedicated - LED"),
                        Choice("5", "Dedicated w/ Low Beam"), Choice("6", "Fog Lights"),
                    ),
                    null,
                ),
            ),
            Def(
                "BCM", "Daytime Running Lights", "165", "DRL - Full brightness at night",
                listOf("On trucks with separate LED running lights, keeps them at full brightness at night instead of dimming."),
                "Disabled", null, yesNo("Disabled", "Enabled"),
            ),
            Def(
                "BCM", "Interior Lighting", "377", "Dimming Curve - Heat/Cool Seat Switch Backlight",
                listOf("How bright the seat switch backlights are at each position of the dash dimmer."),
                "[[0.5, 4.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0, 55.0, 60.0, 65.0, 70.0, 75.0, 80.0, 85.0, 90.0, 96.0]]",
                "0.00 – 100.00 % Duty Cycle",
                Editor.Grid(
                    "Global IP Dimmer Level (%)", null,
                    listOf("0", "5", "11", "17", "22", "28", "33", "39", "44", "50", "55", "61", "66", "72", "77", "83", "88", "94", "100"),
                    emptyList(), 0.0, 100.0,
                    listOf(row(0.5, 4, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 96)),
                ),
            ),
            Def(
                "BCM", "Wipers", "374", "Speed-comp-enabled Intermittent Wiper Delay",
                listOf(
                    "Seconds between wipes for each intermittent switch position (rows) at each vehicle speed range (columns).",
                    "Only used when speed-compensated intermittent wipers are enabled.",
                ),
                "[[22.0, 20.0, 16.0, 10.0], [8.0, 7.0, 5.0, 4.0], [4.0, 3.0, 2.0, 2.0], [2.0, 2.0, 1.0, 1.0], [1.0, 1.0, 1.0, 1.0]]",
                "0.00 – 60.00 Seconds",
                Editor.Grid(
                    "Speed Range", "Wiper Switch Delay Setting",
                    listOf("1", "2", "3", "4"), listOf("1", "2", "3", "4", "5"), 0.0, 60.0,
                    listOf(row(22, 20, 16, 10), row(8, 7, 5, 4), row(4, 3, 2, 2), row(2, 2, 1, 1), row(1, 1, 1, 1)),
                ),
            ),
            // ---- IPC (licensed) ------------------------------------------------------
            Def(
                "IPC", "System", "434", "Coolant Temp Gauge Scaling",
                listOf(
                    "Maps the real coolant temperature (CAN row) to what the dash gauge shows (Displayed row).",
                    "From the factory the gauge holds steady through the normal operating range.",
                ),
                "[[-40.0, 40.0, 63.0, 85.0, 102.0, 115.0, 125.0, 128.0], [75.0, 75.0, 88.0, 100.0, 100.0, 112.0, 125.0, 128.0]]",
                "-40 – 215 Degrees (C)",
                Editor.Grid(
                    "Range", null, (1..8).map { it.toString() }, listOf("CAN", "Displayed"), -40.0, 215.0,
                    listOf(row(-40, 40, 63, 85, 102, 115, 125, 128), row(75, 75, 88, 100, 100, 112, 125, 128)),
                ),
            ),
            Def(
                "IPC", "Audible Alerts", "444", "Critical Warning Chime",
                listOf("Chime settings used for urgent warnings."),
                "[[8.0, 255.0, 120.0, 30.0, 6.0, 255.0]]", "0 – 255",
                Editor.Grid("Chime", null, (1..6).map { it.toString() }, emptyList(), 0.0, 255.0, listOf(row(8, 255, 120, 30, 6, 255))),
            ),
            // ---- EBCM (not licensed) -------------------------------------------------
            Def(
                "EBCM", "System", "504", "Cruise Control Type",
                listOf("Which type of cruise control the brake module is set up for."),
                "ECC Standard", null,
                Editor.Enum(
                    listOf(
                        Choice("255", "ECC Standard"), Choice("256", "ECC Powertrain Autonomous Braking"),
                        Choice("257", "ACC Conventional"), Choice("258", "ACC Full Speed Range (w/Radar)"),
                    ),
                    null,
                ),
            ),
            Def(
                "EBCM", "Features", "527", "Collision Mitigation",
                listOf("Which automatic emergency braking hardware the truck has."),
                "None", null,
                Editor.Enum(
                    listOf(Choice("277", "None"), Choice("278", "Front Camera Module (0.0g prefill)"), Choice("280", "Radar Feature Bundle (0.6g)")),
                    null,
                ),
            ),
            // ---- Radio (not licensed) ------------------------------------------------
            Def(
                "Radio", "System", "675", "Manual-on Radio Timeout",
                listOf("How long the radio plays when turned on with the ignition off."),
                "10 Minutes", "0 – 250 Minutes",
                Editor.Number(0.0, 250.0, 1.0, null),
            ),
            // ---- HMI (not licensed) --------------------------------------------------
            Def(
                "HMI", "System", "854", "HMI Graphical Theme",
                listOf("The look of the radio/infotainment screen."),
                "Chevrolet", null,
                Editor.Enum(
                    listOf(Choice("483", "Buick"), Choice("484", "Cadillac"), Choice("485", "Corvette"), Choice("486", "Chevrolet"), Choice("487", "GMC Blue (MY17+)")),
                    null,
                ),
            ),
            Def(
                "HMI", "System", "83", "Video / Nav Entry In Motion Allowed",
                listOf("Whether video and navigation entry are allowed while not in Park."),
                "Not Allowed", null, yesNo("Not Allowed", "Allowed"),
            ),
        )
    }
}
