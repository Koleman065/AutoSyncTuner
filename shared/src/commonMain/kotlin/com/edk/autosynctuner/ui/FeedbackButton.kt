package com.edk.autosynctuner.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.AppInfo
import com.edk.autosynctuner.data.Feedback

/** This build's version info, provided by App for the feedback links. */
val LocalAppInfo = staticCompositionLocalOf<AppInfo?> { null }

/**
 * Opens the project's GitHub feedback form with the app version and phone type filled in.
 * Pass [setting] (e.g. "BCM · Approach Lighting Theme") to report a problem with one setting.
 */
@Composable
fun FeedbackButton(label: String = "Send feedback", setting: String? = null, modifier: Modifier = Modifier) {
    val app = LocalAppInfo.current ?: return
    val demo = LocalExitDemo.current != null
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { runCatching { uriHandler.openUri(Feedback.url(app, demo, setting)) } }, modifier = modifier) {
        Icon(Icons.Filled.Feedback, null, Modifier.size(18.dp))
        Text(label, Modifier.padding(start = 6.dp))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp).padding(start = 2.dp))
    }
}
