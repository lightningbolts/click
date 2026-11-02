package compose.project.click.click.data.repository // pragma: allowlist secret

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypingBroadcastTest {
    private fun parse(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun decodesIosPayload() {
        // Exact inner payload iOS sends from ChatRealtimeManager.sendTyping.
        val status = TypingBroadcast.decode(parse("""{"user_id":"ios-user","is_typing":true}"""))
        assertEquals(TypingStatus(userId = "ios-user", isTyping = true), status)
    }

    @Test
    fun decodesIosStop() {
        val status = TypingBroadcast.decode(parse("""{"user_id":"ios-user","is_typing":false}"""))
        assertEquals(TypingStatus(userId = "ios-user", isTyping = false), status)
    }

    @Test
    fun decodesWebAndLegacyAndroidPayload() {
        val status = TypingBroadcast.decode(parse("""{"userId":"web-user"}"""))
        assertEquals(TypingStatus(userId = "web-user", isTyping = true), status)
    }

    @Test
    fun prefersSnakeCaseWhenBothPresent() {
        val status = TypingBroadcast.decode(parse("""{"userId":"a","user_id":"b"}"""))
        assertEquals("b", status?.userId)
    }

    @Test
    fun malformedPayloadsReturnNullInsteadOfThrowing() {
        assertNull(TypingBroadcast.decode(parse("""{}""")))
        assertNull(TypingBroadcast.decode(parse("""{"user_id":""}""")))
        assertNull(TypingBroadcast.decode(parse("""{"user_id":42}""")))
        assertNull(TypingBroadcast.decode(parse("""{"user_id":null}""")))
    }

    @Test
    fun nonBooleanIsTypingDefaultsToTyping() {
        val status = TypingBroadcast.decode(parse("""{"user_id":"u","is_typing":"yes"}"""))
        assertEquals(true, status?.isTyping)
    }

    @Test
    fun encodedPayloadCarriesEveryClientsKeys() {
        val payload = TypingBroadcast.encode("me")
        assertEquals("me", payload["userId"]!!.jsonPrimitive.content)
        assertEquals("me", payload["user_id"]!!.jsonPrimitive.content)
        assertEquals(true, payload["is_typing"]!!.jsonPrimitive.boolean)
        // Round-trips through our own decoder.
        assertEquals(TypingStatus("me", true), TypingBroadcast.decode(payload))
    }

    @Test
    fun quotedFalseIsNotAStopEvent() {
        val status = TypingBroadcast.decode(parse("""{"user_id":"u","is_typing":"false"}"""))
        assertEquals(TypingStatus(userId = "u", isTyping = true), status)
    }

    @Test
    fun blankSnakeCaseIdFallsBackToCamelCase() {
        val status = TypingBroadcast.decode(parse("""{"user_id":"  ","userId":"web-user"}"""))
        assertEquals(TypingStatus(userId = "web-user", isTyping = true), status)
    }
}
