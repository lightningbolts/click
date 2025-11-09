"""
Example usage of the Chat functionality for Click app
This demonstrates how to integrate the chat system into your app
"""

# Example 1: Using the ConnectionsScreen in your app
from kotlin_examples import connection_screen_example

def example_1_basic_usage():
    """
    Basic integration of ConnectionsScreen into your app
    """
    print("""
    // In your App.kt or main navigation setup:
    
    @Composable
    fun App() {
        val userId = "current-user-id" // Get from AuthViewModel
        
        NavigationHost {
            // ... other screens
            
            composable("connections") {
                ConnectionsScreen(userId = userId)
            }
        }
    }
    """)


# Example 2: Using ChatViewModel directly
def example_2_advanced_usage():
    """
    Advanced usage with custom UI using ChatViewModel
    """
    print("""
    @Composable
    fun CustomChatScreen(chatId: String, userId: String) {
        val viewModel: ChatViewModel = viewModel()
        val messagesState by viewModel.chatMessagesState.collectAsState()
        val messageInput by viewModel.messageInput.collectAsState()
        
        LaunchedEffect(userId, chatId) {
            viewModel.setCurrentUser(userId)
            viewModel.loadChatMessages(chatId)
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
            when (val state = messagesState) {
                is ChatMessagesState.Loading -> {
                    CircularProgressIndicator()
                }
                is ChatMessagesState.Success -> {
                    val messages = state.messages
                    val chatDetails = state.chatDetails
                    
                    // Header
                    Text(
                        text = "Chat with ${chatDetails.otherUser.name}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    // Messages list
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(messages) { messageWithUser ->
                            MessageBubble(messageWithUser)
                        }
                    }
                    
                    // Input
                    Row {
                        TextField(
                            value = messageInput,
                            onValueChange = { viewModel.updateMessageInput(it) }
                        )
                        Button(onClick = { viewModel.sendMessage() }) {
                            Text("Send")
                        }
                    }
                }
                is ChatMessagesState.Error -> {
                    Text("Error: ${state.message}")
                }
            }
        }
        
        DisposableEffect(Unit) {
            onDispose {
                viewModel.leaveChatRoom()
            }
        }
    }
    """)


# Example 3: Python API Usage
def example_3_python_api():
    """
    How to use the Python Flask API endpoints
    """
    print("""
    import requests
    
    # Base URL
    BASE_URL = "http://localhost:5000"
    
    # Get JWT token (assume you have it from login)
    jwt_token = "your-jwt-token"
    headers = {"Authorization": jwt_token}
    
    # 1. Get all chats for a user
    user_id = "user-uuid"
    response = requests.get(
        f"{BASE_URL}/api/chats/user/{user_id}",
        headers=headers
    )
    chats = response.json()["chats"]
    print(f"User has {len(chats)} chats")
    
    # 2. Get messages for a specific chat
    chat_id = chats[0]["id"]
    response = requests.get(
        f"{BASE_URL}/api/chats/{chat_id}/messages",
        headers=headers
    )
    messages = response.json()["messages"]
    print(f"Chat has {len(messages)} messages")
    
    # 3. Send a new message
    response = requests.post(
        f"{BASE_URL}/api/chats/{chat_id}/messages",
        headers=headers,
        json={
            "user_id": user_id,
            "content": "Hello from Python!"
        }
    )
    new_message = response.json()["message"]
    print(f"Sent message: {new_message['content']}")
    
    # 4. Mark messages as read
    response = requests.post(
        f"{BASE_URL}/api/chats/{chat_id}/mark_read",
        headers=headers,
        json={"user_id": user_id}
    )
    print(f"Marked messages as read: {response.json()['success']}")
    
    # 5. Update a message
    message_id = new_message["id"]
    response = requests.put(
        f"{BASE_URL}/api/messages/{message_id}",
        headers=headers,
        json={
            "user_id": user_id,
            "content": "Updated message content"
        }
    )
    print(f"Updated message: {response.json()['message']['content']}")
    
    # 6. Delete a message
    response = requests.delete(
        f"{BASE_URL}/api/messages/{message_id}",
        headers=headers,
        json={"user_id": user_id}
    )
    print(f"Deleted message: {response.json()['success']}")
    """)


