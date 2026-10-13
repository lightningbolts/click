package compose.project.click.click.data

import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GroupCliqueDetails
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.syntheticConnectionForGroupClique
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InboxUnreadBadgeTest {
    private fun direct(
        id: String,
        unread: Int,
    ) = ChatWithDetails(
        chat = Chat(id = "chat-$id", connectionId = id),
        connection =
            Connection(
                id = id,
                created = 1L,
                expiry = Long.MAX_VALUE,
                user_ids = listOf("viewer", "peer-$id"),
                status = "kept",
            ),
        otherUser = User(id = "peer-$id", name = "Peer"),
        lastMessage = null,
        unreadCount = unread,
    )

    private fun group(
        id: String,
        unread: Int,
    ): ChatWithDetails {
        val members = listOf("viewer", "a", "b")
        return ChatWithDetails(
            chat = Chat(id = "chat-$id", groupId = id),
            connection = syntheticConnectionForGroupClique(groupId = id, memberUserIds = members),
            otherUser = User(id = "a", name = "Member"),
            lastMessage = null,
            unreadCount = unread,
            groupClique =
                GroupCliqueDetails(
                    groupId = id,
                    name = "Group",
                    createdByUserId = "viewer",
                    keyAnchorUserId = "a",
                    memberUserIds = members,
                ),
        )
    }

    @Test
    fun sumsActiveChatsAndGroupsButNotArchivedOrHidden() {
        val chats = listOf(direct("c1", 3), direct("c2", 4), direct("c3", 5), group("g1", 2))

        assertEquals(14, InboxUnreadBadge.total(chats, emptySet(), emptySet()))
        // Archived and hidden 1:1 chats aren't in Active (iOS sums `active` + `groups`).
        assertEquals(5, InboxUnreadBadge.total(chats, archivedIds = setOf("c2"), hiddenIds = setOf("c3")))
    }

    @Test
    fun ignoresNegativeCounts() {
        assertEquals(1, InboxUnreadBadge.total(listOf(direct("c1", -4), direct("c2", 1)), emptySet(), emptySet()))
    }

    @Test
    fun labelCapsAt99Plus() {
        assertNull(InboxUnreadBadge.label(0))
        assertNull(InboxUnreadBadge.label(-1))
        assertEquals("1", InboxUnreadBadge.label(1))
        assertEquals("99", InboxUnreadBadge.label(99))
        assertEquals("99+", InboxUnreadBadge.label(100))
        assertEquals("99+", InboxUnreadBadge.label(4_200))
    }

    @Test
    fun talkBackReadsTheUnreadCount() {
        assertEquals("Clicks, 3 unread", InboxUnreadBadge.contentDescription("Clicks", 3))
        assertEquals("Clicks, 120 unread", InboxUnreadBadge.contentDescription("Clicks", 120))
        assertEquals("Clicks", InboxUnreadBadge.contentDescription("Clicks", 0))
    }
}
