package compose.project.click.click.notifications

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.maskAttachmentEnvelope

/**
 * Chat push bodies are built only from content decrypted on this device, or from a generic label
 * derived from `message_type`. Server-supplied `preview_text` is never shown: for E2EE chats it is
 * either ciphertext or a plaintext copy the server should not have had (mirrors iOS
 * `NotificationService`, which refuses server plaintext).
 */
internal const val PUSH_PREVIEW_MAX_CHARS: Int = 120

internal fun pushPreviewLabel(messageType: String?): String =
    when (messageType?.trim()?.lowercase()) {
        ChatMessageType.IMAGE -> "📷 Photo"
        "video" -> "🎥 Video"
        ChatMessageType.AUDIO -> "🎤 Voice message"
        ChatMessageType.FILE -> "📎 File"
        ChatMessageType.BEACON -> "📍 Shared a beacon"
        else -> "New message"
    }

/**
 * Returns the notification body. [decryptedPreview] must be plaintext produced locally; anything
 * that is still E2EE wire content (v1, group, or v2) is rejected so ciphertext never reaches the shade.
 */
internal fun chatPushBody(
    decryptedPreview: String?,
    messageType: String?,
): String {
    val text =
        decryptedPreview
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !MessageCrypto.isAnyE2eeWireContent(it) }
            ?.let(::maskAttachmentEnvelope)
            ?.replace(Regex("\\s+"), " ")
            ?.take(PUSH_PREVIEW_MAX_CHARS)
    return text ?: pushPreviewLabel(messageType)
}
