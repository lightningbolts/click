package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.data.api.InboxNudgesResponseDto // pragma: allowlist secret
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NudgeCopyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun reconnectCopy_matchesProductExamples() {
        val copy = reconnectNudgeCopy("Sam", 21)
        assertEquals("Check in with Sam", copy.title)
        assertEquals("You and Sam haven't talked since you Clicked 21 days ago.", copy.body)
        assertFalse(copy.body.contains("Hey", ignoreCase = true))
    }

    @Test
    fun sharedEventCopy_usesEventTitle() {
        val copy = sharedEventNudgeCopy("Sam", "Picnic")
        assertEquals("Sam is going too", copy.title)
        assertEquals("You and Sam are both going to Picnic.", copy.body)
    }

    @Test
    fun inboxNudgeDto_parsesReconnectAndSharedEvent() {
        val payload =
            json.decodeFromString<InboxNudgesResponseDto>(
                """{"nudges":[{"id":"n1","nudge_type":"reconnect_lull","connection_id":"c1","headline":"Check in with Sam","body":"You and Sam haven't talked since you Clicked 21 days ago."},{"id":"n2","nudge_type":"shared_upcoming_event","connection_id":"c1","beacon_id":"b1","headline":"Sam is going too","body":"You and Sam are both going to Picnic."}]}""",
            )
        assertEquals(2, payload.nudges.size)
        assertEquals("reconnect_lull", payload.nudges[0].nudgeType)
        assertEquals("c1", payload.nudges[0].connectionId)
        assertEquals("b1", payload.nudges[1].beaconId)
        assertTrue(payload.nudges[1].body.contains("Picnic"))
    }
}
