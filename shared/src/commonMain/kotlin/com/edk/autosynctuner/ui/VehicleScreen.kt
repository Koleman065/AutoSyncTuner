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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.AutoSyncClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    client: AutoSyncClient,
    vin: String,
    onOpenSetting: (id: String, name: String) -> Unit,
    onBack: (() -> Unit)?,
    onSignOut: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoad(vin, onSignedOut = onSignedOut) { refresh -> client.modules(vin, refresh) }
    var selectedModule by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text("Tune Customization"); Text(vin, style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = {
                onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            },
            actions = {
                IconButton(onClick = loader.reload) { Icon(Icons.Filled.Refresh, "Reload") }
                if (onBack == null) {
                    IconButton(onClick = { scope.launch { client.logout(); onSignOut() } }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Sign out")
                    }
                }
            },
        )
    }) { pad ->
        LoadView(loader, Modifier.padding(pad)) { modules ->
            val module = modules.firstOrNull { it.code == selectedModule } ?: modules.firstOrNull()
            Column(Modifier.fillMaxSize().padding(pad)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    modules.forEach { m ->
                        FilterChip(
                            selected = query.isBlank() && m.code == module?.code,
                            onClick = { selectedModule = m.code; query = "" },
                            label = { Text("${m.code} (${m.settingCount})") },
                        )
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
                                supportingContent = { Text("${m.code} · ${c.name}") },
                                modifier = Modifier.clickable { onOpenSetting(s.id, s.name) },
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
                                            .clickable { onOpenSetting(s.id, s.name) }
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
