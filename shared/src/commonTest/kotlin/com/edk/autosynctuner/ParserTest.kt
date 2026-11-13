package com.edk.autosynctuner

import com.edk.autosynctuner.data.Editor
import com.edk.autosynctuner.data.Parser
import com.edk.autosynctuner.data.UpdateChecker
import com.edk.autosynctuner.data.VehicleSettings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/** Runs the parser against trimmed copies of real AutoSync Manager pages. */
class ParserTest {
    private val base = "https://autosync.btdieselworks.com/tuning/1GC1KXEY0JF000001/"

    @Test fun profileVehiclesAndDevices() {
        val p = Parser.parseProfile(Fixtures.PROFILE, "https://autosync.btdieselworks.com/users/me/")
        assertEquals(listOf("1GC1KXEY0JF000001", "1GT12UEY0LF123456"), p.vehicles.map { it.vin })
        assertEquals("2018 CHEVROLET Silverado 2500 4WD 4D 6.6 Diesel", p.vehicles[0].description)
        assertEquals("2018 Chevrolet Silverado 2500", p.vehicles[0].shortName)
        assertEquals("2020 GMC Sierra 2500", p.vehicles[1].shortName)
        assertEquals(2, p.devices.size)
        assertEquals("1A2B3C", p.devices[0].serial)
        assertEquals("AutoSync-v3.95", p.devices[0].firmware)
        assertEquals("Update required", p.devices[0].note)
        assertEquals("AutoSync-v4.10", p.devices[1].firmware)
        assertNull(p.devices[1].note)
    }

    @Test fun moduleLicenses() {
        val l = Parser.parseLicenses(Fixtures.TUNE_STATUS, base)
        assertEquals(setOf("HVAC", "Radio", "HMI", "BCM"), l.keys)
        val bcm = l.getValue("BCM")
        assertTrue(bcm.licensed)
        assertEquals("Licensed - Ready", bcm.status)
        assertEquals("Body Control Module", bcm.description)
        assertNull(bcm.licenseUrl)
        val hmi = l.getValue("HMI")
        assertFalse(hmi.licensed)
        assertEquals("Get License", hmi.status)
        assertEquals("https://www.customcalibrationengineering.com/products/hmi-tuning", hmi.licenseUrl)
        assertEquals("https://www.btdieselworks.com", l.getValue("HVAC").licenseUrl) // site omits https://
        val vs = VehicleSettings(Parser.parseModules(Fixtures.VEHICLE, base), l)
        assertEquals(true, vs.licenseFor("bcm")?.licensed)
        assertNull(vs.licenseFor("IPC")) // module with no Tune Status row
    }

    @Test fun updateManifest() {
        val u = UpdateChecker.parseManifest(
            """{"versionCode": 14, "versionName": "0.4.0", "apkUrl": "https://github.com/x/y/releases/download/latest/AutoSyncTuner.apk"}""",
        )!!
        assertEquals(14L, u.versionCode)
        assertEquals("0.4.0", u.versionName)
        assertNull(UpdateChecker.parseManifest("""{"versionCode": 3, "apkUrl": "http://insecure"}"""))
    }

    @Test fun vehicleTree() {
        val modules = Parser.parseModules(Fixtures.VEHICLE, base)
        assertEquals(listOf("BCM", "IPC"), modules.map { it.code })
        assertEquals(listOf("Approach Lighting", "Daytime Running Lights"), modules[0].categories.map { it.name })
        assertEquals("359", modules[0].categories[0].settings[0].id)
        assertEquals("Approach Lighting Theme", modules[0].categories[0].settings[0].name)
        assertEquals(3, modules[0].settingCount)
    }

    @Test fun binaryGrid() {
        val p = Parser.parseSetting("359", Fixtures.GRID_BINARY_359, base + "359/")
        assertEquals("Approach Lighting - Approach Lighting Theme", p.title)
        val g = p.editor as Editor.Grid
        assertTrue(g.isBinary)
        assertEquals("Lamps", g.title)
        assertEquals(9, g.columnHeaders.size)
        assertEquals("Cornering", g.columnHeaders[0])
        assertEquals(listOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0), g.values[0])
        assertTrue(p.isCustomized)
        assertEquals("Update", p.submitLabel)
        assertTrue(p.enabled)
        assertFalse(p.enabledIsFixed)
        assertEquals("TESTTOKEN", p.csrfToken)
        assertEquals("[[0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0]]", p.stockValue)
        assertTrue(p.description[1].contains("\n")) // <br> kept as line breaks
    }

    @Test fun gridWithRowLabels() {
        val p = Parser.parseSetting("434", Fixtures.GRID_ROWS_434, base + "434/")
        val g = p.editor as Editor.Grid
        assertFalse(g.isBinary)
        assertEquals(listOf("CAN", "Displayed"), g.rowHeaders)
        assertEquals((1..8).map { it.toString() }, g.columnHeaders)
        assertEquals(-40.0, g.min!!, absoluteTolerance = 0.0)
        assertEquals(215.0, g.max!!, absoluteTolerance = 0.0)
        assertEquals(-40.0, g.values[0][0], absoluteTolerance = 0.0)
        assertFalse(p.isCustomized)
        assertEquals("Create", p.submitLabel)
    }

    @Test fun number() {
        val p = Parser.parseSetting("13", Fixtures.NUMBER_13, base + "13/")
        val n = p.editor as Editor.Number
        assertEquals(3.0, n.min!!, absoluteTolerance = 0.0)
        assertEquals(2500.0, n.max!!, absoluteTolerance = 0.0)
        assertNull(n.current)
        assertEquals("30.00 Seconds", p.stockValue)
        assertEquals("3.00 – 2500.00 Seconds", p.range)
    }

    @Test fun enumDropdown() {
        val p = Parser.parseSetting("6", Fixtures.ENUM_6, base + "6/")
        val e = p.editor as Editor.Enum
        assertEquals(8, e.choices.size) // the blank "---------" option is skipped
        assertEquals("Dedicated w/ park & turn lights", e.choices.last().label)
        assertNull(e.current)
    }

    @Test fun radioCustomized() {
        val p = Parser.parseSetting("276", Fixtures.RADIO_276, base + "276/")
        val r = p.editor as Editor.Radio
        assertEquals(listOf("Disabled", "Enabled"), r.choices.map { it.label })
        assertEquals("1", r.current)
        assertTrue(p.enabledIsFixed)
        assertTrue(p.isCustomized)
    }

    @Test fun matrixRoundTrip() {
        val m = Parser.parseNumberMatrix("[[0.0, 1.5], [-40.0, 2.0]]")!!
        assertEquals("[[0,1.5],[-40,2]]", Parser.formatNumberMatrix(m))
        assertNull(Parser.parseNumberMatrix("[[1, x]]"))
    }
}
