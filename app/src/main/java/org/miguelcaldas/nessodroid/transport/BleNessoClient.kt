package org.miguelcaldas.nessodroid.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class NessoBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    internal val device: BluetoothDevice,
)

enum class BleLinkState {
    DISCONNECTED,
    CONNECTING,
    DISCOVERING,
    CONNECTED,
}

@SuppressLint("MissingPermission")
@MainThread
class BleNessoClient(
    context: Context,
    private val scope: CoroutineScope,
) : Closeable {
    companion object {
        private val SERVICE_UUID = UUID.fromString("7bbf0001-6ba5-4e35-9f1f-8d36a7f34c01")
        private val COMMAND_UUID = UUID.fromString("7bbf0002-6ba5-4e35-9f1f-8d36a7f34c01")
        private val STATUS_UUID = UUID.fromString("7bbf0003-6ba5-4e35-9f1f-8d36a7f34c01")
        private const val SCAN_DURATION_MS = 8000L
        private const val CONNECT_TIMEOUT_MS = 12000L
        private const val GATT_OPERATION_TIMEOUT_MS = 5000L
        private const val COMMAND_TIMEOUT_MS = 30000L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private val operationMutex = Mutex()
    private val _devices = MutableStateFlow<List<NessoBleDevice>>(emptyList())
    private val _linkState = MutableStateFlow(BleLinkState.DISCONNECTED)
    private val _message = MutableStateFlow("Not connected")
    private val _scanning = MutableStateFlow(false)
    val devices: StateFlow<List<NessoBleDevice>> = _devices.asStateFlow()
    val linkState: StateFlow<BleLinkState> = _linkState.asStateFlow()
    val message: StateFlow<String> = _message.asStateFlow()
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingWrite: CompletableDeferred<Unit>? = null
    private var pendingRead: CompletableDeferred<ByteArray>? = null
    private var maxAttributeBytes = 20

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(callbackGatt: BluetoothGatt, status: Int, newState: Int) {
            if (callbackGatt !== gatt) {
                runCatching { callbackGatt.close() }
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("Connection failed (GATT $status)")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _linkState.value = BleLinkState.DISCOVERING
                    _message.value = "Negotiating connection"
                    try {
                        if (!callbackGatt.requestMtu(517)) {
                            discoverServices(callbackGatt)
                        }
                    } catch (error: Exception) {
                        failConnection(error.message ?: "Unable to negotiate BLE connection")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> failConnection("Disconnected")
            }
        }

        override fun onMtuChanged(callbackGatt: BluetoothGatt, mtu: Int, status: Int) {
            if (callbackGatt === gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    maxAttributeBytes = (mtu - 3).coerceIn(20, 512)
                }
                if (_linkState.value == BleLinkState.DISCOVERING) {
                    discoverServices(callbackGatt)
                }
            }
        }

        override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
            if (callbackGatt !== gatt) {
                return
            }
            val service: BluetoothGattService? = callbackGatt.getService(SERVICE_UUID)
            commandCharacteristic = service?.getCharacteristic(COMMAND_UUID)
            statusCharacteristic = service?.getCharacteristic(STATUS_UUID)
            val writable = ((commandCharacteristic?.properties ?: 0) and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
            val readable = ((statusCharacteristic?.properties ?: 0) and BluetoothGattCharacteristic.PROPERTY_READ) != 0
            if (status != BluetoothGatt.GATT_SUCCESS || !writable || !readable) {
                failConnection("Nesso command service not found")
                return
            }
            connectTimeoutJob?.cancel()
            connectTimeoutJob = null
            _linkState.value = BleLinkState.CONNECTED
            _message.value = "Connected"
        }

        override fun onCharacteristicWrite(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (callbackGatt === gatt && characteristic.uuid == COMMAND_UUID) {
                val pending = pendingWrite ?: return
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pending.complete(Unit)
                } else {
                    pending.completeExceptionally(IllegalStateException("BLE write failed (GATT $status)"))
                }
            }
        }

        @Deprecated("Used on Android 12 and earlier")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            completeRead(callbackGatt, characteristic, characteristic.value ?: byteArrayOf(), status)
        }

        override fun onCharacteristicRead(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            completeRead(callbackGatt, characteristic, value, status)
        }
    }

    fun startScan() {
        check(_linkState.value == BleLinkState.DISCONNECTED) { "Disconnect before scanning" }
        stopScan()
        val scanner = adapter?.bluetoothLeScanner ?: throw IllegalStateException("Bluetooth is disabled or unavailable")
        _devices.value = emptyList()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (scanCallback === this) {
                    addScanResult(result)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                if (scanCallback === this) {
                    results.forEach(::addScanResult)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                if (scanCallback === this) {
                    stopScan()
                    _message.value = "BLE scan failed ($errorCode)"
                }
            }
        }
        scanCallback = callback
        _scanning.value = true
        _message.value = "Scanning for Nesso devices"
        try {
            scanner.startScan(callback)
        } catch (error: Exception) {
            stopScan()
            _message.value = error.message ?: "Unable to start BLE scan"
            throw error
        }
        scanJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanCallback?.let { callback ->
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        }
        scanCallback = null
        if (_scanning.value) {
            _message.value = if (_devices.value.isEmpty()) "No Nesso devices found" else "Scan complete"
        }
        _scanning.value = false
    }

    fun connect(device: NessoBleDevice) {
        stopScan()
        disconnect()
        _linkState.value = BleLinkState.CONNECTING
        _message.value = "Connecting to ${device.name}"
        try {
            gatt = checkNotNull(device.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, mainHandler)) { "Unable to start BLE connection" }
            connectTimeoutJob = scope.launch {
                delay(CONNECT_TIMEOUT_MS)
                if (_linkState.value != BleLinkState.CONNECTED) {
                    failConnection("Connection timed out")
                }
            }
        } catch (error: Exception) {
            failConnection(error.message ?: "Unable to start BLE connection")
            throw error
        }
    }

    fun disconnect() {
        failConnection("Not connected")
    }

    suspend fun sendCommand(command: String): String = withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
        operationMutex.withLock {
            check(_linkState.value == BleLinkState.CONNECTED) { "Connect to a Nesso device first" }
            val currentGatt = gatt
            val frames = BleCommandFramer.frames(command, maxAttributeBytes)
            val commandBytes = command.toByteArray(Charsets.UTF_8).size
            try {
                var response = ""
                var sentBytes = 0
                for ((index, frame) in frames.withIndex()) {
                    check(gatt === currentGatt) { "BLE connection changed during command" }
                    writeFrame(frame)
                    response = readIngressStatusInternal()
                    if (index < frames.lastIndex) {
                        val expected = if (index == 0) {
                            "chunk_ready"
                        } else {
                            sentBytes += frame.size - 6
                            "chunk:$sentBytes/$commandBytes"
                        }
                        check(response == expected) { "Unexpected BLE chunk response: $response" }
                    }
                }
                response
            } catch (error: Exception) {
                if (gatt === currentGatt) {
                    failConnection("Command interrupted; reconnect before retrying")
                }
                throw error
            }
        }
    } ?: throw IllegalStateException("BLE command timed out; reconnect before retrying")

    private fun discoverServices(currentGatt: BluetoothGatt) {
        try {
            check(currentGatt.discoverServices()) { "Unable to start BLE service discovery" }
        } catch (error: Exception) {
            failConnection(error.message ?: "BLE service discovery failed")
        }
    }

    private fun addScanResult(result: ScanResult) {
        val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
        if (name?.startsWith("Nesso-") != true) {
            return
        }
        val found = NessoBleDevice(name, result.device.address, result.rssi, result.device)
        _devices.update { current ->
            (current.filterNot { it.address == found.address } + found).sortedBy { it.name }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun writeFrame(value: ByteArray) {
        val currentGatt = checkNotNull(gatt) { "Not connected" }
        val characteristic = checkNotNull(commandCharacteristic) { "Command characteristic unavailable" }
        val completion = CompletableDeferred<Unit>()
        pendingWrite = completion
        try {
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = value
                currentGatt.writeCharacteristic(characteristic)
            }
            check(started) { "Unable to start BLE write" }
            checkNotNull(withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MS) {
                completion.await()
            }) { "BLE write timed out; reconnect before retrying" }
        } finally {
            if (pendingWrite === completion) {
                pendingWrite = null
            }
        }
    }

    private suspend fun readIngressStatusInternal(): String {
        val currentGatt = checkNotNull(gatt) { "Not connected" }
        val characteristic = checkNotNull(statusCharacteristic) { "Status characteristic unavailable" }
        val completion = CompletableDeferred<ByteArray>()
        pendingRead = completion
        return try {
            check(currentGatt.readCharacteristic(characteristic)) { "Unable to start BLE status read" }
            checkNotNull(withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MS) {
                completion.await()
            }) { "BLE status read timed out; reconnect before retrying" }.toString(Charsets.UTF_8)
        } finally {
            if (pendingRead === completion) {
                pendingRead = null
            }
        }
    }

    private fun completeRead(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        if (callbackGatt !== gatt || characteristic.uuid != STATUS_UUID) {
            return
        }
        val pending = pendingRead ?: return
        if (status == BluetoothGatt.GATT_SUCCESS) {
            pending.complete(value)
        } else {
            pending.completeExceptionally(IllegalStateException("BLE read failed (GATT $status)"))
        }
    }

    private fun failConnection(reason: String) {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        failPending(IllegalStateException(reason))
        val currentGatt = gatt
        gatt = null
        commandCharacteristic = null
        statusCharacteristic = null
        maxAttributeBytes = 20
        _linkState.value = BleLinkState.DISCONNECTED
        _message.value = reason
        runCatching { currentGatt?.disconnect() }
        runCatching { currentGatt?.close() }
    }

    private fun failPending(error: Throwable) {
        pendingWrite?.completeExceptionally(error)
        pendingRead?.completeExceptionally(error)
        pendingWrite = null
        pendingRead = null
    }

    override fun close() {
        stopScan()
        disconnect()
    }
}