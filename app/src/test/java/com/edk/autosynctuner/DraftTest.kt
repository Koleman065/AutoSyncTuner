package com.edk.autosynctuner

import com.edk.autosynctuner.data.NewValue
import com.edk.autosynctuner.data.Parser
import com.edk.autosynctuner.ui.Draft
import com.edk.autosynctuner.ui.Drafts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edit/validate/read-back logic, checked against the same real-page fixtures. */
class DraftTest {
    private val base = "https://autosync.btdieselworks.com/tuning/1GC1KWEY5JF237761/"
    private fun page(id: String, file: String) =
        Parser.parseSetting(id, javaClass.getResource("/fixtures/$file")!!.readText(), "$base$id/")

    @Test fun lampThemeDescribesLampNames() {
        val p = page("359", "setting_359_grid_binary.html")
        assertEquals("Fog, Low Beam, Park, Puddle, Reverse", Drafts.describe(p, Drafts.initial(p)))
        assertEquals("Low Beam, Park, Puddle, Reverse", Drafts.describe(p, Drafts.stock(p)!!))
    }

    @Test fun lampThemeValidatesAndMatches() {
        val p = page("359", "setting_359_grid_binary.html")
        val v = Drafts.validate(p, Drafts.initial(p)).getOrThrow() as NewValue.Grid
        assertEquals("[[0,0,1,0,1,1,1,1,0]]", Parser.formatNumberMatrix(v.values))
        assertTrue(Drafts.matches(p, v))
        val turnOn = NewValue.Grid(listOf(v.values[0].mapIndexed { i, x -> if (i == 8) 1.0 else x }))
        assertFalse(Drafts.matches(p, turnOn))
    }

    @Test fun gridRangeEnforced() {
        val p = page("434", "setting_434_grid_rows.html")
        val d = Drafts.initial(p) as Draft.Grid
        val bad = Draft.Grid(listOf(d.cells[0], d.cells[1].toMutableList().also { it[0] = "300" }))
        val err = Drafts.validate(p, bad).exceptionOrNull()!!.message!!
        assertTrue(err, err.contains("Displayed / 1") && err.contains("215"))
        val blank = Draft.Grid(listOf(d.cells[0].toMutableList().also { it[2] = "" }, d.cells[1]))
        assertTrue(Drafts.validate(p, blank).isFailure)
    }

    @Test fun numberStartsAtStockAndChecksRange() {
        val p = page("13", "setting_13_number.html")
        assertEquals(Draft.Number("30"), Drafts.initial(p))
        assertTrue(Drafts.validate(p, Draft.Number("2.5")).isFailure)
        assertTrue(Drafts.validate(p, Draft.Number("2501")).isFailure)
        assertEquals(NewValue.Number(45.5), Drafts.validate(p, Draft.Number("45.5")).getOrThrow())
    }

    @Test fun enumStockMatchesOptionByLabel() {
        val p = page("6", "setting_6_enum.html")
        assertEquals(Draft.Choice("3"), Drafts.stock(p))
        assertEquals("Low Beam (reduced intensity)", Drafts.describe(p, Drafts.initial(p)))
        assertTrue(Drafts.validate(p, Draft.Choice(null)).isFailure)
    }

    @Test fun radioCurrentAndStock() {
        val p = page("276", "setting_276_radio_custom.html")
        assertEquals(Draft.Choice("1"), Drafts.initial(p))
        assertEquals(Draft.Choice("0"), Drafts.stock(p))
        assertTrue(Drafts.matches(p, NewValue.Choice("1")))
    }
}
