package com.edk.autosynctuner

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.ui.App as AutoSyncApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        setContent { AutoSyncApp(app.client, app.store) }
    }
}

class App : Application() {
    val store by lazy { AndroidStore(this) }
    val client by lazy { AutoSyncClient(store) }
}
