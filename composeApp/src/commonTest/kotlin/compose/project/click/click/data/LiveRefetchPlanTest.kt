package compose.project.click.click.data

import compose.project.click.click.data.realtime.BeaconChange
import compose.project.click.click.data.realtime.LiveBatch
import compose.project.click.click.data.realtime.LiveCell
import compose.project.click.click.data.realtime.LiveKind
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveRefetchPlanTest {
    private val manhattan = 40.75 to -73.99

    @Test
    fun connectionsRefreshTheInboxOnly() {
        assertEquals(
            LiveRefetchPlan(inbox = true, activity = false, beacons = false),
            liveRefetchPlan(LiveBatch(setOf(LiveKind.CONNECTIONS)), manhattan),
        )
    }

    @Test
    fun activityRefreshesTheBell() {
        assertEquals(
            LiveRefetchPlan(inbox = false, activity = true, beacons = false),
            liveRefetchPlan(LiveBatch(setOf(LiveKind.ACTIVITY)), manhattan),
        )
    }

    @Test
    fun userTopicCatchUpRefreshesInboxAndActivity() {
        val plan = liveRefetchPlan(LiveBatch(LiveKind.catchUp), manhattan)
        assertEquals(LiveRefetchPlan(inbox = true, activity = true, beacons = false), plan)
    }

    @Test
    fun homeOnlyKindsLeaveTheDataLayerAlone() {
        val plan = liveRefetchPlan(LiveBatch(setOf(LiveKind.NUDGES, LiveKind.AVAILABILITY, LiveKind.DROPS)), manhattan)
        assertEquals(LiveRefetchPlan(inbox = false, activity = false, beacons = false), plan)
    }

    @Test
    fun beaconsRefetchOnlyWhenNearOrUnknown() {
        val near = LiveBatch(setOf(LiveKind.BEACONS), BeaconChange(1, setOf(LiveCell(40.7, -73.9))))
        val far = LiveBatch(setOf(LiveKind.BEACONS), BeaconChange(2, setOf(LiveCell(51.5, -0.1))))
        val anywhere = LiveBatch(setOf(LiveKind.BEACONS), BeaconChange(3, null))
        assertEquals(true, liveRefetchPlan(near, manhattan).beacons)
        assertEquals(false, liveRefetchPlan(far, manhattan).beacons)
        assertEquals(true, liveRefetchPlan(anywhere, manhattan).beacons)
        assertEquals(true, liveRefetchPlan(far, null).beacons)
    }
}
