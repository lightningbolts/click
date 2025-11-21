package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.AdaptiveSurface
import compose.project.click.click.ui.components.PageHeader
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.coerceAtLeast
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.viewmodel.ChatViewModel
import compose.project.click.click.viewmodel.ChatListState
import compose.project.click.click.viewmodel.ChatMessagesState
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue

@Composable
fun ConnectionsScreen(
    userId: String,
    viewModel: ChatViewModel = viewModel { ChatViewModel() }
) {
    var selectedChatId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        viewModel.setCurrentUser(userId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.leaveChatRoom()
        }
    }

    if (selectedChatId == null) {
        ConnectionsListView(
            viewModel = viewModel,
            onChatSelected = { chatId ->
                selectedChatId = chatId
                viewModel.loadChatMessages(chatId)
            }
        )
    } else {
        ChatView(
            viewModel = viewModel,
            chatId = selectedChatId!!,
            onBackPressed = {
                selectedChatId = null
                viewModel.leaveChatRoom()
                viewModel.loadChats() // Refresh chat list
            }
        )
    }
}


@Composable
fun ConnectionsListView(
    viewModel: ChatViewModel,
    onChatSelected: (String) -> Unit
) {
    val chatListState by viewModel.chatListState.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerTop = if (topInset > 32.dp) topInset - 32.dp else 0.dp

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = headerTop, end = 20.dp)) {
                when (val state = chatListState) {
                    is ChatListState.Success -> {
                        PageHeader(
                            title = "Clicks",
                            subtitle = "${state.chats.size} ${if (state.chats.size == 1) "connection" else "connections"}"
                        )
                    }
                    else -> {
                        PageHeader(title = "Clicks", subtitle = "Loading...")
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            when (val state = chatListState) {
                is ChatListState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ChatListState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Error loading chats",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadChats() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is ChatListState.Success -> {
                    if (state.chats.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No connections yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Start clicking with people nearby!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.chats, key = { it.connection.id }) { chatDetails ->
                                ConnectionItem(
                                    chatDetails = chatDetails,
                                    onClick = { onChatSelected(chatDetails.connection.id) }
                                )
                                if (chatDetails != state.chats.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ConnectionItem(chatDetails: ChatWithDetails, onClick: () -> Unit) {
    val user = chatDetails.otherUser
    val lastMessage = chatDetails.lastMessage
    val unreadCount = chatDetails.unreadCount
    val timeText = lastMessage?.let { formatTimestamp(it.timeCreated) } ?: "No messages"

    AdaptiveCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        user.name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        user.name ?: "Unknown",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        lastMessage?.content ?: "Start a conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                unreadCount.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(viewModel: ChatViewModel, chatId: String, onBackPressed: () -> Unit) {
    val chatMessagesState by viewModel.chatMessagesState.collectAsState()
    val messageInput by viewModel.messageInput.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val chatListState by viewModel.chatListState.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var forwardMessageId by remember { mutableStateOf<String?>(null) }

    // Start typing monitoring for this chat
    LaunchedEffect(chatId) {
        viewModel.startTypingMonitoring(chatId)
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(chatMessagesState) {
        if (chatMessagesState is ChatMessagesState.Success) {
            val messages = (chatMessagesState as ChatMessagesState.Success).messages
            if (messages.isNotEmpty()) {
                coroutineScope.launch { listState.animateScrollToItem(messages.size - 1) }
            }
        }
    }

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (val state = chatMessagesState) {
                is ChatMessagesState.Loading -> {
                    // Header
                    AdaptiveSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackPressed) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                "Loading...",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                is ChatMessagesState.Error -> {
                    AdaptiveSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackPressed) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Error loading chat",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is ChatMessagesState.Success -> {
                    val chatDetails = state.chatDetails
                    val messages = state.messages

                    // Header
                    AdaptiveSurface(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onBackPressed) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.primaryContainer
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        chatDetails.otherUser.name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        chatDetails.otherUser.name ?: "Unknown",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        chatDetails.otherUser.email ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(onClick = { searchOpen = !searchOpen }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search")
                                }
                            }

                            if (searchOpen) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        placeholder = { Text("Search messages") }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = {
                                        if (searchQuery.isNotBlank()) {
                                            viewModel.searchMessages(chatId, searchQuery)
                                        }
                                    }) { Text("Go") }
                                    Spacer(Modifier.width(4.dp))
                                    TextButton(onClick = {
                                        searchOpen = false
                                        searchQuery = ""
                                        // reload chat messages
                                        viewModel.loadChatMessages(chatId)
                                    }) { Text("Clear") }
                                }
                            }
                        }
                    }

                    // Messages
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No messages yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Say hi to ${chatDetails.otherUser.name}!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.message.id }) { messageWithUser ->
                                ChatMessageBubble(
                                    messageWithUser = messageWithUser,
                                    currentUserId = viewModel.currentUserId.collectAsState().value,
                                    onToggleReaction = { reaction ->
                                        // TODO: Re-implement reactions with separate table
                                        // Reactions removed temporarily since Message model was updated
                                    },
                                    onForward = { msgId ->
                                        forwardMessageId = msgId
                                    }
                                )
                            }
                        }
                    }

                    // Typing indicator
                    if (typingUsers.isNotEmpty()) {
                        Text(
                            text = typingUsers.joinToString(", ") + " is typing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                        )
                    }

                    // Forward dialog
                    if (forwardMessageId != null) {
                        ForwardDialog(
                            chatListState = chatListState,
                            currentChatId = chatId,
                            onSelect = { targetChatId ->
                                val msgId = forwardMessageId
                                if (msgId != null) {
                                    viewModel.forwardMessage(msgId, targetChatId)
                                }
                                forwardMessageId = null
                            },
                            onDismiss = { forwardMessageId = null }
                        )
                    }

                    // Input
                    AdaptiveSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = {
                                    viewModel.updateMessageInput(it)
                                    viewModel.onUserTyping(chatId)
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Message ${chatDetails.otherUser.name}") },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 5
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { viewModel.sendMessage() },
                                enabled = messageInput.trim().isNotEmpty(),
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (messageInput.trim().isNotEmpty())
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(24.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (messageInput.trim().isNotEmpty())
                                        Color.White
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardDialog(
    chatListState: ChatListState,
    currentChatId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward to...") },
        text = {
            when (chatListState) {
                is ChatListState.Success -> {
                    val options = chatListState.chats.filter { it.connection.id != currentChatId }
                    if (options.isEmpty()) Text("No other chats available")
                    else LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(options, key = { it.connection.id }) { item ->
                            ListItem(
                                headlineContent = { Text(item.otherUser.name ?: "Unknown") },
                                supportingContent = { Text(item.otherUser.email ?: "") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp)
                                    .clickable {
                                        onSelect(item.connection.id)
                                    }
                            )
                        }
                    }
                }
                is ChatListState.Loading -> { CircularProgressIndicator() }
                is ChatListState.Error -> { Text("Failed to load chats") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ChatMessageBubble(
    messageWithUser: MessageWithUser,
    currentUserId: String?,
    onToggleReaction: (String) -> Unit,
    onForward: (String) -> Unit
) {
    val message = messageWithUser.message
    val isSent = messageWithUser.isSent

    // TODO: Re-implement reactions with separate table
    val grouped = emptyMap<String, List<Any>>()
    val commonReactions = listOf("👍", "❤️", "😂")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isSent) Alignment.End else Alignment.Start) {
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isSent) 16.dp else 4.dp,
                    bottomEnd = if (isSent) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        message.content,
                        color = if (isSent) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatMessageTime(message.timeCreated),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSent) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // TODO: Add message status back when we have that in the schema
                    }
                }
            }

            // Actions row (reactions temporarily disabled)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                // Forward action
                TextButton(onClick = { onForward(message.id) }) { Text("Forward") }
            }
        }
    }
}

// Utility function to format timestamps
private fun formatTimestamp(timestamp: Long): String {
    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> "${diff / 604800_000}w ago"
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    val hour = dateTime.hour
    val minute = dateTime.minute.toString().padStart(2, '0')
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    return "$displayHour:$minute $amPm"
}
