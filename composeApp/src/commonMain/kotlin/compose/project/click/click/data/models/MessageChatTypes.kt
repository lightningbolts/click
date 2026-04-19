package compose.project.click.click.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
    /** Encrypted arbitrary attachment — decrypted body is an `AttachmentCrypto` envelope. */
    const val FILE = "file"
}

/** Client / UI lifecycle for an outgoing or hydrated inbox row (not a DB enum). */
@Serializable
enum class MessageDeliveryState {
    /** Optimistic row; not yet confirmed by click-web insert. */
    PENDING,
    /** Persisted on the server; recipient has not yet ack'd device receipt. */
    SENT,
    /** At least one recipient client reported device receipt ([Message.deliveredAt]). */
    DELIVERED,
    READ,
    ERROR,
}

/**
 * Derives [MessageDeliveryState] from [Message.readAt], [Message.deliveredAt], and
 * persistence (SENT) while preserving in-flight [MessageDeliveryState.PENDING] /
 * [MessageDeliveryState.ERROR] rows.
 */
fun Message.withDbDerivedDeliveryState(): Message =
    when (deliveryState) {
        MessageDeliveryState.PENDING,
        MessageDeliveryState.ERROR,
        -> this
        MessageDeliveryState.SENT,
        MessageDeliveryState.DELIVERED,
        MessageDeliveryState.READ,
        -> copy(
            deliveryState =
                when {
                    readAt != null -> MessageDeliveryState.READ
                    deliveredAt != null -> MessageDeliveryState.DELIVERED
                    else -> MessageDeliveryState.SENT
                },
        )
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

fun Message.isEncryptedMedia(): Boolean {
    val root = metadata as? JsonObject ?: return false
    return root["is_encrypted_media"]?.jsonPrimitive?.booleanOrNull == true ||
    root["is_encrypted_media"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true) == true ||
    root["isEncryptedMedia"]?.jsonPrimitive?.booleanOrNull == true ||
    root["isEncryptedMedia"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true) == true
}

fun Message.originalMimeTypeOrNull(): String? {
    val root = metadata as? JsonObject ?: return null
    return root["original_mime_type"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: root["originalMimeType"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

fun Message.audioCacheFileExtension(): String {
    val mt = originalMimeTypeOrNull()?.lowercase() ?: return "m4a"
    return when {
        "wav" in mt -> "wav"
        "webm" in mt -> "webm"
        "ogg" in mt -> "ogg"
        "mpeg" in mt || "mp3" in mt -> "mp3"
        else -> "m4a"
    }
}

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
        ChatMessageType.FILE -> "File"
        // Mask raw attachment envelopes (`ccx:v1:{...}`) so chat-list previews never
        // leak the encrypted JSON before the client has a chance to decrypt it.
        else -> maskAttachmentEnvelope(content)
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
