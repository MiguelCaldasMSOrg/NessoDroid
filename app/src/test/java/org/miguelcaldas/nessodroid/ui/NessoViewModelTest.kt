package org.miguelcaldas.nessodroid.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.miguelcaldas.nessodroid.model.NessoStatusParser
import org.miguelcaldas.nessodroid.transport.BleLinkState
import org.miguelcaldas.nessodroid.transport.BleNessoClient
import org.miguelcaldas.nessodroid.transport.HttpCommandResponse
import org.miguelcaldas.nessodroid.transport.HttpNessoClient
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NessoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val httpClient = mock(HttpNessoClient::class.java)
    private val bleClient = mock(BleNessoClient::class.java)
    private val endpoint = "http://192.168.4.1"
    private val status = NessoStatusParser.parse("""{"node":"Nesso-test"}""")
    private lateinit var viewModel: NessoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        `when`(bleClient.devices).thenReturn(MutableStateFlow(emptyList()))
        `when`(bleClient.linkState).thenReturn(MutableStateFlow(BleLinkState.CONNECTED))
        `when`(bleClient.message).thenReturn(MutableStateFlow("Connected"))
        `when`(bleClient.scanning).thenReturn(MutableStateFlow(false))
        viewModel = NessoViewModel(mock(Application::class.java), httpClient, bleClient)
        store.put("controller", viewModel)
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun duplicateActionsCannotOverlap() = runTest(dispatcher) {
        `when`(httpClient.sendCommand(endpoint, "p")).thenReturn(HttpCommandResponse(true, 202, "queued", ""))
        `when`(httpClient.readStatus(endpoint)).thenReturn(status)
        viewModel.setCommand("p")

        viewModel.sendCommand()
        viewModel.sendCommand()
        viewModel.refreshHttpStatus()
        assertTrue(viewModel.uiState.value.busy)
        dispatcher.scheduler.runCurrent()

        verify(httpClient, times(1)).sendCommand(endpoint, "p")
        verify(httpClient, times(1)).readStatus(endpoint)
        assertFalse(viewModel.uiState.value.busy)
        assertEquals("", viewModel.uiState.value.command)
    }

    @Test
    fun queueFullAndUnknownBleStatusesKeepCommandForRetry() = runTest(dispatcher) {
        viewModel.selectTransport(TransportMode.BLE)
        for (response in listOf("queue_full", "ready", "cancelled", "unknown", "")) {
            `when`(bleClient.sendCommand("p")).thenReturn(response)
            viewModel.setCommand("p")

            viewModel.sendCommand()
            dispatcher.scheduler.runCurrent()

            assertEquals("p", viewModel.uiState.value.command)
            assertTrue(viewModel.uiState.value.activity.first().error)
            assertFalse(viewModel.uiState.value.busy)
        }
    }

    @Test
    fun queuedBleCommandClearsSubmittedDraft() = runTest(dispatcher) {
        `when`(bleClient.sendCommand("p")).thenReturn("queued")
        viewModel.selectTransport(TransportMode.BLE)
        viewModel.setCommand(" p ")

        viewModel.sendCommand()
        dispatcher.scheduler.runCurrent()

        assertEquals("", viewModel.uiState.value.command)
        assertFalse(viewModel.uiState.value.activity.first().error)
    }

    @Test
    fun acceptedResponseCannotEraseEditedCommand() = runTest(dispatcher) {
        `when`(httpClient.sendCommand(endpoint, "p")).thenReturn(HttpCommandResponse(true, 202, "queued", ""))
        `when`(httpClient.readStatus(endpoint)).thenReturn(status)
        viewModel.setCommand("p")

        viewModel.sendCommand()
        viewModel.setCommand("s")
        dispatcher.scheduler.runCurrent()

        assertEquals("s", viewModel.uiState.value.command)
    }

    @Test
    fun oldEndpointResponseCannotPopulateNewEndpoint() = runTest(dispatcher) {
        `when`(httpClient.readStatus(endpoint)).thenReturn(status)

        viewModel.refreshHttpStatus()
        viewModel.setHttpEndpoint("http://192.168.4.2")
        dispatcher.scheduler.runCurrent()

        assertEquals("http://192.168.4.2", viewModel.uiState.value.httpEndpoint)
        assertNull(viewModel.uiState.value.httpStatus)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun changingEndpointClearsPreviousDeviceStatus() = runTest(dispatcher) {
        `when`(httpClient.readStatus(endpoint)).thenReturn(status)
        viewModel.refreshHttpStatus()
        dispatcher.scheduler.runCurrent()
        assertEquals(status, viewModel.uiState.value.httpStatus)

        viewModel.setHttpEndpoint("http://192.168.4.2")

        assertNull(viewModel.uiState.value.httpStatus)
    }

    @Test
    fun failedRefreshClearsStaleStatusAndBusyState() = runTest(dispatcher) {
        `when`(httpClient.readStatus(endpoint)).thenReturn(status).thenAnswer { throw IOException("Offline") }
        viewModel.refreshHttpStatus()
        dispatcher.scheduler.runCurrent()

        viewModel.refreshHttpStatus()
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.uiState.value.httpStatus)
        assertFalse(viewModel.uiState.value.busy)
        assertTrue(viewModel.uiState.value.activity.first().error)
        assertEquals("Offline", viewModel.uiState.value.activity.first().message)
    }

    @Test
    fun httpCancellationIsNotReportedAsDeviceFailure() = runTest(dispatcher) {
        `when`(httpClient.readStatus(endpoint)).thenThrow(CancellationException("Screen closed"))

        viewModel.refreshHttpStatus()
        dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.uiState.value.busy)
        assertTrue(viewModel.uiState.value.activity.isEmpty())
    }

    @Test
    fun bleCancellationIsNotReportedAsDeviceFailure() = runTest(dispatcher) {
        `when`(bleClient.sendCommand("p")).thenThrow(CancellationException("Screen closed"))
        viewModel.selectTransport(TransportMode.BLE)
        viewModel.setCommand("p")

        viewModel.sendCommand()
        dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.uiState.value.busy)
        assertTrue(viewModel.uiState.value.activity.isEmpty())
        assertEquals("p", viewModel.uiState.value.command)
    }

    @Test
    fun bleDeadlineIsReportedAsFailureAndKeepsDraft() = runTest(dispatcher) {
        `when`(bleClient.sendCommand("p")).thenThrow(IllegalStateException("BLE write timed out; reconnect before retrying"))
        viewModel.selectTransport(TransportMode.BLE)
        viewModel.setCommand("p")

        viewModel.sendCommand()
        dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.uiState.value.busy)
        assertTrue(viewModel.uiState.value.activity.first().error)
        assertTrue(viewModel.uiState.value.activity.first().message.contains("timed out"))
        assertEquals("p", viewModel.uiState.value.command)
    }

    @Test
    fun refreshFailureDoesNotUndoAcceptedCommand() = runTest(dispatcher) {
        `when`(httpClient.sendCommand(endpoint, "p")).thenReturn(HttpCommandResponse(true, 202, "queued", ""))
        `when`(httpClient.readStatus(endpoint)).thenAnswer { throw IOException("Offline") }
        viewModel.setCommand("p")

        viewModel.sendCommand()
        dispatcher.scheduler.runCurrent()

        assertEquals("", viewModel.uiState.value.command)
        assertEquals(2, viewModel.uiState.value.activity.size)
        assertTrue(viewModel.uiState.value.activity.first().message.startsWith("Command accepted; status refresh failed:"))
        assertFalse(viewModel.uiState.value.activity.last().error)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun leavingBleTabStopsScanning() {
        viewModel.selectTransport(TransportMode.BLE)

        viewModel.selectTransport(TransportMode.HTTP)

        verify(bleClient).stopScan()
    }

    @Test
    fun clearingViewModelClosesBleClient() {
        store.clear()

        verify(bleClient).close()
    }

    @Test
    fun activityLogIsBoundedAndUsesUniqueIds() {
        repeat(120) {
            viewModel.reportBlePermissionDenied()
        }

        val entries = viewModel.uiState.value.activity
        assertEquals(100, entries.size)
        assertEquals(100, entries.map { it.id }.toSet().size)
        assertEquals(120L, entries.first().id)
        assertEquals(21L, entries.last().id)
    }
}