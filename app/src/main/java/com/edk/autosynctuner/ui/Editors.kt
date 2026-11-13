package com.edk.autosynctuner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.Choice
import com.edk.autosynctuner.data.Editor
import com.edk.autosynctuner.data.Parser

/** Yes/No-style radios and dropdown lists both become a simple tappable list. */
@Composable
fun ChoiceEditor(choices: List<Choice>, selected: String?, onSelect: (String) -> Unit) {
    Card {
        Column {
            choices.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = c.value == selected, role = Role.RadioButton) { onSelect(c.value) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = c.value == selected, onClick = null)
                    Text(c.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
                if (i < choices.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
fun NumberEditor(e: Editor.Number, text: String, onChange: (String) -> Unit) {
    val step = e.step?.takeIf { it > 0 }?.let { if (it < 1.0) 1.0 else it } ?: 1.0
    fun bump(dir: Int) {
        val cur = text.trim().toDoubleOrNull() ?: e.min ?: 0.0
        var next = cur + dir * step
        e.min?.let { if (next < it) next = it }
        e.max?.let { if (next > it) next = it }
        onChange(Parser.formatNumber(next))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = { bump(-1) }) { Icon(Icons.Filled.Remove, "Decrease") }
            OutlinedTextField(
                value = text,
                onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }) },
                label = { Text("Custom value") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = { bump(1) }) { Icon(Icons.Filled.Add, "Increase") }
        }
        if (e.min != null || e.max != null) {
            Text(
                "Allowed: ${e.min?.let(Parser::formatNumber) ?: "…"} to ${e.max?.let(Parser::formatNumber) ?: "…"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Replaces the website's spreadsheet-style grid.
 * 0/1 tables (lamp themes and the like) become labeled switches.
 * Number tables become one labeled box per cell, grouped by row.
 */
@Composable
fun GridEditor(e: Editor.Grid, cells: List<List<String>>, onChange: (List<List<String>>) -> Unit) {
    fun set(r: Int, c: Int, v: String) =
        onChange(cells.mapIndexed { ri, row -> if (ri != r) row else row.mapIndexed { ci, x -> if (ci == c) v else x } })

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        e.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        e.rowAxisLabel?.let {
            Text("Rows: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        cells.forEachIndexed { r, row ->
            Card {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (cells.size > 1) {
                        Text(
                            e.rowHeaders.getOrNull(r)?.let { if (it.toIntOrNull() != null) "Row $it" else it } ?: "Row ${r + 1}",
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    row.forEachIndexed { c, value ->
                        val label = e.columnHeaders.getOrNull(c)?.ifBlank { null } ?: "${c + 1}"
                        if (e.isBinary) {
                            val on = value.trim().toDoubleOrNull() == 1.0
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { set(r, c, if (on) "0" else "1") }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = on, onCheckedChange = { set(r, c, if (it) "1" else "0") })
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(columnLabel(e, label), Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge)
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { v -> set(r, c, v.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }) },
                                    singleLine = true,
                                    isError = value.trim().toDoubleOrNull()?.let { v ->
                                        (e.min != null && v < e.min) || (e.max != null && v > e.max)
                                    } ?: true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(130.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (!e.isBinary && (e.min != null || e.max != null)) {
            Text(
                "Each cell: ${e.min?.let(Parser::formatNumber) ?: "…"} to ${e.max?.let(Parser::formatNumber) ?: "…"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Plain numbered columns read better with a prefix ("Column 3" rather than "3"). */
private fun columnLabel(e: Editor.Grid, header: String): String =
    if (header.toDoubleOrNull() != null && e.title != null) "${e.title} $header" else header
