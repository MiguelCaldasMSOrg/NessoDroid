package org.miguelcaldas.nessodroid.ui

import android.app.Application
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
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

@MainThread
class NessoViewModel @JvmOverloads constructor(application: Application, private val httpClient: HttpNessoClient = HttpNessoClient(), providedBleClient: BleNessoClient? = null) : AndroidViewModel(application) {
    private val bleClient = providedBleClient ?: BleNessoClient(application, viewModelScope)
    private var sequence = 0L
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
        if (transport != TransportMode.BLE) {
            bleClient.stopScan()
        }
        _uiState.update { it.copy(transport = transport) }
    }

    fun setHttpEndpoint(endpoint: String) {
        _uiState.update { state ->
            if (state.httpEndpoint == endpoint) state else state.copy(httpEndpoint = endpoint, httpStatus = null)
        }
    }

    fun setCommand(command: String) {
        _uiState.update { it.copy(command = command) }
    }

    fun refreshHttpStatus() {
        val endpoint = _uiState.value.httpEndpoint
        launchOperation("HTTP") {
            updateHttpStatus(endpoint, null)
            val status = httpClient.readStatus(endpoint)
            updateHttpStatus(endpoint, status)
            addActivity("HTTP", "Status received from ${status.node}")
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
        if (state.busy) {
            return
        }
        val command = state.command.trim()
        if (command.isEmpty()) {
            addActivity(state.transport.name, "Enter a command first", true)
            return
        }
        launchOperation(state.transport.name) {
            when (state.transport) {
                TransportMode.HTTP -> sendHttpCommand(state.httpEndpoint, command, state.command)
                TransportMode.BLE -> sendBleCommand(command, state.command)
            }
        }
    }

    fun reportBlePermissionDenied() {
        addActivity("BLE", "Nearby-device permission is required", true)
    }

    fun clearLog() {
        _uiState.update { it.copy(activity = emptyList()) }
    }

    private fun launchOperation(source: String, operation: suspend () -> Unit) {
        if (_uiState.value.busy) {
            return
        }
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                addActivity(source, error.userMessage(), true)
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun sendHttpCommand(endpoint: String, command: String, draft: String) {
        val response = httpClient.sendCommand(endpoint, command)
        addActivity("HTTP", "$command -> ${response.status}", !response.accepted)
        if (response.accepted) {
            clearSubmittedCommand(draft)
            if (_uiState.value.httpEndpoint == endpoint) {
                updateHttpStatus(endpoint, null)
                try {
                    updateHttpStatus(endpoint, httpClient.readStatus(endpoint))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    addActivity("HTTP", "Command accepted; status refresh failed: ${error.userMessage()}", true)
                }
            }
        }
    }

    private suspend fun sendBleCommand(command: String, draft: String) {
        val response = bleClient.sendCommand(command)
        addActivity("BLE", "$command -> $response", response != "queued")
        if (response == "queued") {
            clearSubmittedCommand(draft)
        }
    }

    private fun updateHttpStatus(endpoint: String, status: NessoStatus?) {
        _uiState.update { state ->
            if (state.httpEndpoint == endpoint) state.copy(httpStatus = status) else state
        }
    }

    private fun clearSubmittedCommand(draft: String) {
        _uiState.update { state ->
            if (state.command == draft) state.copy(command = "") else state
        }
    }

    private fun addActivity(source: String, message: String, error: Boolean = false) {
        val entry = ActivityEntry(++sequence, LocalTime.now().format(timestamp), source, message, error)
        _uiState.update { state ->
            state.copy(activity = (listOf(entry) + state.activity).take(100))
        }
    }

    private fun Throwable.userMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    }

    override fun onCleared() {
        bleClient.close()
        super.onCleared()
    }
}