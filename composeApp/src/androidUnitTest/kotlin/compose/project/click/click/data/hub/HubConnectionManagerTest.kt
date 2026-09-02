package compose.project.click.click.data.hub

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
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
        assertEquals("Check in to this event to join the hub.", error.message)
    }

    @Test
    fun legacyErrorStringRemainsSupported() {
        val error = parseHubError(json, """{"error":"This hub is not available."}""")

        assertNull(error.code)
        assertEquals("This hub is not available.", error.message)
    }
}
