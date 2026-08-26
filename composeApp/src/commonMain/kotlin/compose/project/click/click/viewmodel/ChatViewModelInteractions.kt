@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.toBeaconChatContent
import compose.project.click.click.data.models.toBeaconChatMetadata
import compose.project.click.click.data.models.withCoercedBeaconType
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random

/**
 * Toggle a reaction on a message. If the current user already has this reaction,
 * remove it; otherwise add it.
 */
internal fun ChatViewModel.toggleReactionImpl(
    messageId: String,
    reactionType: String,
) {
    val userId = _currentUserId.value ?: return
    val existingList = _messageReactions.value[messageId].orEmpty()
    val existing =
        existingList
            ?.firstOrNull { it.userId == userId && it.reactionType == reactionType }

    viewModelScope.launch {
        if (existing != null) {
            // Optimistic local removal
            val current = _messageReactions.value.toMutableMap()
            current[messageId] = (current[messageId] ?: emptyList()).filter { it.id != existing.id }
            _messageReactions.value = current
            chatRepository.removeReaction(messageId, userId, reactionType)
        } else {
            // Optimistic local insert
            val tempReaction =
                MessageReaction(
                    id = "temp-$messageId-$reactionType",
                    messageId = messageId,
                    userId = userId,
                    reactionType = reactionType,
                    createdAt =
                        kotlinx.datetime.Clock.System
                            .now()
                            .toEpochMilliseconds(),
                )
            val current = _messageReactions.value.toMutableMap()
            val deduped = existingList.filterNot { it.userId == userId && it.reactionType == reactionType }
            current[messageId] = deduped + tempReaction
            _messageReactions.value = current
            chatRepository.addReaction(messageId, userId, reactionType)
        }
    }
}

internal fun ChatViewModel.addReactionImpl(
    messageId: String,
    reactionType: String,
) {
    toggleReaction(messageId, reactionType)
}

internal fun ChatViewModel.removeReactionImpl(
    messageId: String,
    reactionType: String,
) {
    val userId = _currentUserId.value ?: return
    viewModelScope.launch {
        chatRepository.removeReaction(messageId, userId, reactionType)
    }
}

internal fun ChatViewModel.forwardMessageImpl(
    messageId: String,
    targetChatId: String,
) {
    val userId = _currentUserId.value ?: return
    viewModelScope.launch {
        chatRepository.forwardMessage(messageId, targetChatId, userId)
    }
}

/**
 * Sends a plaintext [ChatMessageType.BEACON] card into the active 1:1 or group chat.
 * Public map metadata — not E2EE. Records share telemetry when possible.
 */
