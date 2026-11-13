package com.edk.autosynctuner.ui

import com.edk.autosynctuner.data.NewValue
import com.edk.autosynctuner.data.Editor
import com.edk.autosynctuner.data.Parser
import com.edk.autosynctuner.data.SettingPage

/**
 * The value being edited on screen. Text is kept as typed so half-finished
 * numbers ("-", "12.") don't get mangled; it's validated before saving.
 */
sealed interface Draft {
    data class Choice(val value: String?) : Draft
    data class Number(val text: String) : Draft
    data class Grid(val cells: List<List<String>>) : Draft
    data object None : Draft
}

object Drafts {

    /** Starting value: the saved custom value if there is one, otherwise the stock value. */
    fun initial(page: SettingPage): Draft = when (val e = page.editor) {
        is Editor.Radio -> Draft.Choice(e.current ?: matchLabel(e.choices, page.stockValue))
        is Editor.Enum -> Draft.Choice(e.current ?: matchLabel(e.choices, page.stockValue))
        is Editor.Number -> Draft.Number(
            (e.current ?: leadingNumber(page.stockValue))?.let { Parser.formatNumber(it) }.orEmpty(),
        )
        is Editor.Grid -> Draft.Grid(e.values.map { row -> row.map { Parser.formatNumber(it) } })
        is Editor.Unsupported -> Draft.None
    }

    /** The stock value expressed as a draft, when the page's "Stock Value" text can be understood. */
    fun stock(page: SettingPage): Draft? = when (val e = page.editor) {
        is Editor.Radio -> matchLabel(e.choices, page.stockValue)?.let { Draft.Choice(it) }
        is Editor.Enum -> matchLabel(e.choices, page.stockValue)?.let { Draft.Choice(it) }
        is Editor.Number -> leadingNumber(page.stockValue)?.let { Draft.Number(Parser.formatNumber(it)) }
        is Editor.Grid -> page.stockValue?.let { Parser.parseNumberMatrix(it) }
            ?.takeIf { m -> m.size == e.values.size && m.indices.all { m[it].size == e.values[it].size } }
            ?.let { m -> Draft.Grid(m.map { row -> row.map { Parser.formatNumber(it) } }) }
        is Editor.Unsupported -> null
    }

    /** Checks the draft against the page's limits. Returns the value to send, or an error message. */
    fun validate(page: SettingPage, draft: Draft): Result<NewValue> = runCatching {
        when (val e = page.editor) {
            is Editor.Radio -> NewValue.Choice((draft as Draft.Choice).value ?: fail("Pick an option"))
            is Editor.Enum -> NewValue.EnumChoice((draft as Draft.Choice).value ?: fail("Pick an option"))
            is Editor.Number -> {
                val v = (draft as Draft.Number).text.trim().toDoubleOrNull() ?: fail("Enter a number")
                checkRange(v, e.min, e.max)
                NewValue.Number(v)
            }
            is Editor.Grid -> {
                val cells = (draft as Draft.Grid).cells.mapIndexed { r, row ->
                    row.mapIndexed { c, t ->
                        val v = t.trim().toDoubleOrNull()
                            ?: fail("${cellName(e, r, c)} needs a number")
                        checkRange(v, e.min, e.max, cellName(e, r, c))
                        v
                    }
                }
                NewValue.Grid(cells)
            }
            is Editor.Unsupported -> fail("This setting can't be edited in the app yet")
        }
    }

    /** Human-readable version of a value, for the confirm dialog and the result message. */
    fun describe(page: SettingPage, draft: Draft): String = when (val e = page.editor) {
        is Editor.Radio -> labelFor(e.choices, (draft as? Draft.Choice)?.value)
        is Editor.Enum -> labelFor(e.choices, (draft as? Draft.Choice)?.value)
        is Editor.Number -> (draft as? Draft.Number)?.text?.ifBlank { "—" } ?: "—"
        is Editor.Grid -> {
            val cells = (draft as? Draft.Grid)?.cells.orEmpty()
            if (e.isBinary && cells.size == 1) {
                val on = cells[0].indices.filter { cells[0][it].trim().toDoubleOrNull() == 1.0 }
                    .map { e.columnHeaders.getOrElse(it) { "#${it + 1}" } }
                if (on.isEmpty()) "none" else on.joinToString(", ")
            } else {
                cells.joinToString(" / ") { it.joinToString(", ") }
            }
        }
        is Editor.Unsupported -> "—"
    }

    /** Does the page the site sent back actually hold the value we submitted? */
    fun matches(page: SettingPage, sent: NewValue): Boolean = when (val e = page.editor) {
        is Editor.Radio -> sent is NewValue.Choice && e.current == sent.value
        is Editor.Enum -> sent is NewValue.EnumChoice && e.current == sent.value
        is Editor.Number -> sent is NewValue.Number && e.current != null &&
            kotlin.math.abs(e.current - sent.value) < 1e-6
        is Editor.Grid -> sent is NewValue.Grid && e.values.size == sent.values.size &&
            e.values.indices.all { r ->
                e.values[r].size == sent.values[r].size &&
                    e.values[r].indices.all { c -> kotlin.math.abs(e.values[r][c] - sent.values[r][c]) < 1e-6 }
            }
        is Editor.Unsupported -> false
    }

    fun cellName(e: Editor.Grid, r: Int, c: Int): String {
        val col = e.columnHeaders.getOrNull(c)?.ifBlank { null } ?: "${c + 1}"
        val row = e.rowHeaders.getOrNull(r)
        return when {
            row != null -> "$row / $col"
            e.values.size > 1 -> "Row ${r + 1} / $col"
            else -> col
        }
    }

    private fun checkRange(v: Double, min: Double?, max: Double?, what: String = "Value") {
        if (min != null && v < min) fail("$what must be at least ${Parser.formatNumber(min)}")
        if (max != null && v > max) fail("$what must be at most ${Parser.formatNumber(max)}")
    }

    private fun labelFor(choices: List<com.edk.autosynctuner.data.Choice>, value: String?): String =
        choices.firstOrNull { it.value == value }?.label ?: "—"

    private fun matchLabel(choices: List<com.edk.autosynctuner.data.Choice>, stock: String?): String? {
        val s = stock?.trim() ?: return null
        return choices.firstOrNull { it.label.equals(s, ignoreCase = true) }?.value
    }

    private fun leadingNumber(text: String?): Double? =
        text?.let { Regex("""^-?\d+(\.\d+)?""").find(it.trim())?.value?.toDoubleOrNull() }

    private fun fail(msg: String): Nothing = throw IllegalArgumentException(msg)
}
