package com.edk.autosynctuner

import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.data.Editor
import com.edk.autosynctuner.data.KeyValueStore
import com.edk.autosynctuner.ui.Drafts
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.fail

/**
 * Daily check that the AutoSync Manager website still looks the way the app expects
 * (run by .github/workflows/site-check.yml). It signs in with a real account and only
 * READS pages: it never saves, changes or deletes a setting.
 *
 * The repository is public, so nothing personal is ever printed: no email, no VIN, no
 * setting values. Only counts, setting numbers/names, and what didn't parse.
 *
 * Skipped unless AUTOSYNC_EMAIL and AUTOSYNC_PASSWORD are set, so normal builds never run it.
 */
class LiveSiteCheck {

    private class MemoryStore : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private val report = StringBuilder()
    private val problems = mutableListOf<String>()
    private var secretsToHide = listOf<String>()

    private fun clean(text: String): String =
        secretsToHide.filter { it.isNotBlank() }.fold(text) { t, s -> t.replace(s, "<hidden>", ignoreCase = true) }
            .replace(Regex("""[A-HJ-NPR-Z0-9]{17}"""), "<VIN>")

    private fun note(line: String) { report.appendLine(clean(line)) }
    private fun problem(line: String) { problems += clean(line); note("- ❌ $line") }

    @Test
    fun websiteStillMatchesTheApp() = runBlocking {
        val email = System.getenv("AUTOSYNC_EMAIL").orEmpty()
        val password = System.getenv("AUTOSYNC_PASSWORD").orEmpty()
        assumeTrue("AUTOSYNC_EMAIL / AUTOSYNC_PASSWORD not set; skipping live website check", email.isNotBlank() && password.isNotBlank())
        secretsToHide = listOf(email, password)

        val client = AutoSyncClient(MemoryStore())
        note("## AutoSync Manager website check (${LocalDate.now()})")
        note("Read-only: signs in and reads pages; never saves anything.\n")
        try {
            run(client, email, password)
        } catch (e: Throwable) {
            problem("Stopped early: ${e::class.simpleName}: ${e.message ?: "no details"}")
        } finally {
            runCatching { client.logout() }
            writeReport()
        }
        if (problems.isNotEmpty()) {
            // A plain message with no cause chain, so no URL (with a VIN in it) reaches the public log.
            fail(clean("Website check found ${problems.size} problem(s):\n" + problems.joinToString("\n")))
        }
    }

    private suspend fun run(client: AutoSyncClient, email: String, password: String) {
        // 1. Sign-in page and login form.
        if (!client.login(email, password)) {
            problem("Sign-in failed: the website's sign-in page may have changed, or the stored password is wrong.")
            return
        }
        note("- ✅ Signed in")

        // 2. Profile: vehicles and AutoSync devices.
        val profile = client.profile(refresh = true)
        if (profile.vehicles.isEmpty()) {
            problem("Profile page: no vehicles found (vehicle list layout may have changed).")
            return
        }
        note("- ✅ Profile page: ${profile.vehicles.size} vehicle(s), ${profile.devices.size} AutoSync device(s)")
        if (profile.devices.isEmpty()) problem("Profile page: no AutoSync devices found (device list layout may have changed).")

        // 3. Vehicle page: settings tree and Tune Status (licenses).
        val vin = profile.vehicles.first().vin
        val vehicle = client.vehicle(vin, refresh = true)
        val ids = vehicle.modules.flatMap { m -> m.categories.flatMap { c -> c.settings.map { s -> Triple(m.code, c.name, s) } } }
        if (ids.isEmpty()) {
            problem("Vehicle page: no settings found (settings list layout may have changed).")
            return
        }
        note("- ✅ Vehicle page: ${vehicle.modules.size} modules, ${ids.size} settings")
        if (vehicle.licenses.isEmpty()) problem("Vehicle page: Tune Status (license) list not found.")
        else note("- ✅ Tune Status: ${vehicle.licenses.size} modules (${vehicle.licenses.values.count { it.licensed }} licensed)")
        vehicle.modules.filter { vehicle.licenseFor(it.code) == null }.forEach {
            note("- ⚠️ ${it.code} has settings but no Tune Status row")
        }

        // 4. A rotating slice of setting pages (all of them about once a week),
        //    or every page when SITE_CHECK_ALL=true.
        val all = System.getenv("SITE_CHECK_ALL") == "true"
        val sorted = ids.sortedBy { it.third.id.toIntOrNull() ?: Int.MAX_VALUE }
        val chunk = 30
        val slices = (sorted.size + chunk - 1) / chunk
        val today = if (all) sorted else sorted.chunked(chunk)[LocalDate.now().dayOfYear % slices]
        note("\nChecking ${today.size} of ${sorted.size} setting pages" + if (all) " (all)" else " (slice ${LocalDate.now().dayOfYear % slices + 1} of $slices)")

        val kinds = mutableMapOf<String, Int>()
        var ok = 0
        for ((module, category, ref) in today) {
            val label = "$module · $category · ${ref.name} (#${ref.id})"
            try {
                val page = client.setting(vin, ref.id)
                val kind = when (val e = page.editor) {
                    is Editor.Radio -> "yes/no"
                    is Editor.Number -> "number"
                    is Editor.Enum -> "dropdown"
                    is Editor.Grid -> if (e.isBinary) "on/off table" else "number table"
                    is Editor.Unsupported -> { problem("$label: layout not understood (${e.reason})"); continue }
                }
                kinds[kind] = (kinds[kind] ?: 0) + 1
                val issues = buildList {
                    if (page.title.isBlank()) add("no title")
                    if (page.stockValue != null && Drafts.stock(page) == null) add("stock value not understood")
                    // Only check a value that's actually there (some dropdowns have no stock value shown).
                    val initial = Drafts.initial(page)
                    val hasValue = when (initial) {
                        is com.edk.autosynctuner.ui.Draft.Choice -> initial.value != null
                        is com.edk.autosynctuner.ui.Draft.Number -> initial.text.isNotBlank()
                        else -> true
                    }
                    if (hasValue) Drafts.validate(page, initial).exceptionOrNull()?.let { add("current value fails the app's checks (${it.message})") }
                }
                if (issues.isEmpty()) ok++ else problem("$label: ${issues.joinToString("; ")}")
            } catch (e: Exception) {
                problem("$label: could not load (${e::class.simpleName}: ${e.message ?: "no details"})")
            }
            delay(400) // be gentle with BT DieselWorks' server
        }
        note("- ✅ $ok of ${today.size} setting pages fully understood (" + kinds.entries.joinToString(", ") { "${it.value} ${it.key}" } + ")")
    }

    private fun writeReport() {
        val text = report.toString() + if (problems.isEmpty()) "\n**All checks passed.**\n" else "\n**${problems.size} problem(s) found.**\n"
        println(text)
        System.getenv("SITE_CHECK_REPORT")?.let { path -> runCatching { File(path).writeText(text) } }
    }
}
