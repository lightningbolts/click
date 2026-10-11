package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GroupCliqueDetails
import compose.project.click.click.data.models.User
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** G0-11: `has_begun` means someone sent a message, not that the chat was opened. */
class ChatHasBegunTest {
    private fun details(
        hasBegun: Boolean,
        group: GroupCliqueDetails? = null,
    ) = ChatWithDetails(
        chat = Chat(id = "ch1", connectionId = "c1"),
        connection = Connection(id = "c1", created = 0L, expiry = 0L, user_ids = listOf("u1", "u2"), has_begun = hasBegun),
        otherUser = User(id = "u2"),
        lastMessage = null,
        unreadCount = 0,
        groupClique = group,
    )

    @Test
    fun hasBegunStaysFalseAfterOpenAndIsWrittenOnTheFirstSend() {
        // Opening the chat no longer writes anything; the first successful send does.
        val opened = details(hasBegun = false)
        assertTrue(shouldMarkChatBegunAfterSend(opened))
        val afterFirstSend = opened.copy(connection = opened.connection.copy(has_begun = true))
        assertFalse(shouldMarkChatBegunAfterSend(afterFirstSend))
    }

    @Test
    fun groupChatsNeverWriteHasBegun() {
        val group = GroupCliqueDetails("g1", "Crew", "u1", "u1", listOf("u1", "u2", "u3"))
        assertFalse(shouldMarkChatBegunAfterSend(details(hasBegun = false, group = group)))
    }
}
