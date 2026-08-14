package compose.project.click.click.ui.components // pragma: allowlist secret

import compose.project.click.click.data.models.Connection // pragma: allowlist secret
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

    @Test
    fun mapPinFallsBackToConnectionPeerWhenUserCacheIsEmpty() {
        val connection =
            Connection(
                id = "c1",
                created = 1_000L,
                expiry = 86_400_000L,
                user_ids = listOf("me", "peer-from-edge"),
            )
        assertEquals(
            "peer-from-edge",
            resolveProfilePeerUserId(
                requestedUserId = null,
                viewerUserId = "me",
                connectionId = "c1",
                connections = listOf(connection),
            ),
        )
    }
}
