package compose.project.click.click.data.realtime

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveUpdatesCoalescerTest {
    private fun TestScope.coalescer(batches: MutableList<LiveBatch>) = LiveUpdatesCoalescer(backgroundScope) { batches += it }

    // region coalescing (fake clock)

    @Test
    fun burstsCoalesceIntoOneBatchPerKind() =
        runTest {
            val batches = mutableListOf<LiveBatch>()
            val live = coalescer(batches)
            live.receive("drops")
            live.receive("drops")
            advanceTimeBy(300)
            live.receive("nudges")
            advanceTimeBy(299)
            runCurrent()
            assertTrue(batches.isEmpty())
            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf(LiveBatch(setOf(LiveKind.DROPS, LiveKind.NUDGES))), batches)
        }

    @Test
    fun laterHintsStartANewBatch() =
        runTest {
            val batches = mutableListOf<LiveBatch>()
            val live = coalescer(batches)
            live.receive("activity")
            advanceTimeBy(700)
            live.receive("activity")
            advanceTimeBy(700)
            assertEquals(2, batches.size)
        }

    @Test
    fun unknownAndLocalOnlyKindsAreIgnored() =
        runTest {
            val batches = mutableListOf<LiveBatch>()
            val live = coalescer(batches)
            live.receive("flags")
            live.receive("home")
            live.receive(null)
            advanceTimeBy(1_000)
            assertTrue(batches.isEmpty())
        }

    @Test
    fun beaconCellsAreCollectedPerBatch() =
        runTest {
            val batches = mutableListOf<LiveBatch>()
            val live = coalescer(batches)
            live.receive("beacons", 40.7, -73.9)
            live.receive("beacons", 51.5, -0.1)
            advanceTimeBy(700)
            val change = batches.single().beaconChange!!
            assertEquals(setOf(LiveCell(40.7, -73.9), LiveCell(51.5, -0.1)), change.cells)
            live.receive("beacons")
            advanceTimeBy(700)
            val anywhere = batches.last().beaconChange!!
            assertNull(anywhere.cells)
            assertTrue(anywhere.revision > change.revision)
        }

    // endregion

    // region foreground flush

    @Test
    fun backgroundAccumulatesAndForegroundFlushes() =
        runTest {
            val batches = mutableListOf<LiveBatch>()
            val live = coalescer(batches)
            live.setActive(false)
            live.receive("connections")
            live.receive("availability")
            advanceTimeBy(5_000)
            assertTrue(batches.isEmpty())
            live.setActive(true)
            advanceTimeBy(700)
            assertEquals(setOf(LiveKind.CONNECTIONS, LiveKind.AVAILABILITY), batches.single().kinds)
        }

    @Test
    fun goingInactiveCancelsAPendingFlush() =
        runTest {
            val batches = mutableListOf<LiveBatch>()
            val live = coalescer(batches)
            live.receive("drops")
            advanceTimeBy(100)
            live.setActive(false)
            advanceTimeBy(5_000)
            assertTrue(batches.isEmpty())
            live.setActive(true)
            advanceTimeBy(700)
            assertEquals(setOf(LiveKind.DROPS), batches.single().kinds)
        }

    @Test
    fun resetDropsPendingHints() =
        runTest {
            val batches = mutableListOf<LiveBatch>()
            val live = coalescer(batches)
            live.receive("drops")
            live.reset()
            advanceTimeBy(1_000)
            assertTrue(batches.isEmpty())
        }

    // endregion

    // region rejoin catch-up

    @Test
    fun userTopicRejoinCatchesUpOnHomeDropsActivityConnections() =
        runTest {
            val batches = mutableListOf<LiveBatch>()
            val live = coalescer(batches)
            live.userTopicRejoined()
            advanceTimeBy(700)
            assertEquals(setOf(LiveKind.HOME, LiveKind.DROPS, LiveKind.ACTIVITY, LiveKind.CONNECTIONS), batches.single().kinds)
        }

    @Test
    fun beaconsRejoinMeansAnywhere() =
        runTest {
            val batches = mutableListOf<LiveBatch>()
            val live = coalescer(batches)
            live.receive("beacons", 40.7, -73.9)
            live.beaconsTopicRejoined()
            advanceTimeBy(700)
            assertNull(batches.single().beaconChange!!.cells)
        }

    // endregion

    // region geo filter (fixtures from iOS LiveUpdatesTests)

    private val manhattanLat = 40.75
    private val manhattanLng = -73.99

    @Test
    fun anywhereChangeAffectsEveryRead() {
        val change = BeaconChange(revision = 1, cells = null)
        assertTrue(change.affects(manhattanLat, manhattanLng))
        assertTrue(change.affects(null, null))
    }

    @Test
    fun unknownCenterIsAffected() {
        val change = BeaconChange(revision = 1, cells = setOf(LiveCell(51.5, -0.1)))
        assertTrue(change.affects(null, null))
    }

    @Test
    fun nearbyCellAffectsAndDistantCellDoesNot() {
        val brooklyn = BeaconChange(revision = 1, cells = setOf(LiveCell(40.7, -73.9)))
        val london = BeaconChange(revision = 2, cells = setOf(LiveCell(51.5, -0.1)))
        assertTrue(brooklyn.affects(manhattanLat, manhattanLng))
        assertFalse(london.affects(manhattanLat, manhattanLng))
    }

    @Test
    fun edgeOfRadiusStillCounts() {
        // ~55 km north: past the 50 km discovery radius, but inside it once the cell's rounding is allowed for.
        assertTrue(BeaconChange(1, setOf(LiveCell(41.25, -73.99))).affects(manhattanLat, manhattanLng))
        assertFalse(BeaconChange(1, setOf(LiveCell(41.5, -73.99))).affects(manhattanLat, manhattanLng))
    }

    // endregion
}
