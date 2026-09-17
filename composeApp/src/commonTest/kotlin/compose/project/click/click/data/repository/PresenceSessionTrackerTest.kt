package compose.project.click.click.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceSessionTrackerTest {
    @Test
    fun closingOneDeviceKeepsTheOtherDeviceOnline() {
        val tracker = PresenceSessionTracker()
        assertEquals(setOf("peer"), tracker.update(mapOf("phone" to "peer", "web" to "peer"), emptySet()))
        assertEquals(setOf("peer"), tracker.update(emptyMap(), setOf("phone")))
        assertEquals(emptySet(), tracker.update(emptyMap(), setOf("web")))
    }

    @Test
    fun heartbeatReplacementDoesNotBrieflyMarkPeerOffline() {
        val tracker = PresenceSessionTracker()
        tracker.update(mapOf("old" to "peer"), emptySet())
        assertEquals(setOf("peer"), tracker.update(mapOf("new" to "peer"), setOf("old")))
        // A delayed duplicate leave must not remove the replacement session.
        assertEquals(setOf("peer"), tracker.update(emptyMap(), setOf("old")))
    }

    @Test
    fun leavingOnePeerDoesNotRemoveAnotherPeer() {
        val tracker = PresenceSessionTracker()
        tracker.update(mapOf("one" to "a", "two" to "b"), emptySet())
        assertEquals(setOf("b"), tracker.update(emptyMap(), setOf("one")))
    }
}
