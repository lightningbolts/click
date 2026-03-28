package compose.project.click.click.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import compose.project.click.click.getPlatform
import compose.project.click.click.calls.CallSessionManager
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.notifications.NotificationRuntimeState
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer
import compose.project.click.click.ui.components.PlatformBackHandler
import compose.project.click.click.ui.components.AdaptiveSurface
import compose.project.click.click.ui.components.GlassCard
import compose.project.click.click.ui.components.PageHeader
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.IcebreakerPrompt
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
import compose.project.click.click.viewmodel.ChatViewModel
import compose.project.click.click.viewmodel.ChatListState
import compose.project.click.click.viewmodel.ChatMessagesState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.absoluteValue
import com.mohamedrejeb.calf.ui.dialog.AdaptiveAlertDialog
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState

@Composable
fun ConnectionsScreen(
    userId: String,
    searchQuery: String = "",
    initialChatId: String? = null,
    onChatDismissed: (() -> Unit)? = null,
    onChatOpenStateChanged: (Boolean) -> Unit = {},
    onNavigateToLocationSettings: (() -> Unit)? = null,
    viewModel: ChatViewModel = viewModel { ChatViewModel() }
) {
    var selectedChatId by remember { mutableStateOf(initialChatId) }
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    var chatTransitionMode by remember { mutableStateOf(ChatTransitionMode.Tap) }
    var isTapCloseInFlight by remember { mutableStateOf(false) }
    val screenScope = rememberCoroutineScope()
    var closeCleanupJob by remember { mutableStateOf<Job?>(null) }

    fun finalizeChatClose() {
        viewModel.leaveChatRoom()
        viewModel.loadChats()
        onChatDismissed?.invoke()
    }

    fun closeActiveChat(mode: ChatTransitionMode = ChatTransitionMode.Tap) {
        if (selectedChatId != null) {
            closeCleanupJob?.cancel()
            chatTransitionMode = mode
            isTapCloseInFlight = mode == ChatTransitionMode.Tap
            selectedChatId = null
            if (mode == ChatTransitionMode.Tap) {
                closeCleanupJob = screenScope.launch {
                    delay(CHAT_TRANSITION_DURATION_MS)
                    if (selectedChatId == null) {
                        finalizeChatClose()
                    }
                    isTapCloseInFlight = false
                    closeCleanupJob = null
                }
            } else {
                isTapCloseInFlight = false
                finalizeChatClose()
            }
        }
    }

    LaunchedEffect(userId, initialChatId) {
        viewModel.setCurrentUser(userId)
        if (initialChatId != null) {
            selectedChatId = initialChatId
            viewModel.loadChats(isForced = true)
            viewModel.loadChatMessages(initialChatId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            closeCleanupJob?.cancel()
            isTapCloseInFlight = false
            onChatOpenStateChanged(false)
            viewModel.leaveChatRoom()
        }
    }

    LaunchedEffect(selectedChatId, isTapCloseInFlight) {
        onChatOpenStateChanged(selectedChatId != null || isTapCloseInFlight)
    }

    PlatformBackHandler(
        enabled = selectedChatId != null && !isIOS,
        onBack = { closeActiveChat(ChatTransitionMode.Tap) }
    )

    fun openChat(chatId: String) {
        closeCleanupJob?.cancel()
        isTapCloseInFlight = false
        chatTransitionMode = ChatTransitionMode.Tap
        selectedChatId = chatId
        viewModel.loadChatMessages(chatId)
    }

    if (isIOS) {
        // Persistent base layer + chat overlay architecture.
        // ConnectionsListView is always composed and never torn down, so the
        // swipe-back gesture reveals it instantly without a recomposition gap.
        Box(modifier = Modifier.fillMaxSize()) {
            ConnectionsListView(
                viewModel = viewModel,
                searchQuery = searchQuery,
                onChatSelected = { chatId -> openChat(chatId) },
                onNavigateToLocationSettings = onNavigateToLocationSettings
            )

            AnimatedContent(
                targetState = selectedChatId,
                transitionSpec = {
                    val slideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                    val fadeSpec = tween<Float>(220, easing = LinearOutSlowInEasing)
                    when {
                        initialState == null && targetState != null -> {
                            (slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) +
                                fadeIn(animationSpec = fadeSpec))
                                .togetherWith(ExitTransition.None)
                        }
                        initialState != null && targetState == null && chatTransitionMode == ChatTransitionMode.Tap -> {
                            EnterTransition.None
                                .togetherWith(
                                    slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { it }) +
                                        fadeOut(animationSpec = fadeSpec)
                                )
                        }
                        else -> {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    }
                },
                label = "ios_chat_open_transition"
            ) { activeChatId ->
                if (activeChatId != null) {
                    InteractiveSwipeBackContainer(
                        enabled = true,
                        edgeSwipeWidth = 44.dp,
                        onBack = { closeActiveChat(ChatTransitionMode.Gesture) },
                        previousContent = {
                            // Must mirror the persistent list layer so the edge swipe reveals
                            // the connections UI instead of an empty (black) placeholder.
                            ConnectionsListView(
                                viewModel = viewModel,
                                searchQuery = searchQuery,
                                onChatSelected = { chatId -> openChat(chatId) },
                                onNavigateToLocationSettings = onNavigateToLocationSettings
                            )
                        },
                        currentContent = {
                            ChatView(
                                viewModel = viewModel,
                                chatId = activeChatId,
                                onBackPressed = { closeActiveChat(ChatTransitionMode.Tap) }
                            )
                        }
                    )
                }
            }
        }
    } else {
        AnimatedContent(
            targetState = selectedChatId,
            transitionSpec = {
                val slideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                val fadeSpec = tween<Float>(220, easing = LinearOutSlowInEasing)
                if (targetState != null) {
                    (slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) + fadeIn(animationSpec = fadeSpec))
                        .togetherWith(slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { -it }) + fadeOut(animationSpec = fadeSpec))
                        .using(SizeTransform(clip = true))
                } else {
                    (slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { -it }) + fadeIn(animationSpec = fadeSpec))
                        .togetherWith(slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { it }) + fadeOut(animationSpec = fadeSpec))
                        .using(SizeTransform(clip = true))
                }
            },
            label = "chat_open_close_transition"
        ) { activeChatId ->
            if (activeChatId == null) {
                ConnectionsListView(
                    viewModel = viewModel,
                    searchQuery = searchQuery,
                    onChatSelected = { chatId -> openChat(chatId) },
                    onNavigateToLocationSettings = onNavigateToLocationSettings
                )
            } else {
                ChatView(
                    viewModel = viewModel,
                    chatId = activeChatId,
                    onBackPressed = { closeActiveChat(ChatTransitionMode.Tap) }
                )
            }
        }
    }
}

