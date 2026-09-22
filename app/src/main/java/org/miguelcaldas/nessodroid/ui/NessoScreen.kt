package org.miguelcaldas.nessodroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.miguelcaldas.nessodroid.model.NessoStatus
import org.miguelcaldas.nessodroid.transport.BleLinkState
import org.miguelcaldas.nessodroid.transport.NessoBleDevice

private data class CommandPreset(val label: String, val command: String)

private val commandPresets = listOf(
    CommandPreset("Ping", "p"),
    CommandPreset("Hello", "h"),
    CommandPreset("Status", "s"),
    CommandPreset("Visuals on", "visuals on"),
    CommandPreset("Visuals off", "visuals off"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NessoScreen(state: NessoUiState, onTransportSelected: (TransportMode) -> Unit, onHttpEndpointChanged: (String) -> Unit, onRefreshHttpStatus: () -> Unit, onStartBleScan: () -> Unit, onStopBleScan: () -> Unit, onConnectBle: (NessoBleDevice) -> Unit, onDisconnectBle: () -> Unit, onCommandChanged: (String) -> Unit, onPresetSelected: (String) -> Unit, onSendCommand: () -> Unit, onClearLog: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NessoDroid", fontWeight = FontWeight.SemiBold)
                        Text(connectionCaption(state), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
            TabRow(selectedTabIndex = state.transport.ordinal) {
                TransportMode.entries.forEach { transport ->
                    Tab(selected = state.transport == transport, onClick = { onTransportSelected(transport) }, text = { Text(transport.name) })
                }
            }

            Spacer(Modifier.height(14.dp))
            when (state.transport) {
                TransportMode.HTTP -> HttpPanel(state, onHttpEndpointChanged, onRefreshHttpStatus)
                TransportMode.BLE -> BlePanel(state, onStartBleScan, onStopBleScan, onConnectBle, onDisconnectBle)
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Text("Quick commands", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(commandPresets) { preset ->
                    OutlinedButton(onClick = { onPresetSelected(preset.command) }, enabled = !state.busy) {
                        Text(preset.label)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = state.command, onValueChange = onCommandChanged, modifier = Modifier.weight(1f), label = { Text("Command") }, minLines = 1, maxLines = 3, enabled = !state.busy)
                Spacer(Modifier.width(10.dp))
                Button(onClick = onSendCommand, enabled = !state.busy && state.command.isNotBlank(), modifier = Modifier.height(56.dp)) {
                    Text(if (state.busy) "Working" else "Send")
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Activity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearLog, enabled = state.activity.isNotEmpty()) {
                    Text("Clear")
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.activity.isEmpty()) {
                    item {
                        Text("No responses yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
                items(state.activity, key = { it.id }) { entry ->
                    Row(modifier = Modifier.fillMaxWidth().background(if (entry.error) MaterialTheme.colorScheme.errorContainer else Color.Transparent).padding(horizontal = 8.dp, vertical = 7.dp)) {
                        Text(entry.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(entry.source, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                        Text(entry.message, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HttpPanel(state: NessoUiState, onEndpointChanged: (String) -> Unit, onRefresh: () -> Unit) {
    OutlinedTextField(value = state.httpEndpoint, onValueChange = onEndpointChanged, modifier = Modifier.fillMaxWidth(), label = { Text("Nesso address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Device state", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onRefresh, enabled = !state.busy) {
            Text("Refresh")
        }
    }
    state.httpStatus?.let { status ->
        StatusSummary(status)
    } ?: Text("Not queried", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun StatusSummary(status: NessoStatus) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        KeyValue("Node", status.node.ifBlank { "Unknown" })
        KeyValue("Radio", "${status.mode} / ${status.profile} (${if (status.radioReady) "ready" else "not ready"})")
        KeyValue("Peer", status.peer ?: "Not discovered")
        val battery = if (status.batteryPercent == null || status.batteryVoltage == null) status.batteryState else "${status.batteryPercent}% / ${"%.2f".format(status.batteryVoltage)} V / ${status.batteryChargeState}"
        KeyValue("Battery", battery)
        KeyValue("Visuals", if (status.visualsEnabled) "On; ${status.visualTimeoutSeconds} s timeout" else "Off; ${status.visualTimeoutSeconds} s timeout")
    }
}

@Composable
private fun BlePanel(state: NessoUiState, onStartScan: () -> Unit, onStopScan: () -> Unit, onConnect: (NessoBleDevice) -> Unit, onDisconnect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Nearby devices", style = MaterialTheme.typography.titleSmall)
            Text(state.bleMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.bleLinkState == BleLinkState.CONNECTED) {
            OutlinedButton(onClick = onDisconnect) {
                Text("Disconnect")
            }
        } else {
            OutlinedButton(onClick = if (state.bleScanning) onStopScan else onStartScan) {
                Text(if (state.bleScanning) "Stop" else "Scan")
            }
        }
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.bleDevices, key = { it.address }) { device ->
            OutlinedCard(onClick = { onConnect(device) }, enabled = state.bleLinkState == BleLinkState.DISCONNECTED, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.name, fontWeight = FontWeight.SemiBold)
                        Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${device.rssi} dBm", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

private fun connectionCaption(state: NessoUiState): String {
    return when (state.transport) {
        TransportMode.HTTP -> state.httpStatus?.let { "HTTP ${it.node}" } ?: "HTTP not queried"
        TransportMode.BLE -> state.bleMessage
    }
}