internal fun ChatViewModel.sendBeaconMessageImpl(beacon: compose.project.click.click.data.models.MapBeacon) {
    val userId = _currentUserId.value ?: return
    val successState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val connectionId = successState.chatDetails.connection.id
    viewModelScope.launch {
        _isMessageSubmitInProgress.value = true
        var tempId: String? = null
        try {
            val content = beacon.toBeaconChatContent()
            val meta = beacon.toBeaconChatMetadata()
            val localMs = Clock.System.now().toEpochMilliseconds()
            tempId = "temp-beacon-$localMs-${Random.nextLong()}"
            val currentUserFast =
                AppDataManager.currentUser.value?.takeIf { it.id == userId }
                    ?: User(id = userId, name = "You", createdAt = 0L)
            appendOutgoingOptimistic(
                Message(
                    id = tempId!!,
                    user_id = userId,
                    content = content,
                    timeCreated = localMs,
                    messageType = ChatMessageType.BEACON,
                    metadata = meta,
                    localSentAt = localMs,
                    deliveryState = MessageDeliveryState.PENDING,
                ),
                currentUserFast,
            )
            val apiChatId =
                resolveOrCreateApiChatId(connectionId) ?: run {
                    markOptimisticSendFailed(tempId!!)
                    _messageSendError.value = "Failed to send — unable to start chat"
                    return@launch
                }
            val message =
                chatRepository.sendMessage(
                    chatId = apiChatId,
                    userId = userId,
                    content = content,
                    messageType = ChatMessageType.BEACON,
                    metadata = meta,
                    clientLocalSentAtMs = localMs,
                )
            if (message != null) {
                val coerced = message.withCoercedBeaconType()
                val currentUser =
                    resolveMessageUser(userId, apiChatId)
                        ?: currentUserFast
                applyInsertedMessage(coerced, currentUser, userId, optimisticTempId = tempId)
                activateConnectionIfPending(connectionId)
                val shareUrl = meta["share_url"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                mapBeaconRepository.recordBeaconShare(
                    beacon.id,
                    telemetry =
                        compose.project.click.click.data.api
                            .EngagementTelemetryBody(surface = "chat"),
                    shareUrl = shareUrl,
                )
            } else {
                markOptimisticSendFailed(tempId!!)
                _messageSendError.value = "Failed to share beacon"
            }
        } catch (e: Exception) {
            tempId?.let { markOptimisticSendFailed(it) }
            _messageSendError.value =
                "Failed to share beacon — ${e.redactedRestMessage().ifBlank { "error" }}"
        } finally {
            _isMessageSubmitInProgress.value = false
        }
    }
}

/**
 * Share [beacon] into an arbitrary chat (map → chat picker). Hydrates the thread if needed.
 */
internal fun ChatViewModel.sendBeaconMessageToChatImpl(
    chatId: String,
    beacon: compose.project.click.click.data.models.MapBeacon,
) {
    val userId = _currentUserId.value ?: return
    val targetChatId = chatId.trim().takeIf { it.isNotEmpty() } ?: return
    viewModelScope.launch {
        _isMessageSubmitInProgress.value = true
        var tempId: String? = null
        try {
            val content = beacon.toBeaconChatContent()
            val meta = beacon.toBeaconChatMetadata()
            val open = _chatMessagesState.value as? ChatMessagesState.Success
            val threadOpen = open?.chatDetails?.chat?.id == targetChatId
            val localMs = Clock.System.now().toEpochMilliseconds()
            val currentUserFast =
                AppDataManager.currentUser.value?.takeIf { it.id == userId }
                    ?: User(id = userId, name = "You", createdAt = 0L)
            if (threadOpen) {
                tempId = "temp-beacon-$localMs-${Random.nextLong()}"
                appendOutgoingOptimistic(
                    Message(
                        id = tempId!!,
                        user_id = userId,
                        content = content,
                        timeCreated = localMs,
                        messageType = ChatMessageType.BEACON,
                        metadata = meta,
                        localSentAt = localMs,
                        deliveryState = MessageDeliveryState.PENDING,
                    ),
                    currentUserFast,
                )
            }
            val message =
                chatRepository.sendMessage(
                    chatId = targetChatId,
                    userId = userId,
                    content = content,
                    messageType = ChatMessageType.BEACON,
                    metadata = meta,
                    clientLocalSentAtMs = localMs,
                )
            if (message != null) {
                val coerced = message.withCoercedBeaconType()
                val shareUrl = meta["share_url"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                mapBeaconRepository.recordBeaconShare(
                    beacon.id,
                    telemetry =
                        compose.project.click.click.data.api
                            .EngagementTelemetryBody(surface = "chat"),
                    shareUrl = shareUrl,
                )
                if (threadOpen) {
                    val currentUser =
                        resolveMessageUser(userId, targetChatId)
                            ?: currentUserFast
                    applyInsertedMessage(coerced, currentUser, userId, optimisticTempId = tempId)
                }
            } else {
                tempId?.let { markOptimisticSendFailed(it) }
                _messageSendError.value = "Failed to share beacon"
            }
        } catch (e: Exception) {
            tempId?.let { markOptimisticSendFailed(it) }
            _messageSendError.value =
                "Failed to share beacon — ${e.redactedRestMessage().ifBlank { "error" }}"
        } finally {
            _isMessageSubmitInProgress.value = false
        }
    }
}

internal fun ChatViewModel.searchMessagesImpl(
    chatId: String,
    query: String,
) {
    val userId = _currentUserId.value ?: return
    viewModelScope.launch {
        try {
            val results = chatRepository.searchMessages(chatId, query)
            val messagesWithUsers =
                results.mapNotNull { message ->
                    val user = chatRepository.getUserById(message.user_id)
                    if (user != null) MessageWithUser(message, user, message.user_id == userId) else null
                }
            val chatDetails = chatRepository.fetchChatWithDetails(chatId, userId)
            if (chatDetails != null) {
                _chatMessagesState.value = ChatMessagesState.Success(messagesWithUsers, chatDetails)
            }
        } catch (e: Exception) {
            println("Search error: ${e.redactedRestMessage()}")
        }
    }
}
