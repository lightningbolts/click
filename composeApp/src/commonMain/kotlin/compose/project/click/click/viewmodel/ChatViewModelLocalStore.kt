package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.chat.LocalMessageStore // pragma: allowlist secret
import compose.project.click.click.data.chat.toStored // pragma: allowlist secret
import compose.project.click.click.util.isPersistedApiChatId // pragma: allowlist secret
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/*
 * Keeps [LocalMessageStore] (05 §D6) in step with what the open chat has decrypted: new and edited
 * rows are written, deleted ones (realtime deletes and tombstones) are removed.
 */

@OptIn(FlowPreview::class)
internal fun ChatViewModel.startLocalStoreSync() {
    viewModelScope.launch {
        _chatMessagesState.debounce(800L).collect { state ->
            val success = state as? ChatMessagesState.Success ?: return@collect
            val userId = _currentUserId.value ?: return@collect
            val chatId =
                success.chatDetails.chat.id
                    ?.takeIf { isPersistedApiChatId(it) }
                    ?: currentApiChatId?.takeIf { isPersistedApiChatId(it) }
                    ?: return@collect
            val threadKey = currentConnectionId ?: success.chatDetails.connection.id
            val rows = success.messages.mapNotNull { it.message.toStored(chatId, threadKey) }
            runCatching { LocalMessageStore.upsert(userId, rows) }
        }
    }
    viewModelScope.launch {
        _tombstones.collect { tombstones ->
            val userId = _currentUserId.value ?: return@collect
            if (tombstones.isNotEmpty()) runCatching { LocalMessageStore.remove(userId, tombstones.keys) }
        }
    }
}
