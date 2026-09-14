package compose.project.click.click.events

import kotlin.test.Test
import kotlin.test.assertEquals

/** Failure and pending copy must never claim a successful check-in. */
class BeaconCheckInUiTest {
    @Test
    fun failureMessages_matchHttpStatus() {
        assertEquals(
            "Move closer to the event to check in",
            beaconCheckInFailureMessage(403),
        )
        assertEquals(
            "Check-in opens when the event starts",
            beaconCheckInFailureMessage(409),
        )
        assertEquals(
            "Location is required to check in",
            beaconCheckInFailureMessage(400),
        )
        assertEquals(
            "Event check-in is temporarily unavailable",
            beaconCheckInFailureMessage(500),
        )
        assertEquals(
            "Custom",
            beaconCheckInFailureMessage(null, fallback = "Custom"),
        )
    }

    @Test
    fun checkInCtaLabels_areClearAndStateAware() {
        assertEquals("Check in here", eventCheckInCtaLabel(checkedIn = false, pending = false))
        assertEquals("Checking location…", eventCheckInCtaLabel(checkedIn = false, pending = true))
        assertEquals("Checked in", eventCheckInCtaLabel(checkedIn = true, pending = false))
        assertEquals("Updating…", eventCheckInCtaLabel(checkedIn = true, pending = true))
    }
}
