package org.miguelcaldas.nessodroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val server = MockWebServer()
    private val statusJson = """{"node":"S24-TEST","mode":"LoRa","profile":"maximum-range","peer":"PEER-TEST","radio_ready":true,"http_ready":true,"ble_ready":true,"wifi_mode":"station","wifi_address":"192.168.1.25","queued_commands":0,"battery_state":"fresh","battery_percent":74,"battery_voltage":3.92,"battery_charge_state":"charging","visuals_enabled":true,"visual_timeout_seconds":60}"""
    private val address = hasText("Nesso address") and hasSetTextAction()
    private val command = hasText("Command") and hasSetTextAction()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun setUp() {
        server.start()
        compose.onNode(address).performTextReplacement(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun acceptedHttpCommandRefreshesStatusAndClearsDraft() {
        server.enqueue(MockResponse().setBody(statusJson))
        click("Refresh")
        awaitText("S24-TEST")
        assertEquals("/status", requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)).path)
        capture("http-status")

        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"status":"queued"}"""))
        server.enqueue(MockResponse().setBody(statusJson))
        click("Ping")
        compose.onNode(command).assertTextContains("p")
        click("Send")

        val request = requireNotNull(server.takeRequest(10, TimeUnit.SECONDS))
        assertEquals("/command", request.path)
        assertEquals("p", request.body.readUtf8())
        assertEquals("/status", requireNotNull(server.takeRequest(10, TimeUnit.SECONDS)).path)
        awaitText("S24-TEST")
        show("Send")
        compose.onNodeWithText("Send").assertIsNotEnabled()
        show("p -> queued")
        compose.onNodeWithText("p -> queued").assertIsDisplayed()
        capture("http-command-accepted")
    }

    @Test
    fun rejectedHttpCommandKeepsDraftAndShowsQueueStatus() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"status":"queue_full"}"""))
        click("Hello")
        click("Send")

        assertEquals("h", requireNotNull(server.takeRequest(10, TimeUnit.SECONDS)).body.readUtf8())
        awaitText("h -> queue_full")
        compose.onNode(command).assertTextContains("h")
        show("Send")
        compose.onNodeWithText("Send").assertIsEnabled()
        capture("http-queue-full")
    }

    @Test
    fun disconnectedBleCannotSend() {
        click("BLE")
        click("Ping")
        show("Send")

        compose.onNodeWithText("Send").assertIsNotEnabled()
        capture("ble-disconnected")
    }

    @Test
    fun activityRecreationPreservesAddressAndDraftWithKeyboard() {
        show("Command")
        compose.onNode(command).performTextReplacement("s24-draft")
        compose.onNode(command).performClick()
        val originalEndpoint = server.url("/").toString()
        compose.activityRule.scenario.recreate()
        show("Command")
        compose.onNode(command).assertTextContains("s24-draft")
        compose.onNode(command).performClick()
        compose.activityRule.scenario.onActivity { activity ->
            val keyboard = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.showSoftInput(activity.window.decorView.findFocus(), InputMethodManager.SHOW_IMPLICIT)
        }
        show("Send")
        compose.onNodeWithText("Send").assertIsDisplayed().assertIsEnabled()
        capture("keyboard-after-recreation")
        device.pressBack()
        show("Nesso address")
        compose.onNode(address).assertTextContains(originalEndpoint)
    }

    @Test
    fun landscapeAndLargeTextKeepCommandControlsReachable() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            device.setOrientationLeft()
            compose.waitUntil(15000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && compose.activity.resources.configuration.fontScale >= 1.9f
            }
            click("Ping")
            show("Send")
            compose.onNodeWithText("Send").assertIsDisplayed().assertIsEnabled()
            capture("landscape-large-text")
        } finally {
            device.executeShellCommand("settings put system font_scale ${if (originalScale == "null") "1.0" else originalScale}")
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    @Test
    fun deniedNearbyDevicePermissionIsReported() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        click("BLE")
        click("Scan")
        val deny = device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_button")), 10000)
        assertNotNull("Nearby devices permission dialog", deny)
        deny.click()

        awaitText("Nearby-device permission is required")
        assertFalse(compose.activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
        capture("ble-permission-denied")
    }

    @Test
    fun permissionActionSurvivesRotationAndScanCanStop() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        click("BLE")
        click("Scan")
        assertTrue(device.wait(Until.hasObject(By.res("com.android.permissioncontroller", "permission_allow_button")), 10000))
        try {
            device.setOrientationLeft()
            val allow = device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_allow_button")), 10000)
            assertNotNull(allow)
            allow.click()
            compose.waitUntil(15000) {
                compose.activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            }
            assertTrue(compose.activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
            val messages = listOf("Scanning for Nesso devices", "No Nesso devices found", "Bluetooth is disabled or unavailable")
            compose.waitUntil(15000) {
                messages.any { message -> compose.onAllNodes(hasText(message)).fetchSemanticsNodes().isNotEmpty() }
            }
            if (compose.onAllNodes(hasText("Stop")).fetchSemanticsNodes().isNotEmpty()) {
                click("Stop")
                awaitText("No Nesso devices found")
            }
            capture("ble-permission-after-rotation")
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun click(text: String) {
        show(text)
        compose.onNodeWithText(text).performClick()
    }

    private fun show(text: String) {
        compose.onNodeWithTag("controller").performScrollToNode(hasText(text))
    }

    private fun awaitText(text: String) {
        compose.waitUntil(15000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        val outputDirectory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (outputDirectory == null) File(compose.activity.getExternalFilesDir(null), "test-screenshots") else File(outputDirectory, "screenshots")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { output ->
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}