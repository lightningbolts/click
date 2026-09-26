package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.api.ScheduledMessageGoneException
import compose.project.click.click.data.models.ScheduleSendTiming
import compose.project.click.click.util.formatShortDateTime
import compose.project.click.click.util.isPersistedApiChatId
import compose.project.click.click.util.redactedRestMessage
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/*
 * Send Later for direct and group chats (hubs use HubChatViewModel and don't schedule, as on iOS).
 * Scheduled rows never enter the timeline; the composer shows a "N scheduled" bar instead, and the
 * delivered message arrives as a normal realtime insert. Mirrors iOS `ConversationModel`.
 */

internal fun ChatViewModel.loadScheduledMessagesImpl(apiChatId: String) {
    val userId = _currentUserId.value ?: return
    if (!isPersistedApiChatId(apiChatId)) return
    viewModelScope.launch {
        chatRepository
            .fetchScheduledMessages(apiChatId, userId)
            .onSuccess { rows ->
                if (currentApiChatId == apiChatId) _scheduledMessages.value = rows
            }
    }
}

/**
 * Schedules [text] (already trimmed by the caller) for [sendAtEpochMs]. Clears the composer and
 * reply target only if they still hold what was scheduled. Returns false (and sets the send error)
 * on failure so the composer keeps the text.
 */
internal suspend fun ChatViewModel.scheduleMessageImpl(
    text: String,
    sendAtEpochMs: Long,
): Boolean {
    val content = text.trim()
    if (content.isEmpty() || _editingMessageId.value != null) return false
    val connectionId = currentConnectionId ?: return false
    val userId = _currentUserId.value ?: return false
    if (!ScheduleSendTiming.isValid(sendAtEpochMs, Clock.System.now().toEpochMilliseconds())) {
        _messageSendError.value = "Pick a time between a minute and a year from now."
        return false
    }
    val replyTarget = _replyingTo.value
    val successState = _chatMessagesState.value as? ChatMessagesState.Success
    val apiChatId =
        resolveOrCreateApiChatId(connectionId)?.takeIf { isPersistedApiChatId(it) } ?: run {
            _messageSendError.value = "Couldn't schedule — unable to start chat"
            return false
        }
    val sendConnectionId =
        successState
            ?.chatDetails
            ?.chat
            ?.connectionId
            ?.takeIf { it.isNotBlank() && it != apiChatId }
            ?: connectionId.takeIf { successState?.chatDetails?.groupClique == null }
    val result =
        chatRepository.scheduleMessage(
            chatId = apiChatId,
            userId = userId,
            content = content,
            replyToId = replyTarget?.message?.id,
            sendAtEpochMs = sendAtEpochMs,
            connectionId = sendConnectionId,
        )
    return result.fold(
        onSuccess = { row ->
            _scheduledMessages.value = (_scheduledMessages.value + row).sortedBy { it.sendAtEpochMs }
            if (_messageInput.value.trim() == content) updateMessageInput("")
            if (replyTarget != null && _replyingTo.value?.message?.id == replyTarget.message.id) {
                _replyingTo.value = null
            }
            _messageSendError.value = null
            _chatNotice.value = "Scheduled for ${formatShortDateTime(row.sendAtEpochMs)}"
            true
        },
        onFailure = { error ->
            _messageSendError.value = error.redactedRestMessage().ifBlank { "Couldn't schedule that message" }
            false
        },
    )
}

internal fun ChatViewModel.cancelScheduledMessageImpl(id: String) {
    val before = _scheduledMessages.value
    _scheduledMessages.value = before.filterNot { it.id == id }
    viewModelScope.launch {
        chatRepository.cancelScheduledMessage(id).onFailure { error ->
            // Already delivered (404) or a transient failure: reload the truth either way.
            if (error !is ScheduledMessageGoneException) {
                _messageSendError.value = "Couldn't cancel — it may already have been sent"
            }
            currentApiChatId?.let { loadScheduledMessagesImpl(it) }
        }
    }
}

/** An outgoing realtime insert may be a delivered scheduled message: drop rows that are due. */
internal fun ChatViewModel.pruneDeliveredScheduledMessages() {
    val now = Clock.System.now().toEpochMilliseconds()
    val current = _scheduledMessages.value
    if (current.any { it.sendAtEpochMs <= now }) {
        _scheduledMessages.value = current.filter { it.sendAtEpochMs > now }
    }
}
