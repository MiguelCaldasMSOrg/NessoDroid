package org.miguelcaldas.nessodroid.transport

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.miguelcaldas.nessodroid.model.NessoStatus
import org.miguelcaldas.nessodroid.model.NessoStatusParser

data class HttpCommandResponse(
    val accepted: Boolean,
    val statusCode: Int,
    val status: String,
    val body: String,
)

class HttpNessoClient(private val client: OkHttpClient = defaultClient) {
    companion object {
        private const val MAX_RESPONSE_BYTES = 64 * 1024L
        private val defaultClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).writeTimeout(5, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    }

    suspend fun sendCommand(baseUrl: String, command: String): HttpCommandResponse {
        val request = Request.Builder().url(endpoint(baseUrl, "command")).post(command.toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
        return execute(request) { response, body ->
            val status = runCatching { JSONObject(body).optString("status") }.getOrDefault("").ifBlank { "HTTP ${response.code}" }
            HttpCommandResponse(response.isSuccessful && status == "queued", response.code, status, body)
        }
    }

    suspend fun readStatus(baseUrl: String): NessoStatus {
        val request = Request.Builder().url(endpoint(baseUrl, "status")).get().build()
        return execute(request) { response, body ->
            if (!response.isSuccessful) {
                throw IOException("Status request failed with HTTP ${response.code}: $body")
            }
            NessoStatusParser.parse(body)
        }
    }

    private fun endpoint(baseUrl: String, path: String): HttpUrl {
        val value = baseUrl.trim()
        require(value.isNotEmpty()) { "Enter a Nesso address" }
        val normalized = if (value.contains("://")) value else "http://$value"
        val url = requireNotNull(normalized.toHttpUrlOrNull()) { "Enter a valid HTTP or HTTPS address" }
        require(url.username.isEmpty() && url.password.isEmpty()) { "Nesso addresses must not contain credentials" }
        require(url.query == null && url.fragment == null) { "Nesso addresses must not contain a query or fragment" }
        return url.newBuilder().addPathSegment(path).build()
    }

    private suspend fun <Result> execute(request: Request, decode: (Response, String) -> Result): Result = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation {
            call.cancel()
        }
        call.enqueue(object : Callback {
            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun onFailure(call: Call, error: IOException) {
                continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val source = response.body?.source() ?: throw IOException("Empty HTTP response")
                        source.request(MAX_RESPONSE_BYTES + 1)
                        if (source.buffer.size > MAX_RESPONSE_BYTES) {
                            throw IOException("HTTP response exceeds $MAX_RESPONSE_BYTES bytes")
                        }
                        continuation.resume(decode(response, source.readUtf8()))
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        })
    }
}