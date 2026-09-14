package compose.project.click.click.data.hub

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EventHubAccessContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun eventHubDenialExplainsRsvpRequirement() {
        val error =
            parseHubError(
                json,
                """{"error":{"code":"EVENT_HUB_ACCESS_DENIED","message":"denied"}}""",
            )

        assertEquals("EVENT_HUB_ACCESS_DENIED", error.code)
        assertEquals("RSVP to this event to join the hub.", error.message)
    }

    @Test
    fun standaloneParticipantDenialIsNotMisreportedAsEventCheckIn() {
        val error =
            parseHubError(
                json,
                """{"error":{"code":"NOT_A_PARTICIPANT","message":"Join this hub before viewing its messages."}}""",
            )

        assertEquals("NOT_A_PARTICIPANT", error.code)
        assertEquals("Join this hub before viewing its messages.", error.message)
    }
}
