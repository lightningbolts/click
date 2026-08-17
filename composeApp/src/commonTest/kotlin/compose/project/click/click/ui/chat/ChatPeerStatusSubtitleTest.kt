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
}
