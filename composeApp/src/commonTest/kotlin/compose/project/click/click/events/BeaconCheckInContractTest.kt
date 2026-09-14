package compose.project.click.click.events

import kotlin.test.Test
import kotlin.test.assertEquals

class BeaconCheckInContractTest {
    @Test
    fun pendingCheckInDoesNotClaimSuccess() {
        assertEquals(
            "Checking location…",
            eventCheckInCtaLabel(checkedIn = false, pending = true),
        )
    }

    @Test
    fun notLiveEventReturnsExplicitScheduleMessage() {
        assertEquals(
            "Check-in opens when the event starts",
            beaconCheckInFailureMessage(httpStatus = 409),
        )
    }

    @Test
    fun authFailureIsNotReportedAsLocationFailure() {
        assertEquals(
            "Please sign in again to check in",
            beaconCheckInFailureMessage(httpStatus = 401),
        )
    }

    @Test
    fun serverFailureIsRetryableCopy() {
        assertEquals(
            "Event check-in is temporarily unavailable",
            beaconCheckInFailureMessage(httpStatus = 503),
        )
    }
}
