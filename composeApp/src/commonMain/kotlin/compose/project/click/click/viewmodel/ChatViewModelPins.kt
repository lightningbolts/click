package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.api.ChatPinDto // pragma: allowlist secret
import compose.project.click.click.data.api.getChatPinsImpl // pragma: allowlist secret
import compose.project.click.click.data.api.setChatPinImpl // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Loads this chat's pins (on open and after a pin change). Failures keep what's shown. */
internal fun ChatViewModel.loadPinsImpl(chatId: String) {
    if (chatId.isBlank()) return
    pinsChatId = chatId
    viewModelScope.launch {
        chatApi.getChatPinsImpl(chatId).onSuccess { pins ->
            if (pinsChatId == chatId) _pinnedMessages.value = pins
        }
    }
}

/** Pin or unpin, optimistically; rolled back if the server says no. */
internal fun ChatViewModel.togglePinImpl(messageId: String) {
    val chatId = pinsChatId ?: return
    val before = _pinnedMessages.value
    val pinning = before.none { it.messageId == messageId }
    _pinnedMessages.value =
        if (pinning) {
            listOf(ChatPinDto(messageId, _currentUserId.value, Clock.System.now().toString())) + before
        } else {
            before.filterNot { it.messageId == messageId }
        }
    viewModelScope.launch {
        chatApi.setChatPinImpl(messageId, pinning).fold(
            onSuccess = { loadPinsImpl(chatId) },
            onFailure = {
                _pinnedMessages.value = before
                _messageSendError.value = if (pinning) "Couldn't pin that message." else "Couldn't unpin that message."
            },
        )
    }
}
