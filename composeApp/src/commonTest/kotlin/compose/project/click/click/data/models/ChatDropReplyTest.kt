package compose.project.click.click.data.models

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatDropReplyTest {
    private fun meta(raw: String) = Json.parseToJsonElement(raw)

    @Test
    fun parsesTheIosAndWebWireShape() {
        assertEquals(
            ChatDropReply("d1", isReaction = true),
            ChatDropReply.parse("text", meta("""{"drop_reply":{"kind":"shared","id":"d1","reaction":true},"crypto_version":2}""")),
        )
        assertEquals(
            ChatDropReply("d1", isReaction = false),
            ChatDropReply.parse("TEXT", meta("""{"drop_reply":{"kind":"shared","id":"d1"}}""")),
        )
        assertEquals(
            ChatDropReply("d1", isReaction = false),
            ChatDropReply.parse("", meta("""{"drop_reply":{"kind":"shared","id":"d1"}}""")),
        )
    }

    @Test
    fun anythingElseIsAPlainMessage() {
        assertNull(ChatDropReply.parse("image", meta("""{"drop_reply":{"kind":"shared","id":"d1"}}""")))
        assertNull(ChatDropReply.parse("text", meta("""{"drop_reply":{"kind":"event","id":"d1"}}""")))
        assertNull(ChatDropReply.parse("text", meta("""{"drop_reply":{"kind":"shared","id":""}}""")))
        assertNull(ChatDropReply.parse("text", meta("""{"drop_reply":{"kind":"shared","id":7}}""")))
        assertNull(ChatDropReply.parse("text", meta("""{"drop_reply":"d1"}""")))
        assertNull(ChatDropReply.parse("text", meta("""{"other":1}""")))
        assertNull(ChatDropReply.parse("text", null))
    }

    @Test
    fun theWireRoundTrips() {
        val metadata = ChatDropReply.metadataFor("d9", isReaction = true)
        assertEquals("""{"drop_reply":{"kind":"shared","id":"d9","reaction":true}}""", metadata.toString())
        assertEquals(ChatDropReply("d9", true), ChatDropReply.parse("text", metadata))
        val message = Message(id = "m", user_id = "u", content = "🔥", timeCreated = 0, metadata = metadata)
        assertEquals(ChatDropReply("d9", true), message.dropReplyOrNull())
    }

    @Test
    fun labelsMatchIos() {
        assertEquals("You reacted to their drop", ChatDropReply("d", true).label(mine = true))
        assertEquals("Reacted to your drop", ChatDropReply("d", true).label(mine = false))
        assertEquals("You replied to their drop", ChatDropReply("d", false).label(mine = true))
        assertEquals("Replied to your drop", ChatDropReply("d", false).label(mine = false))
    }
}
