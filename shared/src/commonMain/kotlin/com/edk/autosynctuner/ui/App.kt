package com.edk.autosynctuner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.edk.autosynctuner.PlatformBackHandler
import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.data.KeyValueStore

/** The app's screens. A plain back stack keeps navigation identical on Android and iOS. */
sealed interface Screen {
    val key: String

    data object Login : Screen { override val key = "login" }
    data object Vehicles : Screen { override val key = "vehicles" }
    data class Vehicle(val vin: String) : Screen { override val key = "vehicle/$vin" }
    data class Setting(val vin: String, val id: String, val name: String) : Screen {
        override val key = "setting/$vin/$id"
    }
}

@Composable
fun App(client: AutoSyncClient, store: KeyValueStore) {
    AppTheme {
        var stack by remember { mutableStateOf(listOf(if (client.hasSession) Screen.Vehicles else Screen.Login)) }
        val saved = rememberSaveableStateHolder()

        fun push(s: Screen) { stack = stack + s }
        fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
        fun reset(s: Screen) {
            stack.forEach { saved.removeState(it.key) }
            stack = listOf(s)
        }
        val signedOut = { client.clearCache(); reset(Screen.Login) }

        PlatformBackHandler(enabled = stack.size > 1) { pop() }

        val screen = stack.last()
        saved.SaveableStateProvider(screen.key) {
            when (screen) {
                Screen.Login -> LoginScreen(client, store) { reset(Screen.Vehicles) }
                Screen.Vehicles -> VehiclesScreen(
                    client = client,
                    onOpen = { vin, single ->
                        if (single) reset(Screen.Vehicle(vin)) else push(Screen.Vehicle(vin))
                    },
                    onSignedOut = signedOut,
                )
                is Screen.Vehicle -> VehicleScreen(
                    client = client,
                    vin = screen.vin,
                    onOpenSetting = { id, name -> push(Screen.Setting(screen.vin, id, name)) },
                    onBack = if (stack.size > 1) ({ pop() }) else null,
                    onSignOut = signedOut,
                    onSignedOut = signedOut,
                )
                is Screen.Setting -> SettingScreen(
                    client = client,
                    vin = screen.vin,
                    id = screen.id,
                    fallbackTitle = screen.name,
                    onBack = { pop() },
                    onSignedOut = signedOut,
                )
            }
        }
    }
}
