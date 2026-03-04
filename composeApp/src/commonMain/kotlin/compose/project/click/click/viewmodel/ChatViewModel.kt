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
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import io.github.jan.supabase.realtime.RealtimeChannel
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.repository.ChatRepository.MessageChangeEvent
import compose.project.click.click.data.repository.ChatRepository.ReactionChangeEvent

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
    private val chatRepository: ChatRepository = ChatRepository(tokenStorage = tokenStorage),
    private val supabaseRepository: SupabaseRepository = SupabaseRepository()
) : ViewModel() {

    private val vibeCheckEnabled = false

    private val _chatListState = MutableStateFlow<ChatListState>(ChatListState.Loading)
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()

    private val _chatMessagesState = MutableStateFlow<ChatMessagesState>(ChatMessagesState.Loading)
    val chatMessagesState: StateFlow<ChatMessagesState> = _chatMessagesState.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _typingUsers = MutableStateFlow<List<String>>(emptyList())
    val typingUsers: StateFlow<List<String>> = _typingUsers.asStateFlow()
    
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

    // ── Message editing state ─────────────────────────────────────────────────
    // Non-null when the user is editing an existing message
    private val _editingMessageId = MutableStateFlow<String?>(null)
    val editingMessageId: StateFlow<String?> = _editingMessageId.asStateFlow()

    // ── Archived connection IDs (in-memory; persisted per-session) ─────────────
    private val _archivedConnectionIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedConnectionIds: StateFlow<Set<String>> = _archivedConnectionIds.asStateFlow()

    // ── Reactions state: messageId → list of reactions ─────────────────────────
    private val _messageReactions = MutableStateFlow<Map<String, List<compose.project.click.click.data.models.MessageReaction>>>(emptyMap())
    val messageReactions: StateFlow<Map<String, List<compose.project.click.click.data.models.MessageReaction>>> = _messageReactions.asStateFlow()

    private var currentConnectionId: String? = null
    private var activeChannel: RealtimeChannel? = null
    private var reactionsChannel: RealtimeChannel? = null
    private var reactionsJob: Job? = null
    private var realtimeJob: Job? = null
    private var typingPollingJob: Job? = null
    private var vibeCheckTimerJob: Job? = null
    private var lastTypingSent: Long = 0L

    init {
        // Observe AppDataManager connections to stay in sync
        viewModelScope.launch {
            AppDataManager.connections.collect { connections ->
                // Refresh when shared connection state changes (e.g. after relogin hydration)
                if (_currentUserId.value != null && connections.isNotEmpty()) {
                    loadChats(isForced = true)
                }
            }
        }
    }

    // Set the current user
    fun setCurrentUser(userId: String) {
        if (_currentUserId.value == userId && _chatListState.value is ChatListState.Success) return
        _currentUserId.value = userId
        loadChats()
    }

    // Load all chats for the current user
    fun loadChats(isForced: Boolean = true) {
        val userId = _currentUserId.value ?: return
        
        // Avoid reload if already success and not forced
        if (!isForced && _chatListState.value is ChatListState.Success) return
        
        viewModelScope.launch {
            val cachedConnections = AppDataManager.connections.value
            val cachedUsers = AppDataManager.connectedUsers.value
            
            // Show cached data immediately if available, BUT only if we don't
            // already have real data from the API (avoids the flash to "Start a
            // conversation" when returning from a chat).
            val alreadyHasRealData = _chatListState.value is ChatListState.Success
            
            if (cachedConnections.isNotEmpty() && !alreadyHasRealData) {
                // Build ChatWithDetails from cached data for instant display
                val cachedChats = cachedConnections.mapNotNull { connection ->
                    val otherUserId = connection.user_ids.firstOrNull { it != userId } ?: return@mapNotNull null
                    val otherUser = cachedUsers[otherUserId] ?: User(id = otherUserId, name = "User", createdAt = 0L)
                    ChatWithDetails(
                        chat = connection.chat,
                        connection = connection,
                        otherUser = otherUser,
                        lastMessage = connection.chat.messages.lastOrNull(),
                        unreadCount = 0 // Will be updated from API
                    )
                }
                _chatListState.value = ChatListState.Success(cachedChats)
            } else if (!alreadyHasRealData && (isForced || _chatListState.value !is ChatListState.Success)) {
                _chatListState.value = ChatListState.Loading
            }
            
            // Fetch fresh data from API in background
            try {
                val chats = chatRepository.fetchUserChatsWithDetails(userId)

                if (chats.isNotEmpty()) {
                    _chatListState.value = ChatListState.Success(chats)
                } else if (cachedConnections.isNotEmpty()) {
                    // Keep hydrated/cached connections visible when API is empty
                    // (common during session bootstrap or backend shape mismatch).
                    if (_chatListState.value !is ChatListState.Success) {
                        _chatListState.value = ChatListState.Success(cachedConnections.mapNotNull { connection ->
                            val otherUserId = connection.user_ids.firstOrNull { it != userId } ?: return@mapNotNull null
                            val otherUser = cachedUsers[otherUserId] ?: User(id = otherUserId, name = "User", createdAt = 0L)
                            ChatWithDetails(
                                chat = connection.chat,
                                connection = connection,
                                otherUser = otherUser,
                                lastMessage = connection.chat.messages.lastOrNull(),
                                unreadCount = 0
                            )
                        })
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

    // Load messages for a specific chat
    fun loadChatMessages(chatId: String) {
        val userId = _currentUserId.value ?: return
        if (currentConnectionId == chatId && _chatMessagesState.value is ChatMessagesState.Success) return
        
        currentConnectionId = chatId

        // Instantly show the chat header from cached list data (no loading spinner)
        val cachedChat = (_chatListState.value as? ChatListState.Success)
            ?.chats?.firstOrNull { it.connection.id == chatId }
        if (cachedChat != null) {
            // Show header immediately with empty message list — user sees the chat "open" instantly
            _chatMessagesState.value = ChatMessagesState.Success(emptyList(), cachedChat, isLoadingMessages = true)
        } else {
            _chatMessagesState.value = ChatMessagesState.Loading
        }

        viewModelScope.launch {
            try {
                // Resolve chat details (use cached if available)
                val chatDetails = cachedChat ?: chatRepository.fetchChatWithDetails(chatId, userId)
                if (chatDetails == null) {
                    _chatMessagesState.value = ChatMessagesState.Error("Chat not found")
                    return@launch
                }

                val apiChatId = chatDetails.chat.id
                if (apiChatId.isNullOrBlank()) {
                    _chatMessagesState.value = ChatMessagesState.Error("Chat not found")
                    return@launch
                }

                // Get messages
                val messages = chatRepository.fetchMessagesForChat(apiChatId)

                // Get all users for this chat in one go
                val participants = chatRepository.fetchChatParticipants(apiChatId)
                val userMap = participants.associateBy { it.id }

                // Map messages to include user info
                val messagesWithUsers = messages.map { message ->
                    val user = userMap[message.user_id] ?: User(id = message.user_id, name = "Unknown", createdAt = 0L)
                    MessageWithUser(
                        message = message,
                        user = user,
                        isSent = message.user_id == userId
                    )
                }

                _chatMessagesState.value = ChatMessagesState.Success(messagesWithUsers, chatDetails)

                // Mark messages as read
                chatRepository.markMessagesAsRead(apiChatId, userId)

                // Subscribe to new messages
                subscribeToNewMessages(apiChatId, userId)

                // Load initial reactions & subscribe to changes via Realtime
                loadAndSubscribeReactions(apiChatId)
                
                // Monitor typing status
                startTypingMonitoring(apiChatId)
                
                // Vibe Check is disabled
                if (vibeCheckEnabled) {
                    startVibeCheckTimer(chatDetails.connection, userId)
                    updateKeepStates(chatDetails.connection, userId)
                }
                
                // Mark chat as begun if this is the first time
                if (!chatDetails.connection.has_begun) {
                    supabaseRepository.updateConnectionHasBegun(chatId, true)
                }
                
                // Load icebreaker prompts for new conversations (show only if few messages)
                if (messagesWithUsers.size < 5) {
                    loadIcebreakerPrompts(chatDetails.connection.context_tag)
                    _showIcebreakerPanel.value = true
                } else {
                    _showIcebreakerPanel.value = false
                }
            } catch (e: Exception) {
                _chatMessagesState.value = ChatMessagesState.Error(e.message ?: "Failed to load messages")
            }
        }
    }

    // ... rest of the file remains the same ...
    // Note: I will only provide the changed parts and then the rest.
    // Actually I must provide the full file as per user rules.
    
    // Subscribe to real-time message updates
    private fun subscribeToNewMessages(chatId: String, userId: String) {
        realtimeJob?.cancel()
        viewModelScope.launch {
            // Clean up previous channel
            activeChannel?.let {
                try { it.unsubscribe() } catch (_: Exception) {}
            }
            activeChannel = null
        }
        realtimeJob = viewModelScope.launch {
            try {
                val (channel, changeFlow) = chatRepository.subscribeToMessages(chatId)
                activeChannel = channel

                // Collect change events in background
                changeFlow
                    .onEach { event ->
                        when (event) {
                            is MessageChangeEvent.Insert -> {
                                val user = chatRepository.getUserById(event.message.user_id)
                                if (user != null) {
                                    val messageWithUser = MessageWithUser(
                                        message = event.message,
                                        user = user,
                                        isSent = event.message.user_id == userId
                                    )
                                    val currentState = _chatMessagesState.value
                                    if (currentState is ChatMessagesState.Success) {
                                        // Avoid duplicates (sender gets own message via realtime)
                                        val exists = currentState.messages.any { it.message.id == event.message.id }
                                        if (!exists) {
                                            _chatMessagesState.value = currentState.copy(
                                                messages = currentState.messages + messageWithUser
                                            )
                                        }
                                        if (!messageWithUser.isSent) {
                                            chatRepository.markMessagesAsRead(chatId, userId)
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

                // Actually subscribe the channel — this is the critical step
                channel.subscribe()
            } catch (e: Exception) {
                println("Error subscribing to messages: ${e.message}")
            }
        }
    }

    fun sendMessage() {
        // If in edit mode, confirm the edit instead of posting a new message
        val editId = _editingMessageId.value
        if (editId != null) {
            confirmEditMessage(editId)
            return
        }
        val connectionId = currentConnectionId ?: return
        val apiChatId = (_chatMessagesState.value as? ChatMessagesState.Success)?.chatDetails?.chat?.id ?: return
        val userId = _currentUserId.value ?: return
        val content = _messageInput.value.trim()
        if (content.isEmpty()) return
        onUserStoppedTyping(apiChatId)
        viewModelScope.launch {
            try {
                val message = chatRepository.sendMessage(apiChatId, userId, content)
                if (message != null) {
                    _messageInput.value = ""
                    // Activate connection on first message (pending → active)
                    activateConnectionIfPending(connectionId)
                } else {
                    println("Failed to send message")
                }
            } catch (e: Exception) {
                println("Error sending message: ${e.message}")
            }
        }
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
                supabaseRepository.updateConnectionExpiryState(connectionId, "active")
            }
        }
    }

    fun updateMessageInput(text: String) {
        _messageInput.value = text
        ((_chatMessagesState.value as? ChatMessagesState.Success)?.chatDetails?.chat?.id)
            ?.let { onUserTyping(it) }
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
        activeChannel?.let { channel ->
            viewModelScope.launch {
                try { channel.unsubscribe() } catch (_: Exception) {}
            }
        }
        activeChannel = null
        reactionsJob?.cancel()
        reactionsJob = null
        reactionsChannel?.let { channel ->
            viewModelScope.launch {
                try { channel.unsubscribe() } catch (_: Exception) {}
            }
        }
        reactionsChannel = null
        _messageReactions.value = emptyMap()
        typingPollingJob?.cancel()
        typingPollingJob = null
        currentConnectionId = null
        _chatMessagesState.value = ChatMessagesState.Loading
        resetVibeCheckState()
        resetIcebreakerState()
    }

    fun startTypingMonitoring(chatId: String) {
        typingPollingJob?.cancel()
        typingPollingJob = viewModelScope.launch {
            chatRepository.observeTypingStatus(chatId).collect { status ->
                val currentUser = _currentUserId.value
                if (status.userId != currentUser) {
                    val currentTyping = _typingUsers.value.toMutableList()
                    if (status.isTyping) {
                        if (status.userId !in currentTyping) {
                            _typingUsers.value = currentTyping + status.userId
                        }
                    } else {
                        _typingUsers.value = currentTyping - status.userId
                    }
                }
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
                delay(3000)
                if (Clock.System.now().toEpochMilliseconds() - lastTypingSent >= 3000L) {
                    onUserStoppedTyping(chatId)
                }
            }
        }
    }
    
    fun onUserStoppedTyping(chatId: String) {
        val userId = _currentUserId.value ?: return
        lastTypingSent = 0L 
        viewModelScope.launch { chatRepository.sendTypingStatus(chatId, userId, false) }
    }

    // ── Reactions ──────────────────────────────────────────────────────────────

    /** Load existing reactions from DB, then subscribe to Realtime changes. */
    private fun loadAndSubscribeReactions(chatId: String) {
        reactionsJob?.cancel()
        reactionsChannel?.let { ch ->
            viewModelScope.launch { try { ch.unsubscribe() } catch (_: Exception) {} }
        }
        reactionsChannel = null

        reactionsJob = viewModelScope.launch {
            // 1. Fetch existing reactions
            val initial = chatRepository.fetchReactionsForChat(chatId)
            _messageReactions.value = initial.groupBy { it.messageId }

            // 2. Subscribe to Realtime inserts/deletes
            try {
                val (channel, changeFlow) = chatRepository.subscribeToReactions(chatId)
                reactionsChannel = channel

                changeFlow
                    .onEach { event ->
                        val current = _messageReactions.value.toMutableMap()
                        when (event) {
                            is ReactionChangeEvent.Insert -> {
                                val list = current.getOrElse(event.reaction.messageId) { emptyList() }
                                // Deduplicate
                                if (list.none { it.id == event.reaction.id }) {
                                    current[event.reaction.messageId] = list + event.reaction
                                    _messageReactions.value = current
                                }
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

                channel.subscribe()
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
        val existing = _messageReactions.value[messageId]
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
                current[messageId] = (current[messageId] ?: emptyList()) + tempReaction
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
        if (!vibeCheckEnabled) return
        val chatId = currentConnectionId ?: return
        val userId = _currentUserId.value ?: return
        val currentState = _chatMessagesState.value
        if (currentState !is ChatMessagesState.Success) return
        val connection = currentState.chatDetails.connection
        viewModelScope.launch {
            val success = supabaseRepository.updateUserKeepDecision(
                connectionId = chatId,
                userId = userId,
                keepConnection = true,
                currentShouldContinue = connection.should_continue,
                userIds = connection.user_ids
            )
            if (success) {
                _currentUserHasKept.value = true
                val otherUserIndex = if (connection.getUserIndex(userId) == 0) 1 else 0
                if (connection.should_continue.getOrNull(otherUserIndex) == true) {
                    _connectionKept.value = true
                    vibeCheckTimerJob?.cancel()
                    // Mutual keep → permanent connection
                    supabaseRepository.updateConnectionExpiryState(chatId, "kept")
                }
                refreshConnectionState(chatId, userId)
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
        val chatId = currentState.chatDetails.chat.id ?: return
        val userId = _currentUserId.value ?: return
        val currentUser = compose.project.click.click.data.AppDataManager.currentUser.value ?: return
        val otherUserName = currentState.chatDetails.otherUser.name ?: "them"
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
        val connectionId = currentConnectionId ?: return
        val newContent = _messageInput.value.trim()
        if (newContent.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()

        // Optimistic local update
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

        viewModelScope.launch {
            try {
                val success = supabaseRepository.editMessage(messageId, newContent)
                if (success) {
                    _editingMessageId.value = null
                    _messageInput.value = ""
                } else {
                    loadChatMessages(connectionId)
                }
            } catch (e: Exception) {
                println("Error editing message: ${e.message}")
                loadChatMessages(connectionId)
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
    fun archiveConnection(onComplete: () -> Unit = {}) {
        val connectionId = currentConnectionId ?: return
        archiveConnectionById(connectionId, onComplete)
    }

    /**
     * Archive a specific connection by ID.
     */
    fun archiveConnectionById(connectionId: String, onComplete: () -> Unit = {}) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            _archivedConnectionIds.value = _archivedConnectionIds.value + connectionId
            supabaseRepository.archiveConnection(userId, connectionId) // no-op if table missing
            if (currentConnectionId == connectionId) {
                leaveChatRoom()
            }
            loadChats(isForced = true)
            onComplete()
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
        }
    }

    /**
     * Hard-delete the current connection (removes from DB).
     */
    fun deleteConnectionPermanently(onComplete: () -> Unit = {}) {
        val connectionId = currentConnectionId ?: return
        deleteConnectionPermanentlyById(connectionId, onComplete)
    }

    /**
     * Hard-delete a specific connection by ID.
     */
    fun deleteConnectionPermanentlyById(connectionId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            supabaseRepository.deleteConnection(connectionId)
            if (currentConnectionId == connectionId) {
                leaveChatRoom()
            }
            loadChats(isForced = true)
            onComplete()
        }
    }

    // ==================== Safety Actions ====================

    /**
     * Block the other user in the current chat.
     * Resolves the other user ID from chat state or AppDataManager connections.
     * This avoids race conditions when called from the connections list
     * where chat state may not have loaded yet.
     */
    fun blockUser(onBlocked: () -> Unit) {
        val connectionId = currentConnectionId ?: return
        blockUserForConnection(connectionId, onBlocked)
    }

    /**
     * Block the other user for a specific connection.
     */
    fun blockUserForConnection(connectionId: String, onBlocked: () -> Unit = {}) {
        val userId = _currentUserId.value ?: return

        // Try to get the other user ID from chat state first, then fall back to AppDataManager
        val otherUserId = resolveOtherUserId(userId, connectionId)
        if (otherUserId == null) {
            println("blockUser: Could not resolve other user ID for connection $connectionId")
            return
        }

        viewModelScope.launch {
            val success = supabaseRepository.blockUser(userId, otherUserId)
            if (success) {
                if (currentConnectionId == connectionId) {
                    leaveChatRoom()
                }
                loadChats(isForced = true)
                onBlocked()
            }
        }
    }

    /**
     * Report the current connection for safety review.
     * Uses currentConnectionId directly — no dependency on chat messages state.
     */
    fun reportConnection(reason: String, onReported: () -> Unit) {
        val connectionId = currentConnectionId ?: return
        reportConnectionForConnection(connectionId, reason, onReported)
    }

    /**
     * Report a specific connection for safety review.
     */
    fun reportConnectionForConnection(connectionId: String, reason: String, onReported: () -> Unit = {}) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            val success = supabaseRepository.reportConnection(connectionId, userId, reason)
            if (success) {
                onReported()
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
        realtimeJob?.cancel()
        reactionsJob?.cancel()
        typingPollingJob?.cancel()
        vibeCheckTimerJob?.cancel()
        activeChannel?.let { channel ->
            viewModelScope.launch {
                try { channel.unsubscribe() } catch (_: Exception) {}
            }
        }
        activeChannel = null
        reactionsChannel?.let { channel ->
            viewModelScope.launch {
                try { channel.unsubscribe() } catch (_: Exception) {}
            }
        }
        reactionsChannel = null
    }
}
