package org.miguelcaldas.nessodroid.transport

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.miguelcaldas.nessodroid.model.NessoStatus
import org.miguelcaldas.nessodroid.model.NessoStatusParser

data class HttpCommandResponse(
    val accepted: Boolean,
    val statusCode: Int,
    val status: String,
    val body: String,
)

class HttpNessoClient {
    suspend fun sendCommand(baseUrl: String, command: String): HttpCommandResponse = withContext(Dispatchers.IO) {
        val payload = command.toByteArray(Charsets.UTF_8)
        val connection = open(baseUrl, "/command")
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { output ->
                output.write(payload)
            }
            val statusCode = connection.responseCode
            val body = connection.responseBody(statusCode)
            val status = runCatching { JSONObject(body).optString("status") }.getOrDefault("").ifBlank { "HTTP $statusCode" }
            HttpCommandResponse(statusCode in 200..299, statusCode, status, body)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun readStatus(baseUrl: String): NessoStatus = withContext(Dispatchers.IO) {
        val connection = open(baseUrl, "/status")
        try {
            connection.requestMethod = "GET"
            val statusCode = connection.responseCode
            val body = connection.responseBody(statusCode)
            if (statusCode !in 200..299) {
                throw IllegalStateException("Status request failed with HTTP $statusCode: $body")
            }
            NessoStatusParser.parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(baseUrl: String, path: String): HttpURLConnection {
        val normalized = baseUrl.trim().let { value ->
            val withScheme = if (value.startsWith("http://") || value.startsWith("https://")) value else "http://$value"
            withScheme.trimEnd('/')
        }
        return (URL(normalized + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            useCaches = false
        }
    }

    private fun HttpURLConnection.responseBody(statusCode: Int): String {
        val stream = if (statusCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }
}