package com.edk.autosynctuner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.AutoSyncClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(client: AutoSyncClient, onOpen: (vin: String, single: Boolean) -> Unit, onSignedOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoad(Unit, onSignedOut = onSignedOut) { refresh -> client.vehicles(refresh) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Your vehicles") },
            actions = {
                IconButton(onClick = { scope.launch { client.logout(); onSignedOut() } }) {
                    Icon(Icons.AutoMirrored.Filled.Logout, "Sign out")
                }
            },
        )
    }) { pad ->
        LoadView(loader, Modifier.padding(pad)) { vehicles ->
            if (vehicles.size == 1) {
                LaunchedEffect(Unit) { onOpen(vehicles[0].vin, true) }
                return@LoadView
            }
            if (vehicles.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No vehicles on this AutoSync account yet.")
                }
            } else {
                LazyColumn(Modifier.padding(pad)) {
                    items(vehicles, key = { it.vin }) { v ->
                        ListItem(
                            headlineContent = { Text(v.description.ifBlank { v.vin }) },
                            supportingContent = { Text(v.vin) },
                            leadingContent = { Icon(Icons.Filled.LocalShipping, null) },
                            modifier = Modifier.clickable { onOpen(v.vin, false) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
