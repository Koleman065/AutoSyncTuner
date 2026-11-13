package com.edk.autosynctuner

import com.edk.autosynctuner.data.NewValue
import com.edk.autosynctuner.data.Parser
import com.edk.autosynctuner.ui.Draft
import com.edk.autosynctuner.ui.Drafts
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/** Edit/validate/read-back logic, checked against the same real-page fixtures. */
class DraftTest {
    private val base = "https://autosync.btdieselworks.com/tuning/1GC1KXEY0JF000001/"
    private fun page(id: String, html: String) = Parser.parseSetting(id, html, "$base$id/")

    @Test fun lampThemeDescribesLampNames() {
        val p = page("359", Fixtures.GRID_BINARY_359)
        assertEquals("Fog, Low Beam, Park, Puddle, Reverse", Drafts.describe(p, Drafts.initial(p)))
        assertEquals("Low Beam, Park, Puddle, Reverse", Drafts.describe(p, Drafts.stock(p)!!))
    }

    @Test fun lampThemeValidatesAndMatches() {
        val p = page("359", Fixtures.GRID_BINARY_359)
        val v = Drafts.validate(p, Drafts.initial(p)).getOrThrow() as NewValue.Grid
        assertEquals("[[0,0,1,0,1,1,1,1,0]]", Parser.formatNumberMatrix(v.values))
        assertTrue(Drafts.matches(p, v))
        val turnOn = NewValue.Grid(listOf(v.values[0].mapIndexed { i, x -> if (i == 8) 1.0 else x }))
        assertFalse(Drafts.matches(p, turnOn))
    }

    @Test fun gridRangeEnforced() {
        val p = page("434", Fixtures.GRID_ROWS_434)
        val d = Drafts.initial(p) as Draft.Grid
        val bad = Draft.Grid(listOf(d.cells[0], d.cells[1].toMutableList().also { it[0] = "300" }))
        val err = Drafts.validate(p, bad).exceptionOrNull()!!.message!!
        assertTrue(err.contains("Displayed / 1") && err.contains("215"), err)
        val blank = Draft.Grid(listOf(d.cells[0].toMutableList().also { it[2] = "" }, d.cells[1]))
        assertTrue(Drafts.validate(p, blank).isFailure)
    }

    @Test fun numberStartsAtStockAndChecksRange() {
        val p = page("13", Fixtures.NUMBER_13)
        assertEquals(Draft.Number("30"), Drafts.initial(p))
        assertTrue(Drafts.validate(p, Draft.Number("2.5")).isFailure)
        assertTrue(Drafts.validate(p, Draft.Number("2501")).isFailure)
        assertEquals(NewValue.Number(45.5), Drafts.validate(p, Draft.Number("45.5")).getOrThrow())
    }

    @Test fun enumStockMatchesOptionByLabel() {
        val p = page("6", Fixtures.ENUM_6)
        assertEquals(Draft.Choice("3"), Drafts.stock(p))
        assertEquals("Low Beam (reduced intensity)", Drafts.describe(p, Drafts.initial(p)))
        assertTrue(Drafts.validate(p, Draft.Choice(null)).isFailure)
    }

    @Test fun radioCurrentAndStock() {
        val p = page("276", Fixtures.RADIO_276)
        assertEquals(Draft.Choice("1"), Drafts.initial(p))
        assertEquals(Draft.Choice("0"), Drafts.stock(p))
        assertTrue(Drafts.matches(p, NewValue.Choice("1")))
    }
}
