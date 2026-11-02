package compose.project.click.click

import compose.project.click.click.data.ChatMuteDuration
import compose.project.click.click.data.ChatMuteStore
import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GroupCliqueDetails
import compose.project.click.click.data.models.User
import compose.project.click.click.ui.chat.muteIds
import compose.project.click.click.ui.chat.muteResultMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mute menu (iOS `MuteMenu` / `ConversationListModel.isMuted`, click-ios `Click/Features/Clicks`). */
class ChatMuteMenuTest {
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun durationsMatchIos() {
        assertEquals(
            listOf("For 1 hour", "For 8 hours", "For 1 week", "Until I turn it back on"),
            ChatMuteDuration.entries.map { it.label },
        )
        assertEquals(now + hour, ChatMuteStore.muteUntilEpochMs(ChatMuteDuration.ONE_HOUR, now))
        assertEquals(now + 8 * hour, ChatMuteStore.muteUntilEpochMs(ChatMuteDuration.EIGHT_HOURS, now))
        assertEquals(now + 7 * 24 * hour, ChatMuteStore.muteUntilEpochMs(ChatMuteDuration.ONE_WEEK, now))
        assertEquals(ChatMuteStore.FOREVER, ChatMuteStore.muteUntilEpochMs(ChatMuteDuration.FOREVER, now))
    }

    @Test
    fun mutedUntilWireValue() {
        assertNull(ChatMuteStore.mutedUntilIso(ChatMuteStore.FOREVER))
        assertEquals("2023-11-14T22:13:20Z", ChatMuteStore.mutedUntilIso(now))
    }

    @Test
    fun aliasesAndExpiry() {
        val mutes = mapOf("conn-1" to now + hour, "chat-2" to ChatMuteStore.FOREVER, "old" to now - 1)
        assertTrue(ChatMuteStore.isMuted(mutes, listOf("chat-1", "conn-1"), now))
        assertFalse(ChatMuteStore.isMuted(mutes, listOf("chat-1", "conn-1"), now + hour))
        assertTrue(ChatMuteStore.isMuted(mutes, listOf(null, "chat-2"), now + 1_000 * hour))
        assertFalse(ChatMuteStore.isMuted(mutes, listOf("old", null), now))
        assertFalse(ChatMuteStore.isMuted(mutes, emptyList(), now))
    }

    @Test
    fun nextExpiryIgnoresForeverAndPast() {
        val mutes = mapOf("a" to now + 8 * hour, "b" to now + hour, "c" to ChatMuteStore.FOREVER, "d" to now - 5)
        assertEquals(now + hour, ChatMuteStore.nextExpiryEpochMs(mutes, now))
        assertEquals(now + 8 * hour, ChatMuteStore.nextExpiryEpochMs(mutes, now + hour))
        assertNull(ChatMuteStore.nextExpiryEpochMs(mutes, now + 8 * hour))
        assertNull(ChatMuteStore.nextExpiryEpochMs(emptyMap(), now))
    }

    @Test
    fun muteIdsAddConnectionForDirectChatsOnly() {
        val direct = details(chatId = "chat-1", connectionId = "conn-1", group = false)
        assertEquals(listOf("chat-1", "conn-1"), direct.muteIds())
        val group = details(chatId = "chat-g", connectionId = "g1", group = true)
        assertEquals(listOf("chat-g"), group.muteIds())
        assertEquals(listOf("conn-1"), details(chatId = null, connectionId = "conn-1", group = false).muteIds())
    }

    @Test
    fun toastCopyMatchesIos() {
        assertEquals("Notifications muted", muteResultMessage(ChatMuteDuration.ONE_WEEK, true))
        assertEquals("Notifications on", muteResultMessage(null, true))
        assertEquals("Couldn't change notifications. Try again.", muteResultMessage(null, false))
    }

    private fun details(
        chatId: String?,
        connectionId: String,
        group: Boolean,
    ) = ChatWithDetails(
        chat = Chat(id = chatId),
        connection =
            Connection(
                id = connectionId,
                created = 0L,
                expiry = 1L,
                user_ids = listOf("me", "peer"),
                status = "active",
                isGroup = group,
            ),
        otherUser = User(id = "peer", name = "Peer", createdAt = 0L),
        lastMessage = null,
        unreadCount = 0,
        groupClique =
            if (group) {
                GroupCliqueDetails(
                    groupId = connectionId,
                    name = "Crew",
                    createdByUserId = "me",
                    keyAnchorUserId = "me",
                    memberUserIds = listOf("me", "peer"),
                )
            } else {
                null
            },
    )
}
