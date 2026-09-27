package compose.project.click.click

import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.crypto.PushPreviewKeyStore
import compose.project.click.click.data.ChatMuteDuration
import compose.project.click.click.data.ChatMuteStore
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageDeliveryState
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.MessageTombstone
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.canForward
import compose.project.click.click.data.models.isDeletedPlaceholder
import compose.project.click.click.data.models.isForwarded
import compose.project.click.click.data.models.seenByPlacement
import compose.project.click.click.data.models.withTombstonePlaceholders
import compose.project.click.click.data.storage.FakeTokenStorage
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.ui.chat.ChatTimelineEntry
import compose.project.click.click.ui.chat.chatSearchMatches
import compose.project.click.click.ui.chat.firstUnreadMessageId
import compose.project.click.click.ui.chat.muteResultMessage
import compose.project.click.click.ui.chat.muteStatusLine
import compose.project.click.click.ui.chat.reactionTabs
import compose.project.click.click.ui.chat.reactorRows
import compose.project.click.click.ui.chat.typingLabel
import compose.project.click.click.ui.chat.withUnreadDivider
import compose.project.click.click.ui.components.EmojiSearch
import compose.project.click.click.ui.screens.HUB_CATEGORIES
import compose.project.click.click.ui.screens.hubCategoryOptions
import compose.project.click.click.viewmodel.canDiscardFailed
import compose.project.click.click.viewmodel.canRetrySend
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase3HelpersTest {
    private fun msg(
        id: String,
        time: Long,
        user: String = "a",
        type: String = "text",
        content: String = "hi",
        read: Boolean = false,
        state: MessageDeliveryState = MessageDeliveryState.SENT,
        metadata: kotlinx.serialization.json.JsonElement? = null,
    ) = Message(
        id = id,
        user_id = user,
        content = content,
        timeCreated = time,
        messageType = type,
        isRead = read,
        deliveryState = state,
        metadata = metadata,
    )

    private fun mwu(
        m: Message,
        viewer: String = "me",
    ) = MessageWithUser(m, User(id = m.user_id, name = m.user_id), isSent = m.user_id == viewer)

    // region mutes

    @Test
    fun muteExpiryAndCopy() {
        val mutes = mapOf("c1" to 2_000L, "c2" to ChatMuteStore.FOREVER)
        assertTrue(ChatMuteStore.isMuted(mutes, "c1", 1_000))
        assertFalse(ChatMuteStore.isMuted(mutes, "c1", 2_000))
        assertTrue(ChatMuteStore.isMuted(mutes, "c2", Long.MAX_VALUE - 1))
        assertFalse(ChatMuteStore.isMuted(mutes, "c3", 0))
        assertFalse(ChatMuteStore.isMuted(mutes, null, 0))
        assertEquals("Muted", muteStatusLine(ChatMuteStore.FOREVER, 0))
        assertNull(muteStatusLine(500, 1_000))
        assertEquals("Muted for 1 hour", muteResultMessage(ChatMuteDuration.ONE_HOUR, true))
        assertEquals("Notifications on", muteResultMessage(null, true))
        assertEquals("Couldn't mute", muteResultMessage(ChatMuteDuration.FOREVER, false))
    }

    // endregion

    // region forwarding

    @Test
    fun forwardingRules() {
        assertTrue(msg("1", 1).canForward())
        assertTrue(msg("2", 1, type = "image", content = " ").canForward())
        assertFalse(msg("temp-3", 1).canForward())
        assertFalse(msg("4", 1, state = MessageDeliveryState.ERROR).canForward())
        assertFalse(msg("5", 1, type = "call_log").canForward())
        assertFalse(msg("6", 1, type = "audio").canForward())
        assertFalse(msg("7", 1, metadata = buildJsonObject { put("disposable_roll", true) }).canForward())
        val plan =
            buildJsonObject {
                put(
                    "plan",
                    buildJsonObject {
                        put("title", "x")
                        put("starts_at", 1L)
                    },
                )
            }
        assertFalse(msg("8", 1, metadata = plan).canForward())
        assertTrue(msg("9", 1, metadata = buildJsonObject { put("forwarded", true) }).isForwarded())
        assertFalse(msg("10", 1).isForwarded())
    }

    // endregion

    // region reactions

    @Test
    fun reactorsViewerFirstAndTabsByCount() {
        val reactions =
            listOf(
                MessageReaction("1", "m", "b", "❤️", 10),
                MessageReaction("2", "m", "me", "❤️", 20),
                MessageReaction("3", "m", "c", "😂", 5),
                MessageReaction("4", "m", "b", "❤️", 30),
            )
        assertEquals(listOf("❤️" to 2, "😂" to 1), reactionTabs(reactions))
        val all = reactorRows(reactions, null, "me")
        assertEquals("me", all.first().userId)
        assertTrue(all.first().isMe)
        assertEquals(listOf("me", "b"), reactorRows(reactions, "❤️", "me").map { it.userId })
    }

    // endregion

    // region push preview keys

    private class MemoryStorage(
        private val base: TokenStorage = FakeTokenStorage(),
    ) : TokenStorage by base {
        var keys: String? = null

        override suspend fun savePushPreviewKeys(json: String?) {
            keys = json
        }

        override suspend fun getPushPreviewKeys(): String? = keys
    }

    @Test
    fun pushPreviewKeysDecryptV2AndStayBounded() =
        runTest {
            val storage = MemoryStorage()
            val key = MessageCryptoV2.generateEpochKey()
            val wire =
                MessageCryptoV2.encryptMessage(
                    metadata = MessageCryptoV2.MessageMetadata("chat-1", 3, "device-1", MessageCryptoV2.generateClientMessageId()),
                    epochKey = key,
                    plaintext = "see you at 7",
                )
            assertNull(PushPreviewKeyStore.decryptPreview(storage, wire))
            PushPreviewKeyStore.remember(storage, "chat-1", mapOf(3 to key.copyOf()))
            assertEquals("see you at 7", PushPreviewKeyStore.decryptPreview(storage, wire))
            assertNull(PushPreviewKeyStore.decryptPreview(storage, "e2e:legacy"))

            var merged: Map<String, Map<String, String>> = emptyMap()
            (1..5).forEach { epoch -> merged = PushPreviewKeyStore.merge(merged, "c", mapOf(epoch to "k$epoch")) }
            assertEquals(setOf("5", "4", "3"), merged["c"]!!.keys)
            (1..(PushPreviewKeyStore.MAX_CHATS + 3)).forEach { merged = PushPreviewKeyStore.merge(merged, "chat$it", mapOf(1 to "k")) }
            assertEquals(PushPreviewKeyStore.MAX_CHATS, merged.size)
            assertFalse("c" in merged)
        }

    // endregion

    // region read cursors + tombstones

    @Test
    fun seenByPlacesEachReaderUnderNewestReadMessage() {
        val messages = listOf(msg("m1", 100, user = "me"), msg("m2", 200, user = "me"), msg("m3", 300, user = "b"))
        val placement = seenByPlacement(messages, mapOf("b" to 250, "c" to 300, "me" to 300), viewerUserId = "me")
        assertEquals(listOf("b"), placement["m2"])
        assertEquals(listOf("c"), placement["m3"])
        assertNull(placement["m1"])
    }

    @Test
    fun tombstonesOnlyInsideLoadedWindow() {
        val loaded = listOf(mwu(msg("m1", 100)), mwu(msg("m2", 300)))
        val tombs =
            listOf(
                MessageTombstone("gone", "b", 200),
                MessageTombstone("ancient", "b", 50),
                MessageTombstone("m2", "a", 300),
            )
        val merged = withTombstonePlaceholders(loaded, tombs, { User(id = it) }, viewerUserId = "b")
        val placeholders = merged.filter { it.message.isDeletedPlaceholder() }
        assertEquals(listOf("gone"), placeholders.map { it.message.id })
        assertTrue(placeholders.single().isSent)
    }

    // endregion

    // region navigation

    @Test
    fun unreadDividerSitsAboveFirstUnread() {
        val messages = listOf(mwu(msg("m1", 1, read = true)), mwu(msg("m2", 2)), mwu(msg("m3", 3)), mwu(msg("m4", 4, user = "me")))
        assertEquals("m2", firstUnreadMessageId(messages))
        val entries =
            listOf("m4", "m3", "m2", "m1").map { id ->
                ChatTimelineEntry.MessageEntry(id, messages.first { it.message.id == id })
            }
        val withDivider = withUnreadDivider(entries, "m2")
        assertTrue(withDivider[3] is ChatTimelineEntry.UnreadDivider)
        assertEquals(entries, withUnreadDivider(entries, null))
        assertNull(firstUnreadMessageId(listOf(mwu(msg("x", 1, read = true)))))
    }

    @Test
    fun searchAndTypingLabels() {
        val messages =
            listOf(
                mwu(msg("1", 1, content = "Dinner at 7?")),
                mwu(msg("2", 2, content = "dinner works")),
                mwu(msg("3", 3, type = "image", content = "dinner")),
            )
        assertEquals(listOf("2", "1"), chatSearchMatches(messages, "DINNER"))
        assertTrue(chatSearchMatches(messages, "d").isEmpty())
        assertNull(typingLabel(emptyList()))
        assertEquals("Lena is typing…", typingLabel(listOf("Lena")))
        assertEquals("Lena and Sam are typing…", typingLabel(listOf("Lena", "Sam")))
        assertEquals("3 people are typing…", typingLabel(listOf("a", "b", "c")))
    }

    // endregion

    // region emoji, retry, hubs

    @Test
    fun emojiSearchAndRecents() {
        val names = mapOf("❤️" to "heavy black heart", "😂" to "face with tears of joy", "🔥" to "fire")
        assertEquals(listOf("❤️"), EmojiSearch.search("heart", names))
        assertEquals(listOf("😂"), EmojiSearch.search("joy face", names))
        assertEquals(names.keys.toList(), EmojiSearch.search("  ", names))
        val recents = (1..30).fold(emptyList<String>()) { acc, i -> EmojiSearch.pushRecent(acc, "e$i") }
        assertEquals(EmojiSearch.RECENTS_MAX, recents.size)
        assertEquals("e30", recents.first())
        assertEquals(listOf("e5", "e30"), EmojiSearch.pushRecent(listOf("e30", "e5"), "e5").take(2))
    }

    @Test
    fun retryOnlyFailedLocalText() {
        assertTrue(msg("temp-1", 1, state = MessageDeliveryState.ERROR).canRetrySend())
        assertFalse(msg("temp-2", 1, type = "image", state = MessageDeliveryState.ERROR).canRetrySend())
        assertTrue(msg("temp-2", 1, type = "image", state = MessageDeliveryState.ERROR).canDiscardFailed())
        assertFalse(msg("real", 1, state = MessageDeliveryState.ERROR).canRetrySend())
        assertFalse(msg("temp-3", 1, state = MessageDeliveryState.PENDING).canDiscardFailed())
    }

    @Test
    fun hubCategoriesKeepLegacyValue() {
        assertEquals(HUB_CATEGORIES, hubCategoryOptions("music"))
        assertEquals(HUB_CATEGORIES + "boardgames", hubCategoryOptions("Boardgames"))
    }

    // endregion
}
