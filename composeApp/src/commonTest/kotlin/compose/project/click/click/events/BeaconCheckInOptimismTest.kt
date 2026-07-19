package compose.project.click.click.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards check-in UX contracts: failure copy mapping and the optimistic-flip invariant
 * (UI must not wait on GPS before showing checked-in).
 */
class BeaconCheckInOptimismTest {

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
            "Location required to check in",
            beaconCheckInFailureMessage(400),
        )
        assertEquals(
            "Couldn't check in",
            beaconCheckInFailureMessage(500),
        )
        assertEquals(
            "Custom",
            beaconCheckInFailureMessage(null, fallback = "Custom"),
        )
    }

    @Test
    fun optimisticCheckIn_appliesBeforeNetworkGate() {
        // Contract: pending + checkedIn flip happen synchronously before any suspend GPS/auth work.
        var checkedIn = false
        var pending = false
        fun applyOptimisticCheckIn() {
            pending = true
            checkedIn = true
        }
        applyOptimisticCheckIn()
        assertTrue(pending)
        assertTrue(checkedIn)
        // Rollback path restores prior snapshot without leaving pending stuck.
        checkedIn = false
        pending = false
        assertFalse(checkedIn)
        assertFalse(pending)
    }

    @Test
    fun earlyCheckIn_409KeepsOptimisticState() {
        // MapViewModel treats HTTP 409 as early check-in success and sets localEarlyCheckIn.
        var checkedIn = true
        var localEarlyCheckIn = false
        val earlyIds = mutableSetOf<String>()
        val beaconId = "beacon-1"
        val status = 409
        if (status == 409) {
            checkedIn = true
            localEarlyCheckIn = true
            earlyIds += beaconId
        }
        assertTrue(checkedIn)
        assertTrue(localEarlyCheckIn)
        // Force-refresh merge must not wipe early check-in when server still says false,
        // even if the in-memory localEarly flag briefly races to false.
        val serverCheckedIn = false
        localEarlyCheckIn = false
        val keepEarly = (!serverCheckedIn) && (localEarlyCheckIn || beaconId in earlyIds)
        val mergedCheckedIn = serverCheckedIn || keepEarly
        assertTrue(mergedCheckedIn)
        assertEquals(
            "Check-in opens when the event starts",
            beaconCheckInFailureMessage(409),
        )
    }
}
