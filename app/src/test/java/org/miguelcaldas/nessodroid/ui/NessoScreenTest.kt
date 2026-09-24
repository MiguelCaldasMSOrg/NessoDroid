package org.miguelcaldas.nessodroid.ui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.miguelcaldas.nessodroid.model.NessoStatusParser
import org.miguelcaldas.nessodroid.transport.BleLinkState
import org.miguelcaldas.nessodroid.ui.theme.NessoTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NessoScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun commandControlsRemainReachableInCompactViewportWithLargeText() {
        val status = NessoStatusParser.parse("""{"node":"Nesso-test","mode":"LoRa","profile":"maximum-range","wifi_mode":"station","wifi_address":"192.168.1.12","battery_state":"unavailable"}""")
        render(NessoUiState(httpStatus = status, command = "p"), fontScale = 2f)

        compose.onNodeWithTag("controller").performScrollToNode(hasText("Send"))

        compose.onNodeWithText("Send").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun disconnectedBleCannotSendCommands() {
        render(NessoUiState(transport = TransportMode.BLE, command = "p"))

        compose.onNodeWithTag("controller").performScrollToNode(hasText("Send"))

        compose.onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun connectingBleCanBeCancelled() {
        var cancellations = 0
        render(NessoUiState(transport = TransportMode.BLE, bleLinkState = BleLinkState.CONNECTING), onDisconnect = { cancellations++ })

        compose.onNodeWithTag("controller").performScrollToNode(hasContentDescription("Cancel"))
        compose.onNodeWithContentDescription("Cancel").performClick()

        assertEquals(1, cancellations)
    }

    @Test
    fun presetsEditCommandWithoutSendingIt() {
        var draft = ""
        var sends = 0
        render(NessoUiState(), onCommandChanged = { draft = it }, onSend = { sends++ })

        compose.onNodeWithTag("controller").performScrollToNode(hasText("Ping"))
        compose.onNodeWithText("Ping").performClick()

        assertEquals("p", draft)
        assertEquals(0, sends)
    }

    @Test
    fun detailedTelemetryHasItsOwnView() {
        val status = NessoStatusParser.parse("""{"node":"Nesso-test","mode":"LoRa","profile":"maximum-range","peer":"peer-42","wifi_address":"192.168.1.12"}""")
        render(NessoUiState(httpStatus = status))

        compose.onNodeWithTag("page-device").performClick()
        compose.onNodeWithTag("controller").performScrollToNode(hasText("peer-42"))
        compose.onNodeWithText("peer-42").assertIsDisplayed()
        compose.onNodeWithTag("page-control").performClick()
        compose.onNodeWithTag("controller").performScrollToNode(hasText("Send"))
        compose.onNodeWithText("Send").assertIsDisplayed()
    }

    @Test
    fun activityViewShowsHistoryAndCanClearIt() {
        var cleared = false
        render(NessoUiState(activity = listOf(ActivityEntry(1, "12:34:56", "HTTP", "p -> queued"))), onClear = { cleared = true })

        compose.onNodeWithTag("page-activity").performClick()
        compose.onNodeWithTag("controller").performScrollToNode(hasText("p -> queued"))
        compose.onNodeWithText("p -> queued").assertIsDisplayed()
        compose.onNodeWithTag("controller").performScrollToNode(hasContentDescription("Clear activity"))
        compose.onNodeWithContentDescription("Clear activity").performClick()

        assertEquals(true, cleared)
    }

    private fun render(state: NessoUiState, fontScale: Float = 1f, onDisconnect: () -> Unit = {}, onCommandChanged: (String) -> Unit = {}, onSend: () -> Unit = {}, onClear: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                NessoTheme {
                    NessoScreen(state = state, onTransportSelected = {}, onHttpEndpointChanged = {}, onRefreshHttpStatus = {}, onStartBleScan = {}, onStopBleScan = {}, onConnectBle = {}, onDisconnectBle = onDisconnect, onCommandChanged = onCommandChanged, onSendCommand = onSend, onClearLog = onClear, modifier = Modifier.requiredSize(320.dp, 280.dp))
                }
            }
        }
    }
}