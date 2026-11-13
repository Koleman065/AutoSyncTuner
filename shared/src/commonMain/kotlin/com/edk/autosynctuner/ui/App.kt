package com.edk.autosynctuner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.edk.autosynctuner.PlatformBackHandler
import com.edk.autosynctuner.data.AppInfo
import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.data.UpdateChecker
import com.edk.autosynctuner.data.UpdateInfo
import com.edk.autosynctuner.data.KeyValueStore
import com.edk.autosynctuner.data.ModuleLicense

/** The app's screens. A plain back stack keeps navigation identical on Android and iOS. */
sealed interface Screen {
    val key: String

    data object Login : Screen { override val key = "login" }
    data object Vehicles : Screen { override val key = "vehicles" }
    data class Vehicle(val vin: String, val name: String) : Screen { override val key = "vehicle/$vin" }
    data class Setting(val vin: String, val id: String, val name: String, val license: ModuleLicense?) : Screen {
        override val key = "setting/$vin/$id"
    }
}

@Composable
fun App(client: AutoSyncClient, store: KeyValueStore, appInfo: AppInfo) {
    AppTheme {
        // Android: quietly check GitHub for a newer build once per launch.
        val updates = remember { UpdateChecker(appInfo) }
        var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) {
            if (updates.supported) pendingUpdate = runCatching { updates.check() }.getOrNull()
        }
        pendingUpdate?.let { UpdateDialog(it, appInfo) { pendingUpdate = null } }

        // The vehicle picker is always at the bottom of the stack, so Back (or the
        // vehicle name in the title bar) always leads to it. On launch the app reopens
        // the vehicle used last on this phone.
        fun signedInStack(): List<Screen> {
            val last = client.lastVin
            return if (last != null) {
                listOf(Screen.Vehicles, Screen.Vehicle(last, client.lastVehicleName.orEmpty()))
            } else {
                listOf(Screen.Vehicles)
            }
        }

        var stack by remember { mutableStateOf(if (client.hasSession) signedInStack() else listOf(Screen.Login)) }
        // With only one vehicle on the account, skip the picker the first time it appears.
        var autoOpenDone by remember { mutableStateOf(client.lastVin != null) }
        val saved = rememberSaveableStateHolder()
        val scope = rememberCoroutineScope()

        fun push(s: Screen) { stack = stack + s }
        fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
        fun reset(newStack: List<Screen>) {
            stack.forEach { saved.removeState(it.key) }
            stack = newStack
        }
        fun openVehicle(vin: String, name: String) {
            client.lastVin = vin
            client.lastVehicleName = name
            // Replace whatever vehicle was open with the chosen one, on top of the picker.
            stack.filterIsInstance<Screen.Vehicle>().forEach { saved.removeState(it.key) }
            stack = listOf(Screen.Vehicles, Screen.Vehicle(vin, name))
        }
        val signedOut = {
            client.clearCache()
            reset(listOf(Screen.Login))
        }

        PlatformBackHandler(enabled = stack.size > 1) { pop() }

        val screen = stack.last()
        val exitDemo: (() -> Unit)? = if (client.isDemo) ({ scope.launch { client.logout(); signedOut() } }) else null
        CompositionLocalProvider(LocalExitDemo provides exitDemo) {
        saved.SaveableStateProvider(screen.key) {
            when (screen) {
                Screen.Login -> LoginScreen(
                    client = client,
                    store = store,
                    onSignedIn = {
                        client.lastVin = null
                        autoOpenDone = false
                        reset(listOf(Screen.Vehicles))
                    },
                    onTryDemo = {
                        client.startDemo()
                        reset(listOf(Screen.Vehicles))
                    },
                )
                Screen.Vehicles -> VehiclesScreen(
                    client = client,
                    currentVin = client.lastVin,
                    autoOpenSingle = !autoOpenDone,
                    onAutoOpenChecked = { autoOpenDone = true },
                    onOpen = { v -> openVehicle(v.vin, v.shortName) },
                    footer = { VersionFooter(appInfo, updates) },
                    onSignedOut = signedOut,
                )
                is Screen.Vehicle -> VehicleScreen(
                    client = client,
                    vin = screen.vin,
                    vehicleName = screen.name,
                    onOpenSetting = { id, name, license -> push(Screen.Setting(screen.vin, id, name, license)) },
                    onSwitchVehicle = { pop() },
                    onSignedOut = signedOut,
                )
                is Screen.Setting -> SettingScreen(
                    client = client,
                    vin = screen.vin,
                    id = screen.id,
                    fallbackTitle = screen.name,
                    license = screen.license,
                    onBack = { pop() },
                    onSignedOut = signedOut,
                )
            }
        }
        }
    }
}
