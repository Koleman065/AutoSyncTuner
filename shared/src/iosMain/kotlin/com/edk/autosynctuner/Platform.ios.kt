package com.edk.autosynctuner

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import com.edk.autosynctuner.data.AppInfo
import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.data.KeyValueStore
import com.edk.autosynctuner.ui.App
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

class IosStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun get(key: String): String? = defaults.stringForKey("autosync.$key")
    override fun put(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey("autosync.$key") else defaults.setObject(value, "autosync.$key")
    }
}

private val store by lazy { IosStore() }
private val client by lazy { AutoSyncClient(store) }

/** iOS updates come through TestFlight / the App Store, so there's no in-app check. */
private val appInfo by lazy {
    val info = NSBundle.mainBundle.infoDictionary
    AppInfo(
        versionName = info?.get("CFBundleShortVersionString") as? String ?: "?",
        versionCode = (info?.get("CFBundleVersion") as? String)?.toLongOrNull() ?: 0L,
        updateManifestUrl = null,
    )
}

/** Called from Swift (iosApp/ContentView.swift). */
fun MainViewController(): UIViewController = ComposeUIViewController { App(client, store, appInfo) }

/** iOS uses the on-screen back arrow; there's no system back button to intercept. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? = null