# Example 4: Direct Supabase Operations (Kotlin)
def example_4_direct_supabase():
    """
    Using ChatRepository directly without ViewModel
    """
    print("""
    // In your code:
    import compose.project.click.click.data.repository.ChatRepository
    import kotlinx.coroutines.runBlocking
    
    fun example() = runBlocking {
        val chatRepo = ChatRepository()
        
        // 1. Fetch chats for a user
        val userId = "user-uuid"
        val chats = chatRepo.fetchUserChatsWithDetails(userId)
        println("Found ${chats.size} chats")
        
        // 2. Fetch messages for a chat
        val chatId = chats.first().chat.id
        val messages = chatRepo.fetchMessagesForChat(chatId)
        println("Chat has ${messages.size} messages")
        
        // 3. Send a message
        val newMessage = chatRepo.sendMessage(
            chatId = chatId,
            userId = userId,
            content = "Hello from Kotlin!"
        )
        println("Sent message: ${newMessage?.content}")
        
        // 4. Subscribe to new messages
        chatRepo.subscribeToMessages(chatId).collect { message ->
            println("New message received: ${message.content}")
        }
    }
    """)


# Example 5: Creating a connection with chat
def example_5_create_connection():
    """
    How to create a new connection and associated chat
    """
    print("""
    // Using Python API:
    
    import requests
    
    BASE_URL = "http://localhost:5000"
    headers = {"Authorization": jwt_token}
    
    # Create a connection (this automatically creates a chat via trigger)
    response = requests.post(
        f"{BASE_URL}/api/connections",
        headers=headers,
        json={
            "user1_id": "user1-uuid",
            "user2_id": "user2-uuid",
            "location": "Red Square, Seattle",
            "created": int(time.time() * 1000)
        }
    )
    connection = response.json()["connection"]
    chat_id = connection["chat_id"]
    
    print(f"Created connection with chat_id: {chat_id}")
    
    # Now you can send messages in this chat
    requests.post(
        f"{BASE_URL}/api/chats/{chat_id}/messages",
        headers=headers,
        json={
            "user_id": "user1-uuid",
            "content": "Hey! Nice to meet you!"
        }
    )
    """)


# Example 6: Testing real-time updates
def example_6_realtime_test():
    """
    Testing real-time message updates
    """
    print("""
    // Open two instances of the app (or web/mobile combo)
    // Login as different users who are connected
    
    // Instance 1 (User A):
    @Composable
    fun TestRealtime() {
        val viewModel: ChatViewModel = viewModel()
        val userId = "user-a-id"
        val chatId = "shared-chat-id"
        
        LaunchedEffect(Unit) {
            viewModel.setCurrentUser(userId)
            viewModel.loadChatMessages(chatId)
        }
        
        val messagesState by viewModel.chatMessagesState.collectAsState()
        
        // Watch the messages list update in real-time
        when (val state = messagesState) {
            is ChatMessagesState.Success -> {
                LazyColumn {
                    items(state.messages) { msg ->
                        Text("${msg.user.name}: ${msg.message.content}")
                    }
                }
            }
        }
    }
    
    // Instance 2 (User B):
    // Send a message from User B's UI
    // Watch it appear instantly in User A's chat without refreshing!
    """)


# Example 7: Error handling
def example_7_error_handling():
    """
    Proper error handling in chat operations
    """
    print("""
    @Composable
    fun RobustChatScreen(userId: String, chatId: String) {
        val viewModel: ChatViewModel = viewModel()
        val messagesState by viewModel.chatMessagesState.collectAsState()
        var showError by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        
        LaunchedEffect(userId, chatId) {
            try {
                viewModel.setCurrentUser(userId)
                viewModel.loadChatMessages(chatId)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unknown error"
                showError = true
            }
        }
        
        if (showError) {
            AlertDialog(
                onDismissRequest = { showError = false },
                title = { Text("Error") },
                text = { Text(errorMessage) },
                confirmButton = {
                    Button(onClick = {
                        showError = false
                        viewModel.loadChatMessages(chatId)
                    }) {
                        Text("Retry")
                    }
                }
            )
        }
        
        // Rest of your UI...
    }
    """)


if __name__ == "__main__":
    print("=== Click Chat Implementation Examples ===\n")

    print("\n1. Basic Usage:")
    example_1_basic_usage()

    print("\n2. Advanced Usage with ViewModel:")
    example_2_advanced_usage()

    print("\n3. Python API Usage:")
    example_3_python_api()

    print("\n4. Direct Supabase Operations:")
    example_4_direct_supabase()

    print("\n5. Creating Connection with Chat:")
    example_5_create_connection()

    print("\n6. Testing Real-time Updates:")
    example_6_realtime_test()

    print("\n7. Error Handling:")
    example_7_error_handling()

