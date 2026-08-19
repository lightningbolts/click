@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Enter editing mode for a sent message.
 * Pre-fills the message input with the current content.
 */
internal fun ChatViewModel.startEditMessageImpl(
    messageId: String,
    currentContent: String,
) {
    _replyingTo.value = null
    _editingMessageId.value = messageId
    _messageInput.value = currentContent
}

/**
 * Cancel an in-progress edit and restore the input to empty.
 */
internal fun ChatViewModel.cancelEditMessageImpl() {
    _editingMessageId.value = null
    _messageInput.value = ""
}

/**
 * Submit the edited message content to Supabase.
 */
internal fun ChatViewModel.confirmEditMessage(messageId: String) {
    if (_isMessageSubmitInProgress.value) return
    val connectionId = currentConnectionId ?: return
    val newContent = _messageInput.value.trim()
    if (newContent.isEmpty()) return
    val now = Clock.System.now().toEpochMilliseconds()
    val apiChatId = currentApiChatId

    val previousState = _chatMessagesState.value
    if (previousState is ChatMessagesState.Success) {
        _chatMessagesState.value =
            previousState.copy(
                messages =
                    previousState.messages.map { mwu ->
                        if (mwu.message.id == messageId) {
                            mwu.copy(
                                message =
                                    mwu.message.copy(
                                        content = newContent,
                                        timeEdited = now,
                                    ),
                            )
                        } else {
                            mwu
                        }
                    },
            )
    }

    _isMessageSubmitInProgress.value = true
    viewModelScope.launch {
        try {
            val success = supabaseRepository.editMessage(messageId, newContent, chatId = apiChatId)
            if (success) {
                _editingMessageId.value = null
                _messageInput.value = ""
            } else {
                loadChatMessages(connectionId)
            }
        } catch (e: Exception) {
            println("Error editing message: ${e.redactedRestMessage()}")
            loadChatMessages(connectionId)
        } finally {
            _isMessageSubmitInProgress.value = false
        }
    }
}

/**
 * Delete a message from the chat (with optimistic local removal).
 */
internal fun ChatViewModel.deleteMessageImpl(messageId: String) {
    val connectionId = currentConnectionId ?: return

    // Optimistic: remove from local state immediately
    val currentState = _chatMessagesState.value
    if (currentState is ChatMessagesState.Success) {
        _chatMessagesState.value =
            currentState.copy(
                messages = currentState.messages.filter { it.message.id != messageId },
            )
    }
    // Also remove any reactions for this message
    val currentReactions = _messageReactions.value.toMutableMap()
    currentReactions.remove(messageId)
    _messageReactions.value = currentReactions

    viewModelScope.launch {
        try {
            val success = supabaseRepository.deleteMessage(messageId)
            if (!success) {
                loadChatMessages(connectionId)
            }
        } catch (e: Exception) {
            println("Error deleting message: ${e.redactedRestMessage()}")
            // Revert optimistic removal on failure — reload full state
            loadChatMessages(connectionId)
        }
    }
}

/**
 * Archive the current connection (hide from main list, recoverable).
 * State is stored in-memory for this session; backed by Supabase when the
 * connection_archives table is provisioned (see database/add_connection_archives.sql).
 */
internal fun ChatViewModel.archiveConnectionImpl(onComplete: (Boolean) -> Unit = {}) {
    val connectionId = currentConnectionId ?: return
    archiveConnectionById(connectionId, onComplete)
}

/**
 * Archive a specific connection by ID.
 */
internal fun ChatViewModel.archiveConnectionByIdImpl(
    connectionId: String,
    onComplete: (Boolean) -> Unit = {},
) {
    val userId = _currentUserId.value ?: return
    viewModelScope.launch {
        AppDataManager.markConnectionArchivedLocally(connectionId)
        supabaseRepository.archiveConnection(userId, connectionId) // non-fatal if table missing
        reapplyChatListVisibility()
        if (currentConnectionId == connectionId) {
            leaveChatRoom()
        }
        loadChats(isForced = true)
        _nudgeResult.value = "Connection archived"
        onComplete(true)
    }
}

/**
 * Unarchive a connection so it re-appears in the main list.
 */
internal fun ChatViewModel.unarchiveConnectionImpl(connectionId: String) {
    val userId = _currentUserId.value ?: return
    viewModelScope.launch {
        AppDataManager.markConnectionUnarchivedLocally(connectionId)
        supabaseRepository.unarchiveConnection(userId, connectionId)
        reapplyChatListVisibility()
        loadChats(isForced = true)
        _nudgeResult.value = "Connection unarchived"
    }
}

internal fun ChatViewModel.addConnectionToCoreImpl(connectionId: String) {
    val userId = _currentUserId.value ?: return
    viewModelScope.launch {
        AppDataManager.markConnectionCoreLocally(connectionId)
        val ok = supabaseRepository.addConnectionToCore(userId, connectionId)
        if (!ok) {
            AppDataManager.markConnectionNotCoreLocally(connectionId)
            _nudgeResult.value = "Couldn't add to Core"
            return@launch
        }
        loadChats(isForced = true)
        _nudgeResult.value = "Added to Core"
    }
}

internal fun ChatViewModel.removeConnectionFromCoreImpl(connectionId: String) {
    val userId = _currentUserId.value ?: return
    viewModelScope.launch {
        AppDataManager.markConnectionNotCoreLocally(connectionId)
        val ok = supabaseRepository.removeConnectionFromCore(userId, connectionId)
        if (!ok) {
            AppDataManager.markConnectionCoreLocally(connectionId)
            _nudgeResult.value = "Couldn't remove from Core"
            return@launch
        }
        loadChats(isForced = true)
        _nudgeResult.value = "Removed from Core"
    }
}

