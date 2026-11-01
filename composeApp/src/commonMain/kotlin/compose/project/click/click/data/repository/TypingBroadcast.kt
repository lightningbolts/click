package compose.project.click.click.data.repository

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Wire format for the `typing` broadcast on the `chat:{chatId}` realtime channel.
 *
 * The clients disagree on key names, so we write both and read either:
 * - iOS sends and reads `{"user_id": "...", "is_typing": true|false}` (`ChatRealtimeManager.swift`).
 * - Web and older Android send and read `{"userId": "..."}` (no stop event).
 *
 * Android writes `{"userId", "user_id", "is_typing": true}` so every client decodes it. It never
 * sends a stop: web treats any `typing` event as "started typing", and peers expire the indicator
 * after a few seconds on their own.
 */
internal object TypingBroadcast {
    const val EVENT = "typing"

    private val USER_ID_KEYS = listOf("user_id", "userId")

    fun encode(userId: String): JsonObject =
        buildJsonObject {
            put("userId", userId)
            put("user_id", userId)
            put("is_typing", true)
        }

    /**
     * Returns null when the payload has no usable user id. It never throws, so one malformed
     * payload can't end the typing subscription.
     */
    fun decode(payload: JsonObject): TypingStatus? {
        val userId =
            USER_ID_KEYS.firstNotNullOfOrNull { key ->
                payload.stringOrNull(key)?.trim()?.takeIf { it.isNotEmpty() }
            } ?: return null
        // Only a real JSON boolean counts; a quoted "false" is not a stop event.
        val isTyping =
            (payload["is_typing"] as? JsonPrimitive)
                ?.takeIf { !it.isString }
                ?.booleanOrNull
                ?: true
        return TypingStatus(userId = userId, isTyping = isTyping)
    }

    private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
