package compose.project.click.click.data.models

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Max chats one message can be forwarded to at once (iOS `ChatTargetPicker`). */
const val FORWARD_MAX_TARGETS: Int = 5

/** True when a message was re-sent from another chat (`metadata.forwarded`). */
fun Message.isForwarded(): Boolean {
    val root = metadata as? JsonObject ?: return false
    return runCatching { root["forwarded"]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true
}

/**
 * Whether a message can be forwarded (iOS `ConversationModel.canForward`): not deleted, not a
 * beacon, call log, plan or Click Drop, and actually delivered. Text and photos are supported.
 */
fun Message.canForward(): Boolean {
    if (id.startsWith("temp-")) return false
    if (deliveryState == MessageDeliveryState.PENDING || deliveryState == MessageDeliveryState.ERROR) return false
    if (isDisposableRoll() || isBeaconChatMessage() || planOrNull() != null) return false
    return when (messageType.ifBlank { ChatMessageType.TEXT }.lowercase()) {
        ChatMessageType.TEXT -> content.isNotBlank()
        ChatMessageType.IMAGE -> true
        else -> false
    }
}

/** The caption to carry over when forwarding a photo (image rows use " " for no caption). */
fun Message.forwardableCaption(): String = content.trim()
