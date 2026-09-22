package org.miguelcaldas.nessodroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.miguelcaldas.nessodroid.model.NessoStatus
import org.miguelcaldas.nessodroid.transport.BleLinkState
import org.miguelcaldas.nessodroid.transport.BleNessoClient
import org.miguelcaldas.nessodroid.transport.HttpNessoClient
import org.miguelcaldas.nessodroid.transport.NessoBleDevice

enum class TransportMode {
    HTTP,
    BLE,
}

data class ActivityEntry(
    val id: Long,
    val time: String,
    val source: String,
    val message: String,
    val error: Boolean = false,
)

data class NessoUiState(
    val transport: TransportMode = TransportMode.HTTP,
    val httpEndpoint: String = "http://192.168.4.1",
    val httpStatus: NessoStatus? = null,
    val bleDevices: List<NessoBleDevice> = emptyList(),
    val bleLinkState: BleLinkState = BleLinkState.DISCONNECTED,
    val bleMessage: String = "Not connected",
    val bleScanning: Boolean = false,
    val command: String = "",
    val busy: Boolean = false,
    val activity: List<ActivityEntry> = emptyList(),
)

class NessoViewModel(application: Application) : AndroidViewModel(application) {
    private val httpClient = HttpNessoClient()
    private val bleClient = BleNessoClient(application, viewModelScope)
    private val sequence = AtomicLong()
    private val timestamp = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val _uiState = MutableStateFlow(NessoUiState())
    val uiState: StateFlow<NessoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bleClient.devices.collect { devices ->
                _uiState.update { it.copy(bleDevices = devices) }
            }
        }
        viewModelScope.launch {
            bleClient.linkState.collect { linkState ->
                _uiState.update { it.copy(bleLinkState = linkState) }
            }
        }
        viewModelScope.launch {
            bleClient.message.collect { message ->
                _uiState.update { it.copy(bleMessage = message) }
            }
        }
        viewModelScope.launch {
            bleClient.scanning.collect { scanning ->
                _uiState.update { it.copy(bleScanning = scanning) }
            }
        }
    }

    fun selectTransport(transport: TransportMode) {
        _uiState.update { it.copy(transport = transport) }
    }

    fun setHttpEndpoint(endpoint: String) {
        _uiState.update { it.copy(httpEndpoint = endpoint) }
    }

    fun setCommand(command: String) {
        _uiState.update { it.copy(command = command) }
    }

    fun refreshHttpStatus() {
        val endpoint = _uiState.value.httpEndpoint
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            runCatching { httpClient.readStatus(endpoint) }
                .onSuccess { status ->
                    _uiState.update { it.copy(httpStatus = status) }
                    addActivity("HTTP", "Status received from ${status.node}")
                }
                .onFailure { error -> addActivity("HTTP", error.userMessage(), true) }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun startBleScan() {
        runCatching { bleClient.startScan() }
            .onFailure { error -> addActivity("BLE", error.userMessage(), true) }
    }

    fun stopBleScan() {
        bleClient.stopScan()
    }

    fun connectBle(device: NessoBleDevice) {
        runCatching { bleClient.connect(device) }
            .onSuccess { addActivity("BLE", "Connecting to ${device.name}") }
            .onFailure { error -> addActivity("BLE", error.userMessage(), true) }
    }

    fun disconnectBle() {
        bleClient.disconnect()
        addActivity("BLE", "Disconnected")
    }

    fun sendCommand() {
        val state = _uiState.value
        val command = state.command.trim()
        if (command.isEmpty()) {
            addActivity(state.transport.name, "Enter a command first", true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            when (state.transport) {
                TransportMode.HTTP -> sendHttpCommand(state.httpEndpoint, command)
                TransportMode.BLE -> sendBleCommand(command)
            }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun reportBlePermissionDenied() {
        addActivity("BLE", "Nearby-device permission is required", true)
    }

    fun clearLog() {
        _uiState.update { it.copy(activity = emptyList()) }
    }

    private suspend fun sendHttpCommand(endpoint: String, command: String) {
        runCatching { httpClient.sendCommand(endpoint, command) }
            .onSuccess { response ->
                addActivity("HTTP", "$command -> ${response.status}", !response.accepted)
                if (response.accepted) {
                    _uiState.update { it.copy(command = "") }
                    runCatching { httpClient.readStatus(endpoint) }
                        .onSuccess { status -> _uiState.update { it.copy(httpStatus = status) } }
                        .onFailure { error -> addActivity("HTTP", "Command accepted; status refresh failed: ${error.userMessage()}", true) }
                }
            }
            .onFailure { error -> addActivity("HTTP", error.userMessage(), true) }
    }

    private suspend fun sendBleCommand(command: String) {
        runCatching { bleClient.sendCommand(command) }
            .onSuccess { response ->
                addActivity("BLE", "$command -> $response", response.isIngressFailure())
                if (!response.isIngressFailure()) {
                    _uiState.update { it.copy(command = "") }
                }
            }
            .onFailure { error -> addActivity("BLE", error.userMessage(), true) }
    }

    private fun addActivity(source: String, message: String, error: Boolean = false) {
        val entry = ActivityEntry(sequence.incrementAndGet(), LocalTime.now().format(timestamp), source, message, error)
        _uiState.update { state ->
            state.copy(activity = (listOf(entry) + state.activity).take(100))
        }
    }

    private fun Throwable.userMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    }

    private fun String.isIngressFailure(): Boolean {
        return startsWith("invalid_") || startsWith("no_chunk_") || startsWith("chunk_too_") || startsWith("length_") || this == "empty" || this == "too_long" || this == "full"
    }

    override fun onCleared() {
        bleClient.close()
        super.onCleared()
    }
}