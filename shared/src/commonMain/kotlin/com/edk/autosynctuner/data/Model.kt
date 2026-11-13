package com.edk.autosynctuner.data

/** A vehicle listed on the user's AutoSync profile. */
data class Vehicle(val vin: String, val description: String) {
    /** "2018 CHEVROLET Silverado 2500 4WD 4D 6.6 Diesel" -> "2018 Chevrolet Silverado 2500" */
    val shortName: String
        get() {
            val words = description.split(' ').filter { it.isNotBlank() }
            if (words.isEmpty()) return vin
            return words.take(4).joinToString(" ") { w ->
                if (w.length > 3 && w.all { it.isUpperCase() || !it.isLetter() }) w.lowercase().replaceFirstChar { it.uppercase() } else w
            }
        }
}

/** An AutoSync module registered to the account, e.g. serial "13CDF0", firmware "AutoSync-v3.95". */
data class Device(val serial: String, val firmware: String, val note: String?)

/** What the profile page lists: the vehicles you can tune and the AutoSync modules on the account. */
data class Profile(val vehicles: List<Vehicle>, val devices: List<Device>)

/** One entry in the vehicle's settings tree. */
data class SettingRef(val id: String, val name: String)

data class Category(val name: String, val settings: List<SettingRef>)

data class Module(val code: String, val categories: List<Category>) {
    val settingCount: Int get() = categories.sumOf { it.settings.size }
}

/**
 * One row of the website's "Tune Status" list, e.g.
 * BCM · Body Control Module · "Licensed - Ready", or HMI · ... · "Get License" (a link).
 */
data class ModuleLicense(
    val code: String,
    val description: String,
    /** The status text exactly as the website shows it. */
    val status: String,
    val licensed: Boolean,
    /** Where the website's "Get License" link points, if the module isn't licensed. */
    val licenseUrl: String?,
)

/** Everything on a vehicle's tuning page: the settings tree plus each module's license status. */
data class VehicleSettings(val modules: List<Module>, val licenses: Map<String, ModuleLicense>) {
    fun licenseFor(moduleCode: String): ModuleLicense? =
        licenses[moduleCode] ?: licenses.entries.firstOrNull { it.key.equals(moduleCode, ignoreCase = true) }?.value
}

/** A choice for Yes/No-style and dropdown settings. */
data class Choice(val value: String, val label: String)

/** What kind of control the setting page uses, plus its current value. */
sealed interface Editor {
    /** Two or more radio buttons posted as `custom_value` (e.g. No/Yes, Disabled/Enabled). */
    data class Radio(val choices: List<Choice>, val current: String?) : Editor

    /** Free number posted as `custom_value`. */
    data class Number(val min: Double?, val max: Double?, val step: Double?, val current: Double?) : Editor

    /** Dropdown posted as `custom_type_enum`. */
    data class Enum(val choices: List<Choice>, val current: String?) : Editor

    /** Table posted as `custom_map` (JSON list of rows). */
    data class Grid(
        val title: String?,
        val rowAxisLabel: String?,
        val columnHeaders: List<String>,
        val rowHeaders: List<String>,
        val min: Double?,
        val max: Double?,
        val values: List<List<Double>>,
    ) : Editor {
        /** 0/1 tables (lamp themes etc.) are shown as switches instead of number cells. */
        val isBinary: Boolean get() = min == 0.0 && max == 1.0
    }

    /** A page layout the app does not understand yet. Shown read-only, never saved. */
    data class Unsupported(val reason: String) : Editor
}

data class SettingPage(
    val id: String,
    val title: String,
    val description: List<String>,
    val stockValue: String?,
    val range: String?,
    val editor: Editor,
    /** Whether the "Setting Enabled" box is ticked. */
    val enabled: Boolean,
    /** Some pages always send enabled=True via a hidden field and show no checkbox. */
    val enabledIsFixed: Boolean,
    /** "Create" for a stock setting, "Update" once customized. Sent back as the `update` field. */
    val submitLabel: String,
    /** True once the setting has a saved custom value (the page offers Delete). */
    val isCustomized: Boolean,
    val csrfToken: String,
    /** Any alert/message the site showed on this page (e.g. after saving). */
    val messages: List<String>,
)

/** The value the user picked, in the shape the site's form expects. */
sealed interface NewValue {
    data class Choice(val value: String) : NewValue            // radio
    data class Number(val value: Double) : NewValue            // number box
    data class EnumChoice(val value: String) : NewValue        // dropdown
    data class Grid(val values: List<List<Double>>) : NewValue // table
}

class NotLoggedInException : Exception("Not signed in")
class SiteChangedException(msg: String) : Exception(msg)
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Tiny key/value storage: SharedPreferences on Android, NSUserDefaults on iOS. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}
