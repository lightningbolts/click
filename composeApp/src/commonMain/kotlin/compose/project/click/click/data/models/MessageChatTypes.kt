package compose.project.click.click.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Values for [Message.messageType] aligned with `public.messages.message_type` (lowercase in DB).
 */
object ChatMessageType {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val AUDIO = "audio"
    const val CALL_LOG = "call_log"
}

@Serializable
data class MessageMediaMetadata(
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
)

fun Message.parsedMediaMetadata(): MessageMediaMetadata? {
    val root = metadata as? JsonObject ?: return null
    val url = root["media_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: root["mediaUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    val dur = root["duration_seconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        ?: root["durationSeconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    if (url == null && dur == null) return null
    return MessageMediaMetadata(mediaUrl = url, durationSeconds = dur)
}

fun Message.mediaUrlOrNull(): String? = parsedMediaMetadata()?.mediaUrl

/** Chat list / previews: short label independent of encryption noise in [Message.content]. */
fun Message.previewLabel(): String {
    return when (messageType.lowercase()) {
        ChatMessageType.IMAGE -> {
            val cap = content.trim()
            if (cap.isNotEmpty()) cap else "Photo"
        }
        ChatMessageType.AUDIO -> {
            val cap = content.trim()
            if (cap.isNotEmpty()) cap else "Voice message"
        }
        ChatMessageType.CALL_LOG -> "Call"
        else -> content
    }
}

/** Text shown when copying a message to the clipboard. */
fun Message.copyableText(): String {
    return when (messageType.lowercase()) {
        ChatMessageType.IMAGE -> {
            val cap = content.trim()
            val url = mediaUrlOrNull()
            when {
                cap.isNotEmpty() && url != null -> "$cap\n$url"
                url != null -> url
                cap.isNotEmpty() -> cap
                else -> "Photo"
            }
        }
        ChatMessageType.AUDIO -> {
            val cap = content.trim()
            val url = mediaUrlOrNull()
            when {
                cap.isNotEmpty() && url != null -> "$cap\n$url"
                url != null -> url
                cap.isNotEmpty() -> cap
                else -> "Voice message"
            }
        }
        else -> content
    }
}

fun replySnippetForMessage(message: Message, maxLen: Int = 140): String {
    return replySnippetForMetadata(message.previewLabel(), maxLen)
}
