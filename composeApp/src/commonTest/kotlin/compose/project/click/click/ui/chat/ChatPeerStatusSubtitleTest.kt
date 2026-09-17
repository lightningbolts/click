package compose.project.click.click.ui.chat // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatPeerStatusSubtitleTest {
    @Test
    fun typingWinsOverOnline() {
        assertEquals("Typing…", chatPeerStatusSubtitle(isTyping = true, isOnline = true))
        assertEquals("Typing…", chatPeerStatusSubtitle(isTyping = true, isOnline = false))
    }

    @Test
    fun presenceWhenNotTyping() {
        assertEquals("Online", chatPeerStatusSubtitle(isTyping = false, isOnline = true))
        assertEquals("Offline", chatPeerStatusSubtitle(isTyping = false, isOnline = false))
    }

    @Test
    fun offlinePresenceIncludesLastSeenWhenKnown() {
        val now = 10_000_000L
        assertEquals(
            "Last seen just now",
            chatPeerStatusSubtitle(isTyping = false, isOnline = false, lastSeenAtMs = now - 30_000L, nowMs = now),
        )
        assertEquals(
            "Last seen 5m ago",
            chatPeerStatusSubtitle(isTyping = false, isOnline = false, lastSeenAtMs = now - 300_000L, nowMs = now),
        )
    }

    @Test
    fun groupPresencePrefersLiveCountThenLatestActivity() {
        val now = 20_000_000L
        assertEquals("2 online", chatGroupPresenceSubtitle(listOf(now - 60_000L), 2, now))
        assertEquals(
            "Last active 2h ago",
            chatGroupPresenceSubtitle(listOf(now - 7_200_000L, now - 86_400_000L), 0, now),
        )
        assertEquals(null, chatGroupPresenceSubtitle(listOf(null), 0, now))
    }
}
