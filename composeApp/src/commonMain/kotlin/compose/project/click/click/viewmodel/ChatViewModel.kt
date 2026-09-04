@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.chat.attachments.AttachmentCrypto // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.IcebreakerPrompt // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.hasLocalMediaUri // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.originalMimeTypeOrNull // pragma: allowlist secret
import compose.project.click.click.data.realtime.RealtimeCoordinator
import compose.project.click.click.data.repository.ChatMessageSubscription // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.e2eeV2MediaMetadataOrNull // pragma: allowlist secret
import compose.project.click.click.data.repository.e2eeV2MediaStoragePathOrNull // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.network.ConnectivityMonitor
import compose.project.click.click.network.NetworkConnectivityMonitor
import compose.project.click.click.notifications.ChatPushInboxBridge
import compose.project.click.click.ui.chat.ChatAttachmentDownloadOutcome // pragma: allowlist secret
import compose.project.click.click.ui.chat.secureChatImageBitmapCache // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileSheetLocalMessage // pragma: allowlist secret
import compose.project.click.click.util.LruMemoryCache // pragma: allowlist secret
import compose.project.click.click.util.chatMediaDispatcher // pragma: allowlist secret
import compose.project.click.click.util.chatMediaVaultExtensionForMessage // pragma: allowlist secret
import compose.project.click.click.util.dedupeOneToOneChatsByPeer
import compose.project.click.click.util.imageVaultFileExtension // pragma: allowlist secret
import compose.project.click.click.util.readChatMediaVaultBytesForMessage // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.teardownBlocking // pragma: allowlist secret
import compose.project.click.click.util.writeChatMediaVaultFile // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.put

