package compose.project.click.click.data.models

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class MessageReplyRef(
    val replyToId: String,
    val replyToContent: String,
)

/**
 * Wire prefix for encrypted-attachment envelopes (see `chat/attachments/AttachmentCrypto`).
 * Chat list previews and reply snippets must never render the raw envelope JSON — it's
 * uselessly verbose to humans and leaks key/hash material into the UI. [maskAttachmentEnvelope]
 * collapses it to a single `"📎 Attachment"` marker.
 */
private const val ATTACHMENT_ENVELOPE_PREFIX: String = "ccx:v1:"
private const val ATTACHMENT_PLACEHOLDER: String = "\uD83D\uDCCE Attachment" // 📎 Attachment

fun maskAttachmentEnvelope(content: String): String {
    return if (content.startsWith(ATTACHMENT_ENVELOPE_PREFIX)) ATTACHMENT_PLACEHOLDER else content
}

fun Message.replyRef(): MessageReplyRef? {
    val root = metadata as? JsonObject ?: return null
    val id = root["reply_to_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val snippet = root["reply_to_content"]?.jsonPrimitive?.contentOrNull ?: ""
    return MessageReplyRef(replyToId = id, replyToContent = maskAttachmentEnvelope(snippet))
}

fun replySnippetForMetadata(content: String, maxLen: Int = 140): String {
    val masked = maskAttachmentEnvelope(content)
    val oneLine = masked.replace("\n", " ").trim()
    if (oneLine.length <= maxLen) return oneLine
    return oneLine.take(maxLen - 1).trimEnd() + "…"
}
