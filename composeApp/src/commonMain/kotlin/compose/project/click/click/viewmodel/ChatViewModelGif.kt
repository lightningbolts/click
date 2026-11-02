package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatGif // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

/*
 * GIF messages (iOS `ConversationModel.sendGif`, click-ios `Click/Features/Chat/ConversationModel.swift`).
 * An ordinary E2EE text message whose content is the KLIPY media URL plus plaintext
 * `metadata.gif = { provider, width, height }`. Direct and group chats only (not hubs, as on iOS).
 */

internal fun ChatViewModel.sendGifImpl(gif: ChatGif) {
    val userId = _currentUserId.value ?: return
    val successState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val connectionId = currentConnectionId ?: return
    val replyTarget = _replyingTo.value
    val metadata = gif.messageMetadata(replyToId = replyTarget?.message?.id)
    _replyingTo.value = null
    viewModelScope.launch {
        _isMessageSubmitInProgress.value = true
        val localMs = Clock.System.now().toEpochMilliseconds()
        val tempId = "temp-gif-$localMs-${Random.nextLong()}"
        val me =
            AppDataManager.currentUser.value?.takeIf { it.id == userId }
                ?: User(id = userId, name = "You", createdAt = 0L)
        try {
            appendOutgoingOptimistic(
                Message(
                    id = tempId,
                    user_id = userId,
                    content = gif.content,
                    timeCreated = localMs,
                    messageType = ChatMessageType.TEXT,
                    metadata = metadata,
                    localSentAt = localMs,
                    deliveryState = MessageDeliveryState.PENDING,
                ),
                me,
            )
            val apiChatId =
                resolveOrCreateApiChatId(connectionId) ?: run {
                    markOptimisticSendFailed(tempId)
                    _messageSendError.value = "Couldn't send the GIF — unable to start chat"
                    return@launch
                }
            val sendConnectionId =
                successState.chatDetails.chat.connectionId
                    ?.takeIf { it.isNotBlank() && it != apiChatId }
                    ?: connectionId.takeIf { successState.chatDetails.groupClique == null }
            val sent =
                chatRepository.sendMessage(
                    chatId = apiChatId,
                    userId = userId,
                    content = gif.content,
                    messageType = ChatMessageType.TEXT,
                    metadata = metadata,
                    clientLocalSentAtMs = localMs,
                    connectionId = sendConnectionId,
                )
            if (sent == null) {
                markOptimisticSendFailed(tempId)
                _messageSendError.value = "Couldn't send the GIF"
                return@launch
            }
            applyInsertedMessage(sent, resolveMessageUser(userId, apiChatId) ?: me, userId, optimisticTempId = tempId)
            activateConnectionIfPending(connectionId)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            markOptimisticSendFailed(tempId)
            _messageSendError.value = "Couldn't send the GIF — ${e.redactedRestMessage().ifBlank { "error" }}"
        } finally {
            _isMessageSubmitInProgress.value = false
        }
    }
}
