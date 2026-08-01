package compose.project.click.click.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class ProximityConnectionChangeTargetsTest {
    @Test
    fun proximityConnectionChangeTargets_dedupesAndDropsViewer() {
        val targets = proximityConnectionChangeTargets(
            peerUserIds = listOf("peer-a", " peer-a ", "", "viewer-1", "peer-b"),
            connectionIds = listOf("conn-1", "conn-1", " "),
            currentUserId = "viewer-1",
        )
        assertEquals(listOf("peer-a", "peer-b"), targets.peerUserIds)
        assertEquals(listOf("conn-1"), targets.connectionIds)
    }
}
