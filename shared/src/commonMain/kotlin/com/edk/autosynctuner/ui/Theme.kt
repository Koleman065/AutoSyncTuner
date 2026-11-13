package com.edk.autosynctuner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.edk.autosynctuner.platformColorScheme

private val Red = Color(0xFFC62828)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = platformColorScheme(dark) ?: when {
        dark -> darkColorScheme(primary = Color(0xFFFF8A80), secondary = Color(0xFFB0BEC5))
        else -> lightColorScheme(primary = Red, secondary = Color(0xFF455A64))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
