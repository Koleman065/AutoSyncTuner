package com.edk.autosynctuner.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Set by App while the demo is running: closes the demo and returns to sign-in. Null otherwise. */
val LocalExitDemo = compositionLocalOf<(() -> Unit)?> { null }

/** Shown at the bottom of every screen during the demo so it's never mistaken for a real truck. */
@Composable
fun DemoBar() {
    val exit = LocalExitDemo.current ?: return
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Science, null)
            Text(
                "Demo mode · sample data, nothing is sent",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            TextButton(onClick = exit) { Text("Exit demo") }
        }
    }
}
