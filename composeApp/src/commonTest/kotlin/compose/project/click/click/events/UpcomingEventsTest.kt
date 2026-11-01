package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpcomingEventsTest {
    private val now = Instant.parse("2026-11-01T12:00:00Z").toEpochMilliseconds()

    private fun mine(
        id: String,
        start: String?,
        role: String = "rsvp",
        end: String? = null,
        expires: String? = null,
    ) = MineEventDto(beaconId = id, title = id, eventStartAt = start, eventEndAt = end, expiresAt = expires, role = role)

    private fun saved(
        id: String,
        start: String?,
    ) = EventBookmarkItemDto(beaconId = id, title = id, eventStartAt = start)

    @Test
    fun decodesServerShape() {
        // click-web lib/server/events/mineEvents.ts
        val e =
            Json { ignoreUnknownKeys = true }
                .decodeFromString<MineEventsEnvelope>(
                    """{"events":[{"beacon_id":"b1","title":"Jazz","event_start_at":"2026-11-02T03:00:00Z","rsvp_count":4,"role":"creator"}]}""",
                ).events
                .single()
        assertEquals("creator", e.role)
        assertEquals(4, e.rsvpCount)
    }

    @Test
    fun mergesTagsSortsAndDedupes() {
        val result =
            upcomingEvents(
                mine =
                    listOf(
                        mine("host", "2026-11-03T03:00:00Z", role = "creator"),
                        mine("going", "2026-11-02T03:00:00Z"),
                        mine("both", "2026-11-04T03:00:00Z"),
                    ),
                saved = listOf(saved("both", "2026-11-04T03:00:00Z"), saved("savedOnly", "2026-11-01T20:00:00Z")),
                nowEpochMs = now,
            )
        assertEquals(listOf("savedOnly", "going", "host", "both"), result.map { it.event.beaconId })
        assertEquals(
            listOf(UpcomingTag.SAVED, UpcomingTag.GOING, UpcomingTag.HOSTING, UpcomingTag.GOING),
            result.map { it.tag },
            "a going event that's also saved reads Going",
        )
    }

    @Test
    fun pastEventsDropOutButOngoingStay() {
        assertFalse(isStillUpcoming("2026-10-30T03:00:00Z", "2026-10-30T06:00:00Z", null, now))
        assertTrue(isStillUpcoming("2026-11-01T11:00:00Z", "2026-11-01T13:00:00Z", null, now), "happening now")
        assertTrue(isStillUpcoming("2026-11-01T10:00:00Z", null, null, now), "no end: assume 3 hours")
        assertFalse(isStillUpcoming("2026-11-01T08:00:00Z", null, null, now))
    }

    @Test
    fun untimedEventsFollowExpiryAndSortLast() {
        assertTrue(isStillUpcoming(null, null, "2026-11-02T00:00:00Z", now))
        assertFalse(isStillUpcoming(null, null, "2026-10-31T00:00:00Z", now))
        val result = upcomingEvents(listOf(mine("tbd", null), mine("soon", "2026-11-01T18:00:00Z")), emptyList(), now)
        assertEquals(listOf("soon", "tbd"), result.map { it.event.beaconId })
    }
}
