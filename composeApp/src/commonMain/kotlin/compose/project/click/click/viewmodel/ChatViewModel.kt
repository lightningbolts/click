package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionEncounter // pragma: allowlist secret
import compose.project.click.click.data.models.IcebreakerPrompt // pragma: allowlist secret
import compose.project.click.click.data.models.IcebreakerRepository // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.replySnippetForMessage // pragma: allowlist secret
import compose.project.click.click.data.models.replySnippetForMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.audioCacheFileExtension // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isArchivedChannelForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isResolvedDisplayName // pragma: allowlist secret
import compose.project.click.click.chat.attachments.AttachmentCrypto // pragma: allowlist secret
import compose.project.click.click.chat.attachments.ChatAttachmentValidator // pragma: allowlist secret
import compose.project.click.click.crypto.MessageCrypto // pragma: allowlist secret
import compose.project.click.click.data.CHAT_ATTACHMENTS_BUCKET // pragma: allowlist secret
import compose.project.click.click.domain.VerifiedCliqueCreation // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.Clock
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatMessageSubscription // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRealtimeEvent // pragma: allowlist secret
import compose.project.click.click.data.repository.MessageChangeEvent // pragma: allowlist secret
import compose.project.click.click.data.repository.ReactionChangeEvent // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileSheetLocalMessage // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAttachmentDownloadOutcome // pragma: allowlist secret
import compose.project.click.click.ui.chat.deleteSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.ui.chat.saveDecryptedAttachmentToDownloads // pragma: allowlist secret
import compose.project.click.click.ui.chat.writeSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.util.LruMemoryCache // pragma: allowlist secret
import compose.project.click.click.util.chatMediaDispatcher // pragma: allowlist secret
import compose.project.click.click.util.teardownBlocking // pragma: allowlist secret
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
import kotlin.random.Random
import kotlinx.coroutines.withContext

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
@Serializable
private data class ConnectionJunctionRealtimeRow(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("connection_id") val connectionId: String? = null,
)

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

private sealed class ConnectionsRealtimeEvent {
    data class MainTable(val action: PostgresAction) : ConnectionsRealtimeEvent()
    data class ArchiveJunction(val action: PostgresAction) : ConnectionsRealtimeEvent()
    data class HiddenJunction(val action: PostgresAction) : ConnectionsRealtimeEvent()
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

private data class CombinedInboxState(
    val chats: List<ChatWithDetails>,
    val directLoaded: Boolean,
    val groupLoaded: Boolean,
)

class ChatViewModel(
    tokenStorage: TokenStorage = createTokenStorage(),
    private val chatRepository: ChatRepository = SupabaseChatRepository(tokenStorage = tokenStorage),
    private val supabaseRepository: SupabaseRepository = SupabaseRepository()
) : ViewModel(), SecureChatMediaHost {

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

    /** Relational archive/hide junction state (shared with Home / Map / ConnectionViewModel). */
    val archivedConnectionIds: StateFlow<Set<String>> = AppDataManager.archivedConnectionIds
    val hiddenConnectionIds: StateFlow<Set<String>> = AppDataManager.hiddenConnectionIds

    // ── Reactions state: messageId → list of reactions ─────────────────────────
    private val _messageReactions = MutableStateFlow<Map<String, List<compose.project.click.click.data.models.MessageReaction>>>(emptyMap())
    val messageReactions: StateFlow<Map<String, List<compose.project.click.click.data.models.MessageReaction>>> = _messageReactions.asStateFlow()

    private val _secureChatMediaLoadState = MutableStateFlow<Map<String, SecureChatMediaLoadState>>(emptyMap())
    override val secureChatMediaLoadState: StateFlow<Map<String, SecureChatMediaLoadState>> =
        _secureChatMediaLoadState.asStateFlow()
    private val secureImageBytesCache =
        LruMemoryCache<String, ByteArray>(SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES)
    private val secureAudioPathCache =
        LruMemoryCache<String, String>(SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES)

    private var currentConnectionId: String? = null
    private var currentApiChatId: String? = null
    private var activeMessageSubscription: ChatMessageSubscription? = null
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
    private var loadChatMessagesJob: Job? = null
    private var lastTypingSent: Long = 0L
    private val prefetchedChatPayloads = mutableMapOf<String, PrefetchedChatPayload>()
    private val prefetchedChatLimit = 3

    /**
     * Connection ids for which the inbox row should show zero unread immediately after
     * [chatRepository.markMessagesAsRead] (or while the active thread is open). Prevents stale
     * counts from sticking until the next cold start when server-driven list refreshes lag.
     */
    private val _readClearedConnectionIds = MutableStateFlow<Set<String>>(emptySet())

    /** List rows may omit prefetch; still reuse any [prefetchedChatPayloads] so refresh never blanks the thread. */
    private fun bootstrapMessagesFromPrefetch(connectionId: String): List<MessageWithUser> =
        prefetchedChatPayloads[connectionId]?.messages.orEmpty()

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

                    val mergedChats = currentListState.chats.map { chat ->
                        val cachedChat = cachedChatsByConnectionId[chat.connection.id]
                        val freshUser = cachedChat?.otherUser ?: connectedUsers[chat.otherUser.id]
                        mergeChatRowWithCache(chat, cachedChat, freshUser)
                    }

                    val currentConnectionIds = mergedChats.map { it.connection.id }.toSet()
                    val missingChats = cachedChatsByConnectionId.values
                        .filter { it.connection.id !in currentConnectionIds }
                        .sortedByDescending { chatListActivityTimestamp(it) }

                    val reconciledBase = applyChatListVisibility(
                        (missingChats + mergedChats)
                            .distinctBy { it.connection.id }
                            .sortedByDescending { chatListActivityTimestamp(it) }
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

        viewModelScope.launch {
            AppDataManager.foregroundRealtimeRecovery.collect {
                val uid = _currentUserId.value ?: return@collect
                startGlobalConnectionsRealtime(uid)
                startGlobalMessageListRealtime()
                restoreActiveChatSubscriptionsIfNeeded()
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
                    val connectionsFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "connections"
                    }.map { ConnectionsRealtimeEvent.MainTable(it) }
                    val archivesFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "connection_archives"
                    }.map { ConnectionsRealtimeEvent.ArchiveJunction(it) }
                    val hiddenFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "connection_hidden"
                    }.map { ConnectionsRealtimeEvent.HiddenJunction(it) }
                    channel.subscribe(blockUntilSubscribed = true)
                    merge(connectionsFlow, archivesFlow, hiddenFlow).collect { event ->
                        when (event) {
                            is ConnectionsRealtimeEvent.MainTable -> {
                                if (connectionRowRelevantToUser(event.action, userId)) {
                                    scheduleDebouncedChatListRefresh()
                                    reapplyChatListVisibilityFromAppData()
                                }
                            }
                            is ConnectionsRealtimeEvent.ArchiveJunction -> {
                                handleConnectionArchivesRealtime(event.action, userId)
                            }
                            is ConnectionsRealtimeEvent.HiddenJunction -> {
                                handleConnectionHiddenRealtime(event.action, userId)
                            }
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
                println("ChatViewModel: global connections realtime unavailable: ${e.redactedRestMessage()}")
                connectionsRealtimeChannel = null
            }
        }
    }

