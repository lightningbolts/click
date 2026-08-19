@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.IcebreakerRepository // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

internal fun ChatViewModel.loadMoreConnectionsPageImpl() {
    _connectionsDisplayLimit.value += CONNECTIONS_PAGE_SIZE
}

internal fun ChatViewModel.resetConnectionsDisplayLimitImpl() {
    _connectionsDisplayLimit.value = CONNECTIONS_PAGE_SIZE
}

internal fun ChatViewModel.loadOlderMessagesImpl() {
    viewModelScope.launch { loadOlderMessagesPage() }
}

internal suspend fun ChatViewModel.ensureTargetMessageLoadedImpl(messageId: String): Boolean {
    val id = messageId.trim()
    if (id.isEmpty()) return false
    repeat(TARGET_MESSAGE_MAX_PAGES) {
        val state = _chatMessagesState.value as? ChatMessagesState.Success ?: return false
        if (state.messages.any { it.message.id == id }) return true
        if (!_hasMoreOlderMessages.value) return false
        if (!loadOlderMessagesPage()) return false
    }
    return (_chatMessagesState.value as? ChatMessagesState.Success)
        ?.messages
        ?.any { it.message.id == id } == true
}

internal suspend fun ChatViewModel.loadOlderMessagesPage(): Boolean {
    val userId = _currentUserId.value ?: return false
    val state = _chatMessagesState.value as? ChatMessagesState.Success ?: return false
    val apiChatId =
        state.chatDetails.chat.id
            ?.takeIf { it.isNotBlank() } ?: currentApiChatId ?: return false
    if (_isLoadingOlderMessages.value || !_hasMoreOlderMessages.value) return false
    val oldest = state.messages.minByOrNull { it.message.timeCreated }?.message ?: return false

    _isLoadingOlderMessages.value = true
    try {
        suspend fun fetchPage(): List<Message>? =
            chatRepository.fetchMessagesForChat(
                chatId = apiChatId,
                viewerUserId = userId,
                limit = OLDER_MESSAGES_PAGE_SIZE,
                beforeTimeCreated = oldest.timeCreated,
            )

        var fetched = fetchPage()
        var authReadyAfterRetry = false
        if (fetched == null || fetched.isEmpty()) {
            authReadyAfterRetry = !chatRepository.ensureFreshAuthToken().isNullOrBlank()
            if (authReadyAfterRetry) {
                fetched = fetchPage()
            }
        }
        when (olderMessagesPageOutcome(fetched, authReadyAfterRetry)) {
            OlderMessagesPageOutcome.KeepHasMore -> {
                println("ChatViewModel: loadOlderMessages failed; keeping hasMoreOlderMessages")
                return false
            }
            OlderMessagesPageOutcome.EndOfHistory -> {
                _hasMoreOlderMessages.value = false
                return false
            }
            OlderMessagesPageOutcome.MergePage -> Unit
        }
        val page = fetched ?: return false
        val vaulted = vaultMessagesForUi(apiChatId, userId, page)
        val knownUsers =
            buildMap {
                state.messages.forEach { put(it.user.id, it.user) }
                AppDataManager.currentUser.value?.let { put(it.id, it) }
                put(state.chatDetails.otherUser.id, state.chatDetails.otherUser)
            }.toMutableMap()
        val incoming =
            vaulted.map { message ->
                val user =
                    knownUsers[message.user_id]
                        ?: User(id = message.user_id, name = "Unknown", createdAt = 0L)
                MessageWithUser(
                    message = message,
                    user = user,
                    isSent = message.user_id == userId,
                )
            }
        val active = _chatMessagesState.value as? ChatMessagesState.Success ?: return false
        if (active.chatDetails.chat.id != apiChatId) return false
        val merged = mergeMessageTimelinesPreservingLiveState(active.messages, incoming)
        _chatMessagesState.value = active.copy(messages = merged)
        prefetchedChatPayloads[active.chatDetails.connection.id] =
            (
                prefetchedChatPayloads[active.chatDetails.connection.id] ?: ChatViewModel.PrefetchedChatPayload(
                    messages = merged,
                    reactionsByMessageId = _messageReactions.value,
                    icebreakerPrompts = _icebreakerPrompts.value,
                    showIcebreakerPanel = _showIcebreakerPanel.value,
                )
            ).copy(messages = merged)
        if (page.size < OLDER_MESSAGES_PAGE_SIZE) {
            _hasMoreOlderMessages.value = false
        }
        return true
    } finally {
        _isLoadingOlderMessages.value = false
    }
}

