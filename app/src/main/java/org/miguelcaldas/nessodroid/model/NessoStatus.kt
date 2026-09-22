package org.miguelcaldas.nessodroid.model

import org.json.JSONObject

data class NessoStatus(
    val node: String,
    val mode: String,
    val profile: String,
    val peer: String?,
    val radioReady: Boolean,
    val httpReady: Boolean,
    val bleReady: Boolean,
    val wifiMode: String,
    val wifiAddress: String,
    val benchmarkActive: Boolean,
    val sweepActive: Boolean,
    val surveyActive: Boolean,
    val queuedCommands: Int,
    val batteryState: String,
    val batteryPercent: Int?,
    val batteryVoltage: Double?,
    val batteryChargeState: String,
    val visualsEnabled: Boolean,
    val visualTimeoutSeconds: Int,
)

object NessoStatusParser {
    fun parse(json: String): NessoStatus {
        val value = JSONObject(json)
        val node = requireNotNull(value.stringOrNull("node")) { "Status response is missing a valid node" }
        return NessoStatus(
            node = node,
            mode = value.optString("mode"),
            profile = value.optString("profile"),
            peer = value.stringOrNull("peer"),
            radioReady = value.optBoolean("radio_ready"),
            httpReady = value.optBoolean("http_ready"),
            bleReady = value.optBoolean("ble_ready"),
            wifiMode = value.optString("wifi_mode"),
            wifiAddress = value.optString("wifi_address"),
            benchmarkActive = value.optBoolean("benchmark_active"),
            sweepActive = value.optBoolean("sweep_active"),
            surveyActive = value.optBoolean("survey_active"),
            queuedCommands = value.optInt("queued_commands"),
            batteryState = value.optString("battery_state", "unavailable"),
            batteryPercent = value.intOrNull("battery_percent")?.takeIf { it in 0..100 },
            batteryVoltage = value.doubleOrNull("battery_voltage"),
            batteryChargeState = value.optString("battery_charge_state", "unavailable"),
            visualsEnabled = value.optBoolean("visuals_enabled", true),
            visualTimeoutSeconds = value.optInt("visual_timeout_seconds", 60),
        )
    }

    private fun JSONObject.stringOrNull(name: String): String? {
        return (opt(name) as? String)?.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.intOrNull(name: String): Int? {
        val number = (opt(name) as? Number)?.toDouble() ?: return null
        return number.takeIf { it.isFinite() && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() && it % 1.0 == 0.0 }?.toInt()
    }

    private fun JSONObject.doubleOrNull(name: String): Double? {
        return (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    }
}