package compose.project.click.click.data.models

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A GIF sent from the KLIPY picker. Wire-compatible with iOS `ChatGif`
 * (click-ios `Click/Core/Chat/ChatGif.swift`) and click-web `lib/chat/gif.ts`:
 *
 * - `message_type: text`; the (encrypted) content is the KLIPY media URL, so the server never
 *   learns which GIF was sent and clients without GIF support still show a link.
 * - `metadata.gif = { provider: "klipy", width, height }`: layout hints only, no URL or slug.
 *
 * KLIPY's terms require media to load straight from the URL its API returned (no re-hosting),
 * so GIFs are never uploaded to chat storage.
 */
data class ChatGif(
    val url: String,
    val width: Int,
    val height: Int,
) {
    /** The message text: the media URL. */
    val content: String get() = url

    val aspectRatio: Float get() = max(1, width).toFloat() / max(1, height).toFloat()

    /** `metadata.gif`. */
    fun toWire(): JsonObject =
        buildJsonObject {
            put("provider", PROVIDER)
            put("width", max(1, width))
            put("height", max(1, height))
        }

    /** The full message metadata: `gif`, plus `reply_to_id` when replying (iOS `sendGif`). */
    fun messageMetadata(replyToId: String? = null): JsonObject =
        buildJsonObject {
            put(METADATA_KEY, toWire())
            replyToId?.takeIf { it.isNotBlank() }?.let { put("reply_to_id", it) }
        }

    /**
     * Bubble size in dp: fit within 240×320, never a sliver (min 120×90). Mirrors iOS
     * `GifMessageView.fittedSize`.
     */
    fun bubbleSizeDp(): Pair<Float, Float> {
        val w = max(1, width).toFloat()
        val h = max(1, height).toFloat()
        val scale = min(MAX_BUBBLE_WIDTH_DP / w, MAX_BUBBLE_HEIGHT_DP / h)
        return max(MIN_BUBBLE_WIDTH_DP, w * scale) to max(MIN_BUBBLE_HEIGHT_DP, h * scale)
    }

    companion object {
        const val PROVIDER = "klipy"
        const val METADATA_KEY = "gif"
        const val MAX_BUBBLE_WIDTH_DP = 240f
        const val MAX_BUBBLE_HEIGHT_DP = 320f
        const val MIN_BUBBLE_WIDTH_DP = 120f
        const val MIN_BUBBLE_HEIGHT_DP = 90f

        private val klipyHost = Regex("^static\\d*\\.klipy\\.com$", RegexOption.IGNORE_CASE)

        /** KLIPY serves media from `static.klipy.com` and numbered shards (`static1`, `static2`, …). */
        fun isKlipyMediaUrl(raw: String?): Boolean {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd <= 0 || !trimmed.substring(0, schemeEnd).equals("https", ignoreCase = true)) return false
            val rest = trimmed.substring(schemeEnd + 3)
            val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
            if (authority.contains('@')) return false
            val host = authority.substringBefore(':')
            return klipyHost.matches(host)
        }

        /**
         * The GIF a decrypted message carries. Needs both the metadata marker and a KLIPY URL body,
         * so a GIF bubble can never be pointed at an arbitrary host.
         */
        fun parse(
            messageType: String,
            metadata: JsonElement?,
            content: String,
        ): ChatGif? {
            if (!messageType.equals(ChatMessageType.TEXT, ignoreCase = true)) return null
            val gif = (metadata as? JsonObject)?.get(METADATA_KEY) as? JsonObject ?: return null
            if ((gif["provider"] as? JsonPrimitive)?.contentOrNull != PROVIDER) return null
            val width = positiveInt(gif["width"]) ?: return null
            val height = positiveInt(gif["height"]) ?: return null
            val body = content.trim()
            if (!isKlipyMediaUrl(body)) return null
            return ChatGif(url = body, width = width, height = height)
        }

        /** Inbox / reply label for decrypted text that is only a GIF URL. */
        fun previewLabel(text: String): String? = if (isKlipyMediaUrl(text)) "GIF" else null

        private fun positiveInt(raw: JsonElement?): Int? {
            val p = raw as? JsonPrimitive ?: return null
            val n = p.doubleOrNull ?: p.contentOrNull?.toDoubleOrNull() ?: return null
            if (!n.isFinite() || n <= 0) return null
            return n.roundToInt().takeIf { it > 0 }
        }
    }
}

fun Message.gifOrNull(): ChatGif? = ChatGif.parse(messageType, metadata, content)