/** List rows may omit prefetch; still reuse any [prefetchedChatPayloads] so refresh never blanks the thread. */
internal fun ChatViewModel.bootstrapMessagesFromPrefetch(connectionId: String): List<MessageWithUser> =
    normalizeChatTimeline(
        prefetchedChatPayloads[connectionId]?.messages
            ?: messagesWithUsersFromHotTimeline(connectionId)
            ?: payloadFromLocalCache(connectionId)?.messages
            ?: emptyList(),
    )

internal fun ChatViewModel.messagesWithUsersFromHotTimeline(connectionId: String): List<MessageWithUser>? {
    val userId = _currentUserId.value ?: return null
    val raw = chatRepository.peekCachedMessageTimeline(connectionId) ?: return null
    if (raw.isEmpty()) return null
    return normalizeChatTimeline(
        raw.map { message ->
            MessageWithUser(
                message = message,
                user =
                    AppDataManager.getConnectedUser(message.user_id)
                        ?: User(id = message.user_id, name = "Unknown", createdAt = 0L),
                isSent = message.user_id == userId,
            )
        },
    )
}

internal fun ChatViewModel.syncPrefetchFromHotTimeline(connectionId: String) {
    val hot = messagesWithUsersFromHotTimeline(connectionId) ?: return
    val existing = prefetchedChatPayloads[connectionId]
    val messages =
        if (existing?.messages?.isNotEmpty() == true) {
            mergeMessageTimelinesPreservingLiveState(existing.messages, hot)
        } else {
            normalizeChatTimeline(hot)
        }
    val hotTs = hot.maxOfOrNull { it.message.timeCreated } ?: 0L
    val existingTs = existing?.messages?.maxOfOrNull { it.message.timeCreated } ?: 0L
    if (existing == null || hotTs >= existingTs || messages.size > (existing.messages.size)) {
        prefetchedChatPayloads[connectionId] =
            ChatViewModel.PrefetchedChatPayload(
                messages = messages,
                reactionsByMessageId = existing?.reactionsByMessageId.orEmpty(),
                icebreakerPrompts = existing?.icebreakerPrompts.orEmpty(),
                showIcebreakerPanel = existing?.showIcebreakerPanel ?: (messages.size < 5),
            )
    }
}

internal fun ChatViewModel.payloadFromLocalCache(threadId: String): ChatViewModel.PrefetchedChatPayload? {
    val cached = AppDataManager.cachedChatThreadFor(threadId) ?: return null
    val userId = _currentUserId.value
    val participants = cached.participants.associateBy { it.id }
    val messagesWithUsers =
        cached.messages.map { message ->
            val user =
                participants[message.user_id]
                    ?: AppDataManager.currentUser.value?.takeIf { it.id == message.user_id }
                    ?: AppDataManager.getConnectedUser(message.user_id)
                    ?: User(id = message.user_id, name = "Unknown", createdAt = 0L)
            MessageWithUser(
                message = message,
                user = user,
                isSent = userId != null && message.user_id == userId,
            )
        }
    val shouldShowIcebreaker = messagesWithUsers.size < 5
    return ChatViewModel.PrefetchedChatPayload(
        messages = messagesWithUsers,
        reactionsByMessageId = cached.reactions.groupBy { it.messageId },
        icebreakerPrompts =
            if (shouldShowIcebreaker) {
                IcebreakerRepository.getPromptsForContext(
                    cachedChatRowForThreadId(threadId)?.connection?.context_tag,
                    count = 3,
                    stableSelectionKey = cached.connectionId,
                )
            } else {
                emptyList()
            },
        showIcebreakerPanel = shouldShowIcebreaker,
    )
}
