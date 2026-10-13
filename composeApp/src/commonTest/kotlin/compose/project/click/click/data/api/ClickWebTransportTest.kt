package compose.project.click.click.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ClickWebTransportTest {
    private val url = "https://click.test/api/thing"

    /** A client with the real policy over a scripted engine; [statuses] are answered in order (the last repeats). */
    private class Scripted(
        vararg statuses: HttpStatusCode,
    ) {
        private val queue = statuses.toList()
        var calls = 0
        val client =
            HttpClient(
                MockEngine {
                    val status = queue[minOf(calls, queue.lastIndex)]
                    calls++
                    respond("", status)
                },
            ) {
                expectSuccess = false
                installClickWebTransportPolicy(retryDelayMs = 1)
            }
    }

    @Test
    fun getIsRetriedOnceOn503() =
        runTest {
            val s = Scripted(HttpStatusCode.ServiceUnavailable)
            val response = s.client.get(url)
            assertEquals(2, s.calls)
            assertEquals(503, response.status.value)
        }

    @Test
    fun getRecoversWhenTheRetrySucceeds() =
        runTest {
            val s = Scripted(HttpStatusCode.BadGateway, HttpStatusCode.OK)
            assertEquals(200, s.client.get(url).status.value)
            assertEquals(2, s.calls)
        }

    @Test
    fun headIsRetriedOn504() =
        runTest {
            val s = Scripted(HttpStatusCode.GatewayTimeout)
            s.client.head(url)
            assertEquals(2, s.calls)
        }

    @Test
    fun postIsNeverRetried() =
        runTest {
            val s = Scripted(HttpStatusCode.ServiceUnavailable)
            assertEquals(503, s.client.post(url).status.value)
            assertEquals(1, s.calls)
        }

    @Test
    fun putAndDeleteAreNeverRetried() =
        runTest {
            val put = Scripted(HttpStatusCode.BadGateway)
            put.client.put(url)
            assertEquals(1, put.calls)
            val delete = Scripted(HttpStatusCode.GatewayTimeout)
            delete.client.delete(url)
            assertEquals(1, delete.calls)
        }

    @Test
    fun otherServerErrorsAreNotRetried() =
        runTest {
            val s = Scripted(HttpStatusCode.InternalServerError)
            s.client.get(url)
            assertEquals(1, s.calls)
        }

    @Test
    fun getIsRetriedOnceOnATransportErrorButPostIsNot() =
        runTest {
            var getCalls = 0
            val getClient =
                HttpClient(
                    MockEngine {
                        getCalls++
                        throw IOException("connection reset")
                    },
                ) { installClickWebTransportPolicy(retryDelayMs = 1) }
            assertFailsWith<IOException> { getClient.get(url) }
            assertEquals(2, getCalls)

            var postCalls = 0
            val postClient =
                HttpClient(
                    MockEngine {
                        postCalls++
                        throw IOException("connection reset")
                    },
                ) { installClickWebTransportPolicy(retryDelayMs = 1) }
            assertFailsWith<IOException> { postClient.post(url) }
            assertEquals(1, postCalls)
        }

    @Test
    fun timeoutsMatchIos() {
        assertEquals(15_000L, ClickWebTransport.REQUEST_TIMEOUT_MS)
        assertEquals(10_000L, ClickWebTransport.CONNECT_TIMEOUT_MS)
        assertEquals(1, ClickWebTransport.MAX_RETRIES)
        val client = HttpClient(MockEngine { respond("") }) { installClickWebTransportPolicy() }
        assertNotNull(client.plugin(HttpTimeout))
    }
}
