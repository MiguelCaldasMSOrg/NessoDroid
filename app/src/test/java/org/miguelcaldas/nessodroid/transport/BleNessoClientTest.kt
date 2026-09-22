package org.miguelcaldas.nessodroid.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE)
class BleNessoClientTest {
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    private val context = mock(Context::class.java)
    private val device = mock(BluetoothDevice::class.java)
    private val gatt = mock(BluetoothGatt::class.java)
    private val command = BluetoothGattCharacteristic(UUID.fromString("7bbf0002-6ba5-4e35-9f1f-8d36a7f34c01"), BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE)
    private val status = BluetoothGattCharacteristic(UUID.fromString("7bbf0003-6ba5-4e35-9f1f-8d36a7f34c01"), BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
    private lateinit var callback: BluetoothGattCallback
    private lateinit var client: BleNessoClient

    @Before
    fun setUp() {
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSystemService(BluetoothManager::class.java)).thenReturn(mock(BluetoothManager::class.java))
        val service = BluetoothGattService(UUID.fromString("7bbf0001-6ba5-4e35-9f1f-8d36a7f34c01"), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(command)
        service.addCharacteristic(status)
        `when`(gatt.getService(service.uuid)).thenReturn(service)
        `when`(gatt.requestMtu(517)).thenReturn(true)
        `when`(gatt.discoverServices()).thenReturn(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            doAnswer { invocation ->
                command.value = invocation.getArgument(1)
                BluetoothStatusCodes.SUCCESS
            }.`when`(gatt).writeCharacteristic(eq(command), any(ByteArray::class.java), eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        } else {
            `when`(gatt.writeCharacteristic(command)).thenReturn(true)
        }
        `when`(gatt.readCharacteristic(status)).thenReturn(true)
        doAnswer { invocation ->
            callback = invocation.getArgument(2)
            gatt
        }.`when`(device).connectGatt(eq(context), eq(false), any(BluetoothGattCallback::class.java), eq(BluetoothDevice.TRANSPORT_LE), eq(BluetoothDevice.PHY_LE_1M_MASK), any(Handler::class.java))
        client = BleNessoClient(context, scope)
    }

    @After
    fun tearDown() {
        client.close()
        scope.cancel()
    }

    @Test
    fun failedMtuNegotiationUsesMinimumPayload() {
        connect(mtu = 517, mtuStatus = BluetoothGatt.GATT_FAILURE)
        val result = scope.async { client.sendCommand("x".repeat(30)) }
        scheduler.runCurrent()

        assertEquals("@begin:30", command.value.toString(Charsets.UTF_8))
        acknowledge("chunk_ready")
        assertEquals(20, command.value.size)
        result.cancel()
        scheduler.runCurrent()
    }

    @Test
    fun negotiatedMtuControlsChunkSize() {
        connect(mtu = 100)
        val result = scope.async { client.sendCommand("x".repeat(100)) }
        scheduler.runCurrent()

        acknowledge("chunk_ready")
        assertEquals(97, command.value.size)
        acknowledge("chunk:91/100")
        assertEquals(15, command.value.size)
        acknowledge("chunk:100/100")
        assertEquals("@end", command.value.toString(Charsets.UTF_8))
        acknowledge("queued")
        assertEquals("queued", result.getCompleted())
    }

    @Test
    fun queueFullIsReturnedWithoutPretendingAcceptance() {
        connect()
        val result = scope.async { client.sendCommand("p") }
        scheduler.runCurrent()

        acknowledge("queue_full")

        assertEquals("queue_full", result.getCompleted())
        assertEquals(BleLinkState.CONNECTED, client.linkState.value)
    }

    @Test
    fun unexpectedChunkAcknowledgementClosesConnection() {
        connect(mtu = 23)
        val result = scope.async { runCatching { client.sendCommand("x".repeat(30)) } }
        scheduler.runCurrent()

        acknowledge("queued")

        assertTrue(result.getCompleted().isFailure)
        assertEquals(BleLinkState.DISCONNECTED, client.linkState.value)
        verify(gatt).close()
    }

    @Test
    fun writeTimeoutInvalidatesConnection() {
        connect()
        val result = scope.async { runCatching { client.sendCommand("p") } }
        scheduler.runCurrent()
        scheduler.advanceTimeBy(5001)
        scheduler.runCurrent()

        assertTrue(result.getCompleted().isFailure)
        assertFalse(result.getCompleted().exceptionOrNull() is CancellationException)
        assertTrue(result.getCompleted().exceptionOrNull()?.message.orEmpty().contains("timed out"))
        assertEquals(BleLinkState.DISCONNECTED, client.linkState.value)
        verify(gatt).close()
    }

    @Test
    fun readTimeoutInvalidatesConnection() {
        connect()
        val result = scope.async { runCatching { client.sendCommand("p") } }
        scheduler.runCurrent()
        callback.onCharacteristicWrite(gatt, command, BluetoothGatt.GATT_SUCCESS)
        scheduler.runCurrent()
        scheduler.advanceTimeBy(5001)
        scheduler.runCurrent()

        assertTrue(result.getCompleted().isFailure)
        assertFalse(result.getCompleted().exceptionOrNull() is CancellationException)
        assertEquals(BleLinkState.DISCONNECTED, client.linkState.value)
        verify(gatt).close()
    }

    @Test
    fun cancellationInvalidatesConnection() {
        connect()
        val result = scope.async { client.sendCommand("p") }
        scheduler.runCurrent()
        result.cancel()
        scheduler.runCurrent()

        assertTrue(result.isCancelled)
        assertEquals(BleLinkState.DISCONNECTED, client.linkState.value)
        verify(gatt).close()
    }

    @Test
    fun wholeCommandDeadlineBoundsOtherwiseSuccessfulChunks() {
        connect(mtu = 23)
        val result = scope.async { runCatching { client.sendCommand("x".repeat(1232)) } }
        scheduler.runCurrent()
        repeat(7) { index ->
            scheduler.advanceTimeBy(4000)
            acknowledge(if (index == 0) "chunk_ready" else "chunk:${index * 14}/1232")
        }
        assertFalse(result.isCompleted)

        scheduler.advanceTimeBy(2001)
        scheduler.runCurrent()

        assertTrue(result.getCompleted().isFailure)
        assertFalse(result.getCompleted().exceptionOrNull() is CancellationException)
        assertTrue(result.getCompleted().exceptionOrNull()?.message.orEmpty().contains("timed out"))
        assertEquals(BleLinkState.DISCONNECTED, client.linkState.value)
    }

    @Test
    fun lateCallbackCannotCompleteNewConnectionOperation() {
        connect()
        val staleGatt = mock(BluetoothGatt::class.java)
        val result = scope.async { client.sendCommand("p") }
        scheduler.runCurrent()

        callback.onCharacteristicWrite(staleGatt, command, BluetoothGatt.GATT_SUCCESS)
        completeRead(staleGatt, "queued")
        scheduler.runCurrent()
        assertFalse(result.isCompleted)

        acknowledge("queued")
        assertEquals("queued", result.getCompleted())
    }

    @Test
    fun serviceDiscoveryFailureDoesNotWaitForConnectionTimeout() {
        `when`(gatt.discoverServices()).thenReturn(false)
        client.connect(NessoBleDevice("Nesso-test", "00:11:22:33:44:55", -50, device))
        callback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        callback.onMtuChanged(gatt, 517, BluetoothGatt.GATT_SUCCESS)

        assertEquals(BleLinkState.DISCONNECTED, client.linkState.value)
        verify(gatt).close()
    }

    @Test
    fun connectionTimeoutClosesGatt() {
        client.connect(NessoBleDevice("Nesso-test", "00:11:22:33:44:55", -50, device))
        scheduler.advanceTimeBy(12001)
        scheduler.runCurrent()

        assertEquals(BleLinkState.DISCONNECTED, client.linkState.value)
        verify(gatt).close()
    }

    @Test
    fun revokedPermissionCannotPreventCleanup() {
        connect()
        doThrow(SecurityException("Permission revoked")).`when`(gatt).disconnect()

        client.disconnect()

        assertEquals(BleLinkState.DISCONNECTED, client.linkState.value)
        verify(gatt).close()
    }

    private fun connect(mtu: Int = 517, mtuStatus: Int = BluetoothGatt.GATT_SUCCESS) {
        client.connect(NessoBleDevice("Nesso-test", "00:11:22:33:44:55", -50, device))
        callback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        callback.onMtuChanged(gatt, mtu, mtuStatus)
        callback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
        assertEquals(BleLinkState.CONNECTED, client.linkState.value)
    }

    private fun acknowledge(response: String) {
        callback.onCharacteristicWrite(gatt, command, BluetoothGatt.GATT_SUCCESS)
        scheduler.runCurrent()
        completeRead(gatt, response)
        scheduler.runCurrent()
    }

    private fun completeRead(currentGatt: BluetoothGatt, response: String) {
        val value = response.toByteArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            callback.onCharacteristicRead(currentGatt, status, value, BluetoothGatt.GATT_SUCCESS)
        } else {
            status.value = value
            callback.onCharacteristicRead(currentGatt, status, BluetoothGatt.GATT_SUCCESS)
        }
    }
}