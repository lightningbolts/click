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

sealed class ChatListState {
    data object Loading : ChatListState()
    data class Success(val chats: List<ChatWithDetails>) : ChatListState()
    data class Error(val message: String) : ChatListState()
}

sealed class ChatMessagesState {
    data object Loading : ChatMessagesState()
    data class Success(
        val messages: List<MessageWithUser>,
        val chatDetails: ChatWithDetails
    ) : ChatMessagesState()
    data class Error(val message: String) : ChatMessagesState()
}

class ChatViewModel(
    tokenStorage: TokenStorage = createTokenStorage(),
    private val chatRepository: ChatRepository = ChatRepository(tokenStorage = tokenStorage),
    private val supabaseRepository: SupabaseRepository = SupabaseRepository()
) : ViewModel() {

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

    private var currentChatId: String? = null
    private var realtimeJob: Job? = null
    private var typingPollingJob: Job? = null
    private var vibeCheckTimerJob: Job? = null
    private var lastTypingSent: Long = 0L

    init {
        // Observe AppDataManager connections to stay in sync
        viewModelScope.launch {
            AppDataManager.connections.collect {
                // If we are already success, we might want to refresh details if connections list changed
                if (_chatListState.value is ChatListState.Success) {
                    loadChats(isForced = false)
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
            if (isForced || _chatListState.value !is ChatListState.Success) {
                _chatListState.value = ChatListState.Loading
            }
            try {
                val chats = chatRepository.fetchUserChatsWithDetails(userId)
                _chatListState.value = ChatListState.Success(chats)
            } catch (e: Exception) {
                _chatListState.value = ChatListState.Error(e.message ?: "Failed to load chats")
            }
        }
    }

    // Load messages for a specific chat
    fun loadChatMessages(chatId: String) {
        val userId = _currentUserId.value ?: return
        if (currentChatId == chatId && _chatMessagesState.value is ChatMessagesState.Success) return
        
        currentChatId = chatId

        viewModelScope.launch {
            _chatMessagesState.value = ChatMessagesState.Loading
            try {
                // Get chat details
                val chatDetails = chatRepository.fetchChatWithDetails(chatId, userId)
                if (chatDetails == null) {
                    _chatMessagesState.value = ChatMessagesState.Error("Chat not found")
                    return@launch
                }

                // Get messages
                val messages = chatRepository.fetchMessagesForChat(chatId)

                // Get all users for this chat in one go
                val participants = chatRepository.fetchChatParticipants(chatId)
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
                chatRepository.markMessagesAsRead(chatId, userId)

                // Subscribe to new messages
                subscribeToNewMessages(chatId, userId)
                
                // Monitor typing status
                startTypingMonitoring(chatId)
                
                // Start Vibe Check timer and update keep states
                startVibeCheckTimer(chatDetails.connection, userId)
                updateKeepStates(chatDetails.connection, userId)
                
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
        realtimeJob = viewModelScope.launch {
            try {
                chatRepository.subscribeToMessages(chatId)
                    .collect { message ->
                        // Get user for the new message
                        val user = chatRepository.getUserById(message.user_id)
                        if (user != null) {
                            val messageWithUser = MessageWithUser(
                                message = message,
                                user = user,
                                isSent = message.user_id == userId
                            )

                            // Update state with new message
                            val currentState = _chatMessagesState.value
                            if (currentState is ChatMessagesState.Success) {
                                _chatMessagesState.value = currentState.copy(
                                    messages = currentState.messages + messageWithUser
                                )

                                // Mark new message as read if it's not from current user
                                if (!messageWithUser.isSent) {
                                    chatRepository.markMessagesAsRead(chatId, userId)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                println("Error subscribing to messages: ${e.message}")
            }
        }
    }

    fun sendMessage() {
        val chatId = currentChatId ?: return
        val userId = _currentUserId.value ?: return
        val content = _messageInput.value.trim()
        if (content.isEmpty()) return
        onUserStoppedTyping(chatId)
        viewModelScope.launch {
            try {
                val message = chatRepository.sendMessage(chatId, userId, content)
                if (message != null) {
                    _messageInput.value = ""
                } else {
                    println("Failed to send message")
                }
            } catch (e: Exception) {
                println("Error sending message: ${e.message}")
            }
        }
    }

    fun updateMessageInput(text: String) {
        _messageInput.value = text
        currentChatId?.let { onUserTyping(it) }
    }

    fun leaveChatRoom() {
        val chatId = currentChatId
        val userId = _currentUserId.value
        if (chatId != null && userId != null) {
             onUserStoppedTyping(chatId)
        }
        realtimeJob?.cancel()
        realtimeJob = null
        typingPollingJob?.cancel()
        typingPollingJob = null
        currentChatId = null
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

    fun addReaction(messageId: String, reactionType: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            chatRepository.addReaction(messageId, userId, reactionType)
        }
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
        val chatId = currentChatId ?: return
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
    
    fun deleteExpiredConnection() {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            supabaseRepository.deleteConnection(chatId)
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
    
    private fun resetIcebreakerState() {
        _icebreakerPrompts.value = emptyList()
        _showIcebreakerPanel.value = true
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        typingPollingJob?.cancel()
        vibeCheckTimerJob?.cancel()
    }
}
