package compose.project.click.click.util

import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GroupCliqueDetails
import compose.project.click.click.data.models.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionPeerDedupeTest {

    private fun connection(
        id: String,
        userIds: List<String>,
        status: String? = "active",
        created: Long = 1_000L,
        lastMessageAt: Long? = null,
        isGroup: Boolean = false,
    ): Connection = Connection(
        id = id,
        created = created,
        expiry = created + 86_400_000L,
        user_ids = userIds,
        status = status,
        last_message_at = lastMessageAt,
        isGroup = isGroup,
    )

    @Test
    fun dedupe_samePeerDifferentIds_keepsOne() {
        val a = connection("conn-old", listOf("me", "peer"), status = "active", created = 100)
        val b = connection("conn-new", listOf("me", "peer"), status = "active", created = 200)
        val result = dedupeOneToOneConnectionsByPeer("me", listOf(a, b))
        assertEquals(1, result.size)
        assertEquals("conn-new", result.single().id)
    }

    @Test
    fun dedupe_swappedUserIdsOrder_samePeer_keepsOne() {
        val a = connection("conn-ab", listOf("me", "peer"), status = "pending", created = 100)
        val b = connection("conn-ba", listOf("peer", "me"), status = "active", created = 50)
        val result = dedupeOneToOneConnectionsByPeer("me", listOf(a, b))
        assertEquals(1, result.size)
        assertEquals("conn-ba", result.single().id) // active beats pending
    }

    @Test
    fun dedupe_prefersKeptOverActive() {
        val active = connection("c-active", listOf("me", "peer"), status = "active", created = 999)
        val kept = connection("c-kept", listOf("me", "peer"), status = "kept", created = 1)
        val result = dedupeOneToOneConnectionsByPeer("me", listOf(active, kept))
        assertEquals("c-kept", result.single().id)
    }

    @Test
    fun dedupe_prefersNewerActivityWhenStatusEqual() {
        val older = connection(
            "c-old",
            listOf("me", "peer"),
            status = "active",
            created = 100,
            lastMessageAt = 100,
        )
        val newer = connection(
            "c-new",
            listOf("me", "peer"),
            status = "active",
            created = 50,
            lastMessageAt = 500,
        )
        val result = dedupeOneToOneConnectionsByPeer("me", listOf(older, newer))
        assertEquals("c-new", result.single().id)
    }

    @Test
    fun dedupe_preservesGroupRowsAndDistinctPeers() {
        val peerA = connection("c-a", listOf("me", "peer-a"))
        val peerB = connection("c-b", listOf("me", "peer-b"))
        val group = connection(
            "c-group",
            listOf("me", "peer-a", "peer-b"),
            status = "active",
            isGroup = true,
        )
        val result = dedupeOneToOneConnectionsByPeer("me", listOf(peerA, peerB, group))
        assertEquals(3, result.size)
        assertEquals(setOf("c-a", "c-b", "c-group"), result.map { it.id }.toSet())
    }

    @Test
    fun dedupe_blankViewer_stillCollapsesSamePeerPair() {
        val a = connection("conn-ab", listOf("me", "peer"), status = "pending", created = 1)
        val b = connection("conn-ba", listOf("peer", "me"), status = "active", created = 2)
        val c = connection("other", listOf("me", "peer2"))
        val result = dedupeOneToOneConnectionsByPeer("  ", listOf(a, b, c))
        assertEquals(2, result.size)
        assertEquals("conn-ba", result.first { oneToOnePeerPairKey(it.user_ids) == "me|peer" }.id)
        assertTrue(result.any { it.id == "other" })
    }

    @Test
    fun dedupeChats_wrongOtherUserStillCollapsesByUserIds() {
        val a = chatRow("conn-a", "peer", status = "pending", created = 100).copy(
            otherUser = User(id = "me", name = "Wrong", createdAt = 0L),
        )
        val b = chatRow("conn-b", "peer", status = "active", created = 200).copy(
            connection = connection("conn-b", listOf("peer", "me"), status = "active", created = 200),
            otherUser = User(id = "peer", name = "Peer", createdAt = 0L),
        )
        val result = dedupeOneToOneChatsByPeer(listOf(a, b))
        assertEquals(1, result.size)
        assertEquals("conn-b", result.single().connection.id)
    }

    @Test
    fun preferOneToOne_archivedLosesToPending() {
        val archived = connection("arch", listOf("me", "peer"), status = "archived", created = 999)
        val pending = connection("pend", listOf("me", "peer"), status = "pending", created = 1)
        assertEquals("pend", preferOneToOneConnection(archived, pending).id)
    }

    @Test
    fun dedupeChats_samePeerDifferentConnectionIds_keepsOne() {
        val older = chatRow("conn-old", "peer", status = "active", created = 100)
        val newer = chatRow("conn-new", "peer", status = "kept", created = 50)
        val group = ChatWithDetails(
            chat = Chat(id = "g-chat"),
            connection = connection(
                "c-group",
                listOf("me", "peer", "other"),
                status = "active",
                isGroup = true,
            ),
            otherUser = User(id = "group-label", name = "Group", createdAt = 0L),
            lastMessage = null,
            unreadCount = 0,
            groupClique = GroupCliqueDetails(
                groupId = "g1",
                name = "Crew",
                createdByUserId = "me",
                keyAnchorUserId = "me",
                memberUserIds = listOf("me", "peer", "other"),
            ),
        )
        val result = dedupeOneToOneChatsByPeer(listOf(older, newer, group))
        assertEquals(2, result.size)
        assertEquals("conn-new", result.first { it.groupClique == null }.connection.id)
        assertEquals("c-group", result.first { it.groupClique != null }.connection.id)
    }

    private fun chatRow(
        connectionId: String,
        peerId: String,
        status: String = "active",
        created: Long = 1_000L,
    ): ChatWithDetails = ChatWithDetails(
        chat = Chat(id = "chat-$connectionId"),
        connection = connection(connectionId, listOf("me", peerId), status = status, created = created),
        otherUser = User(id = peerId, name = "Peer", createdAt = 0L),
        lastMessage = null,
        unreadCount = 0,
    )
}
