package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class ReconnectEncounterSaveTargetsTest {

    @Test
    fun peersNeedingInsert_dedupesPeersAndSkipsBindPersistedRows() {
        val targets = listOf(
            UserProfile(id = "self", displayName = "Self"),
            UserProfile(id = "peer-a", displayName = "A"),
            UserProfile(id = "peer-a", displayName = "A duplicate"),
            UserProfile(id = "peer-b", displayName = "B"),
            UserProfile(id = "peer-c", displayName = "C"),
        )

        val peers = reconnectEncounterPeersNeedingInsert(
            targetUsers = targets,
            currentUserId = "self",
            bindEncounterPersistedPeerIds = setOf("peer-b"),
        )

        assertEquals(listOf("peer-a", "peer-c"), peers.map { it.id })
    }

    @Test
    fun peersNeedingInsert_returnsEmptyWhenBindPersistedEveryPeer() {
        val targets = listOf(
            UserProfile(id = "peer-a", displayName = "A"),
            UserProfile(id = "peer-b", displayName = "B"),
        )

        val peers = reconnectEncounterPeersNeedingInsert(
            targetUsers = targets,
            currentUserId = "self",
            bindEncounterPersistedPeerIds = setOf("peer-a", "peer-b"),
        )

        assertEquals(emptyList(), peers)
    }
}
