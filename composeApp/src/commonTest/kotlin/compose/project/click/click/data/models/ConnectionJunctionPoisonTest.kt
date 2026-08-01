package compose.project.click.click.data.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionJunctionPoisonTest {

    @Test
    fun preserve_whenLocalHasConnectionsButSnapshotEmpty() {
        assertTrue(
            shouldPreserveLocalConnectionJunctions(
                localConnectionCount = 3,
                snapshotConnectionCount = 0,
                snapshotArchivedCount = 0,
                snapshotHiddenCount = 0,
            ),
        )
    }

    @Test
    fun doNotPreserve_whenSnapshotHasConnections() {
        assertFalse(
            shouldPreserveLocalConnectionJunctions(
                localConnectionCount = 3,
                snapshotConnectionCount = 2,
                snapshotArchivedCount = 0,
                snapshotHiddenCount = 0,
            ),
        )
    }

    @Test
    fun doNotPreserve_whenSnapshotReportsArchives() {
        assertFalse(
            shouldPreserveLocalConnectionJunctions(
                localConnectionCount = 3,
                snapshotConnectionCount = 0,
                snapshotArchivedCount = 1,
                snapshotHiddenCount = 0,
            ),
        )
    }

    @Test
    fun doNotPreserve_whenLocalEmpty() {
        assertFalse(
            shouldPreserveLocalConnectionJunctions(
                localConnectionCount = 0,
                snapshotConnectionCount = 0,
                snapshotArchivedCount = 0,
                snapshotHiddenCount = 0,
            ),
        )
    }
}
