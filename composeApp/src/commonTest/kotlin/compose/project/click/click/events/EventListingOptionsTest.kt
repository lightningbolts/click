package compose.project.click.click.events

import compose.project.click.click.data.models.BeaconVisibilityAudience
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventListingOptionsTest {
    @Test
    fun parseEventListingOptions_readsMetadataKeys() {
        val meta =
            buildJsonObject {
                put("event_visibility", "invite_only")
                put("event_capacity", 120)
                put("approval_required", true)
                put("guest_list_visibility", "hosts_only")
                put("cover_theme_id", "theme:teal")
            }
        val parsed = parseEventListingOptions(meta)
        assertEquals(EventVisibility.INVITE_ONLY, parsed.eventVisibility)
        assertEquals(120, parsed.eventCapacity)
        assertTrue(parsed.approvalRequired)
        assertEquals(GuestListVisibility.HOSTS_ONLY, parsed.guestListVisibility)
        assertEquals("theme:teal", parsed.coverThemeId)
    }

    @Test
    fun toMetadataPatch_roundTripsDefaults() {
        val options =
            EventListingOptions(
                eventVisibility = EventVisibility.UNLISTED,
                eventCapacity = null,
                approvalRequired = false,
                guestListVisibility = GuestListVisibility.PUBLIC,
                coverThemeId = null,
            )
        val patch = options.toMetadataPatch()
        val roundTripped = parseEventListingOptions(patch)
        assertEquals(options.eventVisibility, roundTripped.eventVisibility)
        assertNull(roundTripped.eventCapacity)
        assertFalse(roundTripped.approvalRequired)
        assertEquals(options.guestListVisibility, roundTripped.guestListVisibility)
        assertNull(roundTripped.coverThemeId)
    }

    @Test
    fun mapToAudience_matchesWebContract() {
        assertEquals(
            BeaconVisibilityAudience.EVERYONE,
            EventListingOptions(eventVisibility = EventVisibility.PUBLIC).mapToAudience(),
        )
        assertEquals(
            BeaconVisibilityAudience.CONNECTIONS,
            EventListingOptions(eventVisibility = EventVisibility.UNLISTED).mapToAudience(),
        )
        assertEquals(
            BeaconVisibilityAudience.CONNECTIONS,
            EventListingOptions(eventVisibility = EventVisibility.INVITE_ONLY).mapToAudience(),
        )
    }

    @Test
    fun eventRsvpStatusMessage_mapsKnownStates() {
        assertEquals(
            "Approval required — request to join",
            eventRsvpStatusMessage(EventRsvpRequestStatus.PENDING),
        )
        assertEquals(
            "You're on the list",
            eventRsvpStatusMessage(EventRsvpRequestStatus.WAITLISTED),
        )
        assertEquals(
            "Event full — join waitlist",
            normalizeEventRsvpErrorMessage("This event is full."),
        )
    }

    @Test
    fun parseEventCapacity_ignoresInvalidValues() {
        assertNull(parseEventCapacity(JsonPrimitive("")))
        assertNull(parseEventCapacity(JsonPrimitive("0")))
        assertNull(parseEventCapacity(JsonPrimitive("-3")))
        assertEquals(25, parseEventCapacity(JsonPrimitive("25")))
    }
}
