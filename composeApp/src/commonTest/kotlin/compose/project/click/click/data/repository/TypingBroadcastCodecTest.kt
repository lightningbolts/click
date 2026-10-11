package compose.project.click.click.data.repository

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypingBroadcastCodecTest {
    // Inner broadcast payloads as each client sends them.
    // iOS: ChatRealtimeManager.sendTyping -> {"user_id", "is_typing"}
    private val iosStart = """{"user_id":"u-ios","is_typing":true}"""
    private val iosStop = """{"user_id":"u-ios","is_typing":false}"""

    // Web: useMessageActions.broadcastTyping -> { userId: currentUserId }
    private val web = """{"userId":"u-web"}"""

    // Android before G0-3: buildJsonObject { put("userId", userId) }
    private val androidLegacy = """{"userId":"u-android"}"""

    private fun parse(raw: String): JsonElement = Json.parseToJsonElement(raw)

    @Test
    fun decodesIosPayloads() {
        assertEquals(TypingStatus("u-ios", true), decodeTypingBroadcastPayload(parse(iosStart)))
        assertEquals(TypingStatus("u-ios", false), decodeTypingBroadcastPayload(parse(iosStop)))
    }

    @Test
    fun decodesWebAndLegacyAndroidPayloadsAsTyping() {
        assertEquals(TypingStatus("u-web", true), decodeTypingBroadcastPayload(parse(web)))
        assertEquals(TypingStatus("u-android", true), decodeTypingBroadcastPayload(parse(androidLegacy)))
    }

    @Test
    fun malformedPayloadsDecodeToNull() {
        assertNull(decodeTypingBroadcastPayload(parse("""{"is_typing":true}""")))
        assertNull(decodeTypingBroadcastPayload(parse("""{"user_id":42}""")))
        assertNull(decodeTypingBroadcastPayload(parse("""["u"]""")))
        assertNull(decodeTypingBroadcastPayload(null))
    }

    @Test
    fun encodedPayloadCarriesBothIdKeysAndFlag() {
        val stop = buildTypingBroadcastPayload(userId = "me", isTyping = false)
        assertEquals("me", stop["user_id"]?.jsonPrimitive?.content)
        assertEquals("me", stop["userId"]?.jsonPrimitive?.content)
        assertEquals(false, stop["is_typing"]?.jsonPrimitive?.boolean)
        assertEquals(TypingStatus("me", false), decodeTypingBroadcastPayload(stop))
    }

    @Test
    fun malformedPayloadDoesNotEndCollector() =
        runTest {
            val out =
                flowOf(
                    parse("""{"is_typing":true}"""),
                    JsonPrimitive("garbage"),
                    parse(iosStart),
                    parse(androidLegacy.replace("u-android", "me")),
                    parse(web),
                ).decodeTypingBroadcasts(currentUserId = "me").toList()
            assertEquals(listOf(TypingStatus("u-ios", true), TypingStatus("u-web", true)), out)
        }
}
