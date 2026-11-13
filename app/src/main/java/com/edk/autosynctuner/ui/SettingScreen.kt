package com.edk.autosynctuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.data.NewValue
import com.edk.autosynctuner.data.Editor
import com.edk.autosynctuner.data.NotLoggedInException
import com.edk.autosynctuner.data.SettingPage
import kotlinx.coroutines.launch

private data class Outcome(val ok: Boolean, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    client: AutoSyncClient,
    vin: String,
    id: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val loader = rememberLoad(vin, id, onSignedOut = onSignedOut) { client.setting(vin, id) }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                val t = @Suppress("UNCHECKED_CAST") (loader.state as? Load.Ready<SettingPage>)?.value?.title ?: fallbackTitle
                Text(t, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        LoadView(loader, Modifier.padding(pad)) { loaded ->
            SettingBody(client, vin, loaded, Modifier.padding(pad), onSignedOut)
        }
    }
}

@Composable
private fun SettingBody(
    client: AutoSyncClient,
    vin: String,
    loaded: SettingPage,
    modifier: Modifier,
    onSignedOut: () -> Unit,
) {
    // `page` is replaced by whatever the website sends back after Save / Restore.
    var page by remember(loaded) { mutableStateOf(loaded) }
    var draft by remember(loaded) { mutableStateOf(Drafts.initial(loaded)) }
    var enabled by remember(loaded) { mutableStateOf(loaded.enabled) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember(loaded) { mutableStateOf<Outcome?>(null) }
    var confirmSave by remember { mutableStateOf<NewValue?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    // Pages with safety warnings (e.g. surveillance mode) start fully expanded so they can't be missed.
    var showAllText by remember(loaded) {
        mutableStateOf(loaded.description.any { Regex("DANGER|WARNING|CAUTION|IMPORTANT", RegexOption.IGNORE_CASE).containsMatchIn(it) })
    }
    val scope = rememberCoroutineScope()

    val validation = Drafts.validate(page, draft)
    val stockDraft = Drafts.stock(page)

    fun launchAction(action: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                action()
            } catch (e: NotLoggedInException) {
                onSignedOut()
            } catch (e: Exception) {
                outcome = Outcome(false, friendlyError(e))
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---- Facts ---------------------------------------------------------------------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Fact("Status", if (page.isCustomized) "Customized" else "Stock (not customized)")
                page.stockValue?.let { Fact("Stock value", stockLabel(page, stockDraft) ?: it) }
                page.range?.let { Fact("Range", it) }
            }
        }

        // ---- Description ---------------------------------------------------------------
        if (page.description.isNotEmpty()) {
            val text = page.description.joinToString("\n\n")
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (showAllText) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.length > 300 || page.description.size > 3) {
                TextButton(onClick = { showAllText = !showAllText }) {
                    Text(if (showAllText) "Show less" else "Read full description")
                }
            }
        }

        HorizontalDivider()

        // ---- Editor --------------------------------------------------------------------
        when (val e = page.editor) {
            is Editor.Radio -> ChoiceEditor(e.choices, (draft as Draft.Choice).value) { draft = Draft.Choice(it) }
            is Editor.Enum -> ChoiceEditor(e.choices, (draft as Draft.Choice).value) { draft = Draft.Choice(it) }
            is Editor.Number -> NumberEditor(e, (draft as Draft.Number).text) { draft = Draft.Number(it) }
            is Editor.Grid -> GridEditor(e, (draft as Draft.Grid).cells) { draft = Draft.Grid(it) }
            is Editor.Unsupported -> Text(
                "This setting uses a layout the app doesn't support yet (${e.reason}). Use the website for this one.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (!page.enabledIsFixed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Setting enabled", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "When off, the custom value is kept on the website but not applied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
        }

        validation.exceptionOrNull()?.let {
            if (page.editor !is Editor.Unsupported) Text(it.message.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        // ---- Actions -------------------------------------------------------------------
        Button(
            onClick = { confirmSave = validation.getOrNull() },
            enabled = !busy && validation.isSuccess,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (page.isCustomized) "Save changes" else "Save custom value")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stockDraft != null) {
                OutlinedButton(onClick = { draft = stockDraft }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Fill in stock value")
                }
            }
            if (page.isCustomized) {
                OutlinedButton(onClick = { confirmRestore = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Remove customization")
                }
            }
        }

        outcome?.let { o ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (o.ok) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (o.ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null)
                    Text(o.text)
                }
            }
        }

        Text(
            "Changes are saved to your AutoSync account on the website. Your AutoSync module downloads them " +
                "the next time it syncs over Wi-Fi, the same as when you use the website.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
    }

    // ---- Confirm: save -------------------------------------------------------------------
    confirmSave?.let { value ->
        val newText = Drafts.describe(page, draft)
        val oldText = if (page.isCustomized) Drafts.describe(page, Drafts.initial(page)) else null
        AlertDialog(
            onDismissRequest = { confirmSave = null },
            title = { Text("Save this setting?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(page.title, fontWeight = FontWeight.SemiBold)
                    if (oldText != null) Text("Currently: $oldText")
                    Text("New value: $newText", fontWeight = FontWeight.SemiBold)
                    page.stockValue?.let { Text("Stock: ${stockLabel(page, stockDraft) ?: it}") }
                    if (!page.enabledIsFixed) Text("Setting enabled: ${if (enabled) "Yes" else "No"}")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = null
                    launchAction {
                        val after = client.save(vin, page, value, enabled)
                        page = after
                        draft = Drafts.initial(after)
                        enabled = after.enabled
                        val stuck = Drafts.matches(after, value)
                        val siteMsg = after.messages.joinToString(" ").ifBlank { null }
                        outcome = if (stuck) {
                            Outcome(true, "Saved. The website now shows: ${Drafts.describe(after, Drafts.initial(after))}" +
                                (siteMsg?.let { "\n$it" } ?: ""))
                        } else {
                            Outcome(false, "The website did not keep the new value." +
                                (siteMsg?.let { " It said: $it" } ?: " Check this setting on the website."))
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = null }) { Text("Cancel") } },
        )
    }

    // ---- Confirm: remove customization ---------------------------------------------------
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Remove customization?") },
            text = {
                Text("This is the same as pressing Delete on the website. \"${page.title}\" goes back to the stock value" +
                    (page.stockValue?.let { " (${stockLabel(page, stockDraft) ?: it})" } ?: "") + ".")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    launchAction {
                        val after = runCatching { client.delete(vin, page) }.getOrNull() ?: client.setting(vin, page.id)
                        val fresh = if (after.isCustomized) client.setting(vin, page.id) else after
                        page = fresh
                        draft = Drafts.initial(fresh)
                        enabled = fresh.enabled
                        outcome = if (!fresh.isCustomized) Outcome(true, "Customization removed. This setting is back to stock.")
                        else Outcome(false, "The website still shows a custom value. Check it on the website.")
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Shows grid stock values as lamp names etc. instead of raw [[0.0, 1.0, …]]. */
private fun stockLabel(page: SettingPage, stock: Draft?): String? =
    if (page.editor is Editor.Grid && stock != null) Drafts.describe(page, stock) else null
