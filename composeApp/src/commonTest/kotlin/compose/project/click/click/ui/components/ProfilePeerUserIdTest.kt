package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfilePeerUserIdTest {
    @Test
    fun requestedUserIdWinsEvenWithoutAConnectionRow() {
        assertEquals(
            "peer-1",
            resolveProfilePeerUserId(
                requestedUserId = "peer-1",
                viewerUserId = "me",
                connectionId = "c1",
                connections = emptyList(),
            ),
        )
    }

    @Test
    fun blankRequestedIdFallsThroughToNullWithoutConnections() {
        assertNull(
            resolveProfilePeerUserId(
                requestedUserId = "  ",
                viewerUserId = "me",
                connectionId = "missing",
                connections = emptyList(),
            ),
        )
    }
}
