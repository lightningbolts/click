package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _chatListState = MutableStateFlow<ChatListState>(ChatListState.Loading)
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()

    private val _chatMessagesState = MutableStateFlow<ChatMessagesState>(ChatMessagesState.Loading)
    val chatMessagesState: StateFlow<ChatMessagesState> = _chatMessagesState.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private var currentChatId: String? = null
    private var realtimeJob: kotlinx.coroutines.Job? = null

    // Set the current user
    fun setCurrentUser(userId: String) {
        _currentUserId.value = userId
        loadChats()
    }

    // Load all chats for the current user
    fun loadChats() {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            _chatListState.value = ChatListState.Loading
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

                // Get user details for each message
                val messagesWithUsers = messages.mapNotNull { message ->
                    val user = chatRepository.getUserById(message.userId)
                    if (user != null) {
                        MessageWithUser(
                            message = message,
                            user = user,
                            isSent = message.userId == userId
                        )
                    } else null
                }

                _chatMessagesState.value = ChatMessagesState.Success(messagesWithUsers, chatDetails)

                // Mark messages as read
                chatRepository.markMessagesAsRead(chatId, userId)

                // Subscribe to new messages
                subscribeToNewMessages(chatId, userId)
            } catch (e: Exception) {
                _chatMessagesState.value = ChatMessagesState.Error(e.message ?: "Failed to load messages")
            }
        }
    }

    // Subscribe to real-time message updates
    private fun subscribeToNewMessages(chatId: String, userId: String) {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            try {
                chatRepository.subscribeToMessages(chatId)
                    .collect { message ->
                        // Get user for the new message
                        val user = chatRepository.getUserById(message.userId)
                        if (user != null) {
                            val messageWithUser = MessageWithUser(
                                message = message,
                                user = user,
                                isSent = message.userId == userId
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

    // Send a message
    fun sendMessage() {
        val chatId = currentChatId ?: return
        val userId = _currentUserId.value ?: return
        val content = _messageInput.value.trim()

        if (content.isEmpty()) return

        viewModelScope.launch {
            try {
                val message = chatRepository.sendMessage(chatId, userId, content)
                if (message != null) {
                    _messageInput.value = ""
                    // The message will be added via realtime subscription
                } else {
                    // Handle error
                    println("Failed to send message")
                }
            } catch (e: Exception) {
                println("Error sending message: ${e.message}")
            }
        }
    }

    // Update message input
    fun updateMessageInput(text: String) {
        _messageInput.value = text
    }

    // Clean up when leaving chat
    fun leaveChatRoom() {
        realtimeJob?.cancel()
        realtimeJob = null
        currentChatId = null
        _chatMessagesState.value = ChatMessagesState.Loading
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}

