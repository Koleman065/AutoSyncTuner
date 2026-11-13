package com.edk.autosynctuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.AppInfo
import com.edk.autosynctuner.data.UpdateChecker
import com.edk.autosynctuner.data.UpdateInfo
import kotlinx.coroutines.launch

/** "Update available" prompt. Download opens the APK in the browser; Android then offers to install it. */
@Composable
fun UpdateDialog(update: UpdateInfo, app: AppInfo, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${update.versionName} (build ${update.versionCode}) is ready. You have ${app.versionName} (build ${app.versionCode}).")
                Text(
                    "Tap Download, then open the downloaded file and tap Update. You stay signed in.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { runCatching { uriHandler.openUri(update.downloadUrl) }; onDismiss() }) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

/** Version line plus a "Check for updates" button (Android); just the version on iOS. */
@Composable
fun VersionFooter(app: AppInfo, checker: UpdateChecker) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<UpdateInfo?>(null) }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "AutoSync Tuner ${app.versionName} (build ${app.versionCode})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (checker.supported) {
            OutlinedButton(enabled = !busy, onClick = {
                busy = true; message = null
                scope.launch {
                    message = try {
                        val u = checker.check()
                        found = u
                        if (u == null) "You have the latest version." else null
                    } catch (e: Exception) {
                        friendlyError(e)
                    }
                    busy = false
                }
            }) { Text(if (busy) "Checking…" else "Check for updates") }
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
    found?.let { UpdateDialog(it, app) { found = null } }
}