    private fun handleConnectionArchivesRealtime(action: PostgresAction, userId: String) {
        when (action) {
            is PostgresAction.Insert -> {
                val row = action.decodeRecordOrNull<ConnectionJunctionRealtimeRow>() ?: return
                if (row.userId != userId || row.connectionId.isNullOrBlank()) return
                AppDataManager.markConnectionArchivedLocally(row.connectionId)
                scheduleDebouncedChatListRefresh()
                reapplyChatListVisibilityFromAppData()
            }
            is PostgresAction.Delete -> {
                val cid = action.oldRecord.stringField("connection_id") ?: return
                AppDataManager.markConnectionUnarchivedLocally(cid)
                scheduleDebouncedChatListRefresh()
                reapplyChatListVisibilityFromAppData()
            }
            else -> Unit
        }
    }

    private fun handleConnectionHiddenRealtime(action: PostgresAction, userId: String) {
        when (action) {
            is PostgresAction.Insert -> {
                val row = action.decodeRecordOrNull<ConnectionJunctionRealtimeRow>() ?: return
                if (row.userId != userId || row.connectionId.isNullOrBlank()) return
                AppDataManager.hideConnectionLocally(row.connectionId)
                scheduleDebouncedChatListRefresh()
                reapplyChatListVisibilityFromAppData()
            }
            is PostgresAction.Delete -> {
                val cid = action.oldRecord.stringField("connection_id") ?: return
                AppDataManager.unhideConnectionLocally(cid)
                scheduleDebouncedChatListRefresh()
            }
            else -> Unit
        }
    }

