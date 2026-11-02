package compose.project.click.click.data.api // pragma: allowlist secret

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatPinsTest {
    @Test
    fun decodesServerShape() {
        // click-web GET /api/chat/pins
        val pins =
            Json { ignoreUnknownKeys = true }
                .decodeFromString<ChatPinsEnvelope>(
                    """{"pins":[{"message_id":"m2","pinned_by":"u1","pinned_at":"2026-11-01T10:00:00Z"},{"message_id":"m1"}]}""",
                ).pins
        assertEquals(listOf("m2", "m1"), pins.map { it.messageId })
    }

    @Test
    fun bannerCyclesNewestFirstAndWraps() {
        val pins = listOf(ChatPinDto("m3"), ChatPinDto("m2"), ChatPinDto("m1"))
        assertEquals("m2", nextPinnedMessageId(pins, "m3"))
        assertEquals("m1", nextPinnedMessageId(pins, "m2"))
        assertEquals("m3", nextPinnedMessageId(pins, "m1"))
        assertEquals("m3", nextPinnedMessageId(pins, "gone"), "an unpinned current restarts at the newest")
        assertNull(nextPinnedMessageId(emptyList(), "m1"))
    }
}