/**
 * Soft-remove the current connection (server `status = removed`; row retained).
 */
internal fun ChatViewModel.deleteConnectionPermanentlyImpl(onComplete: (Boolean) -> Unit = {}) {
    val connectionId = currentConnectionId ?: return
    deleteConnectionPermanentlyById(connectionId, onComplete)
}

/**
 * Hide a connection for the current user via [connection_hidden].
 * Saves the connection object before the optimistic hide so it can be
 * restored on failure — even when Ghost Mode blocks [AppDataManager.refresh].
 */
internal fun ChatViewModel.deleteConnectionPermanentlyByIdImpl(
    connectionId: String,
    onComplete: (Boolean) -> Unit = {},
) {
    val userId = _currentUserId.value ?: return
    viewModelScope.launch {
        // Save the connection before optimistic hide so we can restore it on failure
        // (AppDataManager.refresh no-ops when Ghost Mode is active).
        val savedConnection = AppDataManager.getConnection(connectionId)
        val pair =
            savedConnection
                ?.user_ids
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.takeIf { it.size >= 2 }
        if (pair == null || userId !in pair) {
            _nudgeResult.value = "Failed to remove connection"
            onComplete(false)
            return@launch
        }
        AppDataManager.hideConnectionLocally(connectionId)
        reapplyChatListVisibility()
        val success = supabaseRepository.hideConnectionForUser(userId, connectionId)
        if (success) {
            AppDataManager.refresh(force = true)
            if (currentConnectionId == connectionId) {
                leaveChatRoom()
            }
            loadChats(isForced = true)
            _nudgeResult.value = "Connection removed"
            onComplete(true)
        } else {
            // Explicitly revert the optimistic hide instead of relying on refresh()
            // which no-ops when Ghost Mode is active.
            if (savedConnection != null) {
                AppDataManager.revertHideConnectionLocally(connectionId, savedConnection)
            } else {
                AppDataManager.unhideConnectionLocally(connectionId)
            }
            reapplyChatListVisibility()
            loadChats(isForced = true)
            _nudgeResult.value = "Failed to remove connection"
            onComplete(false)
        }
    }
}

/**
 * Block the other user in the current chat.
 * Resolves the other user ID from chat state or AppDataManager connections.
 * This avoids race conditions when called from the connections list
 * where chat state may not have loaded yet.
 */
internal fun ChatViewModel.blockUserImpl(onBlocked: (Boolean) -> Unit) {
    val connectionId = currentConnectionId ?: return
    blockUserForConnection(connectionId, onBlocked)
}

/**
 * Block the other user for a specific connection.
 */
internal fun ChatViewModel.blockUserForConnectionImpl(
    connectionId: String,
    onBlocked: (Boolean) -> Unit = {},
) {
    val userId = _currentUserId.value ?: return

    // Try to get the other user ID from chat state first, then fall back to AppDataManager
    val otherUserId = resolveOtherUserId(userId, connectionId)
    if (otherUserId == null) {
        println("blockUser: Could not resolve other user ID for connection $connectionId")
        _nudgeResult.value = "Could not block user"
        onBlocked(false)
        return
    }

    viewModelScope.launch {
        val success = supabaseRepository.blockUser(userId, otherUserId)
        if (success) {
            // POST /api/connections/hide is JWT-scoped and can only hide for the
            // authenticated user. The blocked user's visibility is handled by the
            // user_blocks row — fetchUserConnectionsSnapshot should exclude connections
            // where the other participant has blocked the current user.
            supabaseRepository.hideConnectionForUser(userId, connectionId)
            AppDataManager.hideConnectionLocally(connectionId)
            reapplyChatListVisibility()
            if (currentConnectionId == connectionId) {
                leaveChatRoom()
            }
            loadChats(isForced = true)
            _nudgeResult.value = "User blocked"
            onBlocked(true)
        } else {
            _nudgeResult.value = "Failed to block user"
            onBlocked(false)
        }
    }
}

/**
 * Report the current connection for safety review.
 * Uses currentConnectionId directly — no dependency on chat messages state.
 */
internal fun ChatViewModel.reportConnectionImpl(
    reason: String,
    onReported: (Boolean) -> Unit,
) {
    val connectionId = currentConnectionId ?: return
    reportConnectionForConnection(connectionId, reason, onReported)
}

/**
 * Report a specific connection for safety review.
 */
internal fun ChatViewModel.reportConnectionForConnectionImpl(
    connectionId: String,
    reason: String,
    onReported: (Boolean) -> Unit = {},
) {
    val userId = _currentUserId.value ?: return
    viewModelScope.launch {
        val success = supabaseRepository.reportConnection(connectionId, userId, reason)
        if (success) {
            _nudgeResult.value = "Report submitted"
            onReported(true)
        } else {
            _nudgeResult.value = "Failed to submit report"
            onReported(false)
        }
    }
}

/**
 * Resolve the other user's ID from either the loaded chat state
 * or the cached connections in AppDataManager. This ensures block/report
 * work even when called from the list without a fully loaded chat.
 */
internal fun ChatViewModel.resolveOtherUserId(
    userId: String,
    connectionId: String,
): String? {
    // 1. Try loaded chat state
    val chatState = _chatMessagesState.value
    if (chatState is ChatMessagesState.Success) {
        val fromChat =
            chatState.chatDetails.connection.user_ids
                .firstOrNull { it != userId }
        if (fromChat != null) return fromChat
    }
    // 2. Fall back to AppDataManager cached connections
    val connection = AppDataManager.connections.value.firstOrNull { it.id == connectionId }
    return connection?.user_ids?.firstOrNull { it != userId }
}
