package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.IcebreakerPrompt
import compose.project.click.click.data.models.IcebreakerRepository
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.replySnippetForMetadata
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.isResolvedDisplayName
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.repository.SupabaseChatRepository
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.repository.ChatMessageSubscription
import compose.project.click.click.data.repository.ChatReactionSubscription
import compose.project.click.click.data.repository.MessageChangeEvent
import compose.project.click.click.data.repository.ReactionChangeEvent
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

@Serializable
private data class ConnectionRealtimeRow(
    val id: String,
    @SerialName("user_ids") val userIds: List<String>? = null,
)

private fun JsonObject.stringField(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull

/**
 * Realtime payloads for [PostgresAction.Update] are often partial; [decodeRecordOrNull] plus raw
 * [JsonObject] fields avoid missing refreshes when only [Connection.last_message_at] changes.
 */
private fun connectionRowRelevantToUser(action: PostgresAction, userId: String): Boolean {
    val knownIds = AppDataManager.connections.value.map { it.id }.toSet()
    return when (action) {
        is PostgresAction.Insert -> {
            val row = action.decodeRecordOrNull<ConnectionRealtimeRow>()
            row?.userIds?.contains(userId) == true
        }
        is PostgresAction.Update -> {
            val row = action.decodeRecordOrNull<ConnectionRealtimeRow>()
            val id = row?.id ?: action.record.stringField("id")
            row?.userIds?.contains(userId) == true || (id != null && id in knownIds)
        }
        is PostgresAction.Delete -> {
            val id = action.oldRecord.stringField("id") ?: return false
            id in knownIds
        }
        else -> false
    }
}

sealed class ChatListState {
    data object Loading : ChatListState()
    data class Success(val chats: List<ChatWithDetails>) : ChatListState()
    data class Error(val message: String) : ChatListState()
}

sealed class ChatMessagesState {
    data object Loading : ChatMessagesState()
    data class Success(
        val messages: List<MessageWithUser>,
        val chatDetails: ChatWithDetails,
        val isLoadingMessages: Boolean = false
    ) : ChatMessagesState()
    data class Error(val message: String) : ChatMessagesState()
}

class ChatViewModel(
    tokenStorage: TokenStorage = createTokenStorage(),
    private val chatRepository: ChatRepository = SupabaseChatRepository(tokenStorage = tokenStorage),
    private val supabaseRepository: SupabaseRepository = SupabaseRepository()
) : ViewModel() {

    private data class PrefetchedChatPayload(
        val messages: List<MessageWithUser>,
        val reactionsByMessageId: Map<String, List<MessageReaction>>,
        val icebreakerPrompts: List<IcebreakerPrompt>,
        val showIcebreakerPanel: Boolean
    )

    private val vibeCheckEnabled = false

    private val _chatListState = MutableStateFlow<ChatListState>(ChatListState.Loading)
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()

    private val _chatMessagesState = MutableStateFlow<ChatMessagesState>(ChatMessagesState.Loading)
    val chatMessagesState: StateFlow<ChatMessagesState> = _chatMessagesState.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _replyingTo = MutableStateFlow<MessageWithUser?>(null)
    val replyingTo: StateFlow<MessageWithUser?> = _replyingTo.asStateFlow()

    /** True while a send or edit-submit is in flight; UI uses this to avoid double sends. */
    private val _isMessageSubmitInProgress = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isMessageSubmitInProgress.asStateFlow()

    private val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping: StateFlow<Boolean> = _isPeerTyping.asStateFlow()

    private val _isPeerOnline = MutableStateFlow(false)
    val isPeerOnline: StateFlow<Boolean> = _isPeerOnline.asStateFlow()

    private val _isLocalTypingActive = MutableStateFlow(false)
    val isLocalTypingActive: StateFlow<Boolean> = _isLocalTypingActive.asStateFlow()
    
    // Vibe Check Timer State
    private val _vibeCheckRemainingMs = MutableStateFlow<Long>(0L)
    val vibeCheckRemainingMs: StateFlow<Long> = _vibeCheckRemainingMs.asStateFlow()
    
    private val _currentUserHasKept = MutableStateFlow(false)
    val currentUserHasKept: StateFlow<Boolean> = _currentUserHasKept.asStateFlow()
    
    private val _otherUserHasKept = MutableStateFlow(false)
    val otherUserHasKept: StateFlow<Boolean> = _otherUserHasKept.asStateFlow()
    
    private val _vibeCheckExpired = MutableStateFlow(false)
    val vibeCheckExpired: StateFlow<Boolean> = _vibeCheckExpired.asStateFlow()
    
    private val _connectionKept = MutableStateFlow(false)
    val connectionKept: StateFlow<Boolean> = _connectionKept.asStateFlow()
    
    // Icebreaker Prompts State
    private val _icebreakerPrompts = MutableStateFlow<List<IcebreakerPrompt>>(emptyList())
    val icebreakerPrompts: StateFlow<List<IcebreakerPrompt>> = _icebreakerPrompts.asStateFlow()
    
    private val _showIcebreakerPanel = MutableStateFlow(true)
    val showIcebreakerPanel: StateFlow<Boolean> = _showIcebreakerPanel.asStateFlow()

    // ── Nudge result feedback ──────────────────────────────────────────────────
    private val _nudgeResult = MutableStateFlow<String?>(null)
    val nudgeResult: StateFlow<String?> = _nudgeResult.asStateFlow()

    // ── Message send error feedback ────────────────────────────────────────────
    private val _messageSendError = MutableStateFlow<String?>(null)
    val messageSendError: StateFlow<String?> = _messageSendError.asStateFlow()

    // ── Message editing state ─────────────────────────────────────────────────
    // Non-null when the user is editing an existing message
    private val _editingMessageId = MutableStateFlow<String?>(null)
    val editingMessageId: StateFlow<String?> = _editingMessageId.asStateFlow()

    // ── Archived connection IDs (in-memory; persisted per-session) ─────────────
    private val _archivedConnectionIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedConnectionIds: StateFlow<Set<String>> = _archivedConnectionIds.asStateFlow()
    private val _hiddenConnectionIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenConnectionIds: StateFlow<Set<String>> = _hiddenConnectionIds.asStateFlow()

    // ── Reactions state: messageId → list of reactions ─────────────────────────
    private val _messageReactions = MutableStateFlow<Map<String, List<compose.project.click.click.data.models.MessageReaction>>>(emptyMap())
    val messageReactions: StateFlow<Map<String, List<compose.project.click.click.data.models.MessageReaction>>> = _messageReactions.asStateFlow()

    private var currentConnectionId: String? = null
    private var currentApiChatId: String? = null
    private var activeMessageSubscription: ChatMessageSubscription? = null
    private var activeReactionSubscription: ChatReactionSubscription? = null
    private var reactionsJob: Job? = null
    private var realtimeJob: Job? = null
    private var activeChatSyncJob: Job? = null
    private var typingPollingJob: Job? = null
    private var peerTypingTimeoutJob: Job? = null
    private var peerOnlineJob: Job? = null
    private var localTypingIdleJob: Job? = null
    private var connectionsRealtimeJob: Job? = null
    private var connectionsRealtimeChannel: RealtimeChannel? = null
    private var globalMessageListJob: Job? = null
    private var debouncedChatListRefreshJob: Job? = null
    private var vibeCheckTimerJob: Job? = null
    private var lastTypingSent: Long = 0L
    private val prefetchedChatPayloads = mutableMapOf<String, PrefetchedChatPayload>()
    private val prefetchedChatLimit = 3

    init {
        viewModelScope.launch {
            combine(
                AppDataManager.connections,
                AppDataManager.connectedUsers
            ) { connections, connectedUsers ->
                connections to connectedUsers
            }.collect { (connections, connectedUsers) ->
                val currentUserId = _currentUserId.value

                // Always patch the open chat screen with the freshest user name.
                val currentMessages = _chatMessagesState.value as? ChatMessagesState.Success
                if (currentMessages != null) {
                    val refreshedOtherUser = connectedUsers[currentMessages.chatDetails.otherUser.id]
                    if (refreshedOtherUser != null && refreshedOtherUser != currentMessages.chatDetails.otherUser) {
                        _chatMessagesState.value = currentMessages.copy(
                            chatDetails = currentMessages.chatDetails.copy(otherUser = refreshedOtherUser)
                        )
                    }
                }

                // When AppDataManager resolves user names (e.g. after the RPC fallback or a
                // retry), patch the chat-list rows in place so the UI updates immediately without
                // waiting for a full API round-trip. Doing this avoids the 30-second heartbeat
                // cycle that previously kept "Connection" visible until the next presence tick.
                val currentListState = _chatListState.value as? ChatListState.Success
                if (currentListState != null && currentUserId != null) {
                    val cachedChatsByConnectionId = buildCachedChats(connections, connectedUsers, currentUserId)
                        .associateBy { it.connection.id }
                    val visibleConnectionIds = connections.map { it.id }.toSet()

                    val mergedChats = currentListState.chats
                        .filter { it.connection.id in visibleConnectionIds }
                        .map { chat ->
                            val cachedChat = cachedChatsByConnectionId[chat.connection.id]
                            val freshUser = cachedChat?.otherUser ?: connectedUsers[chat.otherUser.id]
                            mergeChatRowWithCache(chat, cachedChat, freshUser)
                        }

                    val currentConnectionIds = mergedChats.map { it.connection.id }.toSet()
                    val missingChats = cachedChatsByConnectionId.values
                        .filter { it.connection.id !in currentConnectionIds }
                        .sortedByDescending { chatListActivityTimestamp(it) }

                    val reconciledChats = applyConnectionVisibilityFilters(
                        (missingChats + mergedChats)
                            .distinctBy { it.connection.id }
                            .sortedByDescending { chatListActivityTimestamp(it) }
                    )

                    if (reconciledChats != currentListState.chats) {
                        _chatListState.value = ChatListState.Success(reconciledChats)
                    }

                    if (missingChats.isNotEmpty()) {
                        loadChats(isForced = true)
                    }
                }

                // Only trigger a full chat-list load when we don't already have real data.
                // Previously this called loadChats(isForced = true) on every connectedUsers
                // emission (including the 30-second heartbeat), causing redundant full fetches.
                if (currentUserId != null &&
                    connections.isNotEmpty() &&
                    connectedUsers.isNotEmpty() &&
                    _chatListState.value !is ChatListState.Success
                ) {
                    loadChats(isForced = true)
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
    }

    // Set the current user
    fun setCurrentUser(userId: String) {
        val userUnchanged = _currentUserId.value == userId
        if (!userUnchanged) {
            prefetchedChatPayloads.clear()
        }
        _currentUserId.value = userId
        startGlobalConnectionsRealtime(userId)
        startGlobalMessageListRealtime()
        viewModelScope.launch {
            _archivedConnectionIds.value = supabaseRepository.getArchivedConnectionIds(userId)
        }
        if (userUnchanged && _chatListState.value is ChatListState.Success) return
        loadChats()
    }

    private fun scheduleDebouncedChatListRefresh() {
        debouncedChatListRefreshJob?.cancel()
        debouncedChatListRefreshJob = viewModelScope.launch {
            delay(CONNECTIONS_LIST_DEBOUNCE_MS)
            loadChats(isForced = true)
        }
    }

    /**
     * Supabase updates [connections.last_message_at] when a row is inserted into [messages]
     * (see DB trigger). Subscribing here keeps the connections list preview and order fresh
     * even when no per-chat message channel is open.
     */
    private fun startGlobalConnectionsRealtime(userId: String) {
        connectionsRealtimeJob?.cancel()
        debouncedChatListRefreshJob?.cancel()
        val previous = connectionsRealtimeChannel
        connectionsRealtimeChannel = null
        if (previous != null) {
            viewModelScope.launch {
                runCatching { previous.unsubscribe() }
            }
        }
        // Keep collection in this same Job: `flow.launchIn(this)` + a short `subscribe()` lets the
        // parent coroutine finish and cancels the child collector (no events delivered).
        connectionsRealtimeJob = viewModelScope.launch {
            try {
                val channel = SupabaseConfig.client.channel("chatvm:connections:$userId")
                connectionsRealtimeChannel = channel
                try {
                    channel.subscribe(blockUntilSubscribed = true)
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "connections"
                    }.collect { action ->
                        if (connectionRowRelevantToUser(action, userId)) {
                            scheduleDebouncedChatListRefresh()
                        }
                    }
                } finally {
                    runCatching { channel.unsubscribe() }
                    if (connectionsRealtimeChannel === channel) {
                        connectionsRealtimeChannel = null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // JVM/Robolectric tests and pre-client init: no Supabase — list still updates via
                // bumpConnectionInChatList / loadChats.
                println("ChatViewModel: global connections realtime unavailable: ${e.message}")
                connectionsRealtimeChannel = null
            }
        }
    }

    /**
     * Listens for INSERT on [messages] (RLS-scoped). Updates the Clicks list snippet immediately
     * via [bumpConnectionInChatList], independent of debounced [loadChats] or per-chat subscriptions.
     */
    private fun startGlobalMessageListRealtime() {
        globalMessageListJob?.cancel()
        globalMessageListJob = viewModelScope.launch {
            var sub: ChatMessageSubscription? = null
            try {
                val (subscription, flow) = chatRepository.subscribeToMessageInserts()
                sub = subscription
                subscription.attach()
                flow.collect { event ->
                    bumpConnectionInChatList(event.connectionId, event.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("ChatViewModel: global message list realtime unavailable: ${e.message}")
            } finally {
                runCatching { sub?.detach() }
            }
        }
    }

    // Load all chats for the current user
    fun loadChats(isForced: Boolean = true) {
        val userId = _currentUserId.value ?: return
        
        // Avoid reload if already success and not forced
        if (!isForced && _chatListState.value is ChatListState.Success) return
        
        viewModelScope.launch {
            val cachedConnections = AppDataManager.connections.value
            val cachedUsers = AppDataManager.connectedUsers.value

            // Only use the fast-render cache path when the cached users have RESOLVED names.
            // Using "Connection" placeholder names in the cache would flash unresolved names
            // in the UI, which is exactly the bug we are fixing.
            val canRenderCachedChats = cachedConnections.any { connection ->
                connection.user_ids.any { otherUserId ->
                    otherUserId != userId &&
                    cachedUsers[otherUserId]?.let { isResolvedDisplayName(it.name) } == true
                }
            }
            
            // CRITICAL: Never revert a Success state to Loading. When navigating
            // back to the connections list the previously loaded data must remain
            // visible while the background refresh runs. Only show Loading (or
            // cached placeholders) when no real data has ever been emitted.
            val alreadyHasRealData = _chatListState.value is ChatListState.Success
            
            if (!alreadyHasRealData && cachedConnections.isNotEmpty() && canRenderCachedChats) {
                val cachedChats = buildCachedChats(cachedConnections, cachedUsers, userId)
                val readyChats = cachedChats.filter { isResolvedDisplayName(it.otherUser.name) }
                if (readyChats.isNotEmpty()) {
                    _chatListState.value = ChatListState.Success(applyConnectionVisibilityFilters(readyChats))
                }
            } else if (!alreadyHasRealData && cachedConnections.isNotEmpty()) {
                // Even with unresolved names, prefer showing cached rows over a
                // blank loading spinner – the API response will patch names shortly.
                val fallbackChats = buildCachedChats(cachedConnections, cachedUsers, userId)
                if (fallbackChats.isNotEmpty()) {
                    _chatListState.value = ChatListState.Success(applyConnectionVisibilityFilters(fallbackChats))
                } else {
                    _chatListState.value = ChatListState.Loading
                }
            } else if (!alreadyHasRealData) {
                _chatListState.value = ChatListState.Loading
            }
            
            // Fetch fresh data from API in background
            try {
                val chats = chatRepository.fetchUserChatsWithDetails(userId)

                if (chats.isNotEmpty()) {
                    // Prefer any already-resolved names from AppDataManager's cache over
                    // freshly-fetched users that still carry "Connection" (can happen when the
                    // RPC resolved names in AppDataManager before the ChatRepository fetch ran).
                    val enriched = chats.map { chat ->
                        val cached = cachedUsers[chat.otherUser.id]
                        if (cached != null &&
                            isResolvedDisplayName(cached.name) &&
                            !isResolvedDisplayName(chat.otherUser.name)
                        ) {
                            chat.copy(otherUser = cached)
                        } else {
                            chat
                        }
                    }
                    val cachedChatsById =
                        buildCachedChats(cachedConnections, cachedUsers, userId).associateBy { it.connection.id }
                    val mergedWithLocalPreview = enriched.map { apiChat ->
                        val cachedRow = cachedChatsById[apiChat.connection.id]
                        val freshUser = cachedRow?.otherUser ?: cachedUsers[apiChat.otherUser.id]
                        mergeChatRowWithCache(apiChat, cachedRow, freshUser)
                    }
                    _chatListState.value =
                        ChatListState.Success(applyConnectionVisibilityFilters(mergedWithLocalPreview))
                    prefetchChatPayloads(userId, enriched)
                } else if (cachedConnections.isNotEmpty() && canRenderCachedChats) {
                    // Keep hydrated/cached connections visible when API is empty
                    // (common during session bootstrap or backend shape mismatch).
                    if (_chatListState.value !is ChatListState.Success) {
                        val readyChats = buildCachedChats(cachedConnections, cachedUsers, userId)
                            .filter { isResolvedDisplayName(it.otherUser.name) }
                        if (readyChats.isNotEmpty()) {
                            _chatListState.value = ChatListState.Success(
                                applyConnectionVisibilityFilters(readyChats)
                            )
                        }
                    }
                } else {
                    _chatListState.value = ChatListState.Success(emptyList())
                }
            } catch (e: Exception) {
                // Only show error if we don't have cached data
                if (cachedConnections.isEmpty()) {
                    _chatListState.value = ChatListState.Error(e.message ?: "Failed to load chats")
                }
                // Otherwise keep showing cached data
            }
        }
    }

    private fun buildCachedChats(
        cachedConnections: List<Connection>,
        cachedUsers: Map<String, User>,
        userId: String
    ): List<ChatWithDetails> {
        return cachedConnections.mapNotNull { connection ->
            val otherUserId = connection.user_ids.firstOrNull { it != userId } ?: return@mapNotNull null
            val otherUser = cachedUsers[otherUserId] ?: User(id = otherUserId, name = "Connection", createdAt = 0L)
            ChatWithDetails(
                chat = connection.chat,
                connection = connection,
                otherUser = otherUser,
                lastMessage = connection.chat.messages.lastOrNull(),
                unreadCount = 0
            )
        }.sortedByDescending { chatListActivityTimestamp(it) }
    }

    private fun applyConnectionVisibilityFilters(chats: List<ChatWithDetails>): List<ChatWithDetails> {
        val hiddenIds = _hiddenConnectionIds.value
        val now = Clock.System.now().toEpochMilliseconds()
        return chats.filter { chat ->
            chat.connection.id !in hiddenIds && !isExpiredConnection(chat.connection, now)
        }
    }

    private fun isExpiredConnection(connection: Connection, now: Long): Boolean {
        return connection.expiry_state == "expired" && connection.expiry < now
    }

    private fun chatListActivityTimestamp(chat: ChatWithDetails): Long =
        chat.connection.last_message_at
            ?: chat.lastMessage?.timeCreated
            ?: chat.connection.created

    /**
     * Reconcile a server/AppDataManager-derived row with the in-memory chat list without
     * clobbering fresher [lastMessage] / [Connection.last_message_at] from realtime or send paths.
     */
    private fun mergeChatRowWithCache(
        listChat: ChatWithDetails,
        cachedChat: ChatWithDetails?,
        freshUser: User?
    ): ChatWithDetails {
        if (cachedChat == null) {
            return if (freshUser != null &&
                freshUser != listChat.otherUser &&
                (isResolvedDisplayName(freshUser.name) || !isResolvedDisplayName(listChat.otherUser.name))
            ) {
                listChat.copy(otherUser = freshUser)
            } else {
                listChat
            }
        }

        val listTs = chatListActivityTimestamp(listChat)
        val cacheTs = chatListActivityTimestamp(cachedChat)
        val bestLast = when {
            listChat.lastMessage == null -> cachedChat.lastMessage
            cachedChat.lastMessage == null -> listChat.lastMessage
            listChat.lastMessage.timeCreated >= cachedChat.lastMessage.timeCreated -> listChat.lastMessage
            else -> cachedChat.lastMessage
        }
        val preferredConnection = if (listTs >= cacheTs) listChat.connection else cachedChat.connection
        val mergedAt = listOfNotNull(
            listChat.connection.last_message_at,
            cachedChat.connection.last_message_at,
            bestLast?.timeCreated
        ).maxOrNull()
        val normalizedLast = bestLast?.takeIf { msg ->
            mergedAt == null || msg.timeCreated >= mergedAt
        }
        val mergedChat = if (normalizedLast != null) {
            preferredConnection.chat.copy(messages = listOf(normalizedLast))
        } else if (mergedAt != null) {
            // last_message_at moved ahead, but we don't have the plaintext yet; drop stale preview.
            preferredConnection.chat.copy(messages = emptyList())
        } else {
            preferredConnection.chat
        }
        val mergedConnection = preferredConnection.copy(
            last_message_at = mergedAt ?: preferredConnection.last_message_at,
            chat = mergedChat
        )
        val resolvedOther = when {
            freshUser != null &&
                freshUser != listChat.otherUser &&
                (isResolvedDisplayName(freshUser.name) || !isResolvedDisplayName(listChat.otherUser.name)) -> freshUser
            cachedChat.otherUser != listChat.otherUser &&
                (isResolvedDisplayName(cachedChat.otherUser.name) || !isResolvedDisplayName(listChat.otherUser.name)) -> cachedChat.otherUser
            else -> listChat.otherUser
        }
        return listChat.copy(
            connection = mergedConnection,
            lastMessage = normalizedLast,
            otherUser = resolvedOther
        )
    }

    private fun updateConnectionState(connectionId: String, transform: (Connection) -> Connection) {
        val currentListState = _chatListState.value
        if (currentListState is ChatListState.Success) {
            _chatListState.value = currentListState.copy(
                chats = currentListState.chats.map { chat ->
                    if (chat.connection.id == connectionId) {
                        chat.copy(connection = transform(chat.connection))
                    } else {
                        chat
                    }
                }
            )
        }

        val currentMessageState = _chatMessagesState.value
        if (currentMessageState is ChatMessagesState.Success && currentMessageState.chatDetails.connection.id == connectionId) {
            _chatMessagesState.value = currentMessageState.copy(
                chatDetails = currentMessageState.chatDetails.copy(
                    connection = transform(currentMessageState.chatDetails.connection)
                )
            )
        }
    }

    private fun removeConnectionFromCurrentList(connectionId: String) {
        val state = _chatListState.value
        if (state is ChatListState.Success) {
            _chatListState.value = state.copy(
                chats = state.chats.filter { it.connection.id != connectionId }
            )
        }
    }

    // Load messages for a specific chat
    fun loadChatMessages(chatId: String) {
        val userId = _currentUserId.value ?: return
        val cachedChat = (_chatListState.value as? ChatListState.Success)
            ?.chats?.firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
        val connectionId = cachedChat?.connection?.id ?: chatId

        val currentState = _chatMessagesState.value as? ChatMessagesState.Success
        val currentConnectionStateId = currentState?.chatDetails?.connection?.id
        val activeApiChatId = currentState?.chatDetails?.chat?.id
        val hasRenderableStateForTarget =
            currentState != null && (
                currentConnectionStateId == connectionId ||
                    (activeApiChatId != null && activeApiChatId == chatId)
                )
        val hasLiveSubscriptions =
            currentApiChatId == activeApiChatId &&
                activeMessageSubscription != null &&
                realtimeJob?.isActive == true &&
                activeReactionSubscription != null &&
                reactionsJob?.isActive == true &&
                typingPollingJob?.isActive == true &&
                peerOnlineJob?.isActive == true

        if (currentConnectionId == connectionId && currentState != null && hasLiveSubscriptions) return

        val switchingConnection = currentConnectionId != null && currentConnectionId != connectionId
        if (switchingConnection) {
            currentApiChatId = null
        }
        currentConnectionId = connectionId

        // Instantly show the chat header from cached list data (no loading spinner)
        val prefetchedPayload = prefetchedChatPayloads[connectionId]

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
            _chatMessagesState.value = ChatMessagesState.Success(
                messages = prefetchedPayload.messages,
                chatDetails = cachedChat,
                isLoadingMessages = true
            )
        } else if (hasRenderableStateForTarget && currentState != null) {
            // Keep current content visible while refreshing in background.
            _chatMessagesState.value = currentState.copy(isLoadingMessages = true)
        } else if (cachedChat != null) {
            // Show header, composer, and conversation starters immediately instead of a blank loading screen.
            _chatMessagesState.value = ChatMessagesState.Success(
                messages = emptyList(),
                chatDetails = cachedChat,
                isLoadingMessages = true
            )
        } else {
            _chatMessagesState.value = ChatMessagesState.Loading
        }

        viewModelScope.launch {
            try {
                val previousApiChatId = currentApiChatId
                // Resolve chat details (use cached if available)
                val chatDetails = cachedChat ?: chatRepository.fetchChatWithDetails(chatId, userId)
                if (chatDetails == null) {
                    _chatMessagesState.value = ChatMessagesState.Error("Chat not found")
                    return@launch
                }

                val resolvedConnectionId = chatDetails.connection.id

                val apiChatId = chatDetails.chat.id ?: resolveOrCreateApiChatId(resolvedConnectionId)
                if (apiChatId.isNullOrBlank()) {
                    _chatMessagesState.value = ChatMessagesState.Error("Unable to start chat")
                    return@launch
                }
                currentApiChatId = apiChatId

                if (previousApiChatId != null && previousApiChatId != apiChatId) {
                    chatRepository.leaveChatEphemeralChannel(previousApiChatId)
                }

                val hydratedChatDetails = if (chatDetails.chat.id == apiChatId) {
                    chatDetails
                } else {
                    chatDetails.copy(
                        chat = chatDetails.chat.copy(
                            id = apiChatId,
                            connectionId = resolvedConnectionId
                        )
                    )
                }

                chatRepository.cacheEncryptionKeys(
                    apiChatId,
                    hydratedChatDetails.connection.id,
                    hydratedChatDetails.connection.user_ids
                )

                if (_chatMessagesState.value is ChatMessagesState.Loading) {
                    _icebreakerPrompts.value =
                        IcebreakerRepository.getPromptsForContext(
                            hydratedChatDetails.connection.context_tag,
                            count = 3,
                            stableSelectionKey = hydratedChatDetails.connection.id,
                        )
                    _showIcebreakerPanel.value = true
                    _chatMessagesState.value = ChatMessagesState.Success(
                        messages = emptyList(),
                        chatDetails = hydratedChatDetails,
                        isLoadingMessages = true,
                    )
                }

                val payload = buildChatPayload(hydratedChatDetails, apiChatId, userId)
                prefetchedChatPayloads[resolvedConnectionId] = payload

                _messageReactions.value = payload.reactionsByMessageId
                _showIcebreakerPanel.value = payload.showIcebreakerPanel
                if (payload.showIcebreakerPanel) {
                    if (_icebreakerPrompts.value != payload.icebreakerPrompts) {
                        _icebreakerPrompts.value = payload.icebreakerPrompts
                    }
                } else {
                    _icebreakerPrompts.value = emptyList()
                }
                _chatMessagesState.value = ChatMessagesState.Success(
                    messages = payload.messages,
                    chatDetails = hydratedChatDetails,
                    isLoadingMessages = false
                )

                // Mark messages as read
                chatRepository.markMessagesAsRead(apiChatId, userId)

                chatRepository.joinChatEphemeralChannel(
                    apiChatId,
                    userId,
                    hydratedChatDetails.otherUser.id
                )

                // Subscribe to new messages
                subscribeToNewMessages(apiChatId, userId)

                // Load initial reactions & subscribe to changes via Realtime
                loadAndSubscribeReactions(apiChatId, payload.reactionsByMessageId)
                
                // Monitor typing status (Realtime Broadcast) and peer presence
                startTypingMonitoring(apiChatId)
                startPeerOnlineMonitoring(apiChatId, hydratedChatDetails.otherUser.id)
                startActiveChatSync(apiChatId, userId)
                
                // Vibe Check is disabled
                if (vibeCheckEnabled) {
                    startVibeCheckTimer(chatDetails.connection, userId)
                    updateKeepStates(chatDetails.connection, userId)
                }
                
                // Mark chat as begun if this is the first time
                if (!chatDetails.connection.has_begun) {
                    supabaseRepository.updateConnectionHasBegun(resolvedConnectionId, true)
                }
                
                // Icebreaker state is already prepared in payload before UI render.
            } catch (e: Exception) {
                val latestState = _chatMessagesState.value as? ChatMessagesState.Success
                val sameChatStillVisible =
                    latestState != null && (
                        latestState.chatDetails.connection.id == connectionId ||
                            latestState.chatDetails.chat.id == chatId
                        )

                if (sameChatStillVisible) {
                    _chatMessagesState.value = latestState.copy(isLoadingMessages = false)
                } else {
                    _chatMessagesState.value = ChatMessagesState.Error(e.message ?: "Failed to load messages")
                }
            }
        }
    }

    private fun prefetchChatPayloads(userId: String, chats: List<ChatWithDetails>) {
        viewModelScope.launch {
            chats
                .take(prefetchedChatLimit)
                .forEach { chatDetails ->
                    val connectionId = chatDetails.connection.id
                    if (prefetchedChatPayloads.containsKey(connectionId)) return@forEach
                    val apiChatId = chatDetails.chat.id ?: return@forEach
                    chatRepository.cacheEncryptionKeys(
                        apiChatId, connectionId, chatDetails.connection.user_ids
                    )
                    runCatching {
                        buildChatPayload(chatDetails, apiChatId, userId)
                    }.onSuccess { payload ->
                        prefetchedChatPayloads[connectionId] = payload
                    }
                }
        }
    }

    private suspend fun buildChatPayload(
        chatDetails: ChatWithDetails,
        apiChatId: String,
        userId: String
    ): PrefetchedChatPayload = coroutineScope {
        val messagesDeferred = async { chatRepository.fetchMessagesForChat(apiChatId) }
        val participantsDeferred = async { chatRepository.fetchChatParticipants(apiChatId) }
        val reactionsDeferred = async { chatRepository.fetchReactionsForChat(apiChatId) }

        val participants = participantsDeferred.await().associateBy { it.id }
        val messagesWithUsers = messagesDeferred.await().map { message ->
            val user = participants[message.user_id] ?: User(id = message.user_id, name = "Unknown", createdAt = 0L)
            MessageWithUser(
                message = message,
                user = user,
                isSent = message.user_id == userId
            )
        }
        val reactionsByMessageId = reactionsDeferred.await().groupBy { it.messageId }
        val shouldShowIcebreaker = messagesWithUsers.size < 5
        val prompts = if (shouldShowIcebreaker) {
            IcebreakerRepository.getPromptsForContext(
                chatDetails.connection.context_tag,
                count = 3,
                stableSelectionKey = chatDetails.connection.id,
            )
        } else {
            emptyList()
        }

        PrefetchedChatPayload(
            messages = messagesWithUsers,
            reactionsByMessageId = reactionsByMessageId,
            icebreakerPrompts = prompts,
            showIcebreakerPanel = shouldShowIcebreaker
        )
    }

    // Subscribe to real-time message updates
    private fun subscribeToNewMessages(chatId: String, userId: String) {
        realtimeJob?.cancel()
        currentApiChatId = chatId
        viewModelScope.launch {
            // Clean up previous channel
            activeMessageSubscription?.let {
                try { it.detach() } catch (_: Exception) {}
            }
            activeMessageSubscription = null
        }
        realtimeJob = viewModelScope.launch {
            var attempt = 0
            while (attempt < MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS && currentApiChatId == chatId) {
                try {
                    val (subscription, changeFlow) = chatRepository.subscribeToMessages(chatId)
                    activeMessageSubscription = subscription

                    changeFlow
                        .onEach { event ->
                            when (event) {
                                is MessageChangeEvent.Insert -> {
                                    val user = resolveMessageUser(event.message.user_id, chatId)
                                        ?: User(id = event.message.user_id, name = null, createdAt = 0L)
                                    applyInsertedMessage(event.message, user, userId)
                                    if (event.message.user_id != userId) {
                                        chatRepository.markMessagesAsRead(chatId, userId)
                                    }
                                }
                                is MessageChangeEvent.Update -> {
                                    val currentState = _chatMessagesState.value
                                    if (currentState is ChatMessagesState.Success) {
                                        val updatedMessages = currentState.messages.map { mwu ->
                                            if (mwu.message.id == event.message.id) {
                                                mwu.copy(message = event.message)
                                            } else mwu
                                        }
                                        _chatMessagesState.value = currentState.copy(messages = updatedMessages)
                                        // Refresh the Connections preview when the latest row is edited.
                                        updatedMessages
                                            .maxByOrNull { it.message.timeCreated }
                                            ?.message
                                            ?.takeIf { it.id == event.message.id }
                                            ?.let { newest ->
                                                bumpConnectionInChatList(currentState.chatDetails.connection.id, newest)
                                            }
                                    }
                                }
                                is MessageChangeEvent.Delete -> {
                                    val currentState = _chatMessagesState.value
                                    if (currentState is ChatMessagesState.Success) {
                                        val filtered = currentState.messages.filter { it.message.id != event.messageId }
                                        _chatMessagesState.value = currentState.copy(messages = filtered)
                                    }
                                }
                            }
                        }
                        .launchIn(this)

                    subscription.attach()
                    return@launch
                } catch (e: Exception) {
                    attempt += 1
                    activeMessageSubscription?.let { sub ->
                        try { sub.detach() } catch (_: Exception) {}
                    }
                    activeMessageSubscription = null
                    println("Error subscribing to messages (attempt $attempt): ${e.message}")
                    if (attempt < MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS && currentApiChatId == chatId) {
                        delay(MESSAGE_SUBSCRIPTION_RETRY_DELAY_MS * attempt)
                    }
                }
            }
        }
    }

    private fun restoreActiveChatSubscriptionsIfNeeded() {
        val userId = _currentUserId.value ?: return
        val currentState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
        val apiChatId = currentState.chatDetails.chat.id ?: return
        val peerUserId = currentState.chatDetails.otherUser.id
        val needsMessageSubscription = currentApiChatId != apiChatId || activeMessageSubscription == null || realtimeJob?.isActive != true
        val needsReactionSubscription = activeReactionSubscription == null || reactionsJob?.isActive != true
        val needsTypingSubscription = typingPollingJob?.isActive != true
        val needsPeerPresence = peerOnlineJob?.isActive != true

        currentApiChatId = apiChatId

        viewModelScope.launch {
            if (needsPeerPresence || needsTypingSubscription) {
                chatRepository.joinChatEphemeralChannel(apiChatId, userId, peerUserId)
            }
            if (needsMessageSubscription) {
                subscribeToNewMessages(apiChatId, userId)
            }
            if (needsReactionSubscription) {
                loadAndSubscribeReactions(apiChatId, _messageReactions.value)
            }
            if (needsTypingSubscription) {
                startTypingMonitoring(apiChatId)
            }
            if (needsPeerPresence) {
                startPeerOnlineMonitoring(apiChatId, peerUserId)
            }
            startActiveChatSync(apiChatId, userId)
        }
    }

    private fun startActiveChatSync(chatId: String, userId: String) {
        activeChatSyncJob?.cancel()
        activeChatSyncJob = viewModelScope.launch {
            while (currentApiChatId == chatId) {
                delay(ACTIVE_CHAT_SYNC_INTERVAL_MS)
                syncActiveChatMessages(chatId, userId)
            }
        }
    }

    private suspend fun syncActiveChatMessages(chatId: String, userId: String) {
        val currentState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
        if (currentState.chatDetails.chat.id != chatId) return

        val latestMessages = chatRepository.fetchMessagesForChat(chatId)
        val currentMessages = currentState.messages.map { it.message }
        if (latestMessages == currentMessages) return

        val knownUsers = buildMap {
            put(currentState.chatDetails.otherUser.id, currentState.chatDetails.otherUser)
            currentState.messages.forEach { messageWithUser ->
                put(messageWithUser.user.id, messageWithUser.user)
            }
            AppDataManager.currentUser.value?.let { currentUser ->
                put(currentUser.id, currentUser)
            }
        }.toMutableMap()

        val missingUserIds = latestMessages
            .map { it.user_id }
            .distinct()
            .filterNot { knownUsers.containsKey(it) }

        if (missingUserIds.isNotEmpty()) {
            chatRepository.fetchChatParticipants(chatId).forEach { participant ->
                knownUsers[participant.id] = participant
            }
        }

        val refreshedMessages = latestMessages.map { message ->
            val user = knownUsers[message.user_id] ?: User(
                id = message.user_id,
                name = "Unknown",
                createdAt = 0L
            )
            MessageWithUser(
                message = message,
                user = user,
                isSent = message.user_id == userId
            )
        }

        _chatMessagesState.value = currentState.copy(messages = refreshedMessages)

        latestMessages.lastOrNull()?.let { newest ->
            bumpConnectionInChatList(currentState.chatDetails.connection.id, newest)
        }

        if (latestMessages.any { it.user_id != userId && !it.isRead }) {
            chatRepository.markMessagesAsRead(chatId, userId)
        }
    }

    private suspend fun resolveMessageUser(userId: String, chatId: String): User? {
        val currentState = _chatMessagesState.value as? ChatMessagesState.Success
        if (currentState != null) {
            currentState.messages.firstOrNull { it.user.id == userId }?.let { return it.user }
            if (currentState.chatDetails.otherUser.id == userId) {
                return currentState.chatDetails.otherUser
            }
        }

        AppDataManager.currentUser.value?.takeIf { it.id == userId }?.let { return it }

        return chatRepository.getUserById(userId)
    }

    private fun applyInsertedMessage(message: Message, user: User, currentUserId: String) {
        val currentState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
        val connectionId = currentState.chatDetails.connection.id
        val exists = currentState.messages.any { it.message.id == message.id }
        if (exists) {
            // Realtime often delivers the insert before the REST send returns, or sync refreshes
            // the thread first — the list row must still bump or the Clicks preview stays stale.
            bumpConnectionInChatList(connectionId, message)
            return
        }

        _chatMessagesState.value = currentState.copy(
            messages = currentState.messages + MessageWithUser(
                message = message,
                user = user,
                isSent = message.user_id == currentUserId
            )
        )
        bumpConnectionInChatList(connectionId, message)
    }

    /** Refresh list row + reorder so the active thread moves up when a message arrives or is sent. */
    private fun bumpConnectionInChatList(connectionId: String, message: Message) {
        AppDataManager.updateConnectionChatActivity(connectionId, message.timeCreated, message)
        val state = _chatListState.value as? ChatListState.Success ?: run {
            loadChats(isForced = true)
            return
        }
        val updated = state.chats.map { chat ->
            if (chat.connection.id == connectionId) {
                chat.copy(
                    lastMessage = message,
                    connection = chat.connection.copy(
                        last_message_at = message.timeCreated,
                        chat = chat.connection.chat.copy(messages = listOf(message))
                    )
                )
            } else {
                chat
            }
        }
        val sorted = updated.sortedByDescending { chatListActivityTimestamp(it) }
        _chatListState.value = ChatListState.Success(applyConnectionVisibilityFilters(sorted))
    }

    private suspend fun resolveOrCreateApiChatId(connectionId: String): String? {
        val currentState = _chatMessagesState.value as? ChatMessagesState.Success
        val existingChatId = currentState
            ?.takeIf { it.chatDetails.connection.id == connectionId }
            ?.chatDetails
            ?.chat
            ?.id

        if (!existingChatId.isNullOrBlank()) {
            currentApiChatId = existingChatId
            return existingChatId
        }

        val ensuredChat = chatRepository.ensureChatForConnection(connectionId) ?: return null
        currentApiChatId = ensuredChat.id

        if (currentState != null && currentState.chatDetails.connection.id == connectionId) {
            _chatMessagesState.value = currentState.copy(
                chatDetails = currentState.chatDetails.copy(
                    chat = currentState.chatDetails.chat.copy(
                        id = ensuredChat.id,
                        connectionId = connectionId
                    )
                )
            )
        }

        return ensuredChat.id
    }

    fun sendMessage() {
        // If in edit mode, confirm the edit instead of posting a new message
        val editId = _editingMessageId.value
        if (editId != null) {
            confirmEditMessage(editId)
            return
        }
        val connectionId = currentConnectionId ?: return
        val userId = _currentUserId.value ?: return
        val content = _messageInput.value.trim()
        if (content.isEmpty()) return
        if (_isMessageSubmitInProgress.value) return

        _messageSendError.value = null
        _isMessageSubmitInProgress.value = true
        _messageInput.value = ""
        localTypingIdleJob?.cancel()
        localTypingIdleJob = null
        _isLocalTypingActive.value = false
        val successState = _chatMessagesState.value as? ChatMessagesState.Success
        val typingChatId = successState?.chatDetails?.chat?.id?.takeIf { it.isNotBlank() }
            ?: currentApiChatId?.takeIf { it.isNotBlank() }
        if (typingChatId != null) {
            onUserStoppedTyping(typingChatId)
        }

        viewModelScope.launch {
            try {
                val apiChatId = resolveOrCreateApiChatId(connectionId) ?: run {
                    _messageSendError.value = "Failed to send — unable to start chat"
                    _messageInput.value = content
                    updateMessageInput(content)
                    return@launch
                }
                onUserStoppedTyping(apiChatId)
                val replyTarget = _replyingTo.value
                val metadata = if (replyTarget != null) {
                    buildJsonObject {
                        put("reply_to_id", replyTarget.message.id)
                        put("reply_to_content", replySnippetForMetadata(replyTarget.message.content))
                    }
                } else {
                    null
                }
                val message = chatRepository.sendMessage(
                    chatId = apiChatId,
                    userId = userId,
                    content = content,
                    metadata = metadata,
                )
                if (message != null) {
                    _replyingTo.value = null
                    val currentUser = resolveMessageUser(userId, apiChatId)
                        ?: AppDataManager.currentUser.value?.takeIf { it.id == userId }
                        ?: User(id = userId, name = "You", createdAt = 0L)
                    applyInsertedMessage(message, currentUser, userId)
                    activateConnectionIfPending(connectionId)
                } else {
                    _messageSendError.value = "Failed to send message"
                    _messageInput.value = content
                    updateMessageInput(content)
                    println("Failed to send message")
                }
            } catch (e: Exception) {
                _messageSendError.value = "Failed to send — ${e.message ?: "encryption or network error"}"
                _messageInput.value = content
                updateMessageInput(content)
                println("Error sending message: ${e.message}")
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }

    fun clearMessageSendError() {
        _messageSendError.value = null
    }

    fun startReplyTo(target: MessageWithUser) {
        _editingMessageId.value = null
        _replyingTo.value = target
    }

    fun clearReplyTarget() {
        _replyingTo.value = null
    }

    /**
     * Transition a pending connection to active when the first message is sent.
     * This sets expiry_state = 'active' server-side, starting the 7-day rolling window.
     */
    private suspend fun activateConnectionIfPending(connectionId: String) {
        val currentState = _chatMessagesState.value
        if (currentState is ChatMessagesState.Success) {
            val connection = currentState.chatDetails.connection
            if (connection.isPending()) {
                if (supabaseRepository.updateConnectionExpiryState(connectionId, "active")) {
                    updateConnectionState(connectionId) { it.copy(expiry_state = "active") }
                }
            }
        }
    }

    fun updateMessageInput(text: String) {
        _messageInput.value = text
        val success = _chatMessagesState.value as? ChatMessagesState.Success ?: return
        if (success.chatDetails.connection.id != currentConnectionId) return
        val apiChatId = success.chatDetails.chat.id?.takeIf { it.isNotBlank() }
            ?: currentApiChatId?.takeIf { it.isNotBlank() }
            ?: return
        if (text.isBlank()) {
            localTypingIdleJob?.cancel()
            localTypingIdleJob = null
            _isLocalTypingActive.value = false
            onUserStoppedTyping(apiChatId)
        } else {
            _isLocalTypingActive.value = true
            localTypingIdleJob?.cancel()
            localTypingIdleJob = viewModelScope.launch {
                delay(3000)
                _isLocalTypingActive.value = false
            }
            onUserTyping(apiChatId)
        }
    }

    fun leaveChatRoom() {
        val chatId = (_chatMessagesState.value as? ChatMessagesState.Success)?.chatDetails?.chat?.id
        val userId = _currentUserId.value
        if (chatId != null && userId != null) {
             onUserStoppedTyping(chatId)
        }
        realtimeJob?.cancel()
        realtimeJob = null
        // Remove the realtime channels from Supabase
        activeMessageSubscription?.let { sub ->
            viewModelScope.launch {
                try { sub.detach() } catch (_: Exception) {}
            }
        }
        activeMessageSubscription = null
        reactionsJob?.cancel()
        reactionsJob = null
        activeChatSyncJob?.cancel()
        activeChatSyncJob = null
        activeReactionSubscription?.let { sub ->
            viewModelScope.launch {
                try { sub.detach() } catch (_: Exception) {}
            }
        }
        activeReactionSubscription = null
        _messageReactions.value = emptyMap()
        typingPollingJob?.cancel()
        typingPollingJob = null
        peerTypingTimeoutJob?.cancel()
        peerTypingTimeoutJob = null
        peerOnlineJob?.cancel()
        peerOnlineJob = null
        localTypingIdleJob?.cancel()
        localTypingIdleJob = null
        currentApiChatId?.let { id ->
            viewModelScope.launch { chatRepository.leaveChatEphemeralChannel(id) }
        }
        currentConnectionId = null
        currentApiChatId = null
        _isPeerTyping.value = false
        _isPeerOnline.value = false
        _isLocalTypingActive.value = false
        _isMessageSubmitInProgress.value = false
        _chatMessagesState.value = ChatMessagesState.Loading
        resetVibeCheckState()
        resetIcebreakerState()
    }

    fun startTypingMonitoring(chatId: String) {
        typingPollingJob?.cancel()
        peerTypingTimeoutJob?.cancel()
        typingPollingJob = viewModelScope.launch {
            chatRepository.observeTypingStatus(chatId).collect { status ->
                val currentUser = _currentUserId.value
                if (status.userId != currentUser && status.isTyping) {
                    _isPeerTyping.value = true
                    peerTypingTimeoutJob?.cancel()
                    peerTypingTimeoutJob = launch {
                        delay(3000)
                        _isPeerTyping.value = false
                    }
                }
            }
        }
    }

    private fun startPeerOnlineMonitoring(apiChatId: String, peerUserId: String) {
        peerOnlineJob?.cancel()
        peerOnlineJob = viewModelScope.launch {
            chatRepository.observePeerOnline(apiChatId, peerUserId).collect { online ->
                _isPeerOnline.value = online
            }
        }
    }

    fun onUserTyping(chatId: String) {
        val userId = _currentUserId.value ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastTypingSent > 2000L) {
            lastTypingSent = now
            viewModelScope.launch {
                chatRepository.sendTypingStatus(chatId, userId, true)
            }
        }
    }

    fun onUserStoppedTyping(chatId: String) {
        lastTypingSent = 0L
    }

    // ── Reactions ──────────────────────────────────────────────────────────────

    /** Load existing reactions from DB, then subscribe to Realtime changes. */
    private fun loadAndSubscribeReactions(
        chatId: String,
        initialReactionsByMessageId: Map<String, List<MessageReaction>> = emptyMap()
    ) {
        reactionsJob?.cancel()
        activeReactionSubscription?.let { sub ->
            viewModelScope.launch { try { sub.detach() } catch (_: Exception) {} }
        }
        activeReactionSubscription = null

        reactionsJob = viewModelScope.launch {
            // 1. Fetch existing reactions
            if (initialReactionsByMessageId.isNotEmpty()) {
                _messageReactions.value = initialReactionsByMessageId
            } else {
                val initial = chatRepository.fetchReactionsForChat(chatId)
                _messageReactions.value = initial.groupBy { it.messageId }
            }

            // 2. Subscribe to Realtime inserts/deletes
            try {
                val (reactionSub, changeFlow) = chatRepository.subscribeToReactions(chatId)
                activeReactionSubscription = reactionSub

                changeFlow
                    .onEach { event ->
                        val current = _messageReactions.value.toMutableMap()
                        when (event) {
                            is ReactionChangeEvent.Insert -> {
                                val list = current.getOrElse(event.reaction.messageId) { emptyList() }
                                // Deduplicate against both optimistic and persisted rows
                                val withoutDuplicates = list.filterNot {
                                    it.id == event.reaction.id ||
                                        (it.userId == event.reaction.userId &&
                                            it.reactionType == event.reaction.reactionType)
                                }
                                current[event.reaction.messageId] = withoutDuplicates + event.reaction
                                _messageReactions.value = current
                            }
                            is ReactionChangeEvent.Delete -> {
                                val list = current[event.messageId]
                                if (list != null) {
                                    current[event.messageId] = list.filter { it.id != event.reactionId }
                                    _messageReactions.value = current
                                }
                            }
                        }
                    }
                    .launchIn(this)

                reactionSub.attach()
            } catch (e: Exception) {
                println("Error subscribing to reactions: ${e.message}")
            }
        }
    }

    /**
     * Toggle a reaction on a message. If the current user already has this reaction,
     * remove it; otherwise add it.
     */
    fun toggleReaction(messageId: String, reactionType: String) {
        val userId = _currentUserId.value ?: return
        val existingList = _messageReactions.value[messageId].orEmpty()
        val existing = existingList
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
                val tempReaction = MessageReaction(
                    id = "temp-${messageId}-${reactionType}",
                    messageId = messageId,
                    userId = userId,
                    reactionType = reactionType,
                    createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                )
                val current = _messageReactions.value.toMutableMap()
                val deduped = existingList.filterNot { it.userId == userId && it.reactionType == reactionType }
                current[messageId] = deduped + tempReaction
                _messageReactions.value = current
                chatRepository.addReaction(messageId, userId, reactionType)
            }
        }
    }

    fun addReaction(messageId: String, reactionType: String) {
        toggleReaction(messageId, reactionType)
    }

    fun removeReaction(messageId: String, reactionType: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            chatRepository.removeReaction(messageId, userId, reactionType)
        }
    }

    fun forwardMessage(messageId: String, targetChatId: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            chatRepository.forwardMessage(messageId, targetChatId, userId)
        }
    }

    fun searchMessages(chatId: String, query: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            try {
                val results = chatRepository.searchMessages(chatId, query)
                val messagesWithUsers = results.mapNotNull { message ->
                    val user = chatRepository.getUserById(message.user_id)
                    if (user != null) MessageWithUser(message, user, message.user_id == userId) else null
                }
                val chatDetails = chatRepository.fetchChatWithDetails(chatId, userId)
                if (chatDetails != null) {
                    _chatMessagesState.value = ChatMessagesState.Success(messagesWithUsers, chatDetails)
                }
            } catch (e: Exception) {
                println("Search error: ${e.message}")
            }
        }
    }
    
    private fun startVibeCheckTimer(connection: Connection, userId: String) {
        vibeCheckTimerJob?.cancel()
        if (connection.isMutuallyKept()) {
            _connectionKept.value = true
            _vibeCheckExpired.value = false
            _vibeCheckRemainingMs.value = 0L
            return
        }

        vibeCheckTimerJob = viewModelScope.launch {
            while (true) {
                val now = Clock.System.now().toEpochMilliseconds()
                val remainingMs = connection.getVibeCheckRemainingMs(now)
                _vibeCheckRemainingMs.value = remainingMs
                if (remainingMs == 0L) {
                    handleVibeCheckExpiry(connection, userId)
                    break
                }
                delay(1000L)
            }
        }
    }

    private fun updateKeepStates(connection: Connection, userId: String) {
        val userIndex = connection.getUserIndex(userId)
        val otherUserIndex = if (userIndex == 0) 1 else 0
        if (userIndex != null && connection.should_continue.size >= 2) {
            _currentUserHasKept.value = connection.should_continue[userIndex]
            _otherUserHasKept.value = connection.should_continue[otherUserIndex]
        } else {
            _currentUserHasKept.value = false
            _otherUserHasKept.value = false
        }
        _connectionKept.value = connection.isMutuallyKept()
    }
    
    fun keepConnection() {
        val connectionId = currentConnectionId ?: return
        val userId = _currentUserId.value ?: return
        val currentState = _chatMessagesState.value
        if (currentState !is ChatMessagesState.Success) return
        val connection = currentState.chatDetails.connection
        viewModelScope.launch {
            val success = if (vibeCheckEnabled) {
                supabaseRepository.updateUserKeepDecision(
                    connectionId = connectionId,
                    userId = userId,
                    keepConnection = true,
                    currentShouldContinue = connection.should_continue,
                    userIds = connection.user_ids
                )
            } else {
                supabaseRepository.updateConnectionExpiryState(connectionId, "kept")
            }

            if (success) {
                _currentUserHasKept.value = true
                updateConnectionState(connectionId) { it.copy(expiry_state = "kept") }
                if (!vibeCheckEnabled) {
                    _connectionKept.value = true
                    vibeCheckTimerJob?.cancel()
                    loadChats(isForced = true)
                    return@launch
                }

                val otherUserIndex = if (connection.getUserIndex(userId) == 0) 1 else 0
                if (connection.should_continue.getOrNull(otherUserIndex) == true) {
                    _connectionKept.value = true
                    vibeCheckTimerJob?.cancel()
                    supabaseRepository.updateConnectionExpiryState(connectionId, "kept")
                }
                refreshConnectionState(connectionId, userId)
            }
        }
    }
    
    private suspend fun refreshConnectionState(chatId: String, userId: String) {
        val connection = supabaseRepository.fetchConnectionById(chatId) ?: return
        updateKeepStates(connection, userId)
        if (connection.isMutuallyKept()) {
            _connectionKept.value = true
            vibeCheckTimerJob?.cancel()
        }
    }
    
    private suspend fun handleVibeCheckExpiry(connection: Connection, userId: String) {
        _vibeCheckExpired.value = true
        val latestConnection = supabaseRepository.fetchConnectionById(connection.id)
        if (latestConnection != null && latestConnection.isMutuallyKept()) {
            _connectionKept.value = true
        } else {
            _connectionKept.value = false
        }
    }
    
    /**
     * Handle dismissal of an expired connection.
     * Server-side Edge Function owns actual deletion via pg_cron schedule.
     * Client just refreshes local state so the connection disappears from the list.
     */
    fun handleExpiredConnectionDismiss() {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            // Refresh connections list from server — expired connections
            // will have been deleted or marked by the Edge Function
            loadChats(isForced = true)
        }
    }
    
    private fun resetVibeCheckState() {
        vibeCheckTimerJob?.cancel()
        vibeCheckTimerJob = null
        _vibeCheckRemainingMs.value = 0L
        _currentUserHasKept.value = false
        _otherUserHasKept.value = false
        _vibeCheckExpired.value = false
        _connectionKept.value = false
    }
    
    private fun loadIcebreakerPrompts(contextTag: String?) {
        _icebreakerPrompts.value = IcebreakerRepository.getPromptsForContext(contextTag, count = 3)
    }
    
    fun refreshIcebreakerPrompts() {
        val currentState = _chatMessagesState.value
        if (currentState is ChatMessagesState.Success) {
            loadIcebreakerPrompts(currentState.chatDetails.connection.context_tag)
        }
    }
    
    fun useIcebreakerPrompt(prompt: IcebreakerPrompt) {
        _messageInput.value = prompt.text
        _showIcebreakerPanel.value = false
    }
    
    fun dismissIcebreakerPanel() {
        _showIcebreakerPanel.value = false
    }

    // ==================== Nudge ====================

    /**
     * Send a nudge message to the current chat.
     * Works from any screen that has access to the chat details.
     */
    fun sendNudge() {
        val currentState = _chatMessagesState.value
        if (currentState !is ChatMessagesState.Success) return
        val connectionId = currentState.chatDetails.connection.id
        val userId = _currentUserId.value ?: return
        val currentUser = compose.project.click.click.data.AppDataManager.currentUser.value ?: return
        val otherUserName = currentState.chatDetails.otherUser.name ?: "them"
        viewModelScope.launch {
            val chatId = resolveOrCreateApiChatId(connectionId) ?: return@launch
            val msg = chatRepository.sendMessage(
                chatId = chatId,
                userId = userId,
                content = "👋 ${currentUser.name ?: "Someone"} nudged you!"
            )
            _nudgeResult.value = if (msg != null) "Nudge sent to $otherUserName! 👋" else "Failed to send nudge"
        }
    }

    /**
     * Send a nudge to an explicit chat — usable from Home or Connections list
     * without needing to open the full chat view.
     */
    fun sendNudgeToChat(chatId: String, otherUserName: String) {
        val userId = _currentUserId.value ?: return
        val currentUser = compose.project.click.click.data.AppDataManager.currentUser.value ?: return
        viewModelScope.launch {
            val msg = chatRepository.sendMessage(
                chatId = chatId,
                userId = userId,
                content = "👋 ${currentUser.name ?: "Someone"} nudged you!"
            )
            _nudgeResult.value = if (msg != null) "Nudge sent to $otherUserName! 👋" else "Failed to send nudge"
        }
    }

    fun clearNudgeResult() {
        _nudgeResult.value = null
    }

    // ==================== Message Edit / Delete ====================

    /**
     * Enter editing mode for a sent message.
     * Pre-fills the message input with the current content.
     */
    fun startEditMessage(messageId: String, currentContent: String) {
        _replyingTo.value = null
        _editingMessageId.value = messageId
        _messageInput.value = currentContent
    }

    /**
     * Cancel an in-progress edit and restore the input to empty.
     */
    fun cancelEditMessage() {
        _editingMessageId.value = null
        _messageInput.value = ""
    }

    /**
     * Submit the edited message content to Supabase.
     */
    private fun confirmEditMessage(messageId: String) {
        if (_isMessageSubmitInProgress.value) return
        val connectionId = currentConnectionId ?: return
        val newContent = _messageInput.value.trim()
        if (newContent.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        val apiChatId = currentApiChatId

        val previousState = _chatMessagesState.value
        if (previousState is ChatMessagesState.Success) {
            _chatMessagesState.value = previousState.copy(
                messages = previousState.messages.map { mwu ->
                    if (mwu.message.id == messageId) {
                        mwu.copy(
                            message = mwu.message.copy(
                                content = newContent,
                                timeEdited = now
                            )
                        )
                    } else {
                        mwu
                    }
                }
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
                println("Error editing message: ${e.message}")
                loadChatMessages(connectionId)
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }

    /**
     * Delete a message from the chat (with optimistic local removal).
     */
    fun deleteMessage(messageId: String) {
        val connectionId = currentConnectionId ?: return

        // Optimistic: remove from local state immediately
        val currentState = _chatMessagesState.value
        if (currentState is ChatMessagesState.Success) {
            _chatMessagesState.value = currentState.copy(
                messages = currentState.messages.filter { it.message.id != messageId }
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
                println("Error deleting message: ${e.message}")
                // Revert optimistic removal on failure — reload full state
                loadChatMessages(connectionId)
            }
        }
    }

    // ==================== Connection Archive / Delete (User-initiated) ====================

    /**
     * Archive the current connection (hide from main list, recoverable).
     * State is stored in-memory for this session; backed by Supabase when the
     * connection_archives table is provisioned (see database/add_connection_archives.sql).
     */
    fun archiveConnection(onComplete: (Boolean) -> Unit = {}) {
        val connectionId = currentConnectionId ?: return
        archiveConnectionById(connectionId, onComplete)
    }

    /**
     * Archive a specific connection by ID.
     */
    fun archiveConnectionById(connectionId: String, onComplete: (Boolean) -> Unit = {}) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            _archivedConnectionIds.value = _archivedConnectionIds.value + connectionId
            removeConnectionFromCurrentList(connectionId)
            supabaseRepository.archiveConnection(userId, connectionId) // non-fatal if table missing
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
    fun unarchiveConnection(connectionId: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            _archivedConnectionIds.value = _archivedConnectionIds.value - connectionId
            supabaseRepository.unarchiveConnection(userId, connectionId)
            loadChats(isForced = true)
            _nudgeResult.value = "Connection unarchived"
        }
    }

    /**
     * Hard-delete the current connection (removes from DB).
     */
    fun deleteConnectionPermanently(onComplete: (Boolean) -> Unit = {}) {
        val connectionId = currentConnectionId ?: return
        deleteConnectionPermanentlyById(connectionId, onComplete)
    }

    /**
     * Hard-delete a specific connection by ID.
     */
    fun deleteConnectionPermanentlyById(connectionId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _hiddenConnectionIds.value = _hiddenConnectionIds.value + connectionId
            removeConnectionFromCurrentList(connectionId)
            val success = supabaseRepository.deleteConnection(connectionId)
            if (success) {
                if (currentConnectionId == connectionId) {
                    leaveChatRoom()
                }
                loadChats(isForced = true)
                _nudgeResult.value = "Connection removed"
                onComplete(true)
            } else {
                _hiddenConnectionIds.value = _hiddenConnectionIds.value - connectionId
                loadChats(isForced = true)
                _nudgeResult.value = "Failed to remove connection"
                onComplete(false)
            }
        }
    }

    // ==================== Safety Actions ====================

    /**
     * Block the other user in the current chat.
     * Resolves the other user ID from chat state or AppDataManager connections.
     * This avoids race conditions when called from the connections list
     * where chat state may not have loaded yet.
     */
    fun blockUser(onBlocked: (Boolean) -> Unit) {
        val connectionId = currentConnectionId ?: return
        blockUserForConnection(connectionId, onBlocked)
    }

    /**
     * Block the other user for a specific connection.
     */
    fun blockUserForConnection(connectionId: String, onBlocked: (Boolean) -> Unit = {}) {
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
                _hiddenConnectionIds.value = _hiddenConnectionIds.value + connectionId
                removeConnectionFromCurrentList(connectionId)
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
    fun reportConnection(reason: String, onReported: (Boolean) -> Unit) {
        val connectionId = currentConnectionId ?: return
        reportConnectionForConnection(connectionId, reason, onReported)
    }

    /**
     * Report a specific connection for safety review.
     */
    fun reportConnectionForConnection(connectionId: String, reason: String, onReported: (Boolean) -> Unit = {}) {
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
    private fun resolveOtherUserId(userId: String, connectionId: String): String? {
        // 1. Try loaded chat state
        val chatState = _chatMessagesState.value
        if (chatState is ChatMessagesState.Success) {
            val fromChat = chatState.chatDetails.connection.user_ids.firstOrNull { it != userId }
            if (fromChat != null) return fromChat
        }
        // 2. Fall back to AppDataManager cached connections
        val connection = AppDataManager.connections.value.firstOrNull { it.id == connectionId }
        return connection?.user_ids?.firstOrNull { it != userId }
    }
    
    private fun resetIcebreakerState() {
        _icebreakerPrompts.value = emptyList()
        _showIcebreakerPanel.value = true
    }

    override fun onCleared() {
        super.onCleared()
        connectionsRealtimeJob?.cancel()
        connectionsRealtimeJob = null
        globalMessageListJob?.cancel()
        globalMessageListJob = null
        debouncedChatListRefreshJob?.cancel()
        debouncedChatListRefreshJob = null
        connectionsRealtimeChannel?.let { ch ->
            runBlocking(Dispatchers.Default) {
                runCatching { ch.unsubscribe() }
            }
        }
        connectionsRealtimeChannel = null
        realtimeJob?.cancel()
        reactionsJob?.cancel()
        typingPollingJob?.cancel()
        peerTypingTimeoutJob?.cancel()
        peerOnlineJob?.cancel()
        localTypingIdleJob?.cancel()
        vibeCheckTimerJob?.cancel()
        val apiIdToLeave = currentApiChatId
        if (apiIdToLeave != null) {
            runBlocking(Dispatchers.Default) {
                chatRepository.leaveChatEphemeralChannel(apiIdToLeave)
            }
        }
        activeMessageSubscription?.let { sub ->
            viewModelScope.launch {
                try { sub.detach() } catch (_: Exception) {}
            }
        }
        activeMessageSubscription = null
        activeReactionSubscription?.let { sub ->
            viewModelScope.launch {
                try { sub.detach() } catch (_: Exception) {}
            }
        }
        activeReactionSubscription = null
    }
}

private const val MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS = 3
private const val MESSAGE_SUBSCRIPTION_RETRY_DELAY_MS = 750L
private const val ACTIVE_CHAT_SYNC_INTERVAL_MS = 1500L
private const val CONNECTIONS_LIST_DEBOUNCE_MS = 450L
