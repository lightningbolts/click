package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GroupCliqueDetails
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionContextMenuItemsTest {

    private val userId = "user-1"
    private val connId = "conn-1"

    private fun baseConnection(): Connection = Connection(
        id = connId,
        created = 0L,
        expiry = 0L,
        user_ids = listOf(userId, "user-2"),
        last_message_at = 1L,
    )

    private fun oneToOneDetails(unreadCount: Int = 0): ChatWithDetails =
        ChatWithDetails(
            chat = Chat(id = "chat-1", connectionId = connId),
            connection = baseConnection(),
            otherUser = User(id = "user-2", name = "Alex"),
            lastMessage = Message(
                id = "m1",
                user_id = userId,
                content = "hi",
                timeCreated = 1L,
            ),
            unreadCount = unreadCount,
            groupClique = null,
        )

    @Test
    fun oneToOne_includesNudgeArchiveAndDestructiveActions() {
        val items = buildConnectionContextMenuItems(
            chatDetails = oneToOneDetails(),
            currentUserId = userId,
            isArchived = false,
            isServerLifecycleArchived = false,
            isCore = false,
            onMenuAction = {},
        )
        val labels = items.map { it.label }
        assertTrue("Nudge" in labels)
        assertTrue("Archive" in labels)
        assertTrue("Add to Core" in labels)
        assertTrue(labels.any { it == "Remove Connection" && items.first { i -> i.label == it }.destructive })
        assertTrue(labels.any { it == "Block" && items.first { i -> i.label == it }.destructive })
    }

    @Test
    fun oneToOne_coreShowsRemoveFromCore() {
        val items = buildConnectionContextMenuItems(
            chatDetails = oneToOneDetails(),
            currentUserId = userId,
            isArchived = false,
            isServerLifecycleArchived = false,
            isCore = true,
            onMenuAction = {},
        )
        assertTrue(items.any { it.label == "Remove from Core" })
        assertFalse(items.any { it.label == "Add to Core" })
    }

    @Test
    fun group_creatorSeesDeleteGroup() {
        val details = oneToOneDetails().copy(
            groupClique = GroupCliqueDetails(
                groupId = "g1",
                name = "Crew",
                createdByUserId = userId,
                keyAnchorUserId = userId,
                memberUserIds = listOf(userId, "user-2", "user-3"),
            ),
        )
        val items = buildConnectionContextMenuItems(
            chatDetails = details,
            currentUserId = userId,
            isArchived = false,
            isServerLifecycleArchived = false,
            isCore = false,
            onMenuAction = {},
        )
        assertEquals(listOf("Mark as Unread", "Leave Group", "Delete Group"), items.map { it.label })
        assertTrue(items.last().destructive)
    }
}
