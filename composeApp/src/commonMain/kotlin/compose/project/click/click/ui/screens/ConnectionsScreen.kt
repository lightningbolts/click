package compose.project.click.click.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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

    AnimatedContent(
        targetState = selectedChatId,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(initialOffsetX = { it }) + fadeIn())
                    .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut())
            } else {
                (slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn())
                    .togetherWith(slideOutHorizontally(targetOffsetX = { it }) + fadeOut())
            }
        },
        label = "chat_open_close_transition"
    ) { activeChatId ->
        if (activeChatId == null) {
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
                chatId = activeChatId,
                onBackPressed = {
                    selectedChatId = null
                    viewModel.leaveChatRoom()
                    viewModel.loadChats()
                    onChatDismissed?.invoke()
                }
            )
        }
    }
}


@Composable
fun ConnectionsListView(
    viewModel: ChatViewModel,
    searchQuery: String = "",
    onChatSelected: (String) -> Unit
) {
    val chatListState by viewModel.chatListState.collectAsState()
    val archivedConnectionIds by viewModel.archivedConnectionIds.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableStateOf(0) } // 0 = Active, 1 = Archived

    // Connection menu state: holds the chatWithDetails for which the menu is open
    var pendingMenuChat by remember { mutableStateOf<ChatWithDetails?>(null) }

    // Show nudge feedback
    LaunchedEffect(nudgeResult) {
        val result = nudgeResult
        if (result != null) {
            snackbarHostState.showSnackbar(result)
            viewModel.clearNudgeResult()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                when (val state = chatListState) {
                    is ChatListState.Success -> {
                        val activeChats = state.chats.filter { it.connection.id !in archivedConnectionIds }
                        val archivedChats = state.chats.filter { it.connection.id in archivedConnectionIds }
                        val tabChats = if (selectedTabIndex == 0) activeChats else archivedChats
                        val filteredCount = if (searchQuery.isBlank()) {
                            tabChats.size
                        } else {
                            tabChats.count { chat ->
                                chat.otherUser?.name?.contains(searchQuery, ignoreCase = true) == true
                            }
                        }
                        val tabLabel = if (selectedTabIndex == 0) "active" else "archived"
                        PageHeader(
                            title = "Clicks",
                            subtitle = if (searchQuery.isNotBlank()) {
                                "$filteredCount result${if (filteredCount == 1) "" else "s"} for \"$searchQuery\""
                            } else {
                                "$filteredCount $tabLabel ${if (filteredCount == 1) "connection" else "connections"}"
                            }
                        )
                    }
                    else -> {
                        PageHeader(title = "Clicks", subtitle = "Loading…")
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (chatListState is ChatListState.Success) {
                val successState = chatListState as ChatListState.Success
                val activeCount = successState.chats.count { it.connection.id !in archivedConnectionIds }
                val archivedCount = successState.chats.count { it.connection.id in archivedConnectionIds }

                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) }
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Active ($activeCount)") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Archived ($archivedCount)") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

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
                    val activeChats = state.chats.filter { it.connection.id !in archivedConnectionIds }
                    val archivedChats = state.chats.filter { it.connection.id in archivedConnectionIds }
                    val tabChats = if (selectedTabIndex == 0) activeChats else archivedChats

                    val filteredChats = if (searchQuery.isBlank()) {
                        tabChats
                    } else {
                        tabChats.filter { chat ->
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
                                    if (searchQuery.isNotBlank()) "No matches found"
                                    else if (selectedTabIndex == 1) "No archived connections"
                                    else "No connections yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) "Try a different search term"
                                    else if (selectedTabIndex == 1) "Archived chats will appear here"
                                    else "Start clicking with people nearby!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredChats, key = { it.connection.id }) { chatDetails ->
                                ConnectionItem(
                                    chatDetails = chatDetails,
                                    onClick = { onChatSelected(chatDetails.connection.id) },
                                    onNudge = {
                                        val chatId = chatDetails.chat.id
                                        if (chatId != null) {
                                            viewModel.sendNudgeToChat(chatId, chatDetails.otherUser.name ?: "them")
                                        }
                                    },
                                    onOpenMenu = { pendingMenuChat = chatDetails },
                                    onLongPress = { pendingMenuChat = chatDetails }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    )

    // Connection action sheet
    if (pendingMenuChat != null) {
        val selected = pendingMenuChat!!
        val isArchived = selected.connection.id in archivedConnectionIds
        ConnectionActionSheet(
            chatDetails = selected,
            isArchived = isArchived,
            onDismiss = { pendingMenuChat = null },
            onNudge = {
                val selected = pendingMenuChat ?: return@ConnectionActionSheet
                val chatId = selected.chat.id
                if (chatId != null) {
                    viewModel.sendNudgeToChat(chatId, selected.otherUser.name ?: "them")
                }
            },
            onOpenChat = {
                val selected = pendingMenuChat ?: return@ConnectionActionSheet
                onChatSelected(selected.connection.id)
            },
            onArchive = {
                val selected = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.archiveConnectionById(selected.connection.id) { }
            },
            onUnarchive = {
                val selected = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.unarchiveConnection(selected.connection.id)
            },
            onDelete = {
                val selected = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.deleteConnectionPermanentlyById(selected.connection.id) { }
            },
            onReport = { reason ->
                val selected = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.reportConnectionForConnection(selected.connection.id, reason) { }
            },
            onBlock = {
                val selected = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.blockUserForConnection(selected.connection.id) { }
            }
        )
    }
    } // End outer Box
}


@Composable
fun ConnectionItem(
    chatDetails: ChatWithDetails,
    onClick: () -> Unit,
    onNudge: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val user = chatDetails.otherUser
    val lastMessage = chatDetails.lastMessage
    val unreadCount = chatDetails.unreadCount
    val timeText = lastMessage?.let { formatTimestamp(it.timeCreated) } ?: "No messages"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                user.name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    user.name ?: "Unknown",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    lastMessage?.content ?: "Start a conversation",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue)),
                                RoundedCornerShape(10.dp)
                            ),
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

        // Nudge button
        IconButton(
            onClick = onNudge,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = "Nudge",
                modifier = Modifier.size(18.dp),
                tint = PrimaryBlue.copy(alpha = 0.7f)
            )
        }

        // Overflow menu
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "More options",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val editingMessageId by viewModel.editingMessageId.collectAsState()
    val nudgeResult by viewModel.nudgeResult.collectAsState()

    // Icebreaker prompts state
    val icebreakerPrompts by viewModel.icebreakerPrompts.collectAsState()
    val showIcebreakerPanel by viewModel.showIcebreakerPanel.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeStartEdgePx = remember(density) { with(density) { 28.dp.toPx() } }
    val swipeDismissThresholdPx = remember(density) { with(density) { 88.dp.toPx() } }
    var isEdgeSwiping by remember(chatId) { mutableStateOf(false) }
    var horizontalDragDistance by remember(chatId) { mutableStateOf(0f) }

    // Connection action sheet (archive, delete, report, block)
    var showConnectionSheet by remember { mutableStateOf(false) }
    // Message context sheet (reactions, edit, delete, copy)
    var contextMenuMessage by remember { mutableStateOf<MessageWithUser?>(null) }
    var forwardMessageId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show nudge snackbar
    LaunchedEffect(nudgeResult) {
        val r = nudgeResult
        if (r != null) {
            coroutineScope.launch { snackbarHostState.showSnackbar(r) }
            viewModel.clearNudgeResult()
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(chatId) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isEdgeSwiping = offset.x <= swipeStartEdgePx
                        horizontalDragDistance = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isEdgeSwiping) {
                            horizontalDragDistance = (horizontalDragDistance + dragAmount).coerceAtLeast(0f)
                        }
                    },
                    onDragCancel = {
                        isEdgeSwiping = false
                        horizontalDragDistance = 0f
                    },
                    onDragEnd = {
                        if (isEdgeSwiping && horizontalDragDistance >= swipeDismissThresholdPx) {
                            onBackPressed()
                        }
                        isEdgeSwiping = false
                        horizontalDragDistance = 0f
                    }
                )
            }
    ) {
    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (val state = chatMessagesState) {
                is ChatMessagesState.Loading -> {
                    // Compact header while loading
                    Surface(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackPressed) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Loading…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading messages…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ChatMessagesState.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackPressed) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is ChatMessagesState.Success -> {
                    val chatDetails = state.chatDetails
                    val messages = state.messages
                    val typingLabel = remember(typingUsers, chatDetails.otherUser.id, chatDetails.otherUser.name) {
                        val displayNames = typingUsers.map { userId ->
                            if (userId == chatDetails.otherUser.id) chatDetails.otherUser.name ?: "Someone"
                            else "Someone"
                        }.distinct()
                        when (displayNames.size) {
                            0 -> ""
                            1 -> "${displayNames.first()} is typing…"
                            2 -> "${displayNames[0]} and ${displayNames[1]} are typing…"
                            else -> "${displayNames.first()} and ${displayNames.size - 1} others are typing…"
                        }
                    }

                    // Header — compact single row (~56dp)
                    Surface(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onBackPressed) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(PrimaryBlue, LightBlue)
                                            )
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = PrimaryBlue.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(18.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        chatDetails.otherUser.name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Name + status
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chatDetails.otherUser.name ?: "Unknown",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Active in this Click",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Nudge button
                                IconButton(onClick = { viewModel.sendNudge() }) {
                                    Icon(
                                        Icons.Filled.Notifications,
                                        contentDescription = "Nudge",
                                        tint = PrimaryBlue.copy(alpha = 0.85f)
                                    )
                                }
                                // Overflow / connection options
                                IconButton(onClick = { showConnectionSheet = true }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "More options",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
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
                    if (messages.isEmpty() && state.isLoadingMessages) {
                        // Messages loading in background — show subtle spinner
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    } else if (messages.isEmpty()) {
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
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(messages, key = { it.message.id }) { messageWithUser ->
                                val reactionsMap = viewModel.messageReactions.collectAsState().value
                                val msgReactions = reactionsMap[messageWithUser.message.id] ?: emptyList()
                                ChatMessageBubble(
                                    messageWithUser = messageWithUser,
                                    currentUserId = viewModel.currentUserId.collectAsState().value,
                                    reactions = msgReactions,
                                    onToggleReaction = { reaction ->
                                        viewModel.toggleReaction(messageWithUser.message.id, reaction)
                                    },
                                    onForward = { msgId ->
                                        forwardMessageId = msgId
                                    },
                                    onLongPress = { contextMenuMessage = messageWithUser }
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
                                    text = typingLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic
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

                    // Edit mode indicator strip
                    if (editingMessageId != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = PrimaryBlue.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = PrimaryBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Editing message",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PrimaryBlue
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.cancelEditMessage() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Cancel edit",
                                        modifier = Modifier.size(16.dp),
                                        tint = PrimaryBlue
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = {
                                viewModel.updateMessageInput(it)
                                viewModel.onUserTyping(chatId)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            placeholder = {
                                Text(
                                    if (editingMessageId != null) "Edit message…"
                                    else "Message ${chatDetails.otherUser.name}…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue.copy(alpha = 0.65f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.width(8.dp))

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
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(sendGradient)
                                .then(
                                    if (canSend) Modifier.clickable { viewModel.sendMessage() }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (editingMessageId != null) Icons.Filled.Check
                                else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (editingMessageId != null) "Confirm edit" else "Send",
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

    // Snackbar overlay
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 80.dp) // above input bar
    )

    // Message long-press context sheet
    if (contextMenuMessage != null) {
        MessageActionSheet(
            messageWithUser = contextMenuMessage!!,
            viewModel = viewModel,
            onDismiss = { contextMenuMessage = null }
        )
    }

    // Connection action sheet
    if (showConnectionSheet) {
        val successState = chatMessagesState as? ChatMessagesState.Success
        ConnectionActionSheet(
            chatDetails = successState?.chatDetails,
            onDismiss = { showConnectionSheet = false },
            onNudge = {
                viewModel.sendNudge()
            },
            onOpenChat = { },
            onArchive = {
                viewModel.archiveConnection { success ->
                    if (success) onBackPressed()
                }
            },
            onDelete = {
                viewModel.deleteConnectionPermanently { success ->
                    if (success) onBackPressed()
                }
            },
            onReport = { reason ->
                viewModel.reportConnection(reason) { }
            },
            onBlock = {
                viewModel.blockUser { success ->
                    if (success) onBackPressed()
                }
            }
        )
    }
    } // End outer Box
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
    reactions: List<compose.project.click.click.data.models.MessageReaction> = emptyList(),
    onToggleReaction: (String) -> Unit = {},
    onForward: (String) -> Unit,
    onLongPress: (MessageWithUser) -> Unit = {}
) {
    val message = messageWithUser.message
    val isSent = messageWithUser.isSent

    // Gradient for sent bubbles — matches brand violet palette
    val sentGradient = Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue))

    // Tail corner is slightly squared, opposite corners fully rounded
    val sentShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp)
    val receivedShape = RoundedCornerShape(topStart = 5.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    // Group reactions by type: emoji → count
    val reactionGroups = reactions.groupBy { it.reactionType }
        .mapValues { (_, list) -> list.size }
        .entries
        .sortedByDescending { it.value }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
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
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPress(messageWithUser) }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = message.content,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // "(edited)" label for edited messages
                        if (message.timeEdited != null) {
                            Text(
                                text = "(edited)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatMessageTime(message.timeCreated),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.align(Alignment.End)
                        )
                        Text(
                            text = if (message.isRead) "Read" else "Sent",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
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
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPress(messageWithUser) }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = message.content,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // "(edited)" label for edited messages
                        if (message.timeEdited != null) {
                            Text(
                                text = "(edited)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
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

        // ── Reaction chips row below the bubble ────────────────────────────────
        if (reactionGroups.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                reactionGroups.forEach { (emoji, count) ->
                    val isOwnReaction = reactions.any { it.reactionType == emoji && it.userId == currentUserId }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isOwnReaction) PrimaryBlue.copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isOwnReaction) PrimaryBlue.copy(alpha = 0.5f)
                                else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onToggleReaction(emoji) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet that appears when a user long-presses a message.
 * Shows emoji reactions strip + contextual actions (copy, edit, delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    messageWithUser: MessageWithUser,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val isSent = messageWithUser.isSent
    val message = messageWithUser.message

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showDeleteMessageConfirm by remember { mutableStateOf(false) }
    var showDeleteMessageFinalConfirm by remember { mutableStateOf(false) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // ── Emoji reaction strip ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "😡")
                emojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .clickable {
                                viewModel.addReaction(message.id, emoji)
                                dismiss()
                            }
                            .padding(8.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // ── Copy action ───────────────────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text("Copy", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(message.content))
                    dismiss()
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // ── Edit action (sent messages only) ──────────────────────────────
            if (isSent) {
                ListItem(
                    headlineContent = {
                        Text("Edit", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit message",
                            tint = LightBlue
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.startEditMessage(message.id, message.content)
                        dismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                // ── Delete action (sent messages only) ────────────────────────
                ListItem(
                    headlineContent = {
                        Text("Delete", color = Color(0xFFFF4444), style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete message",
                            tint = Color(0xFFFF4444)
                        )
                    },
                    modifier = Modifier.clickable {
                        showDeleteMessageConfirm = true
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    // ── Delete message confirmation dialog ──────────────────────────────────
    if (showDeleteMessageConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteMessageConfirm = false },
            title = { Text("Delete Message?", color = Color.White) },
            text = {
                Text(
                    "This message will be permanently deleted. This cannot be undone.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteMessageConfirm = false
                    showDeleteMessageFinalConfirm = true
                }) {
                    Text("Delete", color = Color(0xFFFF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMessageConfirm = false }) {
                    Text("Cancel", color = LightBlue)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // ── Final delete confirmation dialog ───────────────────────────────────
    if (showDeleteMessageFinalConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteMessageFinalConfirm = false },
            title = { Text("Delete Message Permanently?", color = Color.White) },
            text = {
                Text(
                    "This action is permanent and cannot be undone.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(message.id)
                    showDeleteMessageFinalConfirm = false
                    dismiss()
                }) {
                    Text("Yes, Delete", color = Color(0xFFFF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMessageFinalConfirm = false }) {
                    Text("Cancel", color = LightBlue)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

/**
 * Bottom sheet for connection-level actions (nudge, archive, remove, report, block).
 * Used both from the Connections list overflow menu and from within an open chat.
 * All actions are full callbacks so this composable stays ViewModel-agnostic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionActionSheet(
    chatDetails: ChatWithDetails?,
    isArchived: Boolean = false,
    onDismiss: () -> Unit,
    onNudge: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onArchive: () -> Unit = {},
    onUnarchive: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReport: (String) -> Unit = {},
    onBlock: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showUnarchiveConfirm by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var showFinalConfirm by remember { mutableStateOf(false) }
    var finalConfirmTitle by remember { mutableStateOf("") }
    var finalConfirmBody by remember { mutableStateOf("") }
    var finalConfirmButtonLabel by remember { mutableStateOf("") }
    var finalConfirmButtonColor by remember { mutableStateOf(Color.White) }
    var finalConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun openFinalConfirm(
        title: String,
        body: String,
        buttonLabel: String,
        buttonColor: Color,
        action: () -> Unit
    ) {
        finalConfirmTitle = title
        finalConfirmBody = body
        finalConfirmButtonLabel = buttonLabel
        finalConfirmButtonColor = buttonColor
        finalConfirmAction = action
        showFinalConfirm = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Connection name header
            chatDetails?.let { details ->
                Text(
                    text = details.otherUser.name ?: "Connection",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .align(Alignment.CenterHorizontally)
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }

            // ── Nudge ──────────────────────────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text("Nudge 👋", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                },
                supportingContent = {
                    Text("Send a quick ping", color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall)
                },
                modifier = Modifier.clickable {
                    onNudge()
                    dismiss()
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            if (isArchived) {
                ListItem(
                    headlineContent = {
                        Text("Unarchive", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    },
                    supportingContent = {
                        Text("Move this connection back to Active", color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Unarchive, contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f))
                    },
                    modifier = Modifier.clickable {
                        showUnarchiveConfirm = true
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            } else {
                // ── Archive ────────────────────────────────────────────────────
                ListItem(
                    headlineContent = {
                        Text("Archive", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    },
                    supportingContent = {
                        Text("Hide this connection (recoverable)", color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Archive, contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f))
                    },
                    modifier = Modifier.clickable {
                        showArchiveConfirm = true
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // ── Remove Connection ──────────────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text("Remove Connection", color = Color(0xFFFF4444),
                        style = MaterialTheme.typography.bodyLarge)
                },
                leadingContent = {
                    Icon(Icons.Default.PersonRemove, contentDescription = null,
                        tint = Color(0xFFFF4444))
                },
                modifier = Modifier.clickable { showDeleteConfirm = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // ── Report ─────────────────────────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text("Report", color = Color(0xFFFF8C00),
                        style = MaterialTheme.typography.bodyLarge)
                },
                leadingContent = {
                    Icon(Icons.Default.Flag, contentDescription = null,
                        tint = Color(0xFFFF8C00))
                },
                modifier = Modifier.clickable { showReportDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // ── Block ──────────────────────────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text("Block", color = Color(0xFFFF4444),
                        style = MaterialTheme.typography.bodyLarge)
                },
                leadingContent = {
                    Icon(Icons.Default.Block, contentDescription = null,
                        tint = Color(0xFFFF4444))
                },
                modifier = Modifier.clickable {
                    showBlockConfirm = true
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }

    if (showUnarchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showUnarchiveConfirm = false },
            title = { Text("Unarchive Connection?", color = Color.White) },
            text = {
                Text(
                    "This connection will return to your Active list.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUnarchiveConfirm = false
                    openFinalConfirm(
                        title = "Confirm Unarchive",
                        body = "Move this connection back to Active now?",
                        buttonLabel = "Yes, Unarchive",
                        buttonColor = PrimaryBlue
                    ) {
                        onUnarchive()
                    }
                }) {
                    Text("Unarchive", color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveConfirm = false }) {
                    Text("Cancel", color = LightBlue)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // ── Archive confirmation dialog ────────────────────────────────────────────
    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive Connection?", color = Color.White) },
            text = {
                Text(
                    "This connection will be hidden from your list. You can recover it later.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveConfirm = false
                    openFinalConfirm(
                        title = "Confirm Archive",
                        body = "Archive this connection now? You can unarchive it later.",
                        buttonLabel = "Yes, Archive",
                        buttonColor = PrimaryBlue
                    ) {
                        onArchive()
                    }
                }) {
                    Text("Archive", color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text("Cancel", color = LightBlue)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // ── Block confirmation dialog ──────────────────────────────────────────────
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block User?", color = Color.White) },
            text = {
                Text(
                    "They won't be able to contact you and this connection will be removed. This cannot be undone.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirm = false
                    openFinalConfirm(
                        title = "Confirm Block",
                        body = "Block this user and remove this connection? This cannot be undone.",
                        buttonLabel = "Yes, Block",
                        buttonColor = Color(0xFFFF4444)
                    ) {
                        onBlock()
                    }
                }) {
                    Text("Block", color = Color(0xFFFF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text("Cancel", color = LightBlue)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Connection?", color = Color.White) },
            text = {
                Text(
                    "This will permanently remove this connection and all messages. This cannot be undone.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    openFinalConfirm(
                        title = "Confirm Remove",
                        body = "Permanently remove this connection and all messages? This cannot be undone.",
                        buttonLabel = "Yes, Remove",
                        buttonColor = Color(0xFFFF4444)
                    ) {
                        onDelete()
                    }
                }) {
                    Text("Remove", color = Color(0xFFFF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = LightBlue)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // ── Report reason dialog ───────────────────────────────────────────────────
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report User", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Please describe the issue:",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        placeholder = { Text("Reason for report...", color = Color.White.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (reportReason.isNotBlank()) {
                        showReportDialog = false
                        val reasonToSubmit = reportReason.trim()
                        openFinalConfirm(
                            title = "Confirm Report",
                            body = "Submit this report for review?",
                            buttonLabel = "Yes, Report",
                            buttonColor = Color(0xFFFF8C00)
                        ) {
                            onReport(reasonToSubmit)
                        }
                    }
                }) {
                    Text("Submit", color = Color(0xFFFF8C00))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel", color = LightBlue)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // ── Final destructive-action confirmation dialog ────────────────────────
    if (showFinalConfirm) {
        AlertDialog(
            onDismissRequest = {
                showFinalConfirm = false
                finalConfirmAction = null
            },
            title = { Text(finalConfirmTitle, color = Color.White) },
            text = {
                Text(
                    finalConfirmBody,
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    finalConfirmAction?.invoke()
                    showFinalConfirm = false
                    finalConfirmAction = null
                    dismiss()
                }) {
                    Text(finalConfirmButtonLabel, color = finalConfirmButtonColor)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFinalConfirm = false
                    finalConfirmAction = null
                }) {
                    Text("Cancel", color = LightBlue)
                }
            },
            containerColor = SurfaceDark
        )
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
