package com.edk.autosynctuner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalUriHandler
import com.edk.autosynctuner.data.ModuleLicense
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.AutoSyncClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    client: AutoSyncClient,
    vin: String,
    vehicleName: String,
    onOpenSetting: (id: String, name: String, license: ModuleLicense?) -> Unit,
    onSwitchVehicle: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val loader = rememberLoad(vin, onSignedOut = onSignedOut) { refresh -> client.vehicle(vin, refresh) }
    var selectedModule by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }

    Scaffold(topBar = {
        TopAppBar(
            // Tapping the vehicle name opens the vehicle picker.
            title = {
                Column(Modifier.clickable(onClick = onSwitchVehicle)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(vehicleName.ifBlank { "Tune Customization" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    Text(vin, style = MaterialTheme.typography.labelSmall)
                }
            },
            navigationIcon = {
                IconButton(onClick = onSwitchVehicle) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vehicles") }
            },
            actions = {
                IconButton(onClick = onSwitchVehicle) { Icon(Icons.Filled.SwapHoriz, "Switch vehicle") }
                IconButton(onClick = loader.reload) { Icon(Icons.Filled.Refresh, "Reload") }
            },
        )
    }) { pad ->
        LoadView(loader, Modifier.padding(pad)) { vehicle ->
            val modules = vehicle.modules
            val module = modules.firstOrNull { it.code == selectedModule } ?: modules.firstOrNull()
            val uriHandler = LocalUriHandler.current
            Column(Modifier.fillMaxSize().padding(pad)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    modules.forEach { m ->
                        val lic = vehicle.licenseFor(m.code)
                        FilterChip(
                            selected = query.isBlank() && m.code == module?.code,
                            onClick = { selectedModule = m.code; query = "" },
                            label = { Text("${m.code} (${m.settingCount})") },
                            // Same information as the website's "Tune Status" list:
                            // a check for "Licensed - Ready", a lock for "Get License".
                            leadingIcon = when {
                                lic == null -> null
                                lic.licensed -> {
                                    { Icon(Icons.Filled.CheckCircle, "Licensed", Modifier.size(FilterChipDefaults.IconSize), tint = LicensedGreen) }
                                }
                                else -> {
                                    { Icon(Icons.Filled.Lock, "Not licensed", Modifier.size(FilterChipDefaults.IconSize)) }
                                }
                            },
                        )
                    }
                }
                // Status line for the selected module.
                if (query.isBlank() && module != null) {
                    vehicle.licenseFor(module.code)?.let { lic ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (lic.licensed) Icons.Filled.CheckCircle else Icons.Filled.Lock, null,
                                Modifier.size(16.dp),
                                tint = if (lic.licensed) LicensedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                listOf(lic.description.ifBlank { lic.code }, if (lic.licensed) lic.status else "Not licensed")
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (lic.licensed) LicensedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!lic.licensed && lic.licenseUrl != null) {
                                TextButton(onClick = { runCatching { uriHandler.openUri(lic.licenseUrl) } }) {
                                    Text("Get license")
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp).padding(start = 2.dp))
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search all ${modules.sumOf { it.settingCount }} settings") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )

                LazyColumn(Modifier.fillMaxSize()) {
                    if (query.isNotBlank()) {
                        val q = query.trim()
                        val hits = modules.flatMap { m ->
                            m.categories.flatMap { c ->
                                c.settings.filter { s -> s.name.contains(q, true) || c.name.contains(q, true) }
                                    .map { Triple(m, c, it) }
                            }
                        }
                        if (hits.isEmpty()) item { Text("No matches", Modifier.padding(24.dp)) }
                        items(hits, key = { "${it.first.code}/${it.second.name}/${it.third.id}" }) { (m, c, s) ->
                            ListItem(
                                headlineContent = { Text(s.name) },
                                supportingContent = {
                                    val locked = vehicle.licenseFor(m.code)?.licensed == false
                                    Text("${m.code} · ${c.name}" + if (locked) " · not licensed" else "")
                                },
                                modifier = Modifier.clickable { onOpenSetting(s.id, s.name, vehicle.licenseFor(m.code)) },
                            )
                            HorizontalDivider()
                        }
                    } else if (module != null) {
                        module.categories.forEach { cat ->
                            val key = "${module.code}/${cat.name}"
                            val open = key in expanded
                            item(key = "h:$key") {
                                ListItem(
                                    headlineContent = { Text(cat.name, style = MaterialTheme.typography.titleMedium) },
                                    supportingContent = { Text("${cat.settings.size} settings") },
                                    trailingContent = {
                                        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                                    },
                                    modifier = Modifier.clickable {
                                        expanded = if (open) expanded - key else expanded + key
                                    },
                                )
                                HorizontalDivider()
                            }
                            if (open) {
                                items(cat.settings, key = { "s:$key/${it.id}" }) { s ->
                                    ListItem(
                                        headlineContent = { Text(s.name) },
                                        modifier = Modifier
                                            .clickable { onOpenSetting(s.id, s.name, vehicle.licenseFor(module.code)) }
                                            .padding(start = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
