package compose.project.click.click.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.text.style.TextAlign
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
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.IcebreakerPrompt
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
    searchQuery: String = "",
    initialChatId: String? = null,
    onChatDismissed: (() -> Unit)? = null,
    viewModel: ChatViewModel = viewModel { ChatViewModel() }
) {
    var selectedChatId by remember { mutableStateOf(initialChatId) }

    LaunchedEffect(initialChatId) {
        if (initialChatId != null) {
            selectedChatId = initialChatId
            viewModel.loadChatMessages(initialChatId)
        }
    }

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
            searchQuery = searchQuery,
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
                onChatDismissed?.invoke()
            }
        )
    }
}


@Composable
fun ConnectionsListView(
    viewModel: ChatViewModel,
    searchQuery: String = "",
    onChatSelected: (String) -> Unit
) {
    val chatListState by viewModel.chatListState.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                when (val state = chatListState) {
                    is ChatListState.Success -> {
                        val filteredCount = if (searchQuery.isBlank()) {
                            state.chats.size
                        } else {
                            state.chats.count { chat ->
                                chat.otherUser?.name?.contains(searchQuery, ignoreCase = true) == true
                            }
                        }
                        PageHeader(
                            title = "Clicks",
                            subtitle = if (searchQuery.isNotBlank()) {
                                "$filteredCount result${if (filteredCount == 1) "" else "s"} for \"$searchQuery\""
                            } else {
                                "${state.chats.size} ${if (state.chats.size == 1) "connection" else "connections"}"
                            }
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
                    // Filter chats based on search query
                    val filteredChats = if (searchQuery.isBlank()) {
                        state.chats
                    } else {
                        state.chats.filter { chat ->
                            chat.otherUser?.name?.contains(searchQuery, ignoreCase = true) == true
                        }
                    }
                    
                    if (filteredChats.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (searchQuery.isNotBlank()) Icons.Filled.SearchOff else Icons.Filled.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) "No matches found" else "No connections yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) "Try a different search term" else "Start clicking with people nearby!",
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
                            items(filteredChats, key = { it.connection.id }) { chatDetails ->
                                ConnectionItem(
                                    chatDetails = chatDetails,
                                    onClick = { onChatSelected(chatDetails.connection.id) }
                                )
                                if (chatDetails != filteredChats.last()) {
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
            // Avatar with brand gradient
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(
                        Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue))
                    )
                    .border(
                        width = 1.5.dp,
                        color = PrimaryBlue.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(27.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    user.name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
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
    
    // Icebreaker prompts state
    val icebreakerPrompts by viewModel.icebreakerPrompts.collectAsState()
    val showIcebreakerPanel by viewModel.showIcebreakerPanel.collectAsState()

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
                    AdaptiveSurface(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                        Box(modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 8.dp)) {
                            PageHeader(
                                title = "Loading...",
                                navigationIcon = {
                                    IconButton(onClick = onBackPressed) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                is ChatMessagesState.Error -> {
                    AdaptiveSurface(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                        Box(modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 8.dp)) {
                            PageHeader(
                                title = "Chat",
                                navigationIcon = {
                                    IconButton(onClick = onBackPressed) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            )
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
                    AdaptiveSurface(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 8.dp)) {
                                PageHeader(
                                    title = chatDetails.otherUser.name ?: "Unknown",
                                    subtitle = "Active in this Click",
                                    navigationIcon = {
                                        IconButton(onClick = onBackPressed) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    actions = {
                                        // Avatar with brand gradient
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(19.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(PrimaryBlue, LightBlue)
                                                    )
                                                )
                                                .border(
                                                    width = 1.5.dp,
                                                    color = PrimaryBlue.copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(19.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                chatDetails.otherUser.name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(onClick = { searchOpen = !searchOpen }) {
                                            Icon(
                                                Icons.Filled.Search,
                                                contentDescription = "Search",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                )
                            }

                            if (searchOpen) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
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
                    
                    // Icebreaker Prompts Panel
                    if (showIcebreakerPanel && icebreakerPrompts.isNotEmpty() && messages.size < 5) {
                        IcebreakerPanel(
                            prompts = icebreakerPrompts,
                            onPromptClick = { prompt -> viewModel.useIcebreakerPrompt(prompt) },
                            onRefresh = { viewModel.refreshIcebreakerPrompts() },
                            onDismiss = { viewModel.dismissIcebreakerPanel() }
                        )
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

                    // Typing indicator — rendered as a chat bubble for polish
                    AnimatedVisibility(
                        visible = typingUsers.isNotEmpty(),
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 80.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = PrimaryBlue.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
                                    )
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${typingUsers.first()} is typing…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
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

                    // Input bar — floats above nav bar, rises with keyboard
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = {
                                    viewModel.updateMessageInput(it)
                                    viewModel.onUserTyping(chatId)
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        "Message ${chatDetails.otherUser.name}…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue.copy(alpha = 0.65f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                maxLines = 5
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Gradient send button
                            val canSend = messageInput.trim().isNotEmpty()
                            val sendGradient = Brush.linearGradient(
                                colors = if (canSend) listOf(PrimaryBlue, LightBlue)
                                         else listOf(
                                             MaterialTheme.colorScheme.surfaceVariant,
                                             MaterialTheme.colorScheme.surfaceVariant
                                         )
                            )
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(23.dp))
                                    .background(sendGradient)
                                    .then(
                                        if (canSend) Modifier.clickable { viewModel.sendMessage() }
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (canSend) Color.White
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(20.dp)
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

    // Gradient for sent bubbles — matches brand violet palette
    val sentGradient = Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue))

    // Tail corner is slightly squared, opposite corners fully rounded
    val sentShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp)
    val receivedShape = RoundedCornerShape(topStart = 5.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        if (isSent) {
            // ── Sent bubble: violet gradient ─────────────────────────────────
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(sentShape)
                    .background(sentGradient)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.content,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = formatMessageTime(message.timeCreated),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        } else {
            // ── Received bubble: glass-style surface ──────────────────────────
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .border(width = 1.dp, color = PrimaryBlue.copy(alpha = 0.18f), shape = receivedShape)
                    .clip(receivedShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = formatMessageTime(message.timeCreated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
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

/**
 * Format remaining milliseconds as MM:SS for the Vibe Check timer.
 */
private fun formatVibeCheckTime(remainingMs: Long): String {
    val totalSeconds = remainingMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/**
 * Vibe Check Banner - Shows the countdown timer, context tag, and keep button.
 * Displays different states based on timer status and user decisions.
 */
@Composable
fun VibeCheckBanner(
    connection: Connection,
    remainingMs: Long,
    currentUserHasKept: Boolean,
    otherUserHasKept: Boolean,
    vibeCheckExpired: Boolean,
    connectionKept: Boolean,
    onKeepClick: () -> Unit,
    onExpiredDismiss: () -> Unit
) {
    val isTimerActive = remainingMs > 0 && !connectionKept
    val isWarning = remainingMs in 1..300_000 // Last 5 minutes
    
    // Determine banner color
    val bannerColor = when {
        connectionKept -> PrimaryBlue.copy(alpha = 0.15f)
        vibeCheckExpired && !connectionKept -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        else -> PrimaryBlue.copy(alpha = 0.1f)
    }
    
    val bannerBorderColor = when {
        connectionKept -> PrimaryBlue
        vibeCheckExpired && !connectionKept -> MaterialTheme.colorScheme.error
        isWarning -> MaterialTheme.colorScheme.error
        else -> PrimaryBlue.copy(alpha = 0.5f)
    }
    
    // Show expiry dialog
    if (vibeCheckExpired && !connectionKept) {
        AlertDialog(
            onDismissRequest = { },
            icon = {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Vibe Check Complete",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (currentUserHasKept && !otherUserHasKept) {
                            "Unfortunately, the other person didn't choose to keep this connection. The chat will be deleted."
                        } else if (!currentUserHasKept && otherUserHasKept) {
                            "You didn't choose to keep this connection. The chat will be deleted."
                        } else {
                            "Neither of you chose to keep this connection. The chat will be deleted."
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onExpiredDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("OK")
                }
            }
        )
    }
    
    // Show mutual keep celebration dialog
    if (connectionKept && !vibeCheckExpired) {
        // This is shown briefly - the banner will show "Connection Kept!" instead
    }
    
    // Banner
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = bannerColor,
        border = BorderStroke(1.dp, bannerBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Context tag (if available)
            connection.context_tag?.let { tag ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            when {
                connectionKept -> {
                    // Connection kept! 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Connection Kept!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                    Text(
                        "You both chose to continue this connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                isTimerActive -> {
                    // Active timer — show pending or vibe check timer
                    val isPending = connection.isPending()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Timer display
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isPending) Icons.Filled.EmojiPeople else Icons.Filled.Timer,
                                contentDescription = null,
                                tint = if (isWarning) MaterialTheme.colorScheme.error else PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    if (isPending) "Say Hi" else "Vibe Check",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatVibeCheckTime(remainingMs),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        
                        // Keep button and status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Other user status indicator
                            if (otherUserHasKept) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PrimaryBlue.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = PrimaryBlue
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "They want to keep!",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PrimaryBlue,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            
                            // Keep button
                            Button(
                                onClick = onKeepClick,
                                enabled = !currentUserHasKept,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentUserHasKept) PrimaryBlue.copy(alpha = 0.5f) else PrimaryBlue,
                                    disabledContainerColor = PrimaryBlue.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                if (currentUserHasKept) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kept!")
                                } else {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Keep")
                                }
                            }
                        }
                    }
                    
                    // Help text
                    Text(
                        if (currentUserHasKept) {
                            "Waiting for ${if (otherUserHasKept) "mutual confirmation" else "them to decide"}..."
                        } else {
                            "Click 'Keep' if you want to continue this connection"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                else -> {
                    // Expired state (dialog handles main interaction)
                    Text(
                        "Time's up!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Icebreaker Panel - Shows conversation starters to help break the ice.
 * Displays tappable prompts that users can click to auto-fill the message input.
 */
@Composable
fun IcebreakerPanel(
    prompts: List<IcebreakerPrompt>,
    onPromptClick: (IcebreakerPrompt) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = PrimaryBlue.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Conversation Starters",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Get new prompts",
                            modifier = Modifier.size(18.dp),
                            tint = PrimaryBlue.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Prompt chips styled with violet accent
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                prompts.forEach { prompt ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PrimaryBlue.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .clickable { onPromptClick(prompt) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Violet accent dot
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(PrimaryBlue.copy(alpha = 0.6f))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                prompt.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Tap a prompt to use it",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryBlue.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
