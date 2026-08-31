package compose.project.click.click.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventHubAccessTest {
    @Test
    fun hostAlwaysAllowed() {
        assertTrue(
            evaluateEventHubAccess(
                userId = "host",
                hubCreatorId = "host",
                eventCreatorId = "host",
                hasActiveCheckIn = false,
                hasRsvp = false,
            ),
        )
    }

    @Test
    fun checkedInGuestAllowedWhileRsvpFlagOff() {
        assertTrue(
            evaluateEventHubAccess(
                userId = "guest",
                hubCreatorId = "host",
                eventCreatorId = "host",
                hasActiveCheckIn = true,
                hasRsvp = false,
            ),
        )
    }

    @Test
    fun rsvpOnlyDeniedWhileFlagOff() {
        assertFalse(
            evaluateEventHubAccess(
                userId = "guest",
                hubCreatorId = "host",
                eventCreatorId = "host",
                hasActiveCheckIn = false,
                hasRsvp = true,
            ),
        )
    }

    @Test
    fun bothRequiredWhenRsvpFlagOn() {
        val policy = EventHubAccessPolicy(requireCheckIn = true, requireRsvp = true)
        assertFalse(
            evaluateEventHubAccess(
                userId = "guest",
                hubCreatorId = "host",
                eventCreatorId = "host",
                hasActiveCheckIn = true,
                hasRsvp = false,
                policy = policy,
            ),
        )
        assertTrue(
            evaluateEventHubAccess(
                userId = "guest",
                hubCreatorId = "host",
                eventCreatorId = "host",
                hasActiveCheckIn = true,
                hasRsvp = true,
                policy = policy,
            ),
        )
    }

    @Test
    fun hubExpiresOneDayAfterEventEnd() {
        val end = 1_000_000L
        assertEquals(end + EVENT_HUB_TTL_AFTER_END_MS, eventHubExpiresAtEpochMs(end))
    }

    @Test
    fun openCtaNeedsHubIdAndAccess() {
        assertFalse(canOpenEventHub(hubId = null, isCreator = true, checkedIn = true))
        assertTrue(canOpenEventHub(hubId = "hub_1", isCreator = true, checkedIn = false))
        assertTrue(canOpenEventHub(hubId = "hub_1", isCreator = false, checkedIn = true))
        assertFalse(canOpenEventHub(hubId = "hub_1", isCreator = false, checkedIn = false))
    }
}
