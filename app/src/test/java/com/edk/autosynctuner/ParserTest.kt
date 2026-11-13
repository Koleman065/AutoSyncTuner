package com.edk.autosynctuner

import com.edk.autosynctuner.data.Editor
import com.edk.autosynctuner.data.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the parser against trimmed copies of real AutoSync Manager pages. */
class ParserTest {
    private val base = "https://autosync.btdieselworks.com/tuning/1GC1KWEY5JF237761/"
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    @Test fun vehicleTree() {
        val modules = Parser.parseModules(fixture("vehicle.html"), base)
        assertEquals(listOf("BCM", "IPC"), modules.map { it.code })
        assertEquals(listOf("Approach Lighting", "Daytime Running Lights"), modules[0].categories.map { it.name })
        assertEquals("359", modules[0].categories[0].settings[0].id)
        assertEquals("Approach Lighting Theme", modules[0].categories[0].settings[0].name)
        assertEquals(3, modules[0].settingCount)
    }

    @Test fun binaryGrid() {
        val p = Parser.parseSetting("359", fixture("setting_359_grid_binary.html"), base + "359/")
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
        val p = Parser.parseSetting("434", fixture("setting_434_grid_rows.html"), base + "434/")
        val g = p.editor as Editor.Grid
        assertFalse(g.isBinary)
        assertEquals(listOf("CAN", "Displayed"), g.rowHeaders)
        assertEquals((1..8).map { it.toString() }, g.columnHeaders)
        assertEquals(-40.0, g.min!!, 0.0)
        assertEquals(215.0, g.max!!, 0.0)
        assertEquals(-40.0, g.values[0][0], 0.0)
        assertFalse(p.isCustomized)
        assertEquals("Create", p.submitLabel)
    }

    @Test fun number() {
        val p = Parser.parseSetting("13", fixture("setting_13_number.html"), base + "13/")
        val n = p.editor as Editor.Number
        assertEquals(3.0, n.min!!, 0.0)
        assertEquals(2500.0, n.max!!, 0.0)
        assertNull(n.current)
        assertEquals("30.00 Seconds", p.stockValue)
        assertEquals("3.00 – 2500.00 Seconds", p.range)
    }

    @Test fun enumDropdown() {
        val p = Parser.parseSetting("6", fixture("setting_6_enum.html"), base + "6/")
        val e = p.editor as Editor.Enum
        assertEquals(8, e.choices.size) // the blank "---------" option is skipped
        assertEquals("Dedicated w/ park & turn lights", e.choices.last().label)
        assertNull(e.current)
    }

    @Test fun radioCustomized() {
        val p = Parser.parseSetting("276", fixture("setting_276_radio_custom.html"), base + "276/")
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
