package compose.project.click.click.data.beacons

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertConfirmationsTest {
    private fun parse(raw: String): AlertConfirmationState =
        AlertConfirmationState.parse(Json.parseToJsonElement(raw).jsonObject as JsonObject)

    private val now = Instant.parse("2026-11-01T12:00:00Z").toEpochMilliseconds()

    @Test
    fun parsesServerShape() {
        // click-web GET /api/beacons/{id}/confirm
        val s =
            parse(
                """{"state":"active","expires_at":"2026-11-01T14:00:00Z","last_still_here_at":"2026-11-01T11:40:00Z",
                   "my_vote":null,"is_creator":false,"radius_meters":300}""",
            )
        assertEquals(AlertPhase.ACTIVE, s.phase)
        assertNull(s.myVote)
        assertFalse(s.isCreator)
        assertEquals("Last seen 20 minutes ago.", s.caption(now))
    }

    @Test
    fun phasesAndVotes() {
        assertEquals(AlertPhase.CLEARED, parse("""{"state":"cleared"}""").phase)
        assertEquals(AlertPhase.EXPIRED, parse("""{"state":"weird"}""").phase)
        assertEquals(AlertVote.STILL_HERE, parse("""{"state":"active","my_vote":{"status":"still_here"}}""").myVote)
    }

    @Test
    fun oneVotePerWindowButTheCreatorCanStillTakeItDown() {
        val voted = parse("""{"state":"active","my_vote":{"status":"still_here"},"is_creator":false}""")
        assertFalse(voted.canVote(AlertVote.CLEARED))
        val creator = voted.copy(isCreator = true)
        assertTrue(creator.canVote(AlertVote.CLEARED))
        assertFalse(creator.canVote(AlertVote.STILL_HERE))
        assertFalse(parse("""{"state":"cleared"}""").canVote(AlertVote.STILL_HERE))
    }

    @Test
    fun captionsMatchIos() {
        val base = parse("""{"state":"active"}""")
        assertEquals("Only people nearby can confirm.", base.caption(now))
        assertEquals("People nearby can confirm it's still there.", base.copy(isCreator = true).caption(now))
        assertEquals("Thanks — you kept this up to date.", base.copy(myVote = AlertVote.STILL_HERE).caption(now))
        assertEquals("Thanks — it comes down once others agree.", base.copy(myVote = AlertVote.CLEARED).caption(now))
    }

    @Test
    fun rejectionsMapFromStatus() {
        assertEquals(AlertVoteRejection.TOO_FAR, AlertVoteRejection.fromStatus(403))
        assertEquals(AlertVoteRejection.ALREADY_VOTED, AlertVoteRejection.fromStatus(409))
        assertEquals(AlertVoteRejection.ENDED, AlertVoteRejection.fromStatus(410))
        assertEquals(AlertVoteRejection.NEEDS_LOCATION, AlertVoteRejection.fromStatus(400))
        assertNull(AlertVoteRejection.fromStatus(500))
    }

    @Test
    fun reportReasonsMatchIos() {
        assertEquals(listOf("Not accurate anymore", "Inappropriate", "Spam"), BEACON_REPORT_REASONS)
    }
}
