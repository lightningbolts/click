package compose.project.click.click.data.models

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * A chat message that answers a shared Click Drop, story-reply style (`metadata.drop_reply`): a
 * typed reply, or a reaction whose (encrypted) text is the emoji. The drop is referenced by ID only;
 * each side loads its photo through its own access to it. Clients without this marker show the
 * plain text. Mirrors iOS `Click/Core/Chat/ChatDropReply.swift` and click-web `lib/drops/dropReply.ts`.
 */
data class ChatDropReply(
    val dropId: String,
    val isReaction: Boolean,
) {
    fun wire(): JsonObject =
        buildJsonObject {
            put("kind", "shared")
            put("id", dropId)
            put("reaction", isReaction)
        }

    /** The line over the photo (iOS `DropReplyHeader.label`). */
    fun label(mine: Boolean): String =
        when {
            isReaction && mine -> "You reacted to their drop"
            isReaction -> "Reacted to your drop"
            mine -> "You replied to their drop"
            else -> "Replied to your drop"
        }

    companion object {
        const val METADATA_KEY = "drop_reply"

        /** The `metadata` a drop reply is sent with (the E2EE layer adds its own keys beside it). */
        fun metadataFor(
            dropId: String,
            isReaction: Boolean,
        ): JsonObject = buildJsonObject { put(METADATA_KEY, ChatDropReply(dropId, isReaction).wire()) }

        /** Text messages only, `kind: "shared"` with a non-empty `id`; anything else is a plain message. */
        fun parse(
            messageType: String,
            metadata: JsonElement?,
        ): ChatDropReply? {
            if (messageType.ifBlank { ChatMessageType.TEXT }.lowercase() != ChatMessageType.TEXT) return null
            val reply = (metadata as? JsonObject)?.get(METADATA_KEY) as? JsonObject ?: return null
            if ((reply["kind"] as? JsonPrimitive)?.contentOrNull != "shared") return null
            val id = (reply["id"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotEmpty() } ?: return null
            return ChatDropReply(dropId = id, isReaction = (reply["reaction"] as? JsonPrimitive)?.booleanOrNull == true)
        }
    }
}

fun Message.dropReplyOrNull(): ChatDropReply? = ChatDropReply.parse(messageType, metadata)
