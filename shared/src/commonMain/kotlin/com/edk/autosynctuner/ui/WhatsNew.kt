package com.edk.autosynctuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.Changelog
import com.edk.autosynctuner.data.KeyValueStore
import com.edk.autosynctuner.data.Release

/**
 * Decides whether to show "What's new" at launch: only on the first run after an update,
 * never on a fresh install. Returns the releases to show (empty = nothing to show) and
 * records that this version has been seen.
 */
fun whatsNewAtLaunch(store: KeyValueStore, currentVersion: String): List<Release> {
    val seen = store.get("seenVersion")
    store.put("seenVersion", currentVersion)
    if (seen == null) {
        // First run of a version with this feature. Someone who already used the app
        // (they have a saved email) is updating from an older version: show this release.
        // A brand-new install has nothing saved yet, so it gets no popup.
        return if (store.get("email") != null) Changelog.since("0.5.0", currentVersion) else emptyList()
    }
    return Changelog.since(seen, currentVersion)
}

@Composable
fun WhatsNewDialog(releases: List<Release>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                releases.forEach { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Version ${r.version}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        r.changes.forEach { c ->
                            Row {
                                Text("•", Modifier.padding(end = 8.dp))
                                Text(c, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}
