package com.edk.autosynctuner

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.pm.PackageInfoCompat
import com.edk.autosynctuner.data.AppInfo
import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.ui.App as AutoSyncApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        setContent { AutoSyncApp(app.client, app.store, app.info) }
    }
}

class App : Application() {
    val store by lazy { AndroidStore(this) }
    val client by lazy { AutoSyncClient(store) }
    val info by lazy {
        val pkg = packageManager.getPackageInfo(packageName, 0)
        AppInfo(
            versionName = pkg.versionName ?: "?",
            versionCode = PackageInfoCompat.getLongVersionCode(pkg),
            // Published by the GitHub build next to the APK (see .github/workflows/build.yml).
            updateManifestUrl = "https://github.com/Koleman065/AutoSyncTuner/releases/download/latest/version.json",
        )
    }
}
