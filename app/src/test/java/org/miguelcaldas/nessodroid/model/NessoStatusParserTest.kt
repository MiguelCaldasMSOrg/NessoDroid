package org.miguelcaldas.nessodroid.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NessoStatusParserTest {
    @Test
    fun parsesCurrentFirmwareStatus() {
        val status = NessoStatusParser.parse(
            """{"node":"D7A4DCA6","mode":"LoRa","profile":"maximum-range","peer":"C393DCA6","radio_ready":true,"http_ready":true,"ble_ready":true,"wifi_mode":"fallback_ap","wifi_address":"192.168.4.1","benchmark_active":false,"sweep_active":false,"queued_commands":0,"battery_state":"fresh","battery_percent":7,"battery_voltage":3.37,"battery_charging":true,"battery_charge_state":"charging","visuals_enabled":false,"visual_timeout_seconds":60,"survey_active":false}"""
        )

        assertEquals("D7A4DCA6", status.node)
        assertEquals("C393DCA6", status.peer)
        assertEquals(7, status.batteryPercent)
        assertEquals(3.37, status.batteryVoltage!!, 0.001)
        assertEquals("charging", status.batteryChargeState)
        assertTrue(status.radioReady)
        assertFalse(status.visualsEnabled)
    }

    @Test
    fun keepsUnavailableBatteryValuesNull() {
        val status = NessoStatusParser.parse(
            """{"node":"C393DCA6","battery_state":"unavailable","battery_percent":null,"battery_voltage":null}"""
        )

        assertNull(status.peer)
        assertNull(status.batteryPercent)
        assertNull(status.batteryVoltage)
        assertEquals("unavailable", status.batteryChargeState)
    }

    @Test
    fun missingOrInvalidNodeIsNotDeviceStatus() {
        for (json in listOf("{}", """{"node":null}""", """{"node":" "}""", """{"node":42}""")) {
            val result = runCatching { NessoStatusParser.parse(json) }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun malformedOptionalValuesStayNullInsteadOfBecomingZero() {
        val status = NessoStatusParser.parse("""{"node":"Nesso-test","peer":42,"battery_percent":"unknown","battery_voltage":"unknown"}""")

        assertNull(status.peer)
        assertNull(status.batteryPercent)
        assertNull(status.batteryVoltage)
    }

    @Test
    fun invalidBatteryPercentagesStayNull() {
        for (percent in listOf("-1", "101", "42.5", "12345678901234567890")) {
            val status = NessoStatusParser.parse("""{"node":"Nesso-test","battery_percent":$percent}""")

            assertNull(status.batteryPercent)
        }
    }

    @Test
    fun nonFiniteVoltageStaysNull() {
        val status = NessoStatusParser.parse("""{"node":"Nesso-test","battery_voltage":1e999}""")

        assertNull(status.batteryVoltage)
    }

    @Test
    fun ignoresUnknownFieldsAndDefaultsMissingOptionalFields() {
        val status = NessoStatusParser.parse("""{"node":"Nesso-test","future_field":{"enabled":true}}""")

        assertEquals("Nesso-test", status.node)
        assertEquals("unavailable", status.batteryState)
        assertTrue(status.visualsEnabled)
        assertEquals(60, status.visualTimeoutSeconds)
        assertFalse(status.surveyActive)
    }
}