class ChatViewModel(
    internal val tokenStorage: TokenStorage = createTokenStorage(),
    internal val chatRepository: ChatRepository = SupabaseChatRepository(tokenStorage = tokenStorage),
    internal val supabaseRepository: SupabaseRepository = SupabaseRepository(),
    internal val chatApi: ChatApiClient = ChatApiClient(tokenStorage = tokenStorage),
    internal val connectivityMonitor: ConnectivityMonitor = NetworkConnectivityMonitor(),
    internal val mapBeaconRepository: compose.project.click.click.data.repository.MapBeaconRepository =
        compose.project.click.click.data.repository
            .MapBeaconRepository(),
) : ViewModel(),
    SecureChatMediaHost {
    internal companion object {
        const val OFFLINE_SEND_NOTICE =
            "You're offline. Your message is saved on this device and will send when you reconnect."
    }

    internal data class PrefetchedChatPayload(
        val messages: List<MessageWithUser>,
        val reactionsByMessageId: Map<String, List<MessageReaction>>,
        val icebreakerPrompts: List<IcebreakerPrompt>,
        val showIcebreakerPanel: Boolean,
    )

    internal val vibeCheckEnabled = false

    internal val _chatListState = MutableStateFlow<ChatListState>(ChatListState.Loading)
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()

    internal val _decryptedPreviews = MutableStateFlow<Map<String, String>>(emptyMap())
    val decryptedPreviews: StateFlow<Map<String, String>> = _decryptedPreviews.asStateFlow()

    internal val _chatMessagesState = MutableStateFlow<ChatMessagesState>(ChatMessagesState.Loading)
    val chatMessagesState: StateFlow<ChatMessagesState> = _chatMessagesState.asStateFlow()

    internal val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    internal val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    internal val _stagedChatImages = MutableStateFlow<List<StagedChatImage>>(emptyList())
    val stagedChatImages: StateFlow<List<StagedChatImage>> = _stagedChatImages.asStateFlow()

    internal val _stagedBeacon = MutableStateFlow<compose.project.click.click.data.models.MapBeacon?>(null)
    val stagedBeacon: StateFlow<compose.project.click.click.data.models.MapBeacon?> = _stagedBeacon.asStateFlow()

    internal val _replyingTo = MutableStateFlow<MessageWithUser?>(null)
    val replyingTo: StateFlow<MessageWithUser?> = _replyingTo.asStateFlow()

    /** True while a send or edit-submit is in flight; UI uses this to avoid double sends. */
    internal val _isMessageSubmitInProgress = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isMessageSubmitInProgress.asStateFlow()

    internal val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping: StateFlow<Boolean> = _isPeerTyping.asStateFlow()

    internal val _isPeerOnline = MutableStateFlow(false)
    val isPeerOnline: StateFlow<Boolean> = _isPeerOnline.asStateFlow()

    internal val _isLocalTypingActive = MutableStateFlow(false)
    val isLocalTypingActive: StateFlow<Boolean> = _isLocalTypingActive.asStateFlow()

    // Vibe Check Timer State
    internal val _vibeCheckRemainingMs = MutableStateFlow<Long>(0L)
    val vibeCheckRemainingMs: StateFlow<Long> = _vibeCheckRemainingMs.asStateFlow()

    internal val _currentUserHasKept = MutableStateFlow(false)
    val currentUserHasKept: StateFlow<Boolean> = _currentUserHasKept.asStateFlow()

    internal val _otherUserHasKept = MutableStateFlow(false)
    val otherUserHasKept: StateFlow<Boolean> = _otherUserHasKept.asStateFlow()

    internal val _vibeCheckExpired = MutableStateFlow(false)
    val vibeCheckExpired: StateFlow<Boolean> = _vibeCheckExpired.asStateFlow()

    internal val _connectionKept = MutableStateFlow(false)
    val connectionKept: StateFlow<Boolean> = _connectionKept.asStateFlow()

    // Icebreaker Prompts State
    internal val _icebreakerPrompts = MutableStateFlow<List<IcebreakerPrompt>>(emptyList())
    val icebreakerPrompts: StateFlow<List<IcebreakerPrompt>> = _icebreakerPrompts.asStateFlow()

    internal val _showIcebreakerPanel = MutableStateFlow(true)
    val showIcebreakerPanel: StateFlow<Boolean> = _showIcebreakerPanel.asStateFlow()

    /** Seconds remaining for in-chat icebreaker refresh / outbound icebreaker send cooldown (see [armIcebreakerCooldown]). */
    internal val _icebreakerCooldownRemainingSec = MutableStateFlow(0)
    val icebreakerCooldownRemainingSec: StateFlow<Int> = _icebreakerCooldownRemainingSec.asStateFlow()

    internal var icebreakerCooldownTickerJob: Job? = null
    internal var lastIcebreakerRefreshInvokedMs: Long = 0L

    // ── Nudge result feedback ──────────────────────────────────────────────────
    internal val _nudgeResult = MutableStateFlow<String?>(null)
    val nudgeResult: StateFlow<String?> = _nudgeResult.asStateFlow()

    // ── Message send error feedback ────────────────────────────────────────────
    internal val _messageSendError = MutableStateFlow<String?>(null)
    val messageSendError: StateFlow<String?> = _messageSendError.asStateFlow()

    // ── Message editing state ─────────────────────────────────────────────────
    // Non-null when the user is editing an existing message
    internal val _editingMessageId = MutableStateFlow<String?>(null)
    val editingMessageId: StateFlow<String?> = _editingMessageId.asStateFlow()

    /** Relational archive/hide junction state (shared with Home / Map / ConnectionViewModel). */
    val archivedConnectionIds: StateFlow<Set<String>> = AppDataManager.archivedConnectionIds
    val hiddenConnectionIds: StateFlow<Set<String>> = AppDataManager.hiddenConnectionIds

    internal val _connectionsDisplayLimit = MutableStateFlow(CONNECTIONS_PAGE_SIZE)
    val connectionsDisplayLimit: StateFlow<Int> = _connectionsDisplayLimit.asStateFlow()

    internal val _hasMoreOlderMessages = MutableStateFlow(false)
    val hasMoreOlderMessages: StateFlow<Boolean> = _hasMoreOlderMessages.asStateFlow()

    internal val _isLoadingOlderMessages = MutableStateFlow(false)
    val isLoadingOlderMessages: StateFlow<Boolean> = _isLoadingOlderMessages.asStateFlow()

    fun loadMoreConnectionsPage() = loadMoreConnectionsPageImpl()

    fun resetConnectionsDisplayLimit() = resetConnectionsDisplayLimitImpl()

    fun loadOlderMessages() = loadOlderMessagesImpl()

    suspend fun ensureTargetMessageLoaded(messageId: String): Boolean = ensureTargetMessageLoadedImpl(messageId = messageId)

    // ── Reactions state: messageId → list of reactions ─────────────────────────
    internal val _messageReactions =
        MutableStateFlow<Map<String, List<compose.project.click.click.data.models.MessageReaction>>>(emptyMap())
    val messageReactions: StateFlow<Map<String, List<compose.project.click.click.data.models.MessageReaction>>> =
        _messageReactions
            .asStateFlow()

    internal val _secureChatMediaLoadState = MutableStateFlow<Map<String, SecureChatMediaLoadState>>(emptyMap())
    override val secureChatMediaLoadState: StateFlow<Map<String, SecureChatMediaLoadState>> =
        _secureChatMediaLoadState.asStateFlow()
    internal val secureImageBytesCache =
        LruMemoryCache<String, ByteArray>(SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES)
    internal val secureImageNetworkLoads = Semaphore(SECURE_CHAT_IMAGE_NETWORK_CONCURRENCY)
    internal val secureAudioPathCache =
        LruMemoryCache<String, String>(SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES)

    internal var currentConnectionId: String? = null
    internal var currentApiChatId: String? = null
    internal var activeMessageSubscription: ChatMessageSubscription? = null
    internal var realtimeJob: Job? = null
    internal var activeChatSyncJob: Job? = null
    internal var typingPollingJob: Job? = null
    internal var peerTypingTimeoutJob: Job? = null
    internal var peerOnlineJob: Job? = null
    internal var localTypingIdleJob: Job? = null
    internal var connectionsRealtimeJob: Job? = null
    internal var connectionsRealtimeChannel: RealtimeChannel? = null
    internal var globalMessageListJob: Job? = null
    internal var debouncedChatListRefreshJob: Job? = null
    internal var vibeCheckTimerJob: Job? = null
    internal var loadChatMessagesJob: Job? = null
    internal var pendingChatLoadId: String? = null
    internal var inFlightLoadConnectionId: String? = null
    internal var lastTypingSent: Long = 0L
    internal val prefetchedChatPayloads = mutableMapOf<String, PrefetchedChatPayload>()

    /** Matches AppDataManager silent prefetch depth (recent active + archived + groups). */
    internal val prefetchedChatLimit = 12

    /** Serializes outbound chat POSTs so optimistic rows stay ordered; text sends can queue without blocking the composer UI. */
    internal val outboundChatMessageMutex = Mutex()

    /** Prevents concurrent archive-banner icebreaker sends from racing the cooldown gate. */
    internal val archiveBannerIcebreakerMutex = Mutex()

    /**
     * Connection ids for which the inbox row should show zero unread immediately after
     * [chatRepository.markMessagesAsRead] (or while the active thread is open). Prevents stale
     * counts from sticking until the next cold start when server-driven list refreshes lag.
     */
    internal val _readClearedConnectionIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        connectivityMonitor.start()
        viewModelScope.launch {
            AppDataManager.cachedChatThreads.collect { threads ->
                if (threads.isEmpty()) return@collect
                threads.keys.forEach { connectionId ->
                    patchChatListRowFromCachedThread(connectionId)
                }
            }
        }

        viewModelScope.launch {
            RealtimeCoordinator.inboxVersion.collect {
                val state = _chatListState.value as? ChatListState.Success ?: return@collect
                state.chats.forEach { row ->
                    patchChatListRowFromCachedThread(row.connection.id)
                }
            }
        }

        viewModelScope.launch {
            ChatPushInboxBridge.inboxPushEvents.collect { (connectionId, message) ->
                bumpConnectionInChatList(connectionId, message)
            }
        }

        viewModelScope.launch {
            combine(
                AppDataManager.connections,
                AppDataManager.connectedUsers,
            ) { connections, connectedUsers ->
                connections to connectedUsers
            }.collect { (connections, connectedUsers) ->
                val currentUserId = _currentUserId.value

                // Always patch the open chat screen with the freshest user name.
                val currentMessages = _chatMessagesState.value as? ChatMessagesState.Success
                if (currentMessages != null) {
                    val refreshedOtherUser = connectedUsers[currentMessages.chatDetails.otherUser.id]
                    if (refreshedOtherUser != null && refreshedOtherUser != currentMessages.chatDetails.otherUser) {
                        _chatMessagesState.value =
                            currentMessages.copy(
                                chatDetails = currentMessages.chatDetails.copy(otherUser = refreshedOtherUser),
                            )
                    }
                }

                // When AppDataManager resolves user names (e.g. after the RPC fallback or a
                // retry), patch the chat-list rows in place so the UI updates immediately without
                // waiting for a full API round-trip. Doing this avoids the 30-second heartbeat
                // cycle that previously kept "Connection" visible until the next presence tick.
                val currentListState = _chatListState.value as? ChatListState.Success
                if (currentListState != null && currentUserId != null) {
                    val cachedChatsByConnectionId =
                        buildCachedChats(connections, connectedUsers, currentUserId)
                            .associateBy { it.connection.id }

                    val mergedChats =
                        currentListState.chats.map { chat ->
                            val cachedChat =
                                cachedChatsByConnectionId[chat.connection.id]
                                    ?: inboxRowFromCachedThread(
                                        connectionId = chat.connection.id,
                                        listRow = chat,
                                        connections = connections,
                                        users = connectedUsers,
                                        userId = currentUserId,
                                    )
                            val freshUser = cachedChat?.otherUser ?: connectedUsers[chat.otherUser.id]
                            mergeChatRowWithCache(chat, cachedChat, freshUser)
                        }

                    val currentConnectionIds = mergedChats.map { it.connection.id }.toSet()
                    val missingChats =
                        cachedChatsByConnectionId.values
                            .filter { it.connection.id !in currentConnectionIds }
                            .sortedByDescending { chatListActivityTimestamp(it) }

                    val reconciledBase =
                        applyChatListVisibility(
                            dedupeOneToOneChatsByPeer(
                                (missingChats + mergedChats)
                                    .distinctBy { it.connection.id }
                                    .sortedByDescending { chatListActivityTimestamp(it) },
                            ),
                        )
                    pruneStaleReadClearedHints(reconciledBase)
                    val reconciledChats = applyUnreadClearHintsToInboxRows(reconciledBase)

                    if (reconciledChats != currentListState.chats) {
                        _chatListState.value = ChatListState.Success(reconciledChats)
                    }

                    if (missingChats.isNotEmpty()) {
                        loadChats(isForced = true)
                    }
                }

                // Only trigger a chat-list load when we don't already have real data.
                // Prefer painting from AppDataManager when startup data is still fresh.
                if (currentUserId != null &&
                    connections.isNotEmpty() &&
                    connectedUsers.isNotEmpty() &&
                    _chatListState.value !is ChatListState.Success
                ) {
                    loadChats(isForced = false)
                }
            }
        }

        viewModelScope.launch {
            // Session restore after auth; wrapped so JVM unit tests without Android Settings can construct the VM.
            runCatching {
                SupabaseConfig.client.auth.sessionStatus.collect { status ->
                    if (status is SessionStatus.Authenticated) {
                        restoreActiveChatSubscriptionsIfNeeded()
                    }
                }
            }
        }

        viewModelScope.launch {
            AppDataManager.foregroundRealtimeRecovery.collect {
                val uid = _currentUserId.value ?: return@collect
                startGlobalConnectionsRealtime(uid)
                startGlobalMessageListRealtime()
                restoreActiveChatSubscriptionsIfNeeded()
            }
        }

        viewModelScope.launch {
            AppDataManager.inboxReloadRequests.collect {
                if (_currentUserId.value.isNullOrBlank()) return@collect
                loadChats(isForced = true)
            }
        }
    }

    // Set the current user
    fun setCurrentUser(userId: String) {
        val userUnchanged = _currentUserId.value == userId
        if (!userUnchanged) {
            prefetchedChatPayloads.clear()
            clearSecureChatMediaCache(purgePersistentCache = true)
        }
        _currentUserId.value = userId
        startGlobalConnectionsRealtime(userId)
        startGlobalMessageListRealtime()
        pendingChatLoadId?.let { pendingId ->
            pendingChatLoadId = null
            loadChatMessages(pendingId)
        }
        if (userUnchanged && _chatListState.value is ChatListState.Success) return
        loadChats(isForced = false)
    }

    fun loadChats(isForced: Boolean = true) = loadChatsImpl(isForced = isForced)

    fun loadChatMessages(chatId: String) = loadChatMessagesImpl(chatId = chatId)

    fun refreshIcebreakerPrompts() = refreshIcebreakerPromptsImpl()

    fun markConversationUnread(connectionId: String) = markConversationUnreadImpl(connectionId = connectionId)

    override fun ensureSecureChatImageLoaded(
        scopeId: String,
        viewerUserId: String,
        message: Message,
    ) {
        if (message.messageType.lowercase() != ChatMessageType.IMAGE) return
        val url = message.mediaUrlOrNull() ?: return
        if (url.isBlank()) return
        val cachedBytes = secureImageBytesCache.get(message.id)
        if (cachedBytes != null && cachedBytes.isNotEmpty()) {
            _secureChatMediaLoadState.update {
                it + (message.id to SecureChatMediaLoadState(loading = false, imageBytes = cachedBytes))
            }
            return
        }
        val cur = _secureChatMediaLoadState.value[message.id]
        if (cur?.imageBytes != null || cur?.loading == true) return
        if (secureChatImageBitmapCache.get(message.id) != null) return

        viewModelScope.launch(chatMediaDispatcher) {
            val imageExtension = chatMediaVaultExtensionForMessage(message)
            readChatMediaVaultBytesForMessage(
                messageId = message.id,
                mediaUrl = url,
                preferredExtension = imageExtension,
            )?.takeIf { it.isNotEmpty() }?.let { vaultBytes ->
                secureImageBytesCache.put(message.id, vaultBytes)
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, imageBytes = vaultBytes))
                }
                return@launch
            }

            if (!message.isEncryptedMedia()) return@launch

            _secureChatMediaLoadState.update { map ->
                val existing = map[message.id]
                map + (
                    message.id to
                        SecureChatMediaLoadState(
                            loading = true,
                            imageBytes = existing?.imageBytes,
                            uploadProgress = existing?.uploadProgress,
                        )
                )
            }
            val bytes =
                runCatching {
                    secureImageNetworkLoads.withPermit {
                        chatRepository.downloadAndDecryptChatMedia(scopeId, viewerUserId, url)
                    }
                }.onFailure { e ->
                    println("ChatViewModel: secure image decrypt failed for message=${message.id}: ${e.redactedRestMessage()}")
                }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                println("ChatViewModel: secure image bytes missing for message=${message.id}")
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, error = "Could not load image"))
                }
            } else {
                secureImageBytesCache.put(message.id, bytes)
                val extension = imageExtension ?: imageVaultFileExtension(message.originalMimeTypeOrNull(), url)
                writeChatMediaVaultFile(message.id, bytes, extension)
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, imageBytes = bytes))
                }
            }
        }
    }

    override fun ensureSecureChatAudioLoaded(
        scopeId: String,
        viewerUserId: String,
        message: Message,
    ) {
        if (message.messageType.lowercase() != ChatMessageType.AUDIO) return
        val url = message.mediaUrlOrNull() ?: return
        if (url.isBlank() && !message.hasLocalMediaUri()) return
        val extension = chatMediaVaultExtensionForMessage(message)
        val cachedPath = secureAudioPathCache.get(message.id)
        if (!cachedPath.isNullOrBlank()) {
            _secureChatMediaLoadState.update {
                it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = cachedPath))
            }
            return
        }
        resolveVaultedAudioLocalPath(message, extension)?.let { localPath ->
            secureAudioPathCache.put(message.id, localPath)
            _secureChatMediaLoadState.update {
                it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = localPath))
            }
            return
        }
        val cur = _secureChatMediaLoadState.value[message.id]
        if (cur?.audioLocalPath != null || cur?.loading == true) return
        if (!message.isEncryptedMedia()) return
        viewModelScope.launch(chatMediaDispatcher) {
            _secureChatMediaLoadState.update { it + (message.id to SecureChatMediaLoadState(loading = true)) }
            // Refresh JWT before decrypt — stale cold-start tokens made audio appear stuck on "Preparing".
            runCatching { chatRepository.ensureFreshAuthToken() }
            val bytes =
                runCatching {
                    chatRepository.downloadAndDecryptChatMedia(scopeId, viewerUserId, url)
                }.onFailure { e ->
                    println("ChatViewModel: secure audio decrypt failed for message=${message.id}: ${e.redactedRestMessage()}")
                }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                // One more refresh+retry before surfacing a permanent error.
                runCatching { chatRepository.ensureFreshAuthToken() }
                val retried =
                    runCatching {
                        chatRepository.downloadAndDecryptChatMedia(scopeId, viewerUserId, url)
                    }.getOrNull()
                if (retried == null || retried.isEmpty()) {
                    println("ChatViewModel: secure audio bytes missing for message=${message.id}")
                    _secureChatMediaLoadState.update {
                        it + (message.id to SecureChatMediaLoadState(loading = false, error = "Could not load audio"))
                    }
                    return@launch
                }
                cacheAndPublishSecureAudio(message, retried)
                return@launch
            }
            cacheAndPublishSecureAudio(message, bytes)
        }
    }

    suspend fun fetchDecryptedChatMediaBytes(message: Message): ByteArray? = fetchDecryptedChatMediaBytesImpl(message = message)

    fun sendMessage() = sendMessageImpl()

    fun stageMediaForUpload(
        bytes: ByteArray,
        mimeType: String,
    ) = stageMediaForUploadImpl(bytes = bytes, mimeType = mimeType)

    fun removeStagedMedia(id: String) = removeStagedMediaImpl(id = id)

    fun stageBeaconForShare(beacon: compose.project.click.click.data.models.MapBeacon) = stageBeaconForShareImpl(beacon = beacon)

    fun clearStagedBeacon() = clearStagedBeaconImpl()

    fun commitStagedBeacon() = commitStagedBeaconImpl()

    fun commitStagedMediaToUpload() = commitStagedMediaToUploadImpl()

    fun sendDisposableRollPhoto(
        bytes: ByteArray,
        encounterId: String,
        collaborationTtlIso: String,
        mimeType: String = "image/jpeg",
    ) = sendDisposableRollPhotoImpl(
        bytes = bytes,
        encounterId = encounterId,
        collaborationTtlIso = collaborationTtlIso,
        mimeType = mimeType,
    )

    fun sendChatAudio(
        bytes: ByteArray,
        mimeType: String,
        durationSeconds: Int?,
    ) = sendChatAudioImpl(bytes = bytes, mimeType = mimeType, durationSeconds = durationSeconds)

    fun sendChatFile(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ) = sendChatFileImpl(bytes = bytes, mimeType = mimeType, fileName = fileName)

    suspend fun downloadChatAttachment(
        messageId: String,
        envelope: AttachmentCrypto.Envelope,
        message: Message? = null,
    ): ChatAttachmentDownloadOutcome =
        downloadChatAttachmentImpl(
            messageId = messageId,
            envelope = envelope,
            v2Metadata = message?.e2eeV2MediaMetadataOrNull(currentApiChatId),
            v2StoragePath = message?.e2eeV2MediaStoragePathOrNull(),
        )

    fun clearMessageSendError() = clearMessageSendErrorImpl()

    fun startReplyTo(target: MessageWithUser) = startReplyToImpl(target = target)

    fun clearReplyTarget() = clearReplyTargetImpl()

    fun updateMessageInput(text: String) = updateMessageInputImpl(text = text)

    fun leaveChatRoom(clearMessageSurface: Boolean = true) = leaveChatRoomImpl(clearMessageSurface = clearMessageSurface)

    fun startTypingMonitoring(chatId: String) = startTypingMonitoringImpl(chatId = chatId)

    fun onUserTyping(chatId: String) = onUserTypingImpl(chatId = chatId)

    fun onUserStoppedTyping(chatId: String) = onUserStoppedTypingImpl(chatId = chatId)

    // ── Reactions ──────────────────────────────────────────────────────────────

    fun toggleReaction(
        messageId: String,
        reactionType: String,
    ) = toggleReactionImpl(messageId = messageId, reactionType = reactionType)

    fun addReaction(
        messageId: String,
        reactionType: String,
    ) = addReactionImpl(messageId = messageId, reactionType = reactionType)

    fun removeReaction(
        messageId: String,
        reactionType: String,
    ) = removeReactionImpl(messageId = messageId, reactionType = reactionType)

    fun forwardMessage(
        messageId: String,
        targetChatId: String,
    ) = forwardMessageImpl(messageId = messageId, targetChatId = targetChatId)

    fun sendBeaconMessage(beacon: compose.project.click.click.data.models.MapBeacon) = sendBeaconMessageImpl(beacon = beacon)

    fun sendBeaconMessageToChat(
        chatId: String,
        beacon: compose.project.click.click.data.models.MapBeacon,
    ) = sendBeaconMessageToChatImpl(chatId = chatId, beacon = beacon)

    fun searchMessages(
        chatId: String,
        query: String,
    ) = searchMessagesImpl(chatId = chatId, query = query)

    fun keepConnection() = keepConnectionImpl()

    fun handleExpiredConnectionDismiss() = handleExpiredConnectionDismissImpl()

    fun useIcebreakerPrompt(prompt: IcebreakerPrompt) = useIcebreakerPromptImpl(prompt = prompt)

    fun dismissIcebreakerPanel() = dismissIcebreakerPanelImpl()

    // ==================== Nudge ====================

    fun sendNudge() = sendNudgeImpl()

    fun sendNudgeToChat(
        chatId: String,
        otherUserName: String,
    ) = sendNudgeToChatImpl(chatId = chatId, otherUserName = otherUserName)

    fun sendArchiveBannerIcebreaker(
        connectionId: String,
        otherDisplayName: String,
    ) = sendArchiveBannerIcebreakerImpl(connectionId = connectionId, otherDisplayName = otherDisplayName)

    fun clearNudgeResult() = clearNudgeResultImpl()

    fun notifyVerifiedCliqueSelectionBlocked() = notifyVerifiedCliqueSelectionBlockedImpl()

    fun currentChatLocalMessages(): List<ProfileSheetLocalMessage> = currentChatLocalMessagesImpl()

    suspend fun memberSetSatisfiesVerifiedCliqueGraph(memberUserIds: List<String>): Boolean =
        memberSetSatisfiesVerifiedCliqueGraphImpl(memberUserIds = memberUserIds)

    suspend fun computeVerifiedCliqueAddableMask(
        baseMemberUserIds: List<String>,
        candidateUserIds: List<String>,
        selectedCandidateIds: Set<String>,
    ): Map<String, Boolean> =
        computeVerifiedCliqueAddableMaskImpl(
            baseMemberUserIds = baseMemberUserIds,
            candidateUserIds = candidateUserIds,
            selectedCandidateIds = selectedCandidateIds,
        )

    fun createVerifiedClique(
        selectedFriendUserIds: List<String>,
        onResult: (Result<String>) -> Unit,
    ) = createVerifiedCliqueImpl(selectedFriendUserIds = selectedFriendUserIds, onResult = onResult)

    // ==================== Message Edit / Delete ====================

    fun startEditMessage(
        messageId: String,
        currentContent: String,
    ) = startEditMessageImpl(messageId = messageId, currentContent = currentContent)

    fun cancelEditMessage() = cancelEditMessageImpl()

    fun deleteMessage(messageId: String) = deleteMessageImpl(messageId = messageId)

    // ==================== Connection Archive / Delete (User-initiated) ====================

    fun archiveConnection(onComplete: (Boolean) -> Unit = {}) = archiveConnectionImpl(onComplete = onComplete)

    fun archiveConnectionById(
        connectionId: String,
        onComplete: (Boolean) -> Unit = {},
    ) = archiveConnectionByIdImpl(connectionId = connectionId, onComplete = onComplete)

    fun unarchiveConnection(connectionId: String) = unarchiveConnectionImpl(connectionId = connectionId)

    fun addConnectionToCore(connectionId: String) = addConnectionToCoreImpl(connectionId = connectionId)

    fun removeConnectionFromCore(connectionId: String) = removeConnectionFromCoreImpl(connectionId = connectionId)

    fun deleteConnectionPermanently(onComplete: (Boolean) -> Unit = {}) = deleteConnectionPermanentlyImpl(onComplete = onComplete)

    fun deleteConnectionPermanentlyById(
        connectionId: String,
        onComplete: (Boolean) -> Unit = {},
    ) = deleteConnectionPermanentlyByIdImpl(connectionId = connectionId, onComplete = onComplete)

    fun leaveVerifiedClique(
        groupId: String,
        onComplete: (Boolean) -> Unit = {},
    ) = leaveVerifiedCliqueImpl(groupId = groupId, onComplete = onComplete)

    fun deleteVerifiedClique(
        groupId: String,
        onComplete: (Boolean) -> Unit = {},
    ) = deleteVerifiedCliqueImpl(groupId = groupId, onComplete = onComplete)

    fun addMemberToVerifiedClique(
        groupId: String,
        newMemberUserId: String,
        onComplete: (Boolean) -> Unit = {},
    ) = addMemberToVerifiedCliqueImpl(groupId = groupId, newMemberUserId = newMemberUserId, onComplete = onComplete)

    fun addMembersToVerifiedClique(
        groupId: String,
        newMemberUserIds: List<String>,
        onComplete: (Boolean, Int) -> Unit = { _, _ -> },
    ) = addMembersToVerifiedCliqueImpl(groupId = groupId, newMemberUserIds = newMemberUserIds, onComplete = onComplete)

    fun removeMemberFromVerifiedClique(
        groupId: String,
        memberUserId: String,
        onComplete: (Boolean) -> Unit = {},
    ) = removeMemberFromVerifiedCliqueImpl(groupId = groupId, memberUserId = memberUserId, onComplete = onComplete)

    fun renameVerifiedClique(
        groupId: String,
        newName: String,
        onComplete: (Boolean) -> Unit = {},
    ) = renameVerifiedCliqueImpl(groupId = groupId, newName = newName, onComplete = onComplete)

    fun fetchActiveHubDetails(
        hubId: String,
        onComplete: (Result<ChatApiClient.HubDetailsDto>) -> Unit,
    ) = fetchActiveHubDetailsImpl(hubId = hubId, onComplete = onComplete)

    fun updateActiveHub(
        hubId: String,
        name: String,
        category: String,
        onComplete: (Boolean) -> Unit = {},
    ) = updateActiveHubImpl(hubId = hubId, name = name, category = category, onComplete = onComplete)

    fun leaveActiveHub(
        hubId: String,
        onComplete: (Boolean) -> Unit = {},
    ) = leaveActiveHubImpl(hubId = hubId, onComplete = onComplete)

    fun deleteActiveHub(
        hubId: String,
        onComplete: (Boolean) -> Unit = {},
    ) = deleteActiveHubImpl(hubId = hubId, onComplete = onComplete)

    // ==================== Safety Actions ====================

    fun blockUser(onBlocked: (Boolean) -> Unit) = blockUserImpl(onBlocked = onBlocked)

    fun blockUserForConnection(
        connectionId: String,
        onBlocked: (Boolean) -> Unit = {},
    ) = blockUserForConnectionImpl(connectionId = connectionId, onBlocked = onBlocked)

    fun reportConnection(
        reason: String,
        onReported: (Boolean) -> Unit,
    ) = reportConnectionImpl(reason = reason, onReported = onReported)

    fun reportConnectionForConnection(
        connectionId: String,
        reason: String,
        onReported: (Boolean) -> Unit = {},
    ) = reportConnectionForConnectionImpl(connectionId = connectionId, reason = reason, onReported = onReported)

    override fun onCleared() {
        // Cancel jobs first, capture teardown refs, then call super().
        // super.onCleared() cancels viewModelScope — after that point, any
        // viewModelScope.launch here becomes a silent no-op, which is why
        // every remote-side cleanup goes through teardownBlocking (bounded,
        // off-main, NonCancellable) instead.
        clearSecureChatMediaCache(purgePersistentCache = true)
        connectionsRealtimeJob?.cancel()
        connectionsRealtimeJob = null
        globalMessageListJob?.cancel()
        globalMessageListJob = null
        debouncedChatListRefreshJob?.cancel()
        debouncedChatListRefreshJob = null
        realtimeJob?.cancel()
        typingPollingJob?.cancel()
        peerTypingTimeoutJob?.cancel()
        peerOnlineJob?.cancel()
        localTypingIdleJob?.cancel()
        vibeCheckTimerJob?.cancel()
        icebreakerCooldownTickerJob?.cancel()
        icebreakerCooldownTickerJob = null

        val connectionsChannel = connectionsRealtimeChannel
        connectionsRealtimeChannel = null
        val apiIdToLeave = currentApiChatId
        val messageSub = activeMessageSubscription
        activeMessageSubscription = null

        connectivityMonitor.stop()
        super.onCleared()

        if (connectionsChannel != null) {
            teardownBlocking { runCatching { connectionsChannel.unsubscribe() } }
        }
        if (apiIdToLeave != null) {
            teardownBlocking { runCatching { chatRepository.leaveChatEphemeralChannel(apiIdToLeave) } }
        }
        if (messageSub != null) {
            teardownBlocking { runCatching { messageSub.detach() } }
        }
    }
}

private const val SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES = 160
private const val SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES = 80
