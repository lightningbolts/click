package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageReplyRef
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.replyQuoteText
import compose.project.click.click.data.models.replyRef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessagePrivacyAndActionsTest {
    private fun message(
        id: String = "m1",
        content: String = "hello",
        type: String = "text",
        metadata: kotlinx.serialization.json.JsonElement? = null,
    ) = Message(id = id, user_id = "u1", content = content, timeCreated = 0L, messageType = type, metadata = metadata)

    private fun mine(message: Message) = MessageWithUser(message, User(id = "u1"), isSent = true)

    // region reply quotes are rebuilt on-device (P0-1)

    @Test
    fun replyQuotePrefersLocallyDecryptedTarget() {
        val ref = MessageReplyRef(replyToId = "m0", replyToContent = "")
        assertEquals("the original", replyQuoteText(ref, message(id = "m0", content = "the original")))
    }

    @Test
    fun replyQuoteFallsBackToLegacyExcerptThenPlaceholder() {
        assertEquals("legacy", replyQuoteText(MessageReplyRef("m0", "legacy"), localTarget = null))
        assertEquals("Original message", replyQuoteText(MessageReplyRef("m0", ""), localTarget = null))
    }

    @Test
    fun replyRefNeedsOnlyTheId() {
        val reply = message(metadata = buildJsonObject { put("reply_to_id", "m0") })
        assertEquals("m0", reply.replyRef()?.replyToId)
    }

    // endregion

    // region action gating (P0-8)

    @Test
    fun editOnlyForOwnPlainText() {
        assertTrue(canEditMessage(mine(message())))
        assertFalse(canEditMessage(mine(message(type = "image"))))
        assertFalse(canEditMessage(mine(message(type = "call_log"))))
        assertFalse(canEditMessage(mine(message(type = "beacon"))))
        assertFalse(canEditMessage(MessageWithUser(message(), User(id = "u2"), isSent = false)))
    }

    @Test
    fun lockedClickDropCannotBeExported() {
        val locked = message(type = "image", metadata = buildJsonObject { put("disposable_roll", true) })
        assertFalse(canExportMessageMedia(locked))
        val revealed =
            message(
                type = "image",
                metadata =
                    buildJsonObject {
                        put("disposable_roll", true)
                        put("collaboration_ttl", "2020-01-01T00:00:00Z")
                    },
            )
        assertTrue(canExportMessageMedia(revealed))
        assertTrue(canExportMessageMedia(message(type = "image")))
    }

    // endregion
}
