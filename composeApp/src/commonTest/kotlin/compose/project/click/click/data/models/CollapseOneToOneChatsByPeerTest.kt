package compose.project.click.click.data.models

import kotlin.test.Test
import kotlin.test.assertEquals

class CollapseOneToOneChatsByPeerTest {

    @Test
    fun collapse_keepsRicherGeoThenMoreRecentDuplicateForSamePeer() {
        val sparseOlder = stubOneToOne(
            connectionId = "conn-sparse",
            peerId = "peer-a",
            created = 100L,
            lastMessageAt = 100L,
            unread = 0,
            geo = null,
        )
        val richNewer = stubOneToOne(
            connectionId = "conn-rich",
            peerId = "peer-a",
            created = 50L,
            lastMessageAt = 50L,
            unread = 0,
            geo = GeoLocation(lat = 47.6, lon = -122.3),
        )
        val otherPeer = stubOneToOne(
            connectionId = "conn-b",
            peerId = "peer-b",
            created = 10L,
            lastMessageAt = 10L,
        )
        val group = stubDetails(
            id = "group-1",
            title = "Crew",
            isGroup = true,
        )

        val collapsed = collapseOneToOneChatsByPeer(
            chats = listOf(sparseOlder, richNewer, otherPeer, group),
            viewerUserId = "viewer",
            activityTs = { it.connection.oneToOneCollapseRecencyMs() },
        )

        assertEquals(
            listOf("conn-rich", "conn-b", "group-1"),
            collapsed.map { it.connection.id },
        )
    }

    @Test
    fun collapse_prefersHigherUnreadWhenActivityTied() {
        val lowUnread = stubOneToOne(
            connectionId = "conn-low",
            peerId = "peer-a",
            created = 200L,
            lastMessageAt = 200L,
            unread = 1,
        )
        val highUnread = stubOneToOne(
            connectionId = "conn-high",
            peerId = "peer-a",
            created = 200L,
            lastMessageAt = 200L,
            unread = 5,
        )

        val collapsed = collapseOneToOneChatsByPeer(
            chats = listOf(lowUnread, highUnread),
            viewerUserId = "viewer",
            activityTs = { it.connection.oneToOneCollapseRecencyMs() },
        )

        assertEquals(listOf("conn-high"), collapsed.map { it.connection.id })
    }

    @Test
    fun collapse_returnsInputWhenViewerBlank() {
        val chat = stubOneToOne(
            connectionId = "conn-a",
            peerId = "peer-a",
            created = 1L,
            lastMessageAt = 1L,
        )
        val input = listOf(chat)
        assertEquals(
            input,
            collapseOneToOneChatsByPeer(input, viewerUserId = null) { 0L },
        )
    }
}

private fun stubOneToOne(
    connectionId: String,
    peerId: String,
    created: Long,
    lastMessageAt: Long,
    unread: Int = 0,
    geo: GeoLocation? = null,
): ChatWithDetails {
    val conn = Connection(
        id = connectionId,
        created = created,
        expiry = Long.MAX_VALUE,
        geo_location = geo,
        user_ids = listOf("viewer", peerId),
        status = "kept",
        last_message_at = lastMessageAt,
    )
    return ChatWithDetails(
        chat = Chat(
            id = "chat-$connectionId",
            connectionId = connectionId,
            messages = emptyList(),
        ),
        connection = conn,
        otherUser = User(id = peerId, name = peerId),
        lastMessage = null,
        unreadCount = unread,
    )
}

private fun stubDetails(
    id: String,
    title: String,
    isGroup: Boolean = false,
): ChatWithDetails {
    val viewer = "viewer"
    val peer = "peer-$id"
    val conn = if (isGroup) {
        syntheticConnectionForGroupClique(groupId = id, memberUserIds = listOf(viewer, peer, "p3"))
    } else {
        Connection(
            id = id,
            created = 1L,
            expiry = Long.MAX_VALUE,
            user_ids = listOf(viewer, peer),
            status = "kept",
        )
    }
    val gc = if (isGroup) {
        GroupCliqueDetails(
            groupId = id,
            name = title,
            createdByUserId = viewer,
            keyAnchorUserId = peer,
            memberUserIds = conn.user_ids,
        )
    } else {
        null
    }
    return ChatWithDetails(
        chat = Chat(
            id = "chat-$id",
            connectionId = if (isGroup) null else id,
            groupId = if (isGroup) id else null,
            messages = emptyList(),
        ),
        connection = conn,
        otherUser = User(id = peer, name = if (isGroup) "Member" else title),
        lastMessage = null,
        unreadCount = 0,
        groupClique = gc,
        groupMemberUsers = if (isGroup) {
            listOf(User(id = peer, name = "Member"))
        } else {
            emptyList()
        },
    )
}
