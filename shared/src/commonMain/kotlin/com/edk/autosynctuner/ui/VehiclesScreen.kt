package com.edk.autosynctuner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.data.Vehicle
import kotlinx.coroutines.launch

/**
 * The vehicle picker. Lists every vehicle on the AutoSync account (tap one to tune it)
 * and, for reference, every AutoSync module registered to the account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    client: AutoSyncClient,
    currentVin: String?,
    autoOpenSingle: Boolean,
    onAutoOpenChecked: () -> Unit,
    onOpen: (Vehicle) -> Unit,
    footer: @Composable () -> Unit,
    onSignedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoad(Unit, onSignedOut = onSignedOut) { refresh -> client.profile(refresh) }

    Scaffold(bottomBar = { DemoBar() }, topBar = {
        TopAppBar(
            title = { Text("Vehicles") },
            actions = {
                IconButton(onClick = loader.reload) { Icon(Icons.Filled.Refresh, "Reload") }
                IconButton(onClick = { scope.launch { client.logout(); onSignedOut() } }) {
                    Icon(Icons.AutoMirrored.Filled.Logout, "Sign out")
                }
            },
        )
    }) { pad ->
        LoadView(loader, Modifier.padding(pad)) { profile ->
            if (autoOpenSingle) {
                LaunchedEffect(profile) {
                    onAutoOpenChecked()
                    profile.vehicles.singleOrNull()?.let(onOpen)
                }
            }
            if (profile.vehicles.isEmpty() && profile.devices.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No vehicles on this AutoSync account yet. Add them on the AutoSync Manager website.")
                }
                return@LoadView
            }
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                item { SectionHeader("Select a vehicle to tune") }
                if (profile.vehicles.isEmpty()) {
                    item { Text("No vehicles yet.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                }
                items(profile.vehicles, key = { "v:${it.vin}" }) { v ->
                    val current = v.vin == currentVin
                    ListItem(
                        headlineContent = {
                            Text(v.description.ifBlank { v.vin }, fontWeight = if (current) FontWeight.SemiBold else null)
                        },
                        supportingContent = { Text(if (current) "${v.vin} · last opened" else v.vin) },
                        leadingContent = { Icon(Icons.Filled.LocalShipping, null) },
                        trailingContent = {
                            if (current) Icon(Icons.Filled.CheckCircle, "Last opened", tint = MaterialTheme.colorScheme.primary)
                            else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        },
                        modifier = Modifier.clickable { onOpen(v) },
                    )
                    HorizontalDivider()
                }

                if (profile.devices.isNotEmpty()) {
                    item { SectionHeader("AutoSync devices on this account") }
                    items(profile.devices, key = { "d:${it.serial}" }) { d ->
                        val needsUpdate = d.note?.contains("update", ignoreCase = true) == true
                        ListItem(
                            headlineContent = { Text("Serial ${d.serial}") },
                            supportingContent = {
                                Text(
                                    listOfNotNull(d.firmware.ifBlank { null }, d.note).joinToString(" · "),
                                    color = if (needsUpdate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingContent = {
                                Icon(if (needsUpdate) Icons.Filled.Warning else Icons.Filled.Memory, null)
                            },
                            colors = ListItemDefaults.colors(),
                        )
                        HorizontalDivider()
                    }
                    item {
                        Text(
                            "Settings are saved per vehicle (by VIN). Module status, such as \"Update required\", " +
                                "is shown as the AutoSync Manager website reports it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item { footer() }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}
