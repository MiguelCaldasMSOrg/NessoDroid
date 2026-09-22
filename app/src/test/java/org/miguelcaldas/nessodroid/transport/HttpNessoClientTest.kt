package org.miguelcaldas.nessodroid.transport

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpNessoClientTest {
    private val server = MockWebServer()
    private val client = HttpNessoClient()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun postsUtf8CommandAndRequiresQueueAcceptance() = runTest {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"status":"queued"}"""))

        val result = client.sendCommand(server.url("/").toString(), "hello \u00e9")

        assertTrue(result.accepted)
        assertEquals(202, result.statusCode)
        assertEquals("queued", result.status)
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/command", request.path)
        assertEquals("text/plain; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals("hello \u00e9", request.body.readUtf8())
    }

    @Test
    fun queueFullIsNotAccepted() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"status":"queue_full"}"""))

        val result = client.sendCommand(server.url("/").toString(), "p")

        assertFalse(result.accepted)
        assertEquals("queue_full", result.status)
    }

    @Test
    fun successfulHttpCodeWithoutQueuedStatusIsNotAccepted() = runTest {
        for (body in listOf("<html>Not a Nesso device</html>", "{}", """{"status":"ready"}""", """{"status":"queue_full"}""")) {
            server.enqueue(MockResponse().setBody(body))

            assertFalse(client.sendCommand(server.url("/").toString(), "p").accepted)
        }
    }

    @Test
    fun errorHttpCodeCannotClaimQueueAcceptance() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"status":"queued"}"""))

        assertFalse(client.sendCommand(server.url("/").toString(), "p").accepted)
    }

    @Test
    fun commandRedirectsAreNotFollowed() = runTest {
        server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", server.url("/other-device")))
        server.enqueue(MockResponse().setBody("""{"status":"queued"}"""))

        assertFalse(client.sendCommand(server.url("/").toString(), "p").accepted)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun lostResponseDoesNotRetryCommand() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("""{"status":"queued"}"""))

        val result = runCatching { client.sendCommand(server.url("/").toString(), "p") }

        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun readsStatusWithOptionalSchemeAndBasePath() = runTest {
        server.enqueue(MockResponse().setBody("""{"node":"Nesso-test"}"""))
        val address = server.url("/device/").toString().removePrefix("http://")

        val status = client.readStatus(" $address ")

        assertEquals("Nesso-test", status.node)
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/device/status", request.path)
    }

    @Test
    fun invalidAddressesFailBeforeNetworkIo() = runTest {
        for (address in listOf("", "ftp://localhost", "http://user:password@localhost", "http://localhost/?command=p", "http://localhost/#fragment")) {
            val result = runCatching { client.readStatus(address) }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun statusHttpFailureIsReported() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Unavailable"))

        val result = runCatching { client.readStatus(server.url("/").toString()) }

        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("503"))
    }

    @Test
    fun oversizedResponseIsRejected() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(64 * 1024 + 1)))

        val result = runCatching { client.readStatus(server.url("/").toString()) }

        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("exceeds"))
    }

    @Test(timeout = 5000)
    fun wholeCallDeadlineBoundsTricklingResponse() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(100)).throttleBody(1, 50, TimeUnit.MILLISECONDS))
        val boundedClient = HttpNessoClient(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).callTimeout(200, TimeUnit.MILLISECONDS).build())

        val result = runCatching { boundedClient.readStatus(server.url("/").toString()) }

        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun coroutineCancellationCancelsNetworkCall() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val started = CompletableDeferred<Call>()
        val networkClient = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callStart(call: Call) {
                started.complete(call)
            }
        }).build()
        val cancellableClient = HttpNessoClient(networkClient)
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            cancellableClient.readStatus(server.url("/").toString())
        }
        val call = started.await()

        operation.cancelAndJoin()

        assertTrue(call.isCanceled())
    }
}