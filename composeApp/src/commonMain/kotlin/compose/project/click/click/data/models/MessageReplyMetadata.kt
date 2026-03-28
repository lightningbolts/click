package compose.project.click.click.data.models

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class MessageReplyRef(
    val replyToId: String,
    val replyToContent: String,
)

fun Message.replyRef(): MessageReplyRef? {
    val root = metadata as? JsonObject ?: return null
    val id = root["reply_to_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val snippet = root["reply_to_content"]?.jsonPrimitive?.contentOrNull ?: ""
    return MessageReplyRef(replyToId = id, replyToContent = snippet)
}

fun replySnippetForMetadata(content: String, maxLen: Int = 140): String {
    val oneLine = content.replace("\n", " ").trim()
    if (oneLine.length <= maxLen) return oneLine
    return oneLine.take(maxLen - 1).trimEnd() + "…"
}
