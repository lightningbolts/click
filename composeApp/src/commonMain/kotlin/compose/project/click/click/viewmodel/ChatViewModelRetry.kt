package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.planOrNull // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject

/*
 * Failed sends (iOS `ConversationModel.retrySend` / `discardFailed`): a failed text row can be
 * retried in place (same content and metadata, fresh encryption) or discarded.
 */

/** Only failed, not-yet-persisted text rows can be retried; any failed row can be discarded. */
fun Message.canRetrySend(): Boolean =
    deliveryState == MessageDeliveryState.ERROR &&
        id.startsWith("temp-") &&
        messageType.ifBlank { ChatMessageType.TEXT }.lowercase() == ChatMessageType.TEXT &&
        content.isNotBlank()

fun Message.canDiscardFailed(): Boolean = deliveryState == MessageDeliveryState.ERROR && id.startsWith("temp-")

internal fun ChatViewModel.discardFailedMessageImpl(tempId: String) {
    val state = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    _chatMessagesState.value = state.copy(messages = state.messages.filterNot { it.message.id == tempId })
}

internal fun ChatViewModel.retryFailedMessageImpl(tempId: String) {
    val state = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val failed = state.messages.firstOrNull { it.message.id == tempId } ?: return
    if (!failed.message.canRetrySend()) return
    val userId = _currentUserId.value ?: return
    val connectionId = currentConnectionId ?: return
    // Show it as sending again in place.
    _chatMessagesState.value =
        state.copy(
            messages =
                state.messages.map {
                    if (it.message.id == tempId) it.copy(message = it.message.copy(deliveryState = MessageDeliveryState.PENDING)) else it
                },
        )
    viewModelScope.launch {
        val apiChatId =
            resolveOrCreateApiChatId(connectionId) ?: run {
                markOptimisticSendFailed(tempId)
                return@launch
            }
        val sent =
            runCatching {
                chatRepository.sendMessage(
                    chatId = apiChatId,
                    userId = userId,
                    content = failed.message.content,
                    messageType = ChatMessageType.TEXT,
                    // Keep reply / plan metadata; crypto fields are rebuilt by the send path.
                    metadata = (failed.message.metadata as? JsonObject)?.takeIf { it.isNotEmpty() },
                    clientLocalSentAtMs = failed.message.localSentAt ?: Clock.System.now().toEpochMilliseconds(),
                    connectionId = connectionId.takeIf { state.chatDetails.groupClique == null },
                )
            }.getOrNull()
        if (sent == null) {
            markOptimisticSendFailed(tempId)
            _messageSendError.value = "Still couldn't send. Check your connection and try again."
            return@launch
        }
        applyInsertedMessage(sent, failed.user, userId, optimisticTempId = tempId)
        if (sent.planOrNull() != null) setPlanRsvpImpl(sent.id, compose.project.click.click.data.models.PlanRsvp.GOING)
    }
}