    private fun reapplyChatListVisibilityFromAppData() {
        val cur = _chatListState.value
        if (cur !is ChatListState.Success) return
        val filtered = applyChatListVisibility(cur.chats)
        pruneStaleReadClearedHints(filtered)
        _chatListState.value = ChatListState.Success(applyUnreadClearHintsToInboxRows(filtered))
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
                println("ChatViewModel: global message list realtime unavailable: ${e.redactedRestMessage()}")
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
            if (isForced) {
                chatRepository.clearChatListLocalCaches()
            }
            val cachedConnections = AppDataManager.connections.value
            val cachedUsers = AppDataManager.connectedUsers.value

            // CRITICAL: Never revert a Success state to Loading. When navigating
            // back to the connections list the previously loaded data must remain
            // visible while the background refresh runs. Only show Loading when
            // no real data has ever been emitted.
            val alreadyHasRealData = _chatListState.value is ChatListState.Success
            val cachedSeedChats = applyChatListVisibility(
                buildCachedChats(cachedConnections, cachedUsers, userId),
            )
            if (!alreadyHasRealData) {
                if (cachedSeedChats.isNotEmpty()) {
                    _chatListState.value = ChatListState.Success(
                        applyUnreadClearHintsToInboxRows(cachedSeedChats),
                    )
                } else {
                    _chatListState.value = ChatListState.Loading
                }
            }

            // Build direct and group streams with immediate empty emissions so the
            // list can paint direct chats while group chats continue loading.
            try {
                val directChatsFlow: Flow<Pair<List<ChatWithDetails>, Boolean>> = flow {
                    val directChats = chatRepository.fetchDirectUserChatsWithDetails(userId)
                    // Emit direct 1:1 rows first; archived can merge in a second pass.
                    emit(directChats.distinctBy { it.connection.id } to true)

                    val archivedChats = chatRepository.fetchArchivedUserChatsWithDetails(userId)
                    emit((directChats + archivedChats).distinctBy { it.connection.id } to true)
                }.onStart {
                    emit(emptyList<ChatWithDetails>() to false)
                }

                val groupChatsFlow: Flow<Pair<List<ChatWithDetails>, Boolean>> = flow {
                    emit(chatRepository.fetchGroupUserChatsWithDetails(userId) to true)
                }.onStart {
                    emit(emptyList<ChatWithDetails>() to false)
                }

                combine(directChatsFlow, groupChatsFlow) { directState, groupState ->
                    val (directChats, directLoaded) = directState
                    val (groupChats, groupLoaded) = groupState
                    CombinedInboxState(
                        chats = (directChats + groupChats)
                            .distinctBy { it.connection.id }
                            .sortedByDescending { chatListActivityTimestamp(it) },
                        directLoaded = directLoaded,
                        groupLoaded = groupLoaded,
                    )
                }.collect { combinedInbox ->
                    val chats = combinedInbox.chats

                    // Direct 1:1 chat data drives the primary Clicks list. Do not emit
                    // an empty success state before direct rows have loaded.
                    if (!combinedInbox.directLoaded) {
                        return@collect
                    }
                    if (alreadyHasRealData && chats.isEmpty() && (!combinedInbox.directLoaded || !combinedInbox.groupLoaded)) {
                        return@collect
                    }

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
                            val freshUser = if (apiChat.groupClique == null) {
                                cachedRow?.otherUser ?: cachedUsers[apiChat.otherUser.id]
                            } else {
                                null
                            }
                            mergeChatRowWithCache(apiChat, cachedRow, freshUser)
                        }
                        val visibilityFiltered = applyChatListVisibility(mergedWithLocalPreview)
                        pruneStaleReadClearedHints(visibilityFiltered)
                        _chatListState.value = ChatListState.Success(
                            applyUnreadClearHintsToInboxRows(visibilityFiltered),
                        )

                        if (combinedInbox.directLoaded && combinedInbox.groupLoaded) {
                            prefetchChatPayloads(userId, enriched)
                        }
                    } else {
                        _chatListState.value = ChatListState.Success(emptyList())
                    }
                }
            } catch (e: Exception) {
                // Keep an existing list visible; only error on a cold-start failure.
                if (_chatListState.value !is ChatListState.Success) {
                    _chatListState.value = ChatListState.Error(
                        e.redactedRestMessage().ifBlank { "Failed to load chats" },
                    )
                }
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

    /**
     * Full Clicks list: active-channel rows (pending/active/kept), server-archived rows, minus
     * soft-removed rows. [ChatListState.Success.chats] is this superset; the UI splits Active vs Archived tabs.
     * Connection time [Connection.expiry] is not used for visibility (archival uses server / [connection_archives]).
     */
    private fun applyChatListVisibility(chats: List<ChatWithDetails>): List<ChatWithDetails> {
        val hiddenIds = AppDataManager.hiddenConnectionIds.value
        val archivedIds = AppDataManager.archivedConnectionIds.value
        return chats.filter { chat ->
            val c = chat.connection
            when {
                c.id in hiddenIds -> false
                c.normalizedConnectionStatus() == "removed" -> false
                c.isArchivedChannelForUser(archivedIds, hiddenIds) -> true
                c.isActiveForUser(archivedIds, hiddenIds) -> true
                else -> false
            }
        }
    }

    private fun pruneStaleReadClearedHints(rows: List<ChatWithDetails>) {
        if (_readClearedConnectionIds.value.isEmpty()) return
        _readClearedConnectionIds.update { ids ->
            ids.filterNot { id ->
                rows.any { it.connection.id == id && it.unreadCount == 0 }
            }.toSet()
        }
    }

    private fun applyUnreadClearHintsToInboxRows(rows: List<ChatWithDetails>): List<ChatWithDetails> {
        val cleared = _readClearedConnectionIds.value
        val openConnectionId =
            (_chatMessagesState.value as? ChatMessagesState.Success)?.chatDetails?.connection?.id
        if (cleared.isEmpty() && openConnectionId.isNullOrBlank()) return rows
        return rows.map { c ->
            val shouldZero =
                c.connection.id in cleared || (!openConnectionId.isNullOrBlank() && c.connection.id == openConnectionId)
            if (shouldZero && c.unreadCount != 0) {
                c.copy(unreadCount = 0)
            } else {
                c
            }
        }
    }

    private fun markInboxReadOptimistically(connectionId: String) {
        if (connectionId.isBlank()) return
        _readClearedConnectionIds.update { it + connectionId }
        val cur = _chatListState.value as? ChatListState.Success ?: return
        _chatListState.value = ChatListState.Success(
            cur.chats.map { chat ->
                if (chat.connection.id == connectionId) chat.copy(unreadCount = 0) else chat
            },
        )
    }

    private fun markMessagesReadOptimistically(connectionId: String, chatId: String, userId: String) {
        markInboxReadOptimistically(connectionId)
        viewModelScope.launch {
            chatRepository.markMessagesAsRead(chatId, userId)
        }
    }

    private fun chatListActivityTimestamp(chat: ChatWithDetails): Long =
        chat.connection.last_message_at
            ?: chat.lastMessage?.timeCreated
            ?: chat.connection.created

    /**
     * Prefer the connection row that still carries [Connection.connectionEncounters] (or any
     * non-blank [location_name]) so list refresh / timestamp merges never drop timeline data.
     */
    private fun richerConnectionEncounters(a: Connection, b: Connection): List<ConnectionEncounter> {
        val la = a.connectionEncounters
        val lb = b.connectionEncounters
        fun hasPlace(rows: List<ConnectionEncounter>) =
            rows.any { !it.locationName.isNullOrBlank() }
        return when {
            hasPlace(la) && !hasPlace(lb) -> la
            hasPlace(lb) && !hasPlace(la) -> lb
            lb.size > la.size -> lb
            la.size > lb.size -> la
            else -> la
        }
    }

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
        val mergedEncounters = richerConnectionEncounters(
            listChat.connection,
            cachedChat.connection,
        )
        val mergedConnection = preferredConnection.copy(
            last_message_at = mergedAt ?: preferredConnection.last_message_at,
            chat = mergedChat,
            connectionEncounters = mergedEncounters,
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

    private fun reapplyChatListVisibility() {
        val state = _chatListState.value
        if (state is ChatListState.Success) {
            _chatListState.value = ChatListState.Success(applyChatListVisibility(state.chats))
        }
    }

    private fun cachedChatRowForThreadId(threadId: String): ChatWithDetails? =
        (_chatListState.value as? ChatListState.Success)?.chats?.firstOrNull {
            it.connection.id == threadId || it.chat.id == threadId
        }

    // Load messages for a specific chat
    fun loadChatMessages(chatId: String) {
        val cachedChat = (_chatListState.value as? ChatListState.Success)
            ?.chats?.firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
        val connectionId = cachedChat?.connection?.id ?: chatId

        val userId = _currentUserId.value
        if (userId == null) {
            val successMatchesTarget = (_chatMessagesState.value as? ChatMessagesState.Success)?.let { s ->
                s.chatDetails.connection.id == connectionId ||
                    (!s.chatDetails.chat.id.isNullOrBlank() && s.chatDetails.chat.id == chatId)
            } == true
            if (!successMatchesTarget) {
                _chatMessagesState.value = ChatMessagesState.Loading
            }
            return
        }

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
                typingPollingJob?.isActive == true &&
                peerOnlineJob?.isActive == true

        if (currentConnectionId == connectionId && currentState != null && hasLiveSubscriptions) return

        if (_chatMessagesState.value is ChatMessagesState.Error) {
            _chatMessagesState.value = ChatMessagesState.Loading
        }

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
                messages = bootstrapMessagesFromPrefetch(connectionId),
                chatDetails = cachedChat,
                isLoadingMessages = true
            )
        } else {
            _chatMessagesState.value = ChatMessagesState.Loading
        }

        loadChatMessagesJob?.cancel()
        loadChatMessagesJob = viewModelScope.launch {
            try {
                val previousApiChatId = currentApiChatId
                // Resolve chat details (use cached if available). Cold start + deep link often races
                // the inbox fetch; retry briefly while staying on Loading / Success(isLoadingMessages).
                val chatResolveBackoffMs = longArrayOf(0L, 120L, 280L, 520L, 900L, 1400L)
                var chatDetails: ChatWithDetails? = cachedChat ?: chatRepository.fetchChatWithDetails(chatId, userId)
                var resolveAttempt = 0
                while (chatDetails == null && resolveAttempt < chatResolveBackoffMs.size - 1) {
                    delay(chatResolveBackoffMs[resolveAttempt + 1])
                    ensureActive()
                    chatDetails =
                        cachedChatRowForThreadId(chatId) ?: chatRepository.fetchChatWithDetails(chatId, userId)
                    resolveAttempt++
                }
                if (chatDetails == null) {
                    _chatMessagesState.value = ChatMessagesState.Error("Chat not found")
                    return@launch
                }

                val resolvedConnectionId = chatDetails.connection.id

                var apiChatId = chatDetails.chat.id ?: resolveOrCreateApiChatId(resolvedConnectionId)
                var ensureAttempt = 0
                while (apiChatId.isNullOrBlank() && ensureAttempt < 4) {
                    delay(120L * (ensureAttempt + 1))
                    ensureActive()
                    apiChatId = chatDetails.chat.id ?: resolveOrCreateApiChatId(resolvedConnectionId)
                    ensureAttempt++
                }
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

                if (hydratedChatDetails.groupClique == null) {
                    chatRepository.cacheEncryptionKeys(
                        apiChatId,
                        hydratedChatDetails.connection.id,
                        hydratedChatDetails.connection.user_ids
                    )
                }

                if (_chatMessagesState.value is ChatMessagesState.Loading) {
                    _icebreakerPrompts.value =
                        IcebreakerRepository.getPromptsForContext(
                            hydratedChatDetails.connection.context_tag,
                            count = 3,
                            stableSelectionKey = hydratedChatDetails.connection.id,
                        )
                    _showIcebreakerPanel.value = true
                    _chatMessagesState.value = ChatMessagesState.Success(
                        messages = bootstrapMessagesFromPrefetch(resolvedConnectionId),
                        chatDetails = hydratedChatDetails,
                        isLoadingMessages = true,
                    )
                }

                ensureActive()
                var payload: PrefetchedChatPayload? = null
                var payloadAttempt = 0
                while (payload == null && payloadAttempt < 4) {
                    try {
                        payload = buildChatPayload(hydratedChatDetails, apiChatId, userId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        payloadAttempt++
                        if (payloadAttempt >= 4) throw e
                        delay(180L * payloadAttempt)
                        ensureActive()
                    }
                }
                prefetchedChatPayloads[resolvedConnectionId] = payload!!

                _messageReactions.value = payload.reactionsByMessageId
                _showIcebreakerPanel.value = payload.showIcebreakerPanel
                if (payload.showIcebreakerPanel) {
                    if (_icebreakerPrompts.value != payload.icebreakerPrompts) {
                        _icebreakerPrompts.value = payload.icebreakerPrompts
                    }
                } else {
                    _icebreakerPrompts.value = emptyList()
                }
                ensureActive()
                _chatMessagesState.value = ChatMessagesState.Success(
                    messages = payload.messages,
                    chatDetails = hydratedChatDetails,
                    isLoadingMessages = false
                )

                // Mark messages as read (optimistic inbox badge first so the Clicks list updates immediately).
                markMessagesReadOptimistically(
                    connectionId = resolvedConnectionId,
                    chatId = apiChatId,
                    userId = userId,
                )

                chatRepository.joinChatEphemeralChannel(
                    apiChatId,
                    userId,
                    hydratedChatDetails.otherUser.id
                )

                // Subscribe to new messages (merged stream includes message_reactions realtime)
                subscribeToNewMessages(apiChatId, userId)

                // Monitor typing status (Realtime Broadcast) and peer presence
                startTypingMonitoring(apiChatId)
                startPeerOnlineMonitoring(apiChatId, hydratedChatDetails.otherUser.id)
                startActiveChatSync(apiChatId, userId)
                
                // Vibe Check is disabled
                if (vibeCheckEnabled) {
                    startVibeCheckTimer(chatDetails.connection, userId)
                    updateKeepStates(chatDetails.connection, userId)
                }
                
                // Mark chat as begun if this is the first time (1:1 only)
                if (hydratedChatDetails.groupClique == null && !chatDetails.connection.has_begun) {
                    supabaseRepository.updateConnectionHasBegun(resolvedConnectionId, true)
                }
                
                // Icebreaker state is already prepared in payload before UI render.
            } catch (e: CancellationException) {
                throw e
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
                    if (chatDetails.groupClique == null) {
                        chatRepository.cacheEncryptionKeys(
                            apiChatId, connectionId, chatDetails.connection.user_ids
                        )
                    }
                    runCatching {
                        buildChatPayload(chatDetails, apiChatId, userId)
                    }.onSuccess { payload ->
                        // Re-check after suspension: another coroutine in
                        // viewModelScope (e.g. startActiveChatSync) may have
                        // populated the same connectionId while buildChatPayload
                        // was awaiting IO. Prefer the fresher value over this
                        // prefetch result to avoid clobbering read-state.
                        if (!prefetchedChatPayloads.containsKey(connectionId)) {
                            prefetchedChatPayloads[connectionId] = payload
                        }
                    }
                }
        }
    }

    private suspend fun buildChatPayload(
        chatDetails: ChatWithDetails,
        apiChatId: String,
        userId: String
    ): PrefetchedChatPayload = coroutineScope {
        val messagesDeferred = async { chatRepository.fetchMessagesForChat(apiChatId, userId) }
        val participantsDeferred = async { chatRepository.fetchChatParticipants(apiChatId) }
        val reactionsDeferred = async { chatRepository.fetchReactionsForChat(apiChatId) }

        val participants = participantsDeferred.await().associateBy { it.id }
        val rawMessages = messagesDeferred.await()
            ?: error("Failed to load messages for chat")
        val messagesWithUsers = rawMessages.map { message ->
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

    private fun clearSecureChatMediaCache(purgePersistentCache: Boolean = false) {
        _secureChatMediaLoadState.value = emptyMap()
        if (purgePersistentCache) {
            secureAudioPathCache.valuesSnapshot().forEach { path ->
                deleteSecureChatAudioTempFile(path)
            }
            secureAudioPathCache.clear()
            secureImageBytesCache.clear()
        }
    }

    override fun ensureSecureChatImageLoaded(scopeId: String, viewerUserId: String, message: Message) {
        if (!message.isEncryptedMedia()) return
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
        viewModelScope.launch(chatMediaDispatcher) {
            _secureChatMediaLoadState.update { it + (message.id to SecureChatMediaLoadState(loading = true)) }
            val bytes = runCatching {
                chatRepository.downloadAndDecryptChatMedia(scopeId, viewerUserId, url)
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
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, imageBytes = bytes))
                }
            }
        }
    }

    override fun ensureSecureChatAudioLoaded(scopeId: String, viewerUserId: String, message: Message) {
        if (!message.isEncryptedMedia()) return
        if (message.messageType.lowercase() != ChatMessageType.AUDIO) return
        val url = message.mediaUrlOrNull() ?: return
        if (url.isBlank()) return
        val cachedPath = secureAudioPathCache.get(message.id)
        if (!cachedPath.isNullOrBlank()) {
            _secureChatMediaLoadState.update {
                it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = cachedPath))
            }
            return
        }
        val cur = _secureChatMediaLoadState.value[message.id]
        if (cur?.audioLocalPath != null || cur?.loading == true) return
        viewModelScope.launch(chatMediaDispatcher) {
            _secureChatMediaLoadState.update { it + (message.id to SecureChatMediaLoadState(loading = true)) }
            val bytes = runCatching {
                chatRepository.downloadAndDecryptChatMedia(scopeId, viewerUserId, url)
            }.onFailure { e ->
                println("ChatViewModel: secure audio decrypt failed for message=${message.id}: ${e.redactedRestMessage()}")
            }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                println("ChatViewModel: secure audio bytes missing for message=${message.id}")
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, error = "Could not load audio"))
                }
                return@launch
            }
            val path = writeSecureChatAudioTempFile(message.id, bytes, message.audioCacheFileExtension())
            if (path.isNullOrBlank()) {
                println("ChatViewModel: secure audio cache write failed for message=${message.id}")
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, error = "Could not cache audio"))
                }
            } else {
                val evictedPath = secureAudioPathCache.put(message.id, path)
                if (!evictedPath.isNullOrBlank() && evictedPath != path) {
                    deleteSecureChatAudioTempFile(evictedPath)
                }
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = path))
                }
            }
        }
    }

    suspend fun fetchDecryptedChatMediaBytes(message: Message): ByteArray? {
        secureImageBytesCache.get(message.id)?.takeIf { it.isNotEmpty() }?.let { return it }
        val s = _chatMessagesState.value as? ChatMessagesState.Success ?: return null
        val cid = s.chatDetails.chat.id ?: return null
        val uid = _currentUserId.value ?: return null
        val url = message.mediaUrlOrNull() ?: return null
        if (!message.isEncryptedMedia()) return null
        val bytes = withContext(chatMediaDispatcher) {
            chatRepository.downloadAndDecryptChatMedia(cid, uid, url)
        }
        if (bytes != null && bytes.isNotEmpty()) {
            secureImageBytesCache.put(message.id, bytes)
        }
        return bytes
    }

    // Subscribe to real-time message updates.
    //
    // Contract (post-R0.2 refactor):
    // - previous `realtimeJob` is cancelled and **awaited** before a new
    //   subscription is opened. Without this, detach() and a new subscribe()
    //   can run concurrently on the same topic, producing duplicate realtime
    //   events and racing echo dedupe (see audit §1 #3).
    // - `CancellationException` is never swallowed; the retry loop exits
    //   cleanly on scope cancellation (R0.3).
    // - the retry loop is bounded by `MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS`
    //   (NASA P10: every loop has a fixed upper bound).
    private fun subscribeToNewMessages(chatId: String, userId: String) {
        val previousJob = realtimeJob
        val previousSubscription = activeMessageSubscription
        activeMessageSubscription = null
        currentApiChatId = chatId

        realtimeJob = viewModelScope.launch {
            // 1) Await previous job cancellation + detach previous subscription
            //    *before* opening a new channel. Serializing here is what
            //    prevents the brief overlap where two channels are subscribed
            //    to the same topic simultaneously.
            if (previousJob != null) {
                previousJob.cancel()
                try {
                    previousJob.join()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // join() of a cancelled job surfaces the cancellation
                    // cause on some platforms; ignore non-cancellation errors.
                }
            }
            if (previousSubscription != null) {
                try {
                    previousSubscription.detach()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // best-effort teardown
                }
            }

            // 2) Open the new subscription with bounded retry.
            var attempt = 0
            while (attempt < MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS && currentApiChatId == chatId) {
                try {
                    val (subscription, changeFlow) = chatRepository.subscribeToMessages(chatId, userId)
                    activeMessageSubscription = subscription

                    changeFlow
                        .onEach { envelope ->
                            when (envelope) {
                                is ChatRealtimeEvent.Message -> when (val event = envelope.event) {
                                    is MessageChangeEvent.Insert -> {
                                        val user = resolveMessageUser(event.message.user_id, chatId)
                                            ?: User(id = event.message.user_id, name = null, createdAt = 0L)
                                        applyInsertedMessage(event.message, user, userId)
                                        if (event.message.user_id != userId) {
                                            val active = _chatMessagesState.value as? ChatMessagesState.Success
                                            val activeApiChatId = active?.chatDetails?.chat?.id
                                            if (active != null && activeApiChatId == chatId) {
                                                markMessagesReadOptimistically(
                                                    connectionId = active.chatDetails.connection.id,
                                                    chatId = chatId,
                                                    userId = userId,
                                                )
                                            } else {
                                                viewModelScope.launch {
                                                    runCatching { chatRepository.markMessagesAsRead(chatId, userId) }
                                                }
                                            }
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
                                is ChatRealtimeEvent.Reaction -> {
                                    applyReactionChangeEvent(envelope.event)
                                }
                            }
                        }
                        .launchIn(this)

                    subscription.attach()
                    return@launch
                } catch (e: CancellationException) {
                    // Scope was cancelled (chat closed / VM cleared). Do NOT retry.
                    throw e
                } catch (e: Exception) {
                    attempt += 1
                    activeMessageSubscription?.let { sub ->
                        try {
                            sub.detach()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            // best effort
                        }
                    }
                    activeMessageSubscription = null
                    println("Error subscribing to messages (attempt $attempt): ${e.redactedRestMessage()}")
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
                // Re-fetch reactions on an interval: Supabase Realtime delivery for message_reactions
                // is still flaky on some mobile builds; leaving/re-entering the chat only refetched via REST.
                syncActiveChatReactions(chatId)
                syncActiveChatMessages(chatId, userId)
                delay(ACTIVE_CHAT_SYNC_INTERVAL_MS)
            }
        }
    }

    /**
     * Merges server-fetched reactions with in-flight optimistic rows (`temp-…` ids) so polling does not
     * wipe the UI while a toggle is in flight.
     */
    private fun mergeReactionMapsPreserveOptimistic(
        local: Map<String, List<MessageReaction>>,
        server: Map<String, List<MessageReaction>>,
    ): Map<String, List<MessageReaction>> {
        val out = server.toMutableMap()
        for ((msgId, localList) in local) {
            val optimistic = localList.filter { it.id.startsWith("temp-") }
            if (optimistic.isEmpty()) continue
            val base = out[msgId].orEmpty()
            val additions = optimistic.filter { opt ->
                base.none { it.userId == opt.userId && it.reactionType == opt.reactionType }
            }
            if (additions.isNotEmpty()) {
                out[msgId] = base + additions
            }
        }
        for ((msgId, localList) in local) {
            if (out.containsKey(msgId)) continue
            val onlyTemp = localList.filter { it.id.startsWith("temp-") }
            if (onlyTemp.isNotEmpty()) {
                out[msgId] = onlyTemp
            }
        }
        return out
    }

    private suspend fun syncActiveChatReactions(chatId: String) {
        if ((_chatMessagesState.value as? ChatMessagesState.Success)?.chatDetails?.chat?.id != chatId) return
        val server = runCatching { chatRepository.fetchReactionsForChat(chatId).groupBy { it.messageId } }
            .getOrElse { return }
        val merged = mergeReactionMapsPreserveOptimistic(_messageReactions.value, server)
        if (merged != _messageReactions.value) {
            _messageReactions.value = merged
        }
    }

    private suspend fun syncActiveChatMessages(chatId: String, userId: String) {
        val currentState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
        if (currentState.chatDetails.chat.id != chatId) return

        val latestMessages = chatRepository.fetchMessagesForChat(chatId, userId) ?: return
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
            markMessagesReadOptimistically(
                connectionId = currentState.chatDetails.connection.id,
                chatId = chatId,
                userId = userId,
            )
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
        val filtered = applyChatListVisibility(sorted)
        pruneStaleReadClearedHints(filtered)
        _chatListState.value = ChatListState.Success(applyUnreadClearHintsToInboxRows(filtered))
    }

    private suspend fun resolveOrCreateApiChatId(connectionId: String): String? {
        val currentState = _chatMessagesState.value as? ChatMessagesState.Success
        if (currentState?.chatDetails?.groupClique != null) {
            val id = currentState.chatDetails.chat.id?.takeIf { it.isNotBlank() } ?: return null
            currentApiChatId = id
            return id
        }
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
                        put("reply_to_content", replySnippetForMessage(replyTarget.message))
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
                println("Error sending message: ${e.redactedRestMessage()}")
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }

    fun sendChatImage(bytes: ByteArray, mimeType: String) {
        if (bytes.isEmpty()) return
        if (_isMessageSubmitInProgress.value) return
        val connectionId = currentConnectionId ?: return
        val userId = _currentUserId.value ?: return
        val caption = _messageInput.value.trim()
        _messageSendError.value = null
        _isMessageSubmitInProgress.value = true
        viewModelScope.launch {
            try {
                val apiChatId = resolveOrCreateApiChatId(connectionId) ?: run {
                    _messageSendError.value = "Failed to send — unable to start chat"
                    return@launch
                }
                val ext = extensionForChatMedia(mimeType, isImage = true)
                val unique = "${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000_000_000)}"
                val path = "$userId/$apiChatId/$unique.$ext"
                val url = chatRepository.uploadChatMedia(bytes, path, mimeType) ?: run {
                    _messageSendError.value = "Failed to upload photo"
                    return@launch
                }
                val replyTarget = _replyingTo.value
                val meta = if (replyTarget != null) {
                    buildJsonObject {
                        put("media_url", url)
                        put("original_mime_type", mimeType)
                        put("reply_to_id", replyTarget.message.id)
                        put("reply_to_content", replySnippetForMessage(replyTarget.message))
                    }
                } else {
                    buildJsonObject {
                        put("media_url", url)
                        put("original_mime_type", mimeType)
                    }
                }
                val message = chatRepository.sendMessage(
                    chatId = apiChatId,
                    userId = userId,
                    content = if (caption.isEmpty()) " " else caption,
                    messageType = ChatMessageType.IMAGE,
                    metadata = meta,
                )
                if (message != null) {
                    _messageInput.value = ""
                    updateMessageInput("")
                    _replyingTo.value = null
                    val currentUser = resolveMessageUser(userId, apiChatId)
                        ?: AppDataManager.currentUser.value?.takeIf { it.id == userId }
                        ?: User(id = userId, name = "You", createdAt = 0L)
                    applyInsertedMessage(message, currentUser, userId)
                    activateConnectionIfPending(connectionId)
                } else {
                    _messageSendError.value = "Failed to send photo"
                }
            } catch (e: Exception) {
                _messageSendError.value = "Failed to send photo — ${e.message ?: "error"}"
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }

    fun sendChatAudio(bytes: ByteArray, mimeType: String, durationSeconds: Int?) {
        if (bytes.isEmpty()) return
        if (_isMessageSubmitInProgress.value) return
        val connectionId = currentConnectionId ?: return
        val userId = _currentUserId.value ?: return
        val caption = _messageInput.value.trim()
        _messageSendError.value = null
        _isMessageSubmitInProgress.value = true
        viewModelScope.launch {
            try {
                val apiChatId = resolveOrCreateApiChatId(connectionId) ?: run {
                    _messageSendError.value = "Failed to send — unable to start chat"
                    return@launch
                }
                val ext = extensionForChatMedia(mimeType, isImage = false)
                val unique = "${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000_000_000)}"
                val path = "$userId/$apiChatId/$unique.$ext"
                val url = chatRepository.uploadChatMedia(bytes, path, mimeType) ?: run {
                    _messageSendError.value = "Failed to upload audio"
                    return@launch
                }
                val replyTarget = _replyingTo.value
                val meta = if (replyTarget != null) {
                    buildJsonObject {
                        put("media_url", url)
                        put("original_mime_type", mimeType)
                        if (durationSeconds != null) put("duration_seconds", durationSeconds)
                        put("reply_to_id", replyTarget.message.id)
                        put("reply_to_content", replySnippetForMessage(replyTarget.message))
                    }
                } else {
                    buildJsonObject {
                        put("media_url", url)
                        put("original_mime_type", mimeType)
                        if (durationSeconds != null) put("duration_seconds", durationSeconds)
                    }
                }
                val message = chatRepository.sendMessage(
                    chatId = apiChatId,
                    userId = userId,
                    content = if (caption.isEmpty()) " " else caption,
                    messageType = ChatMessageType.AUDIO,
                    metadata = meta,
                )
                if (message != null) {
                    _messageInput.value = ""
                    updateMessageInput("")
                    _replyingTo.value = null
                    val currentUser = resolveMessageUser(userId, apiChatId)
                        ?: AppDataManager.currentUser.value?.takeIf { it.id == userId }
                        ?: User(id = userId, name = "You", createdAt = 0L)
                    applyInsertedMessage(message, currentUser, userId)
                    activateConnectionIfPending(connectionId)
                } else {
                    _messageSendError.value = "Failed to send voice message"
                }
            } catch (e: Exception) {
                _messageSendError.value = "Failed to send audio — ${e.message ?: "error"}"
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }

    /**
     * Send an encrypted arbitrary attachment (C4). Generates a fresh per-file master key inside
     * [ChatRepository.uploadEncryptedBlob], uploads the ciphertext to the `chat-attachments`
     * bucket, then sends a `message_type = file` message whose body is the `ccx:v1:` envelope —
     * so the per-file key travels entirely inside the existing E2EE wire format.
     */
    fun sendChatFile(bytes: ByteArray, mimeType: String, fileName: String) {
        if (bytes.isEmpty()) return
        if (_isMessageSubmitInProgress.value) return
        val connectionId = currentConnectionId ?: return
        val userId = _currentUserId.value ?: return
        val trimmedName = fileName.trim().ifEmpty { "attachment" }

        val validation = ChatAttachmentValidator.validate(
            fileName = trimmedName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
        )
        if (validation is ChatAttachmentValidator.Result.Invalid) {
            _messageSendError.value = validation.message
            return
        }

        _messageSendError.value = null
        _isMessageSubmitInProgress.value = true
        viewModelScope.launch {
            try {
                val apiChatId = resolveOrCreateApiChatId(connectionId) ?: run {
                    _messageSendError.value = "Failed to send — unable to start chat"
                    return@launch
                }
                val uploaded = chatRepository.uploadEncryptedBlob(
                    bucketName = CHAT_ATTACHMENTS_BUCKET,
                    chatId = apiChatId,
                    senderUserId = userId,
                    plainBytes = bytes,
                    mimeType = mimeType,
                    fileName = trimmedName,
                ) ?: run {
                    _messageSendError.value = "Failed to upload attachment"
                    return@launch
                }
                val envelope = AttachmentCrypto.Envelope(
                    v = 1,
                    type = "file",
                    name = uploaded.fileName,
                    mime = uploaded.mimeType,
                    size = uploaded.sizeBytes,
                    path = uploaded.path,
                    key = uploaded.fileMasterKeyBase64,
                    sha256 = uploaded.sha256Base64,
                )
                val envelopeBody = AttachmentCrypto.encodeEnvelope(envelope)

                val replyTarget = _replyingTo.value
                val meta = buildJsonObject {
                    put("attachment_path", uploaded.path)
                    put("attachment_name", uploaded.fileName)
                    put("attachment_mime", uploaded.mimeType)
                    put("attachment_size", uploaded.sizeBytes)
                    if (replyTarget != null) {
                        put("reply_to_id", replyTarget.message.id)
                        put("reply_to_content", replySnippetForMessage(replyTarget.message))
                    }
                }

                val message = chatRepository.sendMessage(
                    chatId = apiChatId,
                    userId = userId,
                    content = envelopeBody,
                    messageType = ChatMessageType.FILE,
                    metadata = meta,
                )
                if (message != null) {
                    _replyingTo.value = null
                    val currentUser = resolveMessageUser(userId, apiChatId)
                        ?: AppDataManager.currentUser.value?.takeIf { it.id == userId }
                        ?: User(id = userId, name = "You", createdAt = 0L)
                    applyInsertedMessage(message, currentUser, userId)
                    activateConnectionIfPending(connectionId)
                } else {
                    _messageSendError.value = "Failed to send attachment"
                }
            } catch (e: Exception) {
                _messageSendError.value = "Failed to send attachment — ${e.message ?: "error"}"
                println("Error sending attachment: ${e.redactedRestMessage()}")
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }

    /**
     * Download + decrypt a chat attachment (Phase 2 — C6). Mints a fresh signed URL, pulls the
     * ciphertext, decrypts with the per-file master key from the envelope, re-verifies SHA-256
     * on the plaintext, then writes the bytes to the platform Downloads surface. All failures
     * are surfaced as a user-visible [ChatAttachmentDownloadOutcome.Failure].
     */
    suspend fun downloadChatAttachment(
        envelope: AttachmentCrypto.Envelope,
    ): ChatAttachmentDownloadOutcome {
        if (envelope.path.isBlank() || envelope.key.isBlank() || envelope.sha256.isBlank()) {
            return ChatAttachmentDownloadOutcome.Failure("Attachment envelope is invalid.")
        }
        val plaintext = chatRepository.downloadAttachmentPlaintext(
            path = envelope.path,
            fileMasterKeyBase64 = envelope.key,
            expectedSha256Base64 = envelope.sha256,
        ) ?: return ChatAttachmentDownloadOutcome.Failure(
            "Download failed — integrity check did not pass.",
        )
        val savedPath = saveDecryptedAttachmentToDownloads(
            bytes = plaintext,
            fileName = envelope.name.ifBlank { "attachment" },
            mimeType = envelope.mime.ifBlank { "application/octet-stream" },
        ) ?: return ChatAttachmentDownloadOutcome.Failure(
            "Couldn't write the file to Downloads.",
        )
        return ChatAttachmentDownloadOutcome.Success(savedPath)
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
                    updateConnectionState(connectionId) { it.copy(expiry_state = "active", status = "active") }
                }
            }
        }
    }

    fun updateMessageInput(text: String) {
        _messageInput.value = text.take(CHAT_MESSAGE_INPUT_MAX_LENGTH)
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

    /**
     * @param clearMessageSurface When false, skips forcing [ChatMessagesState.Loading] after teardown.
     * Use when the chat composable may still be attached for a frame (e.g. iOS interactive back)
     * so the UI does not flash a full-screen loading state over the list.
     */
    fun leaveChatRoom(clearMessageSurface: Boolean = true) {
        val chatId = (_chatMessagesState.value as? ChatMessagesState.Success)?.chatDetails?.chat?.id
        val userId = _currentUserId.value
        if (chatId != null && userId != null) {
             onUserStoppedTyping(chatId)
        }
        clearSecureChatMediaCache()
        realtimeJob?.cancel()
        realtimeJob = null
        // Remove the realtime channels from Supabase
        activeMessageSubscription?.let { sub ->
            viewModelScope.launch {
                try { sub.detach() } catch (_: Exception) {}
            }
        }
        activeMessageSubscription = null
        activeChatSyncJob?.cancel()
        activeChatSyncJob = null
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
        if (clearMessageSurface) {
            _chatMessagesState.value = ChatMessagesState.Loading
        }
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

    private fun applyReactionChangeEvent(event: ReactionChangeEvent) {
        val current = _messageReactions.value.toMutableMap()
        when (event) {
            is ReactionChangeEvent.Insert -> {
                val list = current.getOrElse(event.reaction.messageId) { emptyList() }
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
                println("Search error: ${e.redactedRestMessage()}")
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
                updateConnectionState(connectionId) { it.copy(expiry_state = "kept", status = "kept") }
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
     * Refresh chats after a connection disappears from the client (e.g. archived server-side).
     * Idle archival is handled by [expire-connections] and [connection_archives].
     */
    fun handleExpiredConnectionDismiss() {
        if (_currentUserId.value == null) return
        viewModelScope.launch {
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

    /**
     * Send one contextual icebreaker from the Clicks list archive banner (matches home poll-pair behavior).
     */
    fun sendArchiveBannerIcebreaker(connectionId: String, otherDisplayName: String) {
        val userId = _currentUserId.value ?: return
        val name = otherDisplayName.trim().ifBlank { "them" }
        viewModelScope.launch {
            try {
                val details = chatRepository.fetchChatWithDetails(connectionId, userId)
                val chatId = details?.chat?.id ?: resolveOrCreateApiChatId(connectionId) ?: run {
                    _nudgeResult.value = "Couldn't open chat"
                    return@launch
                }
                val contextTag = details?.connection?.context_tag
                val prompt = IcebreakerRepository.getPromptsForContext(contextTag, count = 1).firstOrNull()
                    ?: IcebreakerRepository.getRandomPrompt()
                val msg = chatRepository.sendMessage(chatId, userId, prompt.text)
                _nudgeResult.value =
                    if (msg != null) "Icebreaker sent to $name!" else "Failed to send icebreaker"
            } catch (_: Exception) {
                _nudgeResult.value = "Failed to send icebreaker"
            }
        }
    }

    fun clearNudgeResult() {
        _nudgeResult.value = null
    }

    /**
     * Snapshot of the current chat's locally-decrypted messages shaped for the
     * [ProfileBottomSheet] Media / Files / Links tabs. Returns an empty list
     * when no chat is loaded or messages haven't been fetched yet.
     */
    fun currentChatLocalMessages(): List<ProfileSheetLocalMessage> {
        val state = _chatMessagesState.value
        if (state !is ChatMessagesState.Success) return emptyList()
        return state.messages.map { mwu ->
            val m = mwu.message
            ProfileSheetLocalMessage(
                id = m.id,
                content = m.content,
                messageType = m.messageType,
                timestamp = kotlinx.datetime.Instant.fromEpochMilliseconds(m.timeCreated).toString(),
                metadata = m.metadata,
            )
        }
    }

    /**
     * True when the unordered member set (including caller) forms a fully connected graph on the server.
     */
    suspend fun memberSetSatisfiesVerifiedCliqueGraph(memberUserIds: List<String>): Boolean =
        chatRepository.verifiedCliqueEdgesExist(memberUserIds)

    private fun buildInitialVerifiedCliqueDisplayName(memberUserIds: List<String>, currentUserId: String): String {
        val ordered = memberUserIds.distinct().sorted()
        return ordered.joinToString(", ") { uid ->
            val u = when {
                uid == currentUserId -> AppDataManager.currentUser.value
                else -> AppDataManager.getConnectedUser(uid)
            }
            u?.firstName?.trim()?.takeIf { it.isNotEmpty() }
                ?: u?.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
                ?: "Friend"
        }
    }

    /**
     * Creates a verified group chat ("click") with [selectedFriendUserIds] (excluding self; self is merged in).
     */
    fun createVerifiedClique(
        selectedFriendUserIds: List<String>,
        onResult: (Result<String>) -> Unit,
    ) {
        val userId = _currentUserId.value ?: run {
            onResult(Result.failure(IllegalStateException("Not signed in")))
            return
        }
        val members = (selectedFriendUserIds + userId).distinct().sorted()
        if (members.size < 2) {
            onResult(Result.failure(IllegalArgumentException("Pick at least one friend")))
            return
        }
        val memberSet = members.toSet()
        val alreadyHaveClick = (_chatListState.value as? ChatListState.Success)?.chats?.any { chat ->
            chat.groupClique != null && chat.groupClique.memberUserIds.toSet() == memberSet
        } == true
        if (alreadyHaveClick) {
            onResult(
                Result.failure(
                    IllegalStateException("verified click already exists for this member set"),
                ),
            )
            return
        }
        viewModelScope.launch {
            try {
                val initialName = buildInitialVerifiedCliqueDisplayName(members, userId)
                val rpc = VerifiedCliqueCreation.createVerifiedCliqueWithWrappedKeys(
                    chatRepository = chatRepository,
                    connections = AppDataManager.connections.value,
                    currentUserId = userId,
                    memberUserIds = members,
                    initialGroupName = initialName,
                )
                val payload = rpc.getOrNull()
                if (payload != null) {
                    val chatId = chatRepository.resolveChatIdForGroupId(payload.groupId)
                    if (chatId != null) {
                        chatRepository.cacheGroupMasterKey(chatId, payload.masterKey32)
                    }
                    loadChats(isForced = true)
                }
                onResult(rpc.map { it.groupId })
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
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
                println("Error deleting message: ${e.redactedRestMessage()}")
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
    fun unarchiveConnection(connectionId: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            AppDataManager.markConnectionUnarchivedLocally(connectionId)
            supabaseRepository.unarchiveConnection(userId, connectionId)
            reapplyChatListVisibility()
            loadChats(isForced = true)
            _nudgeResult.value = "Connection unarchived"
        }
    }

    /**
     * Soft-remove the current connection (server `status = removed`; row retained).
     */
    fun deleteConnectionPermanently(onComplete: (Boolean) -> Unit = {}) {
        val connectionId = currentConnectionId ?: return
        deleteConnectionPermanentlyById(connectionId, onComplete)
    }

    /**
     * Hide a connection for the current user via [connection_hidden].
     * Saves the connection object before the optimistic hide so it can be
     * restored on failure — even when Ghost Mode blocks [AppDataManager.refresh].
     */
    fun deleteConnectionPermanentlyById(connectionId: String, onComplete: (Boolean) -> Unit = {}) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            // Save the connection before optimistic hide so we can restore it on failure
            // (AppDataManager.refresh no-ops when Ghost Mode is active).
            val savedConnection = AppDataManager.getConnection(connectionId)
            val pair = savedConnection?.user_ids
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

    fun leaveVerifiedClique(groupId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            chatRepository.clearChatListLocalCaches()
            val ok = chatRepository.leaveClique(groupId).isSuccess
            if (ok) {
                if (currentConnectionId == groupId) {
                    leaveChatRoom()
                }
                loadChats(isForced = true)
                _nudgeResult.value = "You left the group"
            } else {
                _nudgeResult.value = "Could not leave group"
            }
            onComplete(ok)
        }
    }

    fun deleteVerifiedClique(groupId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            chatRepository.clearChatListLocalCaches()
            val ok = chatRepository.deleteClique(groupId).isSuccess
            if (ok) {
                if (currentConnectionId == groupId) {
                    leaveChatRoom()
                }
                loadChats(isForced = true)
                _nudgeResult.value = "Group deleted"
            } else {
                _nudgeResult.value = "Could not delete group"
            }
            onComplete(ok)
        }
    }

    fun renameVerifiedClique(groupId: String, newName: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) {
                onComplete(false)
                return@launch
            }
            val ok = chatRepository.renameClique(groupId, trimmed).isSuccess
            if (ok) {
                val cur = _chatMessagesState.value as? ChatMessagesState.Success
                val gc = cur?.chatDetails?.groupClique
                if (cur != null && gc != null && gc.groupId == groupId) {
                    _chatMessagesState.value = cur.copy(
                        chatDetails = cur.chatDetails.copy(
                            groupClique = gc.copy(name = trimmed),
                            otherUser = cur.chatDetails.otherUser.copy(name = trimmed),
                        ),
                    )
                }
                loadChats(isForced = true)
                _nudgeResult.value = "Group renamed"
            } else {
                _nudgeResult.value = "Could not rename group"
            }
            onComplete(ok)
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

        val connectionsChannel = connectionsRealtimeChannel
        connectionsRealtimeChannel = null
        val apiIdToLeave = currentApiChatId
        val messageSub = activeMessageSubscription
        activeMessageSubscription = null

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

    private fun extensionForChatMedia(mime: String, isImage: Boolean): String {
        val m = mime.lowercase()
        return when {
            m.contains("png") -> "png"
            m.contains("webp") -> "webp"
            m.contains("jpeg") || m.contains("jpg") -> "jpg"
            m.contains("mpeg") || m.contains("mp3") -> "mp3"
            m.contains("mp4") || m.contains("m4a") || m.contains("aac") -> "m4a"
            m.contains("ogg") -> "ogg"
            m.contains("wav") -> "wav"
            isImage -> "jpg"
            else -> "bin"
        }
    }
}

private const val CHAT_MESSAGE_INPUT_MAX_LENGTH = 1000
private const val MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS = 3
private const val MESSAGE_SUBSCRIPTION_RETRY_DELAY_MS = 750L
private const val ACTIVE_CHAT_SYNC_INTERVAL_MS = 800L
private const val CONNECTIONS_LIST_DEBOUNCE_MS = 450L
private const val SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES = 160
private const val SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES = 80
