package compose.project.click.click.data.hub

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class HubConnectionManagerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun structuredEventAccessErrorUsesFriendlyMessageAndPreservesCode() {
        val error =
            parseHubError(
                json,
                """{"error":{"code":"EVENT_HUB_ACCESS_DENIED","message":"internal policy detail"}}""",
            )

        assertEquals("EVENT_HUB_ACCESS_DENIED", error.code)
        assertEquals("RSVP to this event to join the hub.", error.message)
    }

    @Test
    fun legacyErrorStringRemainsSupported() {
        val error = parseHubError(json, """{"error":"This hub is not available."}""")

        assertNull(error.code)
        assertEquals("This hub is not available.", error.message)
    }

    @Test
    fun everyOpenReauthorizesEvenAfterAPreviousSuccess() =
        runBlocking<Unit> {
            var requests = 0
            val client =
                HttpClient(
                    MockEngine {
                        requests++
                        if (requests == 1) {
                            respond(
                                """{"success":true,"hub_id":"event","channel":"hub:event","name":"Event"}""",
                                HttpStatusCode.OK,
                                headersOf("Content-Type", "application/json"),
                            )
                        } else {
                            respond(
                                """{"error":{"code":"EVENT_HUB_ACCESS_DENIED","message":"denied"}}""",
                                HttpStatusCode.Forbidden,
                                headersOf("Content-Type", "application/json"),
                            )
                        }
                    },
                ) {
                    install(ContentNegotiation) { json() }
                    expectSuccess = true
                }
            try {
                assertIs<HubVerifyResult.Success>(HubConnectionManager.joinEventHub(client, "event", "token"))
                val denied = assertIs<HubVerifyResult.Failure>(HubConnectionManager.joinEventHub(client, "event", "token"))
                assertEquals("RSVP to this event to join the hub.", denied.userMessage)
                assertEquals(2, requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun cancellationCannotTurnIntoAnAuthorizationResult() =
        runBlocking<Unit> {
            val client =
                HttpClient(MockEngine { throw CancellationException("closed") }) {
                    install(ContentNegotiation) { json() }
                }
            try {
                assertFailsWith<CancellationException> { HubConnectionManager.joinEventHub(client, "event", "token") }
            } finally {
                client.close()
            }
        }
}
