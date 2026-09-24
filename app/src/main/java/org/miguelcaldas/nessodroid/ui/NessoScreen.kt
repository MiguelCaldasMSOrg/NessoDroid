package org.miguelcaldas.nessodroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.miguelcaldas.nessodroid.model.NessoStatus
import org.miguelcaldas.nessodroid.transport.BleLinkState
import org.miguelcaldas.nessodroid.transport.NessoBleDevice

private enum class ControllerPage(val label: String) {
    CONTROL("Control"),
    DEVICE("Device"),
    ACTIVITY("Activity"),
}

private data class CommandPreset(val label: String, val command: String, val icon: ImageVector)

private val commandPresets = listOf(
    CommandPreset("Ping", "p", Icons.Outlined.NetworkPing),
    CommandPreset("Hello", "h", Icons.Outlined.Sensors),
    CommandPreset("Status", "s", Icons.Outlined.Info),
)

@Composable
fun NessoScreen(state: NessoUiState, onTransportSelected: (TransportMode) -> Unit, onHttpEndpointChanged: (String) -> Unit, onRefreshHttpStatus: () -> Unit, onStartBleScan: () -> Unit, onStopBleScan: () -> Unit, onConnectBle: (NessoBleDevice) -> Unit, onDisconnectBle: () -> Unit, onCommandChanged: (String) -> Unit, onSendCommand: () -> Unit, onClearLog: () -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf(ControllerPage.CONTROL) }
    val focus = LocalFocusManager.current
    val controlScroll = rememberLazyListState()
    val deviceScroll = rememberLazyListState()
    val activityScroll = rememberLazyListState()
    Scaffold(modifier = modifier, topBar = {
        ControllerHeader(page, onPageSelected = {
            focus.clearFocus()
            page = it
        })
    }) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding).imePadding(), contentAlignment = Alignment.TopCenter) {
            val scroll = when (page) {
                ControllerPage.CONTROL -> controlScroll
                ControllerPage.DEVICE -> deviceScroll
                ControllerPage.ACTIVITY -> activityScroll
            }
            LazyColumn(state = scroll, modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("controller"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (page) {
                    ControllerPage.CONTROL -> {
                        item(key = "connection-heading") {
                            SectionHeading("Connection")
                            TransportSelector(state.transport, onTransportSelected)
                        }
                        item(key = "connection") {
                            when (state.transport) {
                                TransportMode.HTTP -> HttpConnection(state, onHttpEndpointChanged, onRefreshHttpStatus)
                                TransportMode.BLE -> BleConnection(state, onStartBleScan, onStopBleScan, onDisconnectBle)
                            }
                        }
                        if (state.transport == TransportMode.BLE) {
                            items(state.bleDevices, key = { "device-${it.address}" }) { device ->
                                BleDeviceRow(device, !state.busy && state.bleLinkState == BleLinkState.DISCONNECTED, onConnectBle)
                            }
                        }
                        if (state.transport == TransportMode.HTTP && state.httpStatus != null) {
                            item(key = "snapshot") {
                                DeviceSnapshot(state.httpStatus)
                            }
                        }
                        item(key = "composer") {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 20.dp))
                            OutlinedTextField(value = state.command, onValueChange = onCommandChanged, modifier = Modifier.fillMaxWidth(), label = { Text("Command") }, placeholder = { Text("p", fontFamily = FontFamily.Monospace) }, minLines = 2, maxLines = 5, enabled = !state.busy, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), shape = MaterialTheme.shapes.medium, colors = controllerFieldColors())
                        }
                        item(key = "presets") {
                            CommandPresets(state.busy, onCommandChanged)
                        }
                        item(key = "send") {
                            CommandActions(state, onCommandChanged, onSendCommand)
                        }
                        item(key = "recent-heading") {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeading("Latest response", Modifier.weight(1f))
                                ActionIcon("All activity", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { page = ControllerPage.ACTIVITY })
                            }
                        }
                        if (state.activity.isEmpty()) {
                            item(key = "empty-response") {
                                EmptyState("No responses yet", Icons.Outlined.History)
                            }
                        }
                        items(state.activity.take(3), key = { "recent-${it.id}" }) { entry ->
                            ActivityRow(entry)
                        }
                    }
                    ControllerPage.DEVICE -> {
                        item(key = "device-heading") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    SectionHeading("HTTP telemetry")
                                    Text(state.httpEndpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ActionIcon("Refresh", Icons.Outlined.Refresh, enabled = !state.busy, onClick = onRefreshHttpStatus)
                            }
                        }
                        val status = state.httpStatus
                        if (status == null) {
                            item(key = "no-telemetry") {
                                EmptyState("No device status", Icons.Outlined.Router)
                            }
                        } else {
                            item(key = "device-snapshot") { DeviceSnapshot(status) }
                            item(key = "radio-details") {
                                TelemetryGroup("Radio link", listOf("Modulation" to status.mode, "Profile" to status.profile, "Peer" to (status.peer ?: "Not discovered"), "Radio" to if (status.radioReady) "Ready" else "Not ready"))
                            }
                            item(key = "power-details") {
                                TelemetryGroup("Battery & visuals", listOf("Battery" to (status.batteryPercent?.let { "$it%" } ?: "Unavailable"), "Voltage" to (status.batteryVoltage?.let { "%.2f V".format(it) } ?: "Unavailable"), "Charger" to status.batteryChargeState, "Gauge" to status.batteryState, "Visuals" to if (status.visualsEnabled) "On" else "Off", "Inactivity timeout" to if (status.visualTimeoutSeconds == 0) "Disabled" else "${status.visualTimeoutSeconds} s"))
                            }
                            item(key = "network-details") {
                                TelemetryGroup("Network", listOf("Wi-Fi mode" to status.wifiMode, "Address" to status.wifiAddress.ifBlank { "Unavailable" }, "HTTP service" to if (status.httpReady) "Ready" else "Not ready", "BLE service" to if (status.bleReady) "Ready" else "Not ready"))
                            }
                            item(key = "exercise-details") {
                                val exercises = listOfNotNull("Benchmark".takeIf { status.benchmarkActive }, "Sweep".takeIf { status.sweepActive }, "Survey".takeIf { status.surveyActive })
                                TelemetryGroup("Operations", listOf("Exercise" to exercises.joinToString().ifEmpty { "Idle" }, "Queued commands" to status.queuedCommands.toString()))
                            }
                        }
                    }
                    ControllerPage.ACTIVITY -> {
                        item(key = "activity-heading") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    SectionHeading("Responses")
                                    Text("${state.activity.size} entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ActionIcon("Clear activity", Icons.Outlined.DeleteOutline, enabled = state.activity.isNotEmpty(), onClick = onClearLog)
                            }
                        }
                        if (state.activity.isEmpty()) {
                            item(key = "empty-activity") { EmptyState("No responses yet", Icons.Outlined.History) }
                        }
                        items(state.activity, key = { "activity-${it.id}" }) { entry -> ActivityRow(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControllerHeader(page: ControllerPage, onPageSelected: (ControllerPage) -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text("NessoDroid", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TabRow(selectedTabIndex = page.ordinal, containerColor = MaterialTheme.colorScheme.surface, divider = { HorizontalDivider() }) {
            ControllerPage.entries.forEach { destination ->
                Tab(selected = page == destination, onClick = { onPageSelected(destination) }, modifier = Modifier.testTag("page-${destination.name.lowercase()}")) {
                    Text(destination.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportSelector(transport: TransportMode, onSelected: (TransportMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        TransportMode.entries.forEachIndexed { index, option ->
            SegmentedButton(selected = transport == option, onClick = { onSelected(option) }, shape = SegmentedButtonDefaults.itemShape(index, TransportMode.entries.size, RoundedCornerShape(8.dp)), icon = { Icon(if (option == TransportMode.HTTP) Icons.Outlined.Wifi else Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp)) }, colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primaryContainer, activeContentColor = MaterialTheme.colorScheme.primary, inactiveContainerColor = MaterialTheme.colorScheme.surface, inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant, activeBorderColor = MaterialTheme.colorScheme.primary), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(option.name)
            }
        }
    }
}

@Composable
private fun HttpConnection(state: NessoUiState, onEndpointChanged: (String) -> Unit, onRefresh: () -> Unit) {
    val focus = LocalFocusManager.current
    val refresh = {
        focus.clearFocus()
        onRefresh()
    }
    OutlinedTextField(value = state.httpEndpoint, onValueChange = onEndpointChanged, modifier = Modifier.fillMaxWidth(), label = { Text("Nesso address") }, singleLine = true, enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { if (!state.busy) { refresh() } }), textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), shape = MaterialTheme.shapes.medium, colors = controllerFieldColors(), trailingIcon = { ActionIcon("Refresh", Icons.Outlined.Refresh, enabled = !state.busy, onClick = refresh) })
}

@Composable
private fun DeviceSnapshot(status: NessoStatus) {
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).background(if (status.radioReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                Text(status.node, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
            }
            Text("${status.mode} / ${status.profile}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status.batteryPercent != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${status.batteryPercent}%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Icon(if (status.batteryChargeState == "charging" || status.batteryChargeState == "pre-charge") Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull, contentDescription = "Battery", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BleConnection(state: NessoUiState, onStartScan: () -> Unit, onStopScan: () -> Unit, onDisconnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nearby devices", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (state.bleLinkState != BleLinkState.DISCONNECTED) {
                ActionIcon(if (state.bleLinkState == BleLinkState.CONNECTED) "Disconnect" else "Cancel", Icons.Outlined.Close, onClick = onDisconnect)
            } else {
                TextButton(onClick = if (state.bleScanning) onStopScan else onStartScan, enabled = !state.busy) {
                    Icon(if (state.bleScanning) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.bleScanning) "Stop" else "Scan")
                }
            }
        }
        if (state.bleScanning || state.bleLinkState == BleLinkState.CONNECTING || state.bleLinkState == BleLinkState.DISCOVERING) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(state.bleMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(state.bleMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BleDeviceRow(device: NessoBleDevice, enabled: Boolean, onConnect: (NessoBleDevice) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).clickable(enabled = enabled, role = Role.Button, onClickLabel = "Connect to ${device.name}") { onConnect(device) }.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleSmall)
            Text(device.address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${device.rssi} dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CommandPresets(busy: Boolean, onCommandChanged: (String) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        if (maxWidth < 280.dp * fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                commandPresets.forEach { preset -> PresetButton(preset, busy, onCommandChanged, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                commandPresets.forEach { preset -> PresetButton(preset, busy, onCommandChanged, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PresetButton(preset: CommandPreset, busy: Boolean, onCommandChanged: (String) -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick = { onCommandChanged(preset.command) }, enabled = !busy, modifier = modifier.heightIn(min = 44.dp), shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(preset.icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(preset.label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CommandActions(state: NessoUiState, onCommandChanged: (String) -> Unit, onSend: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        if (maxWidth < 280.dp * fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VisualsPresets(state.busy, onCommandChanged)
                SendButton(state, onSend, Modifier.fillMaxWidth())
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                VisualsPresets(state.busy, onCommandChanged)
                Spacer(Modifier.weight(1f))
                SendButton(state, onSend)
            }
        }
    }
}

@Composable
private fun VisualsPresets(busy: Boolean, onCommandChanged: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionIcon("Visuals on", Icons.Outlined.Visibility, enabled = !busy, onClick = { onCommandChanged("visuals on") })
        ActionIcon("Visuals off", Icons.Outlined.VisibilityOff, enabled = !busy, onClick = { onCommandChanged("visuals off") })
    }
}

@Composable
private fun SendButton(state: NessoUiState, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val canSend = !state.busy && state.command.isNotBlank() && (state.transport == TransportMode.HTTP || state.bleLinkState == BleLinkState.CONNECTED)
    Button(onClick = {
        focus.clearFocus()
        onSend()
    }, enabled = canSend, modifier = modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text("Send")
    }
}

@Composable
private fun TelemetryGroup(title: String, values: List<Pair<String, String>>) {
    val fontScale = LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(title)
        BoxWithConstraints {
            val columnCount = if (maxWidth < 300.dp * fontScale) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                values.chunked(columnCount).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        pair.forEach { (label, value) ->
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    val accent = if (entry.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (entry.error) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, contentDescription = if (entry.error) "Error" else "Response", tint = accent, modifier = Modifier.size(18.dp))
            Text(entry.source, style = MaterialTheme.typography.labelMedium, color = accent, modifier = Modifier.weight(1f))
            Text(entry.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SelectionContainer {
            Text(entry.message, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = if (entry.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        HorizontalDivider()
    }
}

@Composable
private fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}

@Composable
private fun EmptyState(text: String, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionIcon(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun controllerFieldColors() = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