private enum class ChatTransitionMode {
    Tap,
    Gesture
}

private const val CHAT_TRANSITION_DURATION_MS = 300L
private const val EXPIRY_WARNING_THRESHOLD_HOURS = 48
private val ExpiryWarningColor = Color(0xFFFFA500)

/** Sort key for Clicks list: prefer server `last_message_at`, then last message time, then connection created. */
private fun connectionListActivityTs(chat: ChatWithDetails): Long =
    chat.connection.last_message_at
        ?: chat.lastMessage?.timeCreated
        ?: chat.connection.created

@Composable
fun ConnectionsListView(
    viewModel: ChatViewModel,
    searchQuery: String = "",
    onChatSelected: (String) -> Unit,
    onNavigateToLocationSettings: (() -> Unit)? = null
) {
    val chatListState by viewModel.chatListState.collectAsState()
    val archivedConnectionIds by viewModel.archivedConnectionIds.collectAsState()
    val cachedConnections by AppDataManager.connections.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableStateOf(0) } // 0 = Active, 1 = Archived

    // Connection menu state: holds the chatWithDetails for which the menu is open
    var pendingMenuChat by remember { mutableStateOf<ChatWithDetails?>(null) }

    val connectionsLazyListState = rememberLazyListState()

    // Build effective chat list: prefer ViewModel Success data, fall back to
    // cached connections during Loading/Error to prevent blank-screen flashes.
    val effectiveChats: List<ChatWithDetails> = when (val state = chatListState) {
        is ChatListState.Success -> state.chats
        else -> {
            val uid = currentUserId
            if (cachedConnections.isNotEmpty() && uid != null) {
                cachedConnections.mapNotNull { connection ->
                    val otherUserId = connection.user_ids.firstOrNull { it != uid }
                        ?: return@mapNotNull null
                    val otherUser = connectedUsers[otherUserId]
                        ?: User(id = otherUserId, name = "Connection", createdAt = 0L)
                    ChatWithDetails(
                        chat = connection.chat,
                        connection = connection,
                        otherUser = otherUser,
                        lastMessage = connection.chat.messages.lastOrNull(),
                        unreadCount = 0
                    )
                }
            } else emptyList()
        }
    }

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
                if (effectiveChats.isNotEmpty()) {
                    val activeChats = effectiveChats.filter { it.connection.id !in archivedConnectionIds }
                    val archivedChats = effectiveChats.filter { it.connection.id in archivedConnectionIds }
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
                } else {
                    val subtitle = if (chatListState is ChatListState.Loading) "Loading…" else ""
                    PageHeader(title = "Clicks", subtitle = subtitle)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (effectiveChats.isNotEmpty()) {
                val activeCount = effectiveChats.count { it.connection.id !in archivedConnectionIds }
                val archivedCount = effectiveChats.count { it.connection.id in archivedConnectionIds }

                val segStyle = LocalPlatformStyle.current
                val segBorderWidth = if (segStyle.isIOS) 0.5.dp else 1.dp
                val segCorner = if (segStyle.isIOS) 10.dp else 12.dp
                val segInnerCorner = if (segStyle.isIOS) 7.dp else 8.dp
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .border(
                            width = segBorderWidth,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(segCorner)
                        )
                        .clip(RoundedCornerShape(segCorner))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (segStyle.isIOS) 0.25f else 0.35f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(segInnerCorner))
                            .then(
                                if (selectedTabIndex == 0) Modifier
                                    .background(PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.14f else 0.18f))
                                    .border(segBorderWidth, PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.25f else 0.35f), RoundedCornerShape(segInnerCorner))
                                else Modifier
                            )
                            .clickable { selectedTabIndex = 0 }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Active ($activeCount)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTabIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTabIndex == 0) LightBlue
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(segInnerCorner))
                            .then(
                                if (selectedTabIndex == 1) Modifier
                                    .background(PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.14f else 0.18f))
                                    .border(segBorderWidth, PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.25f else 0.35f), RoundedCornerShape(segInnerCorner))
                                else Modifier
                            )
                            .clickable { selectedTabIndex = 1 }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Archived ($archivedCount)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTabIndex == 1) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTabIndex == 1) LightBlue
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (effectiveChats.isEmpty() && chatListState is ChatListState.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AdaptiveCircularProgressIndicator()
                }
            } else if (effectiveChats.isEmpty() && chatListState is ChatListState.Error) {
                val errorMsg = (chatListState as ChatListState.Error).message
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
                            errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadChats() }) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                val activeChats = effectiveChats.filter { it.connection.id !in archivedConnectionIds }
                val archivedChats = effectiveChats.filter { it.connection.id in archivedConnectionIds }
                val tabChats = if (selectedTabIndex == 0) activeChats else archivedChats

                val sortedTabChats = tabChats.sortedByDescending { connectionListActivityTs(it) }
                val filteredChats = if (searchQuery.isBlank()) {
                    sortedTabChats
                } else {
                    sortedTabChats.filter { chat ->
                        chat.otherUser?.name?.contains(searchQuery, ignoreCase = true) == true
                    }
                }

                val clicksListOrderSignature = filteredChats.joinToString("\u0000") {
                    "${it.connection.id}\t${connectionListActivityTs(it)}"
                }
                LaunchedEffect(clicksListOrderSignature) {
                    if (filteredChats.isEmpty()) return@LaunchedEffect
                    val nearTop = connectionsLazyListState.firstVisibleItemIndex <= 1 &&
                        connectionsLazyListState.firstVisibleItemScrollOffset < 96
                    if (nearTop) {
                        connectionsLazyListState.animateScrollToItem(0)
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
                        state = connectionsLazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            filteredChats,
                            key = { chat ->
                                val lm = chat.lastMessage
                                if (lm != null) "${chat.connection.id}\u0001${lm.id}\u0001${lm.timeCreated}"
                                else chat.connection.id
                            }
                        ) { chatDetails ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ConnectionItem(
                                    chatDetails = chatDetails,
                                    onClick = {
                                        if (chatDetails.connection.isExpiredConnection()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("This connection has expired")
                                            }
                                        } else {
                                            onChatSelected(chatDetails.connection.id)
                                        }
                                    },
                                    onNudge = {
                                        val chatId = chatDetails.chat.id
                                        if (chatId != null) {
                                            viewModel.sendNudgeToChat(chatId, chatDetails.otherUser.name ?: "them")
                                        }
                                    },
                                    onOpenMenu = { pendingMenuChat = chatDetails },
                                    onLongPress = { pendingMenuChat = chatDetails }
                                )
                                if (connectionHasNoGeo(chatDetails.connection) && onNavigateToLocationSettings != null) {
                                    LocationGapNudge(
                                        otherName = chatDetails.otherUser.name ?: "them",
                                        onClick = onNavigateToLocationSettings
                                    )
                                }
                            }
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


