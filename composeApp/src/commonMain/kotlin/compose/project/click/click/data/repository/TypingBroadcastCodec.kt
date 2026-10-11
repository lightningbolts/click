package compose.project.click.click.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Typing broadcast on `chat:<chatId>` (sheet 08 G0-3 / W-2).
 *
 * iOS sends `{user_id, is_typing}`, web and legacy Android send `{userId}`. Android sends both id
 * keys during the transition so every client can read it.
 */
internal fun buildTypingBroadcastPayload(
    userId: String,
    isTyping: Boolean,
): JsonObject =
    buildJsonObject {
        put("user_id", userId)
        put("userId", userId)
        put("is_typing", isTyping)
    }

/**
 * Lenient decode of one typing broadcast. Accepts `user_id` or `userId`; a missing `is_typing`
 * means `true`. Returns null for a payload without a usable user id (never throws).
 */
internal fun decodeTypingBroadcastPayload(payload: JsonElement?): TypingStatus? {
    val obj = payload as? JsonObject ?: return null
    val userId =
        sequenceOf("user_id", "userId")
            .mapNotNull { key -> (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull }
            .firstOrNull { it.isNotBlank() }
            ?: return null
    val isTypingElement = obj["is_typing"] as? JsonPrimitive
    val isTyping =
        when {
            isTypingElement == null -> true
            else -> isTypingElement.booleanOrNull ?: true
        }
    return TypingStatus(userId = userId, isTyping = isTyping)
}

/**
 * Per-message lenient decode for the typing collector: a malformed payload is logged and skipped,
 * so it never ends the flow. Own broadcasts are dropped.
 */
internal fun Flow<JsonElement?>.decodeTypingBroadcasts(currentUserId: String): Flow<TypingStatus> =
    mapNotNull { raw ->
        val status =
            runCatching { decodeTypingBroadcastPayload(raw) }
                .onFailure { println("ChatRepository: typing payload decode failed: ${it::class.simpleName}") }
                .getOrNull()
        if (status == null) {
            println("ChatRepository: skipped malformed typing payload")
            return@mapNotNull null
        }
        status.takeIf { it.userId != currentUserId }
    }
