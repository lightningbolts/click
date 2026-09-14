package compose.project.click.click.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventCheckInStateMachineTest {
    @Test
    fun pendingRequestPreservesConfirmedPresence() {
        val previous = ConfirmedEventPresence(checkedIn = false, checkInCount = 7)
        assertEquals(previous, pendingEventPresence(previous))
    }

    @Test
    fun early409DoesNotCreateLocalCheckIn() {
        val previous = ConfirmedEventPresence(checkedIn = false, checkInCount = 7)
        val (presence, rejection) = rejectedEventPresence(previous, 409, null)

        assertFalse(presence.checkedIn)
        assertEquals(7, presence.checkInCount)
        assertEquals("Check-in opens when the event starts", rejection.message)
    }

    @Test
    fun serverSuccessIsOnlyTransitionThatChangesConfirmedPresence() {
        val presence = confirmedEventPresence(true, "2026-09-14T05:30:00Z", 8)
        assertTrue(presence.checkedIn)
        assertEquals(8, presence.checkInCount)
    }

    @Test
    fun checkoutDoesNotRevokeEventHubForAcceptedRsvp() {
        assertFalse(shouldRevokeEventHubAfterCheckout(hasAcceptedRsvp = true, isHost = false))
    }

    @Test
    fun checkoutDoesNotRevokeEventHubForHost() {
        assertFalse(shouldRevokeEventHubAfterCheckout(hasAcceptedRsvp = false, isHost = true))
    }

    @Test
    fun checkoutMayRevokePresenceOnlyAccessWhenNoRsvpOrHostAccessExists() {
        assertTrue(shouldRevokeEventHubAfterCheckout(hasAcceptedRsvp = false, isHost = false))
    }
}