private fun connectionHasNoGeo(connection: Connection): Boolean {
    val g = connection.geo_location
    return !g.lat.isFinite() || !g.lon.isFinite() || (g.lat == 0.0 && g.lon == 0.0)
}

@Composable
private fun LocationGapNudge(
    otherName: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 68.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = PrimaryBlue.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Enable location to remember where you met $otherName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ConnectionItem(
    chatDetails: ChatWithDetails,
    onClick: () -> Unit,
    onNudge: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    val user = chatDetails.otherUser
    val connection = chatDetails.connection
    val lastMessage = chatDetails.lastMessage
    val unreadCount = chatDetails.unreadCount
    val timeText = lastMessage?.let { formatTimestamp(it.timeCreated) } ?: "No messages"
    val hoursUntilExpiry = connection.hoursUntilExpiry()
    val showExpiryWarning = hoursUntilExpiry in 0..EXPIRY_WARNING_THRESHOLD_HOURS && !connection.isKept()
    val isExpired = connection.isExpiredConnection()
    val showLoadingSubtitle = lastMessage == null && user.name == "Connection"

    val rowTapModifier = if (isIOS) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongPress
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(rowTapModifier)
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
                if (showLoadingSubtitle) {
                    Box(modifier = Modifier.weight(1f)) {
                        LoadingSubtitlePlaceholder()
                    }
                } else {
                    Text(
                        lastMessage?.content ?: "Start a conversation",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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

            if (showExpiryWarning || isExpired) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isExpired) "Expired" else "⏱ Expires in ${hoursUntilExpiry.coerceAtLeast(0)}h",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExpired) MaterialTheme.colorScheme.error else ExpiryWarningColor,
                    fontWeight = FontWeight.Medium
                )
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

@Composable
private fun LoadingSubtitlePlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "connection_subtitle_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "connection_subtitle_shimmer_alpha"
    )

    Box(
        modifier = modifier
            .height(12.dp)
            .width(120.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(viewModel: ChatViewModel, chatId: String, onBackPressed: () -> Unit) {
    val chatMessagesState by viewModel.chatMessagesState.collectAsState()
    val messageInput by viewModel.messageInput.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val isPeerOnline by viewModel.isPeerOnline.collectAsState()
    val chatListState by viewModel.chatListState.collectAsState()
    val editingMessageId by viewModel.editingMessageId.collectAsState()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val messageSendError by viewModel.messageSendError.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()

    // Icebreaker prompts state
    val icebreakerPrompts by viewModel.icebreakerPrompts.collectAsState()
    val showIcebreakerPanel by viewModel.showIcebreakerPanel.collectAsState()

    // Fresh scroll state per chat so opening a thread doesn't keep the previous scroll offset
    val listState = remember(chatId) { LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val chatOverlayPointerIntercept = remember { MutableInteractionSource() }
    val sendButtonAbsorbInteraction = remember { MutableInteractionSource() }

    // Connection action sheet (archive, delete, report, block)
    var showConnectionSheet by remember { mutableStateOf(false) }
    // Message context sheet (reactions, edit, delete, copy)
    var contextMenuMessage by remember { mutableStateOf<MessageWithUser?>(null) }
    var forwardMessageId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showCallMenu by remember { mutableStateOf(false) }

    // Show nudge snackbar
    LaunchedEffect(nudgeResult) {
        val r = nudgeResult
        if (r != null) {
            coroutineScope.launch { snackbarHostState.showSnackbar(r) }
            viewModel.clearNudgeResult()
        }
    }

    LaunchedEffect(messageSendError) {
        val err = messageSendError
        if (err != null) {
            coroutineScope.launch { snackbarHostState.showSnackbar(err) }
            viewModel.clearMessageSendError()
        }
    }

    LaunchedEffect(chatId) {
        viewModel.loadChatMessages(chatId)
    }

    val activeApiChatId = (chatMessagesState as? ChatMessagesState.Success)?.chatDetails?.chat?.id
    DisposableEffect(activeApiChatId) {
        NotificationRuntimeState.setActiveChatId(activeApiChatId)
        onDispose {
            NotificationRuntimeState.setActiveChatId(null)
        }
    }

    // Newest-first + reverseLayout pins latest messages next to the composer; snap to index 0 after layout
    val successMessages = (chatMessagesState as? ChatMessagesState.Success)?.messages.orEmpty()
    val scrollAnchor = successMessages.lastOrNull()?.message?.id to successMessages.size
    LaunchedEffect(chatId, scrollAnchor) {
        if (successMessages.isEmpty()) return@LaunchedEffect
        repeat(50) {
            if (listState.layoutInfo.totalItemsCount > 0) {
                listState.scrollToItem(0)
                return@LaunchedEffect
            }
            delay(16L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = chatOverlayPointerIntercept
            ) { }
    ) {
    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (val state = chatMessagesState) {
                is ChatMessagesState.Loading -> {
                    ChatChannelLoadingView(
                        topInset = topInset,
                        onBackPressed = onBackPressed
                    )
                }
                is ChatMessagesState.Error -> {
                    Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
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
                    val hoursUntilExpiry = chatDetails.connection.hoursUntilExpiry()
                    val showExpiryBanner = hoursUntilExpiry in 0..EXPIRY_WARNING_THRESHOLD_HOURS && !chatDetails.connection.isKept()
                    val reactionsMap by viewModel.messageReactions.collectAsState()
                    val typingPeerLabel = remember(chatDetails.otherUser.name) {
                        "${chatDetails.otherUser.name ?: "Someone"} is typing"
                    }

                    val messageContentModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()

                    Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
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

                                // Name + realtime presence (channel) + legacy lastPolled when offline in channel
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chatDetails.otherUser.name ?: "Unknown",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AnimatedVisibility(
                                            visible = isPeerOnline,
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF22C55E))
                                            )
                                        }
                                        AnimatedContent(
                                            targetState = isPeerOnline,
                                            transitionSpec = {
                                                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                                            },
                                            label = "peer_presence_subtitle"
                                        ) { online ->
                                            Text(
                                                text = if (online) "Online" else "Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (online) {
                                                    Color(0xFF16A34A)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                                }
                                            )
                                        }
                                    }
                                }

                                Box {
                                    IconButton(onClick = { showCallMenu = true }) {
                                        Icon(
                                            Icons.Filled.Call,
                                            contentDescription = "Call options",
                                            tint = PrimaryBlue.copy(alpha = 0.85f)
                                        )
                                    }
                                    val menuStyle = LocalPlatformStyle.current
                                    DropdownMenu(
                                        expanded = showCallMenu,
                                        onDismissRequest = { showCallMenu = false },
                                        shape = RoundedCornerShape(if (menuStyle.isIOS) 14.dp else 22.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = if (menuStyle.isIOS) {
                                            BorderStroke(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                            )
                                        } else {
                                            null
                                        },
                                        tonalElevation = if (menuStyle.isIOS) 0.dp else 8.dp,
                                        shadowElevation = if (menuStyle.isIOS) 0.dp else 16.dp
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Voice call") },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Call, contentDescription = null)
                                            },
                                            onClick = {
                                                showCallMenu = false
                                                CallSessionManager.startOutgoingCall(
                                                    connectionId = chatDetails.connection.id,
                                                    otherUserId = chatDetails.otherUser.id,
                                                    otherUserName = chatDetails.otherUser.name ?: "Connection",
                                                    videoEnabled = false
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Video call") },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Videocam, contentDescription = null)
                                            },
                                            onClick = {
                                                showCallMenu = false
                                                CallSessionManager.startOutgoingCall(
                                                    connectionId = chatDetails.connection.id,
                                                    otherUserId = chatDetails.otherUser.id,
                                                    otherUserName = chatDetails.otherUser.name ?: "Connection",
                                                    videoEnabled = true
                                                )
                                            }
                                        )
                                    }
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

                    if (showExpiryBanner) {
                        ExpiryBanner(
                            hoursUntilExpiry = hoursUntilExpiry,
                            onKeep = { viewModel.keepConnection() }
                        )
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
                    if (state.isLoadingMessages && messages.isEmpty() && chatDetails.lastMessage != null) {
                        Box(
                            modifier = messageContentModifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = PrimaryBlue,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Loading messages…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (messages.isEmpty()) {
                        Box(
                            modifier = messageContentModifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                usePrimaryBorder = true,
                                contentPadding = 28.dp
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Filled.ChatBubbleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = PrimaryBlue.copy(alpha = 0.85f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "No messages yet",
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Say hi to ${chatDetails.otherUser.name}!",
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        val timelineEntries = remember(messages) {
                            buildChatTimelineEntriesNewestFirst(messages)
                        }
                        Box(
                            modifier = messageContentModifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    // Stronger tint toward the composer (visual bottom)
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            PrimaryBlue.copy(alpha = 0.05f),
                                            Color.Transparent,
                                            Color(0xFF3A86FF).copy(alpha = 0.05f)
                                        )
                                    )
                                )
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                reverseLayout = true,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(timelineEntries, key = { it.key }) { entry ->
                                    when (entry) {
                                        is ChatTimelineEntry.DaySeparator -> {
                                            Column {
                                                ConversationDaySeparator(entry.label)
                                            }
                                        }
                                        is ChatTimelineEntry.MessageEntry -> {
                                            val messageWithUser = entry.messageWithUser
                                            val msgReactions = reactionsMap[messageWithUser.message.id] ?: emptyList()
                                            Column {
                                                AnimatedVisibilityChatBubble(
                                                    messageId = messageWithUser.message.id,
                                                    isSent = messageWithUser.isSent,
                                                    content = {
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
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Typing indicator — label + bouncing dots (Realtime Broadcast)
                    AnimatedVisibility(
                        visible = isPeerTyping,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 80.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = typingPeerLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic
                                    )
                                    ChatTypingDots()
                                }
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

                    // Calculate keyboard padding accounting for the Scaffold bottom bar
                    val density = LocalDensity.current
                    val imeBottomPx = WindowInsets.ime.getBottom(density)
                    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
                    // Subtract system nav bar + approximate Scaffold NavigationBar (~80dp)
                    val bottomBarPx = with(density) { 80.dp.roundToPx() }
                    val effectiveImePadding = with(density) {
                        (imeBottomPx - navBarBottomPx - bottomBarPx).coerceAtLeast(0).toDp()
                    }

                    val composerStyle = LocalPlatformStyle.current
                    val composerCorner = if (composerStyle.isIOS) 20.dp else 22.dp
                    val composerBorderW = if (composerStyle.isIOS) 0.5.dp else 1.dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = effectiveImePadding)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(composerCorner))
                            .background(Color.White.copy(alpha = composerStyle.glassBackgroundAlpha))
                            .border(composerBorderW, Color.White.copy(alpha = composerStyle.glassBorderAlpha), RoundedCornerShape(composerCorner))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val fieldCorner = if (composerStyle.isIOS) 10.dp else 12.dp
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = {
                                viewModel.updateMessageInput(it)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 52.dp),
                            placeholder = {
                                Text(
                                    if (editingMessageId != null) "Edit message…"
                                    else "Message ${chatDetails.otherUser.name}…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            shape = RoundedCornerShape(fieldCorner),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue.copy(alpha = if (composerStyle.isIOS) 0.50f else 0.65f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (composerStyle.isIOS) 0.08f else 0.12f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (composerStyle.isIOS) 0.30f else 0.4f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (composerStyle.isIOS) 0.18f else 0.25f)
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = false,
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.None
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        val canSend = messageInput.trim().isNotEmpty() && !isSending
                        val sendGradient = Brush.linearGradient(
                            colors = if (canSend) listOf(PrimaryBlue, LightBlue)
                            else listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(fieldCorner))
                                .background(sendGradient)
                                .then(
                                    if (canSend) {
                                        Modifier.clickable { viewModel.sendMessage() }
                                    } else {
                                        Modifier.clickable(
                                            indication = null,
                                            interactionSource = sendButtonAbsorbInteraction
                                        ) { }
                                    }
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
private fun ExpiryBanner(
    hoursUntilExpiry: Int,
    onKeep: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ExpiryWarningColor.copy(alpha = 0.14f))
            .border(1.dp, ExpiryWarningColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "This connection expires in ${hoursUntilExpiry.coerceAtLeast(0)} hours — tap Keep to make it permanent",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onKeep) {
            Text(
                text = "Keep",
                color = ExpiryWarningColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun Connection.hoursUntilExpiry(nowMs: Long = Clock.System.now().toEpochMilliseconds()): Int {
    return ((expiry - nowMs) / 3_600_000L).toInt()
}

private sealed interface ChatTimelineEntry {
    val key: String

    data class DaySeparator(
        override val key: String,
        val label: String,
    ) : ChatTimelineEntry

    data class MessageEntry(
        override val key: String,
        val messageWithUser: MessageWithUser,
    ) : ChatTimelineEntry
}

private fun buildChatTimelineEntries(messages: List<MessageWithUser>): List<ChatTimelineEntry> {
    if (messages.isEmpty()) return emptyList()

    val timeline = mutableListOf<ChatTimelineEntry>()
    var previousDayKey: String? = null
    messages.forEach { messageWithUser ->
        val dayKey = messageDayKey(messageWithUser.message.timeCreated)
        if (dayKey != previousDayKey) {
            timeline += ChatTimelineEntry.DaySeparator(
                key = "separator-$dayKey-${messageWithUser.message.id}",
                label = formatConversationDayLabel(messageWithUser.message.timeCreated)
            )
            previousDayKey = dayKey
        }
        timeline += ChatTimelineEntry.MessageEntry(
            key = messageWithUser.message.id,
            messageWithUser = messageWithUser,
        )
    }
    return timeline
}

/**
 * Timeline for [reverseLayout] chat: newest message is **first** in the list (index 0) so it sits
 * next to the composer. Day separators are inserted when the day changes while walking
 * newest → oldest.
 */
private fun buildChatTimelineEntriesNewestFirst(messages: List<MessageWithUser>): List<ChatTimelineEntry> {
    if (messages.isEmpty()) return emptyList()
    val newestFirst = messages.asReversed()
    val out = mutableListOf<ChatTimelineEntry>()
    var currentDayKey: String? = null
    var currentDayTimestamp = 0L

    newestFirst.forEach { messageWithUser ->
        val dayKey = messageDayKey(messageWithUser.message.timeCreated)
        if (currentDayKey != null && dayKey != currentDayKey) {
            out += ChatTimelineEntry.DaySeparator(
                key = "separator-nf-$currentDayKey",
                label = formatConversationDayLabel(currentDayTimestamp)
            )
        }
        if (dayKey != currentDayKey) {
            currentDayTimestamp = messageWithUser.message.timeCreated
        }
        out += ChatTimelineEntry.MessageEntry(
            key = messageWithUser.message.id,
            messageWithUser = messageWithUser
        )
        currentDayKey = dayKey
    }

    if (currentDayKey != null) {
        out += ChatTimelineEntry.DaySeparator(
            key = "separator-nf-tail-$currentDayKey",
            label = formatConversationDayLabel(currentDayTimestamp)
        )
    }

    return out
}

@Composable
private fun ConversationDaySeparator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    }
}

private fun messageDayKey(timestamp: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.year}-${dateTime.monthNumber}-${dateTime.dayOfMonth}"
}

private fun formatConversationDayLabel(timestamp: Long, nowMs: Long = Clock.System.now().toEpochMilliseconds()): String {
    val zone = TimeZone.currentSystemDefault()
    val dateTime = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone)
    val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone)

    val dayDifference = (now.date.toEpochDays() - dateTime.date.toEpochDays())
    return when {
        dayDifference == 0L -> "Today"
        dayDifference == 1L -> "Yesterday"
        dayDifference < 7L -> {
            dateTime.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        else -> {
            val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
            if (dateTime.year == now.year) {
                "$month ${dateTime.dayOfMonth}"
            } else {
                "$month ${dateTime.dayOfMonth}, ${dateTime.year}"
            }
        }
    }
}

private fun Connection.isExpiredConnection(nowMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
    return !isKept() && expiry < nowMs
}

@Composable
private fun ChatTypingDots() {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val delays = listOf(0, 140, 280)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        delays.forEachIndexed { index, delayMs ->
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, delayMillis = delayMs, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .offset(y = (-4f * offsetY).dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
            )
        }
    }
}

@Composable
private fun ChatChannelLoadingView(
    topInset: Dp,
    onBackPressed: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "chat_loading_spinner")
    val spinnerScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chat_loading_scale"
    )
    val spinnerMix by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chat_loading_mix"
    )
    val spinnerColor = lerp(PrimaryBlue, LightBlue, spinnerMix)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
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
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset + 56.dp),
            contentAlignment = Alignment.Center
        ) {
            AdaptiveCircularProgressIndicator(
                modifier = Modifier.size((34f * spinnerScale).dp),
                strokeWidth = 3.dp,
                color = spinnerColor
            )
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Forward to...") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
            ) {
                when (chatListState) {
                    is ChatListState.Success -> {
                        val options = chatListState.chats
                            .filter { it.connection.id != currentChatId }
                            .sortedByDescending { connectionListActivityTs(it) }
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
                    is ChatListState.Loading -> { AdaptiveCircularProgressIndicator() }
                    is ChatListState.Error -> { Text("Failed to load chats") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun AnimatedVisibilityChatBubble(
    messageId: String,
    isSent: Boolean,
    content: @Composable () -> Unit
) {
    var visible by remember(messageId) { mutableStateOf(false) }
    LaunchedEffect(messageId) {
        visible = true
    }
    val bounce = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(bounce) +
            slideInHorizontally { full -> if (isSent) full / 3 else -full / 3 } +
            scaleIn(bounce, initialScale = 0.9f),
        exit = fadeOut(animationSpec = tween(140)) +
            slideOutHorizontally(animationSpec = tween(200)) { full ->
                if (isSent) full / 4 else -full / 4
            } +
            scaleOut(animationSpec = tween(200), targetScale = 0.92f)
    ) {
        content()
    }
}

private fun formatCallDurationForLog(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return if (m > 0) "${m}m ${r.toString().padStart(2, '0')}s" else "${r}s"
}

private fun callLogLabel(message: Message): Pair<String, Boolean> {
    val meta = message.metadata as? JsonObject ?: return "Call" to false
    val state = (meta["call_state"] as? JsonPrimitive)?.content ?: return "Call" to false
    val dur = (meta["duration_seconds"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    return when (state) {
        "missed" -> "Missed Voice Call" to true
        "declined" -> "Declined Call" to false
        "completed" -> ("Call Ended • ${formatCallDurationForLog(dur)}") to false
        else -> "Call" to false
    }
}

@Composable
private fun CallLogSystemRow(message: Message) {
    val (label, isMissed) = remember(message.id, message.metadata) { callLogLabel(message) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isMissed) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMissed) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
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
    if (message.messageType == "call_log") {
        CallLogSystemRow(message = message)
        return
    }
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

    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showDeleteMessageConfirm by remember { mutableStateOf(false) }
    var showDeleteMessageFinalConfirm by remember { mutableStateOf(false) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val sheetStyle = LocalPlatformStyle.current
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = if (sheetStyle.isIOS) Color.Transparent else BottomSheetDefaults.ContainerColor,
        dragHandle = if (sheetStyle.isIOS) null else {{ BottomSheetDefaults.DragHandle() }},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
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
        AdaptiveAlertDialog(
            onConfirm = {
                showDeleteMessageConfirm = false
                showDeleteMessageFinalConfirm = true
            },
            onDismiss = { showDeleteMessageConfirm = false },
            confirmText = "Delete",
            dismissText = "Cancel",
            title = "Delete Message?",
            text = "This message will be permanently deleted. This cannot be undone."
        )
    }

    // ── Final delete confirmation dialog ───────────────────────────────────
    if (showDeleteMessageFinalConfirm) {
        AdaptiveAlertDialog(
            onConfirm = {
                viewModel.deleteMessage(message.id)
                showDeleteMessageFinalConfirm = false
                dismiss()
            },
            onDismiss = { showDeleteMessageFinalConfirm = false },
            confirmText = "Yes, Delete",
            dismissText = "Cancel",
            title = "Delete Message Permanently?",
            text = "This action is permanent and cannot be undone."
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
    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = false)
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

    val actionSheetStyle = LocalPlatformStyle.current
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = if (actionSheetStyle.isIOS) Color.Transparent else BottomSheetDefaults.ContainerColor,
        dragHandle = if (actionSheetStyle.isIOS) null else {{ BottomSheetDefaults.DragHandle() }},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
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
        AdaptiveAlertDialog(
            onConfirm = {
                showUnarchiveConfirm = false
                openFinalConfirm(
                    title = "Confirm Unarchive",
                    body = "Move this connection back to Active now?",
                    buttonLabel = "Yes, Unarchive",
                    buttonColor = PrimaryBlue
                ) {
                    onUnarchive()
                }
            },
            onDismiss = { showUnarchiveConfirm = false },
            confirmText = "Unarchive",
            dismissText = "Cancel",
            title = "Unarchive Connection?",
            text = "This connection will return to your Active list."
        )
    }

    // ── Archive confirmation dialog ────────────────────────────────────────────
    if (showArchiveConfirm) {
        AdaptiveAlertDialog(
            onConfirm = {
                showArchiveConfirm = false
                openFinalConfirm(
                    title = "Confirm Archive",
                    body = "Archive this connection now? You can unarchive it later.",
                    buttonLabel = "Yes, Archive",
                    buttonColor = PrimaryBlue
                ) {
                    onArchive()
                }
            },
            onDismiss = { showArchiveConfirm = false },
            confirmText = "Archive",
            dismissText = "Cancel",
            title = "Archive Connection?",
            text = "This connection will be hidden from your list. You can recover it later."
        )
    }

    // ── Block confirmation dialog ──────────────────────────────────────────────
    if (showBlockConfirm) {
        AdaptiveAlertDialog(
            onConfirm = {
                showBlockConfirm = false
                openFinalConfirm(
                    title = "Confirm Block",
                    body = "Block this user and remove this connection? This cannot be undone.",
                    buttonLabel = "Yes, Block",
                    buttonColor = Color(0xFFFF4444)
                ) {
                    onBlock()
                }
            },
            onDismiss = { showBlockConfirm = false },
            confirmText = "Block",
            dismissText = "Cancel",
            title = "Block User?",
            text = "They won't be able to contact you and this connection will be removed. This cannot be undone."
        )
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    if (showDeleteConfirm) {
        AdaptiveAlertDialog(
            onConfirm = {
                showDeleteConfirm = false
                openFinalConfirm(
                    title = "Confirm Remove",
                    body = "Permanently remove this connection and all messages? This cannot be undone.",
                    buttonLabel = "Yes, Remove",
                    buttonColor = Color(0xFFFF4444)
                ) {
                    onDelete()
                }
            },
            onDismiss = { showDeleteConfirm = false },
            confirmText = "Remove",
            dismissText = "Cancel",
            title = "Remove Connection?",
            text = "This will permanently remove this connection and all messages. This cannot be undone."
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
        AdaptiveAlertDialog(
            onConfirm = {
                finalConfirmAction?.invoke()
                showFinalConfirm = false
                finalConfirmAction = null
                dismiss()
            },
            onDismiss = {
                showFinalConfirm = false
                finalConfirmAction = null
            },
            confirmText = finalConfirmButtonLabel,
            dismissText = "Cancel",
            title = finalConfirmTitle,
            text = finalConfirmBody
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
