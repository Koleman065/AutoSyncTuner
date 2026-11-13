package com.edk.autosynctuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.ui.AppNav
import com.edk.autosynctuner.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val client = (application as App).client
        setContent { AppTheme { AppNav(client) } }
    }
}

class App : android.app.Application() {
    val client: AutoSyncClient by lazy { AutoSyncClient(this) }
}
