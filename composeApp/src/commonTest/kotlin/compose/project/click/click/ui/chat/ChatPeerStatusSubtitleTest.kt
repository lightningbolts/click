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
        val now = 200_000_000L
        assertEquals("2 online", chatGroupPresenceSubtitle(listOf(now - 60_000L), 2, now))
        assertEquals(
            "Last active 2h ago",
            chatGroupPresenceSubtitle(listOf(now - 7_200_000L, now - 86_400_000L), 0, now),
        )
        assertEquals(null, chatGroupPresenceSubtitle(listOf(null), 0, now))
    }

    @Test
    fun unknownTimestampDoesNotShowAnEpochAge() {
        assertEquals("Offline", chatPeerStatusSubtitle(false, false, 0L))
        assertEquals(null, chatGroupPresenceSubtitle(listOf(0L, -1L), 0))
    }

    @Test
    fun elapsedTimeAdvancesAcrossBoundariesAndHandlesClockSkew() {
        val seen = 1_000_000L
        assertEquals("just now", formatLastSeenElapsed(seen, seen - 100L))
        assertEquals("1m ago", formatLastSeenElapsed(seen, seen + 60_000L))
        assertEquals("1h ago", formatLastSeenElapsed(seen, seen + 3_600_000L))
        assertEquals("1d ago", formatLastSeenElapsed(seen, seen + 86_400_000L))
        assertEquals("1w ago", formatLastSeenElapsed(seen, seen + 604_800_000L))
        assertEquals("Online", chatPeerStatusSubtitle(false, true, seen, seen + 60_000L))
        assertEquals("Typing…", chatPeerStatusSubtitle(true, false, seen, seen + 60_000L))
    }
}
