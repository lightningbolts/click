@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.IcebreakerRepository // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatSessionCaches
import compose.project.click.click.notifications.ChatPushInboxBridge
import compose.project.click.click.util.isPersistedApiChatId
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock

internal fun ChatViewModel.cachedChatRowForThreadId(threadId: String): ChatWithDetails? =
    (_chatListState.value as? ChatListState.Success)?.chats?.firstOrNull {
        it.connection.id == threadId || it.chat.id == threadId
    }

internal fun ChatViewModel.isChatThreadCacheFresh(connectionId: String): Boolean {
    val thread = AppDataManager.cachedChatThreadFor(connectionId) ?: return false
    if (thread.messages.isEmpty()) return false
    val ageMs = Clock.System.now().toEpochMilliseconds() - thread.cachedAtMs
    return ageMs in 0 until CHAT_THREAD_CACHE_FRESH_MS
}

internal fun ChatViewModel.resolveCachedChatPayload(connectionId: String): ChatViewModel.PrefetchedChatPayload? {
    prefetchedChatPayloads[connectionId]?.let { return it }
    return payloadFromLocalCache(connectionId)?.also { prefetchedChatPayloads[connectionId] = it }
}

internal suspend fun ChatViewModel.buildChatPayloadWithRetry(
    chatDetails: ChatWithDetails,
    apiChatId: String,
    userId: String,
): ChatViewModel.PrefetchedChatPayload {
    var payload: ChatViewModel.PrefetchedChatPayload? = null
    var payloadAttempt = 0
    while (payload == null && payloadAttempt < 4) {
        try {
            payload = buildChatPayload(chatDetails, apiChatId, userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            payloadAttempt++
            if (payloadAttempt >= 4) throw e
            delay(180L * payloadAttempt)
        }
    }
    return payload!!
}

internal fun ChatViewModel.applyOpenedChatPayload(
    hydratedChatDetails: ChatWithDetails,
    apiChatId: String,
    userId: String,
    connectionId: String,
    payload: ChatViewModel.PrefetchedChatPayload,
) {
    val active = _chatMessagesState.value as? ChatMessagesState.Success
    val mergedMessages =
        if (active != null &&
            active.chatDetails.connection.id == connectionId &&
            active.messages.isNotEmpty()
        ) {
            mergeMessageTimelinesPreservingLiveState(active.messages, payload.messages)
        } else {
            payload.messages
        }
    val mergedPayload = payload.copy(messages = mergedMessages)
    prefetchedChatPayloads[connectionId] = mergedPayload
    AppDataManager.cacheChatThread(
        connectionId = connectionId,
        chatId = apiChatId,
        messages = mergedMessages.map { it.message },
        participants = mergedMessages.map { it.user }.distinctBy { it.id },
        reactions = mergedPayload.reactionsByMessageId.values.flatten(),
    )

    _messageReactions.value =
        mergeReactionMapsPreserveOptimistic(
            _messageReactions.value,
            mergedPayload.reactionsByMessageId,
        )
    _showIcebreakerPanel.value = mergedPayload.showIcebreakerPanel
    if (mergedPayload.showIcebreakerPanel) {
        if (_icebreakerPrompts.value != mergedPayload.icebreakerPrompts) {
            _icebreakerPrompts.value = mergedPayload.icebreakerPrompts
        }
    } else {
        _icebreakerPrompts.value = emptyList()
    }
    _hasMoreOlderMessages.value = mergedMessages.size >= INITIAL_CHAT_MESSAGE_FETCH_LIMIT
    _chatMessagesState.value =
        ChatMessagesState.Success(
            messages = mergedMessages,
            chatDetails = hydratedChatDetails,
            isLoadingMessages = false,
        )

    enqueueInboundDeliveredAck(
        apiChatId,
        userId,
        mergedMessages.map { it.message },
    )
    markMessagesReadOptimistically(
        connectionId = connectionId,
        chatId = apiChatId,
        userId = userId,
    )
}

internal fun ChatViewModel.normalizeChatTimeline(messages: List<MessageWithUser>): List<MessageWithUser> {
    if (messages.isEmpty()) return messages
    val byId = linkedMapOf<String, MessageWithUser>()
    for (mwu in messages) {
        byId[mwu.message.id] = mwu
    }
    val values = byId.values.toList()
    val deliveredStamps =
        values
            .mapNotNull { mwu ->
                val m = mwu.message
                if (m.id.startsWith("temp-")) return@mapNotNull null
                val stamp = m.localSentAt ?: return@mapNotNull null
                (m.user_id to stamp)
            }.toSet()
    val cleaned =
        values.filterNot { mwu ->
            val m = mwu.message
            m.id.startsWith("temp-") &&
                m.localSentAt != null &&
                (m.user_id to m.localSentAt) in deliveredStamps
        }
    return cleaned.sortedWith(
        compareBy({ it.message.timeCreated }, { it.message.id }),
    )
}

internal fun ChatViewModel.mergeMessageTimelinesPreservingLiveState(
    current: List<MessageWithUser>,
    incoming: List<MessageWithUser>,
): List<MessageWithUser> {
    val pendingOptimistic =
        current.filter {
            val m = it.message
            m.id.startsWith("temp-") && m.deliveryState == MessageDeliveryState.PENDING
        }
    val byId = linkedMapOf<String, MessageWithUser>()
    for (mwu in incoming) {
        byId[mwu.message.id] = mwu
    }
    for (mwu in current) {
        if (mwu.message.id.startsWith("temp-")) continue
        val existing = byId[mwu.message.id]
        if (existing == null || mwu.message.timeCreated >= existing.message.timeCreated) {
            byId[mwu.message.id] = mwu
        }
    }
    val merged = byId.values.toList()
    // Drop temp rows already represented by a server row (same sender + localSentAt),
    // otherwise icebreaker / send races show duplicate bubbles with different ids.
    val extras =
        pendingOptimistic.filter { opt ->
            val stamp = opt.message.localSentAt
            merged.none { server ->
                !server.message.id.startsWith("temp-") &&
                    server.message.user_id == opt.message.user_id &&
                    stamp != null &&
                    server.message.localSentAt == stamp
            } &&
                merged.none { it.message.id == opt.message.id }
        }
    return normalizeChatTimeline(merged + extras)
}

internal fun ChatViewModel.applyHotTimelineToOpenChatIfNewer(connectionId: String) {
    val hot = messagesWithUsersFromHotTimeline(connectionId) ?: return
    val active = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    if (active.chatDetails.connection.id != connectionId) return
    val hotMax = hot.maxOfOrNull { it.message.timeCreated } ?: return
    val displayedMax = active.messages.maxOfOrNull { it.message.timeCreated } ?: 0L
    if (hotMax <= displayedMax) return
    val merged = mergeMessageTimelinesPreservingLiveState(active.messages, hot)
    _chatMessagesState.value = active.copy(messages = merged)
}

internal fun ChatViewModel.scheduleBackgroundChatPayloadRefresh(
    hydratedChatDetails: ChatWithDetails,
    apiChatId: String,
    userId: String,
    connectionId: String,
) {
    viewModelScope.launch {
        runCatching {
            buildChatPayload(hydratedChatDetails, apiChatId, userId)
        }.onSuccess { refreshed ->
            if (currentConnectionId != connectionId) return@onSuccess
            prefetchedChatPayloads[connectionId] = refreshed
            AppDataManager.cacheChatThread(
                connectionId = connectionId,
                chatId = apiChatId,
                messages = refreshed.messages.map { it.message },
                participants = refreshed.messages.map { it.user }.distinctBy { it.id },
                reactions = refreshed.reactionsByMessageId.values.flatten(),
            )
            val active = _chatMessagesState.value as? ChatMessagesState.Success ?: return@onSuccess
            if (active.chatDetails.connection.id != connectionId) return@onSuccess
            val mergedMessages =
                mergeMessageTimelinesPreservingLiveState(
                    current = active.messages,
                    incoming = refreshed.messages,
                )
            _messageReactions.value =
                mergeReactionMapsPreserveOptimistic(
                    _messageReactions.value,
                    refreshed.reactionsByMessageId,
                )
            _showIcebreakerPanel.value = refreshed.showIcebreakerPanel
            _icebreakerPrompts.value =
                if (refreshed.showIcebreakerPanel) {
                    refreshed.icebreakerPrompts
                } else {
                    emptyList()
                }
            _hasMoreOlderMessages.value = mergedMessages.size >= INITIAL_CHAT_MESSAGE_FETCH_LIMIT
            _chatMessagesState.value =
                active.copy(
                    messages = mergedMessages,
                    isLoadingMessages = false,
                )
        }
    }
}

// Load messages for a specific chat
internal fun ChatViewModel.loadChatMessagesImpl(chatId: String) {
    val cachedChat =
        (_chatListState.value as? ChatListState.Success)
            ?.chats
            ?.firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
    val connectionId =
        cachedChat?.connection?.id
            ?: ChatSessionCaches.peekListKeyForChatSync(chatId)
            ?: ChatPushInboxBridge.resolveConnectionIdForThread(chatId)
            ?: chatId

    applyPushWarmPrefetch(connectionId, chatId)

    syncPrefetchFromHotTimeline(connectionId)

    payloadFromLocalCache(connectionId)?.let { disk ->
        if (connectionId !in prefetchedChatPayloads) {
            prefetchedChatPayloads[connectionId] = disk
        }
    }
    val mergedPrefetch = resolveCachedChatPayload(connectionId)
    val hasCachedTimeline = mergedPrefetch?.messages?.isNotEmpty() == true

    val userId = _currentUserId.value
    if (userId == null) {
        pendingChatLoadId = chatId
        val successMatchesTarget =
            (_chatMessagesState.value as? ChatMessagesState.Success)?.let { s ->
                s.chatDetails.connection.id == connectionId ||
                    (
                        !s.chatDetails.chat.id
                            .isNullOrBlank() &&
                            s.chatDetails.chat.id == chatId
                    )
            } == true
        val rowForDisk = cachedChat ?: cachedChatRowForThreadId(chatId)
        if (rowForDisk != null && hasCachedTimeline && mergedPrefetch != null) {
            _messageReactions.value = mergedPrefetch.reactionsByMessageId
            _icebreakerPrompts.value = mergedPrefetch.icebreakerPrompts
            _showIcebreakerPanel.value = mergedPrefetch.showIcebreakerPanel
            val liveForMerge =
                (_chatMessagesState.value as? ChatMessagesState.Success)
                    ?.takeIf { it.chatDetails.connection.id == connectionId }
                    ?.messages
                    .orEmpty()
            val messagesForUi =
                if (liveForMerge.isNotEmpty()) {
                    mergeMessageTimelinesPreservingLiveState(liveForMerge, mergedPrefetch.messages)
                } else {
                    normalizeChatTimeline(mergedPrefetch.messages)
                }
            viewModelScope.launch {
                warmSecureMediaForTimeline(messagesForUi)
            }
            _chatMessagesState.value =
                ChatMessagesState.Success(
                    messages = messagesForUi,
                    chatDetails = rowForDisk,
                    isLoadingMessages = false,
                )
            return
        }
        if (!successMatchesTarget) {
            _chatMessagesState.value = ChatMessagesState.Loading
        }
        return
    }

    if (loadChatMessagesJob?.isActive == true && inFlightLoadConnectionId == connectionId) {
        syncPrefetchFromHotTimeline(connectionId)
        if (currentConnectionId == connectionId) {
            applyHotTimelineToOpenChatIfNewer(connectionId)
        }
        return
    }

    val currentState = _chatMessagesState.value as? ChatMessagesState.Success
    val currentConnectionStateId = currentState?.chatDetails?.connection?.id
    val activeApiChatId = currentState?.chatDetails?.chat?.id
    val hasRenderableStateForTarget =
        currentState != null &&
            (
                currentConnectionStateId == connectionId ||
                    (activeApiChatId != null && activeApiChatId == chatId)
            )
    val hasLiveSubscriptions =
        currentApiChatId == activeApiChatId &&
            activeMessageSubscription != null &&
            realtimeJob?.isActive == true &&
            typingPollingJob?.isActive == true &&
            peerOnlineJob?.isActive == true

    if (currentConnectionId == connectionId && currentState != null && hasLiveSubscriptions) {
        syncPrefetchFromHotTimeline(connectionId)
        applyHotTimelineToOpenChatIfNewer(connectionId)
        return
    }

    if (_chatMessagesState.value is ChatMessagesState.Error && !hasCachedTimeline) {
        _chatMessagesState.value = ChatMessagesState.Loading
    }

    val switchingConnection = currentConnectionId != null && currentConnectionId != connectionId
    if (switchingConnection) {
        currentApiChatId = null
        _stagedChatImages.value = emptyList()
        _stagedBeacon.value = null
        _replyingTo.value = null
        _editingMessageId.value = null
    }
    currentConnectionId = connectionId
    _hasMoreOlderMessages.value = false
    _isLoadingOlderMessages.value = false

    // Instantly show the chat header from cached list data (no loading spinner)
    val prefetchedPayload = mergedPrefetch

    if (cachedChat != null && prefetchedPayload == null) {
        _icebreakerPrompts.value =
            IcebreakerRepository.getPromptsForContext(
                cachedChat.connection.context_tag,
                count = 3,
                stableSelectionKey = cachedChat.connection.id,
            )
        // Provisional: payload refines after messages load (hide if thread has 5+ messages).
        _showIcebreakerPanel.value = true
    }

    if (cachedChat != null && prefetchedPayload != null) {
        _messageReactions.value = prefetchedPayload.reactionsByMessageId
        _icebreakerPrompts.value = prefetchedPayload.icebreakerPrompts
        _showIcebreakerPanel.value = prefetchedPayload.showIcebreakerPanel
        // Merge with live timeline so a bounded disk/hot prefetch (80 msgs) never
        // truncates an already-loaded longer window (load-older / realtime).
        val liveForMerge =
            currentState
                ?.takeIf { it.chatDetails.connection.id == connectionId }
                ?.messages
                .orEmpty()
        val messagesForUi =
            if (liveForMerge.isNotEmpty()) {
                mergeMessageTimelinesPreservingLiveState(liveForMerge, prefetchedPayload.messages)
            } else {
                normalizeChatTimeline(prefetchedPayload.messages)
            }
        viewModelScope.launch {
            warmSecureMediaForTimeline(messagesForUi)
        }
        _chatMessagesState.value =
            ChatMessagesState.Success(
                messages = messagesForUi,
                chatDetails = cachedChat,
                isLoadingMessages = !hasCachedTimeline && liveForMerge.isEmpty(),
            )
        _hasMoreOlderMessages.value =
            messagesForUi.size >= INITIAL_CHAT_MESSAGE_FETCH_LIMIT
    } else if (hasRenderableStateForTarget && currentState != null && !switchingConnection) {
        // Keep current content visible while refreshing the same thread in background.
        _chatMessagesState.value =
            currentState.copy(
                isLoadingMessages = currentState.messages.isEmpty(),
            )
    } else if (cachedChat != null) {
        // Show header, composer, and conversation starters immediately instead of a blank loading screen.
        val boot = bootstrapMessagesFromPrefetch(connectionId)
        viewModelScope.launch {
            warmSecureMediaForTimeline(boot)
        }
        _chatMessagesState.value =
            ChatMessagesState.Success(
                messages = boot,
                chatDetails = cachedChat,
                isLoadingMessages = boot.isEmpty(),
            )
    } else {
        _chatMessagesState.value = ChatMessagesState.Loading
    }

    inFlightLoadConnectionId = connectionId
    loadChatMessagesJob?.cancel()
    loadChatMessagesJob =
        viewModelScope.launch {
            try {
                val previousApiChatId = currentApiChatId
                var chatDetails: ChatWithDetails? = cachedChat ?: cachedChatRowForThreadId(chatId)
                if (chatDetails == null) {
                    val chatResolveBackoffMs = longArrayOf(0L, 120L, 280L, 520L, 900L, 1400L)
                    chatDetails = chatRepository.fetchChatWithDetails(chatId, userId)
                    var resolveAttempt = 0
                    while (chatDetails == null && resolveAttempt < chatResolveBackoffMs.size - 1) {
                        delay(chatResolveBackoffMs[resolveAttempt + 1])
                        ensureActive()
                        chatDetails =
                            cachedChatRowForThreadId(chatId) ?: chatRepository.fetchChatWithDetails(chatId, userId)
                        resolveAttempt++
                    }
                }
                if (chatDetails == null) {
                    _chatMessagesState.value = ChatMessagesState.Error("Chat not found")
                    return@launch
                }

                val resolvedConnectionId = chatDetails.connection.id

                var apiChatId = chatDetails.chat.id?.takeIf { isPersistedApiChatId(it) }
                var ensureAttempt = 0
                while (!isPersistedApiChatId(apiChatId) && ensureAttempt < 4) {
                    if (ensureAttempt > 0) delay(120L * ensureAttempt)
                    ensureActive()
                    apiChatId = chatDetails.chat.id?.takeIf { isPersistedApiChatId(it) }
                        ?: resolveOrCreateApiChatId(resolvedConnectionId, chatDetails)
                    ensureAttempt++
                }
                val persistedApiChatId =
                    apiChatId?.takeIf { isPersistedApiChatId(it) } ?: run {
                        _chatMessagesState.value = ChatMessagesState.Error("Unable to start chat")
                        return@launch
                    }
                currentApiChatId = persistedApiChatId

                if (previousApiChatId != null && previousApiChatId != persistedApiChatId) {
                    chatRepository.leaveChatEphemeralChannel(previousApiChatId)
                }

                val hydratedChatDetails =
                    if (chatDetails.chat.id == persistedApiChatId) {
                        chatDetails
                    } else {
                        chatDetails.copy(
                            chat =
                                chatDetails.chat.copy(
                                    id = persistedApiChatId,
                                    connectionId =
                                        if (chatDetails.groupClique != null) {
                                            chatDetails.chat.connectionId
                                        } else {
                                            resolvedConnectionId
                                        },
                                    groupId = chatDetails.groupClique?.groupId ?: chatDetails.chat.groupId,
                                ),
                        )
                    }

                if (hydratedChatDetails.groupClique == null) {
                    chatRepository.cacheEncryptionKeys(
                        persistedApiChatId,
                        hydratedChatDetails.connection.id,
                        hydratedChatDetails.connection.user_ids,
                    )
                }
                chatRepository.seedInboxChatRouting(listOf(hydratedChatDetails))
                subscribeToNewMessages(persistedApiChatId, userId)

                var payload = resolveCachedChatPayload(resolvedConnectionId)
                val cacheFresh =
                    payload != null &&
                        payload.messages.isNotEmpty() &&
                        isChatThreadCacheFresh(resolvedConnectionId)

                if (_chatMessagesState.value is ChatMessagesState.Loading) {
                    _icebreakerPrompts.value = payload?.icebreakerPrompts
                        ?: IcebreakerRepository.getPromptsForContext(
                            hydratedChatDetails.connection.context_tag,
                            count = 3,
                            stableSelectionKey = hydratedChatDetails.connection.id,
                        )
                    _showIcebreakerPanel.value = payload?.showIcebreakerPanel ?: true
                    val bridgeMessages = payload?.messages ?: bootstrapMessagesFromPrefetch(resolvedConnectionId)
                    _chatMessagesState.value =
                        ChatMessagesState.Success(
                            messages = bridgeMessages,
                            chatDetails = hydratedChatDetails,
                            isLoadingMessages = bridgeMessages.isEmpty(),
                        )
                }

                val ephemeralDeferred =
                    async {
                        chatRepository.joinChatEphemeralChannel(
                            persistedApiChatId,
                            userId,
                            hydratedChatDetails.otherUser.id,
                        )
                    }

                if (!cacheFresh) {
                    if (payload != null && payload.messages.isNotEmpty()) {
                        scheduleBackgroundChatPayloadRefresh(
                            hydratedChatDetails = hydratedChatDetails,
                            apiChatId = persistedApiChatId,
                            userId = userId,
                            connectionId = resolvedConnectionId,
                        )
                    } else {
                        payload = buildChatPayloadWithRetry(hydratedChatDetails, persistedApiChatId, userId)
                    }
                } else {
                    scheduleBackgroundChatPayloadRefresh(
                        hydratedChatDetails = hydratedChatDetails,
                        apiChatId = persistedApiChatId,
                        userId = userId,
                        connectionId = resolvedConnectionId,
                    )
                }

                payload = payload ?: run {
                    ephemeralDeferred.await()
                    return@launch
                }
                syncPrefetchFromHotTimeline(resolvedConnectionId)
                applyOpenedChatPayload(
                    hydratedChatDetails = hydratedChatDetails,
                    apiChatId = persistedApiChatId,
                    userId = userId,
                    connectionId = resolvedConnectionId,
                    payload = payload,
                )

                ephemeralDeferred.await()

                startTypingMonitoring(persistedApiChatId)
                startPeerOnlineMonitoring(persistedApiChatId, hydratedChatDetails.otherUser.id)
                startActiveChatSync(persistedApiChatId, userId)

                if (vibeCheckEnabled) {
                    startVibeCheckTimer(chatDetails.connection, userId)
                    updateKeepStates(chatDetails.connection, userId)
                }

                if (hydratedChatDetails.groupClique == null && !chatDetails.connection.has_begun) {
                    supabaseRepository.updateConnectionHasBegun(resolvedConnectionId, true)
                }
                ChatPushInboxBridge.consumeWarmMessage(resolvedConnectionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val latestState = _chatMessagesState.value as? ChatMessagesState.Success
                val sameChatStillVisible =
                    latestState != null &&
                        (
                            latestState.chatDetails.connection.id == connectionId ||
                                latestState.chatDetails.chat.id == chatId
                        )

                if (sameChatStillVisible) {
                    _chatMessagesState.value = latestState.copy(isLoadingMessages = false)
                } else {
                    _chatMessagesState.value =
                        ChatMessagesState.Error(e.redactedRestMessage().ifBlank { "Failed to load messages" })
                }
            } finally {
                if (inFlightLoadConnectionId == connectionId) {
                    inFlightLoadConnectionId = null
                }
            }
        }
}

internal fun ChatViewModel.applyPushWarmPrefetch(
    connectionId: String,
    chatId: String,
) {
    val warm =
        ChatPushInboxBridge.peekWarmMessage(connectionId)
            ?: ChatPushInboxBridge.peekWarmMessageForThread(chatId)
            ?: return
    val viewerId = _currentUserId.value
    if (viewerId != null && connectionId !in prefetchedChatPayloads) {
        val sender =
            AppDataManager.getConnectedUser(warm.user_id)
                ?: User(id = warm.user_id, name = "Unknown", createdAt = 0L)
        prefetchedChatPayloads[connectionId] =
            ChatViewModel.PrefetchedChatPayload(
                messages =
                    listOf(
                        MessageWithUser(
                            message = warm,
                            user = sender,
                            isSent = warm.user_id == viewerId,
                        ),
                    ),
                reactionsByMessageId = emptyMap(),
                icebreakerPrompts = emptyList(),
                showIcebreakerPanel = false,
            )
    }
    bumpConnectionInChatList(connectionId, warm)
    val apiChatId =
        (_chatListState.value as? ChatListState.Success)
            ?.chats
            ?.firstOrNull { it.connection.id == connectionId }
            ?.chat
            ?.id
            ?.takeIf { it.isNotBlank() }
            ?: chatId.takeIf { it != connectionId }
    if (!apiChatId.isNullOrBlank() && connectionId.isNotBlank()) {
        viewModelScope.launch {
            ChatSessionCaches.seedConnectionRouting(apiChatId, connectionId)
        }
    }
}

internal fun ChatViewModel.prefetchChatPayloads(
    userId: String,
    chats: List<ChatWithDetails>,
) {
    viewModelScope.launch {
        // Refresh once before the parallel prefetch storm — each fetch used to call
        // ensureFreshJwtForChat independently and amplify /token rate limits.
        runCatching { chatRepository.ensureFreshAuthToken() }
        val targets =
            chats
                .sortedByDescending { chatListActivityTimestamp(it) }
                .take(prefetchedChatLimit)
        coroutineScope {
            val limiter = Semaphore(CHAT_OPEN_PREFETCH_CONCURRENCY)
            targets
                .map { chatDetails ->
                    async {
                        limiter.withPermit {
                            prefetchChatPayloadForRow(userId, chatDetails)
                        }
                    }
                }.awaitAll()
        }
    }
}

internal suspend fun ChatViewModel.prefetchChatPayloadForRow(
    userId: String,
    chatDetails: ChatWithDetails,
) {
    val connectionId = chatDetails.connection.id
    if (prefetchedChatPayloads.containsKey(connectionId)) return
    if (isChatThreadCacheFresh(connectionId)) {
        payloadFromLocalCache(connectionId)?.let { prefetchedChatPayloads[connectionId] = it }
        return
    }
    val apiChatId = chatDetails.chat.id ?: return
    if (chatDetails.groupClique == null) {
        chatRepository.cacheEncryptionKeys(
            apiChatId,
            connectionId,
            chatDetails.connection.user_ids,
        )
    }
    runCatching {
        buildChatPayload(chatDetails, apiChatId, userId)
    }.onSuccess { payload ->
        if (!prefetchedChatPayloads.containsKey(connectionId)) {
            prefetchedChatPayloads[connectionId] = payload
        }
        AppDataManager.cacheChatThread(
            connectionId = connectionId,
            chatId = apiChatId,
            messages = payload.messages.map { it.message },
            participants = payload.messages.map { it.user }.distinctBy { it.id },
            reactions = payload.reactionsByMessageId.values.flatten(),
        )
        patchChatListRowFromCachedThread(connectionId)
    }
}

internal suspend fun ChatViewModel.buildChatPayload(
    chatDetails: ChatWithDetails,
    apiChatId: String,
    userId: String,
): ChatViewModel.PrefetchedChatPayload =
    coroutineScope {
        val messagesDeferred =
            async {
                chatRepository.fetchMessagesForChat(
                    chatId = apiChatId,
                    viewerUserId = userId,
                    limit = INITIAL_CHAT_MESSAGE_FETCH_LIMIT,
                )
            }
        val participantsDeferred = async { chatRepository.fetchChatParticipants(apiChatId) }
        val participants = participantsDeferred.await().associateBy { it.id }
        val decryptedMessages =
            messagesDeferred.await()
                ?: error("Failed to load messages for chat")
        val rawMessages = chatRepository.vaultEncryptedMediaMessages(apiChatId, userId, decryptedMessages)
        val messageIds = rawMessages.map { it.id }
        val reactionsByMessageId = chatRepository.fetchReactionsForChat(apiChatId, messageIds).groupBy { it.messageId }
        val messagesWithUsers =
            rawMessages.map { message ->
                val user = participants[message.user_id] ?: User(id = message.user_id, name = "Unknown", createdAt = 0L)
                MessageWithUser(
                    message = message,
                    user = user,
                    isSent = message.user_id == userId,
                )
            }
        val shouldShowIcebreaker = messagesWithUsers.size < 5
        val prompts =
            if (shouldShowIcebreaker) {
                IcebreakerRepository.getPromptsForContext(
                    chatDetails.connection.context_tag,
                    count = 3,
                    stableSelectionKey = chatDetails.connection.id,
                )
            } else {
                emptyList()
            }

        ChatViewModel.PrefetchedChatPayload(
            messages = messagesWithUsers,
            reactionsByMessageId = reactionsByMessageId,
            icebreakerPrompts = prompts,
            showIcebreakerPanel = shouldShowIcebreaker,
        )
    }
