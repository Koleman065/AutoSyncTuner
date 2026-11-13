package com.edk.autosynctuner

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Android's system back button/gesture; a no-op on iOS. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Platform-provided colors (Android 12+ wallpaper colors), or null for the app's own. */
@Composable
expect fun platformColorScheme(dark: Boolean): ColorScheme?
