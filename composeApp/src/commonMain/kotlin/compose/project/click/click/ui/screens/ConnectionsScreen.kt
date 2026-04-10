package compose.project.click.click.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.AnnotatedString
import compose.project.click.click.getPlatform // pragma: allowlist secret
import compose.project.click.click.calls.CallSessionManager // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.notifications.NotificationRuntimeState // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveCard // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformBackHandler // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveSurface // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.ripple
import compose.project.click.click.ui.components.AvatarWithOnlineIndicator // pragma: allowlist secret
import compose.project.click.click.ui.components.EmojiCatalog // pragma: allowlist secret
import compose.project.click.click.ui.components.PageHeader // pragma: allowlist secret
import compose.project.click.click.ui.components.UserProfileBottomSheet // pragma: allowlist secret
import compose.project.click.click.data.models.replyRef // pragma: allowlist secret
import compose.project.click.click.data.models.replySnippetForMetadata // pragma: allowlist secret
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isArchivedChannelForUser // pragma: allowlist secret
import compose.project.click.click.data.models.IcebreakerPrompt // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.copyableText // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.previewLabel // pragma: allowlist secret
import compose.project.click.click.data.models.parsedMediaMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.toUserProfile // pragma: allowlist secret
import compose.project.click.click.data.models.mostUrgentArchiveNotice // pragma: allowlist secret
import coil3.compose.AsyncImage // pragma: allowlist secret
import androidx.compose.foundation.layout.offset // pragma: allowlist secret
import androidx.compose.material.icons.outlined.Edit // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionArchiveWarningBanner // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatListState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret
import compose.project.click.click.ui.chat.saveChatImageToGallery // pragma: allowlist secret
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.absoluteValue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import coil3.compose.AsyncImage
import compose.project.click.click.media.rememberChatAudioPlayer // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatLinkifyText // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret

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
    var profileUserId by remember { mutableStateOf<String?>(null) }

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

    Box(modifier = Modifier.fillMaxSize()) {
    if (isIOS) {
        // Persistent base layer + chat overlay architecture.
        // ConnectionsListView is always composed and never torn down, so the
        // swipe-back gesture reveals it instantly without a recomposition gap.
        Box(modifier = Modifier.fillMaxSize()) {
            ConnectionsListView(
                viewModel = viewModel,
                searchQuery = searchQuery,
                onChatSelected = { chatId -> openChat(chatId) },
                onNavigateToLocationSettings = onNavigateToLocationSettings,
                onUserProfileClick = { profileUserId = it }
            )

            // Sits under the chat overlay; any pointer that misses the overlay (Compose "holes")
            // hits this layer first and is consumed so the persistent list never activates.
            if (selectedChatId != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }

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
                        onBack = { closeActiveChat(ChatTransitionMode.Gesture) },
                        // Persistent ConnectionsListView is composed below AnimatedContent; do not
                        // duplicate the list here (a second rememberLazyListState starts at index 0).
                        opaquePreviousBackground = false,
                        previousContent = {},
                        currentContent = {
                            ChatView(
                                viewModel = viewModel,
                                chatId = activeChatId,
                                onBackPressed = { closeActiveChat(ChatTransitionMode.Tap) },
                                onOpenUserProfile = { profileUserId = it }
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
                    onNavigateToLocationSettings = onNavigateToLocationSettings,
                    onUserProfileClick = { profileUserId = it }
                )
            } else {
                ChatView(
                    viewModel = viewModel,
                    chatId = activeChatId,
                    onBackPressed = { closeActiveChat(ChatTransitionMode.Tap) },
                    onOpenUserProfile = { profileUserId = it }
                )
            }
        }
    }
    UserProfileBottomSheet(
        userId = profileUserId,
        viewerUserId = userId,
        onDismiss = { profileUserId = null }
    )
    }
}

private enum class ChatTransitionMode {
    Tap,
    Gesture
}

private const val CHAT_TRANSITION_DURATION_MS = 300L

/** Sort key for Clicks list: prefer server `last_message_at`, then last message time, then connection created. */
private fun connectionListActivityTs(chat: ChatWithDetails): Long =
    chat.connection.last_message_at
        ?: chat.lastMessage?.timeCreated
        ?: chat.connection.created

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsListView(
    viewModel: ChatViewModel,
    searchQuery: String = "",
    onChatSelected: (String) -> Unit,
    onNavigateToLocationSettings: (() -> Unit)? = null,
    onUserProfileClick: (String) -> Unit = {},
) {
    val chatListState by viewModel.chatListState.collectAsState()
    val archivedConnectionIds by viewModel.archivedConnectionIds.collectAsState()
    val hiddenConnectionIds by viewModel.hiddenConnectionIds.collectAsState()
    val cachedConnections by AppDataManager.connections.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val onlineUsers by AppDataManager.onlineUsers.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableStateOf(0) } // 0 = Active, 1 = Archived
    var listBannerNow by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(60_000)
            listBannerNow = Clock.System.now().toEpochMilliseconds()
        }
    }

    // Connection menu state: holds the chatWithDetails for which the menu is open
    var pendingMenuChat by remember { mutableStateOf<ChatWithDetails?>(null) }

    var cliqueSheetVisible by remember { mutableStateOf(false) }
    var selectedCliqueFriendIds by remember { mutableStateOf(setOf<String>()) }
    val cliqueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listScope = rememberCoroutineScope()

    val connectionsLazyListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(0, 0)
    }

    // Build effective chat list: prefer ViewModel Success data, fall back to
    // cached connections during Loading/Error to prevent blank-screen flashes.
    val effectiveChats: List<ChatWithDetails> = when (val state = chatListState) {
        is ChatListState.Success -> state.chats
        else -> {
            val uid = currentUserId
            if (cachedConnections.isNotEmpty() && uid != null) {
                cachedConnections.mapNotNull { connection ->
                    if (connection.normalizedConnectionStatus() == "removed") return@mapNotNull null
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

    val activeOneToOneChats = remember(effectiveChats, archivedConnectionIds, hiddenConnectionIds) {
        effectiveChats
            .filter {
                it.groupClique == null &&
                    it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
            }
            .sortedByDescending { connectionListActivityTs(it) }
    }

    val chatListRefreshEpoch by AppDataManager.chatListRefreshEpoch.collectAsState()
    LaunchedEffect(chatListRefreshEpoch) {
        if (chatListRefreshEpoch > 0) {
            viewModel.loadChats(isForced = true)
        }
    }

    var cliqueAddableMask by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var cliqueCreateGraphOk by remember { mutableStateOf(false) }
    /** False while edge RPCs run — blocks taps so users cannot pick ineligible friends during load. */
    var cliqueSheetEligibilityReady by remember { mutableStateOf(false) }

    LaunchedEffect(
        cliqueSheetVisible,
        selectedCliqueFriendIds,
        currentUserId,
        activeOneToOneChats,
    ) {
        val uid = currentUserId
        if (!cliqueSheetVisible || uid.isNullOrBlank()) {
            cliqueAddableMask = emptyMap()
            cliqueCreateGraphOk = false
            cliqueSheetEligibilityReady = false
            return@LaunchedEffect
        }
        cliqueSheetEligibilityReady = false
        val others = activeOneToOneChats.map { it.otherUser.id }
        coroutineScope {
            val maskEntries = others.map { oid ->
                async {
                    val ok = if (oid in selectedCliqueFriendIds) {
                        true
                    } else {
                        viewModel.memberSetSatisfiesVerifiedCliqueGraph(
                            (listOf(uid) + selectedCliqueFriendIds + oid).distinct().sorted(),
                        )
                    }
                    oid to ok
                }
            }.awaitAll()
            val mask = linkedMapOf<String, Boolean>().apply { putAll(maskEntries) }
            val fullOk = if (selectedCliqueFriendIds.isEmpty()) {
                false
            } else {
                async {
                    viewModel.memberSetSatisfiesVerifiedCliqueGraph(
                        (listOf(uid) + selectedCliqueFriendIds).distinct().sorted(),
                    )
                }.await()
            }
            cliqueAddableMask = mask
            cliqueCreateGraphOk = fullOk
            cliqueSheetEligibilityReady = true
        }
    }

    val memberSetDuplicatesExistingClick = remember(
        effectiveChats,
        selectedCliqueFriendIds,
        currentUserId,
    ) {
        val uid = currentUserId ?: return@remember false
        if (selectedCliqueFriendIds.isEmpty()) return@remember false
        val target = (selectedCliqueFriendIds + uid).toSet()
        effectiveChats.any { chat ->
            chat.groupClique != null &&
                chat.groupClique.memberUserIds.toSet() == target
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
                    val activeChats = effectiveChats.filter {
                        it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                    }
                    val archivedChats = effectiveChats.filter {
                        it.connection.isArchivedChannelForUser(archivedConnectionIds, hiddenConnectionIds)
                    }
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
                val activeCount = effectiveChats.count {
                    it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                }
                val archivedCount = effectiveChats.count {
                    it.connection.isArchivedChannelForUser(archivedConnectionIds, hiddenConnectionIds)
                }

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
                val activeChats = effectiveChats.filter {
                    it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                }
                val archivedChats = effectiveChats.filter {
                    it.connection.isArchivedChannelForUser(archivedConnectionIds, hiddenConnectionIds)
                }
                val tabChats = if (selectedTabIndex == 0) activeChats else archivedChats

                val sortedTabChats = tabChats.sortedByDescending { connectionListActivityTs(it) }
                val filteredChats = if (searchQuery.isBlank()) {
                    sortedTabChats
                } else {
                    sortedTabChats.filter { chat ->
                        val groupHit =
                            chat.groupClique?.name?.contains(searchQuery, ignoreCase = true) == true
                        val userHit =
                            chat.otherUser.name?.contains(searchQuery, ignoreCase = true) == true
                        groupHit || userHit
                    }
                }

                val chatLabelByConnectionId = effectiveChats.associate { chat ->
                    val who = chat.otherUser.name?.trim()?.takeIf { it.isNotBlank() }
                        ?: chat.connection.displayLocationLabel?.trim()?.takeIf { it.isNotBlank() }
                        ?: "this connection"
                    chat.connection.id to who
                }
                val archiveBannerNotice =
                    if (effectiveChats.isNotEmpty() && selectedTabIndex == 0) {
                        activeChats
                            .filter { !it.connection.isServerLifecycleArchived() }
                            .map { it.connection }
                            .mostUrgentArchiveNotice(listBannerNow) { conn ->
                                chatLabelByConnectionId[conn.id] ?: "this connection"
                            }
                    } else {
                        null
                    }

                val clicksListOrderSignature = filteredChats.joinToString("\u0000") {
                    "${it.connection.id}\t${connectionListActivityTs(it)}"
                }
                LaunchedEffect(clicksListOrderSignature) {
                    if (filteredChats.isEmpty()) return@LaunchedEffect
                    val nearTop = connectionsLazyListState.firstVisibleItemIndex <= 2 &&
                        connectionsLazyListState.firstVisibleItemScrollOffset < 96
                    if (nearTop) {
                        connectionsLazyListState.animateScrollToItem(0)
                    }
                }

                if (filteredChats.isEmpty()) {
                    val emptyScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(emptyScroll)
                    ) {
                        archiveBannerNotice?.let { notice ->
                            ConnectionArchiveWarningBanner(
                                notice = notice,
                                onOpenChat = { onChatSelected(notice.connectionId) },
                                onSendIcebreaker = {
                                    viewModel.sendArchiveBannerIcebreaker(
                                        notice.connectionId,
                                        notice.chatLabel,
                                    )
                                },
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = 10.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 360.dp)
                                .padding(horizontal = 20.dp),
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
                    }
                } else {
                    LazyColumn(
                        state = connectionsLazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        archiveBannerNotice?.let { notice ->
                            item(key = "archive_banner") {
                                ConnectionArchiveWarningBanner(
                                    notice = notice,
                                    onOpenChat = { onChatSelected(notice.connectionId) },
                                    onSendIcebreaker = {
                                        viewModel.sendArchiveBannerIcebreaker(
                                            notice.connectionId,
                                            notice.chatLabel,
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 20.dp)
                                        .padding(bottom = 10.dp),
                                )
                            }
                        }

                        items(
                            filteredChats,
                            key = { chat ->
                                val lm = chat.lastMessage
                                val at = chat.connection.last_message_at
                                if (lm != null) "${chat.connection.id}\u0001${lm.id}\u0001${lm.timeCreated}\u0001$at"
                                else "${chat.connection.id}\u0001$at"
                            }
                        ) { chatDetails ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ConnectionItem(
                                    chatDetails = chatDetails,
                                    showOnlineIndicator = chatDetails.groupClique == null &&
                                        chatDetails.otherUser.id in onlineUsers,
                                    onAvatarClick = {
                                        if (chatDetails.groupClique == null) {
                                            onUserProfileClick(chatDetails.otherUser.id)
                                        }
                                    },
                                    onClick = {
                                        onChatSelected(
                                            chatDetails.chat.id ?: chatDetails.connection.id,
                                        )
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
                                if (chatDetails.groupClique == null &&
                                    connectionHasNoGeo(chatDetails.connection) &&
                                    onNavigateToLocationSettings != null
                                ) {
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

    if (selectedTabIndex == 0 && currentUserId != null) {
        FloatingActionButton(
            onClick = {
                selectedCliqueFriendIds = emptySet()
                cliqueSheetVisible = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 12.dp),
            containerColor = PrimaryBlue,
            contentColor = Color.White,
        ) {
            Icon(Icons.Filled.Groups, contentDescription = "Create verified click")
        }
    }

    val uidForClique = currentUserId
    if (cliqueSheetVisible && uidForClique != null) {
        ModalBottomSheet(
            onDismissRequest = { cliqueSheetVisible = false },
            sheetState = cliqueSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Create verified click",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pick friends who are pairwise connected (active or kept). Friend–friend edges are checked on the server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (!cliqueSheetEligibilityReady && activeOneToOneChats.isNotEmpty()) {
                    Text(
                        text = "Checking who can join…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (memberSetDuplicatesExistingClick) {
                    Text(
                        text = "You already have a verified click with this group.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (activeOneToOneChats.isEmpty()) {
                    Text(
                        text = "No active 1:1 connections yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(activeOneToOneChats, key = { it.connection.id }) { chatDetails ->
                            val friendId = chatDetails.otherUser.id
                            val checked = friendId in selectedCliqueFriendIds
                            val canSelect =
                                checked || (cliqueSheetEligibilityReady && cliqueAddableMask[friendId] == true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = canSelect) {
                                        selectedCliqueFriendIds = if (checked) {
                                            selectedCliqueFriendIds - friendId
                                        } else {
                                            selectedCliqueFriendIds + friendId
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { wantChecked ->
                                        if (wantChecked == checked) return@Checkbox
                                        if (!wantChecked) {
                                            selectedCliqueFriendIds = selectedCliqueFriendIds - friendId
                                        } else if (canSelect) {
                                            selectedCliqueFriendIds = selectedCliqueFriendIds + friendId
                                        } else {
                                            listScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "That friend isn’t connected to everyone already selected.",
                                                )
                                            }
                                        }
                                    },
                                    enabled = canSelect,
                                )
                                AvatarWithOnlineIndicator(
                                    isOnline = chatDetails.otherUser.id in onlineUsers,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(PrimaryBlue, LightBlue),
                                                ),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            chatDetails.otherUser.name?.firstOrNull()?.toString()?.uppercase()
                                                ?: "?",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                Text(
                                    text = chatDetails.otherUser.name?.trim()?.ifBlank { null } ?: "Friend",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                val canCreate = cliqueSheetEligibilityReady &&
                    selectedCliqueFriendIds.isNotEmpty() &&
                    cliqueCreateGraphOk &&
                    !memberSetDuplicatesExistingClick
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { cliqueSheetVisible = false }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.createVerifiedClique(selectedCliqueFriendIds.toList()) { result ->
                                result.onSuccess {
                                    cliqueSheetVisible = false
                                    listScope.launch {
                                        snackbarHostState.showSnackbar("Click created")
                                    }
                                }
                                result.onFailure { e ->
                                    listScope.launch {
                                        val raw = e.message?.takeIf { it.isNotBlank() }.orEmpty()
                                        val msg = when {
                                            raw.contains("verified click already exists", ignoreCase = true) ->
                                                "You already have a verified click with this group."
                                            else -> raw.ifBlank { "Couldn’t create click" }
                                        }
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                }
                            }
                        },
                        enabled = canCreate,
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }

    // Connection action sheet
    if (pendingMenuChat != null) {
        val selected = pendingMenuChat!!
        val isUserArchived = selected.connection.id in archivedConnectionIds
        val isServerArchived = selected.connection.isServerLifecycleArchived()
        ConnectionActionSheet(
            chatDetails = selected,
            currentUserId = currentUserId,
            isArchived = isUserArchived,
            isServerLifecycleArchived = isServerArchived,
            onDismiss = { pendingMenuChat = null },
            onNudge = {
                val sel = pendingMenuChat ?: return@ConnectionActionSheet
                val chatId = sel.chat.id
                if (chatId != null) {
                    viewModel.sendNudgeToChat(chatId, sel.otherUser.name ?: "them")
                }
            },
            onOpenChat = {
                val sel = pendingMenuChat ?: return@ConnectionActionSheet
                onChatSelected(sel.chat.id ?: sel.connection.id)
            },
            onArchive = {
                val sel = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.archiveConnectionById(sel.connection.id) { }
            },
            onUnarchive = {
                val sel = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.unarchiveConnection(sel.connection.id)
            },
            onDelete = {
                val sel = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.deleteConnectionPermanentlyById(sel.connection.id) { }
            },
            onReport = { reason ->
                val sel = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.reportConnectionForConnection(sel.connection.id, reason) { }
            },
            onBlock = {
                val sel = pendingMenuChat ?: return@ConnectionActionSheet
                viewModel.blockUserForConnection(sel.connection.id) { }
            },
            onLeaveGroup = {
                val sel = pendingMenuChat ?: return@ConnectionActionSheet
                val gid = sel.groupClique?.groupId ?: return@ConnectionActionSheet
                viewModel.leaveVerifiedClique(gid) { ok -> if (ok) pendingMenuChat = null }
            },
            onDeleteGroup = {
                val sel = pendingMenuChat ?: return@ConnectionActionSheet
                val gid = sel.groupClique?.groupId ?: return@ConnectionActionSheet
                viewModel.deleteVerifiedClique(gid) { ok -> if (ok) pendingMenuChat = null }
            },
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
private fun GroupChatListAvatarStack(memberUsers: List<User>, avatarSize: Dp = 44.dp) {
    val profiles = memberUsers.map { it.toUserProfile() }
    if (profiles.isEmpty()) return
    val shown = profiles.take(3)
    val overflow = (profiles.size - 3).coerceAtLeast(0)
    val overlap = (avatarSize.value * 0.38f).dp
    val badgeSize = (avatarSize.value * 0.92f).dp
    val stackWidth =
        avatarSize + overlap * (shown.size - 1).coerceAtLeast(0) +
            if (overflow > 0) badgeSize + 6.dp else 0.dp
    Row(
        modifier = Modifier
            .width(stackWidth)
            .height(avatarSize + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            shown.forEachIndexed { index, profile ->
                val borderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                Surface(
                    modifier = Modifier
                        .offset(x = overlap * index)
                        .size(avatarSize)
                        .zIndex(index.toFloat())
                        .align(Alignment.CenterStart),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(2.dp, borderColor),
                ) {
                    if (!profile.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size((avatarSize.value * 0.55f).dp),
                            )
                        }
                    }
                }
            }
            if (overflow > 0) {
                Surface(
                    modifier = Modifier
                        .offset(x = overlap * shown.size + 4.dp)
                        .size(badgeSize)
                        .zIndex(shown.size + 1f)
                        .align(Alignment.CenterStart),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = "+$overflow",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionItem(
    chatDetails: ChatWithDetails,
    showOnlineIndicator: Boolean = false,
    onAvatarClick: () -> Unit = {},
    onClick: () -> Unit,
    onNudge: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    val isGroup = chatDetails.groupClique != null
    val headline = if (isGroup) {
        chatDetails.groupClique?.name?.trim()?.ifBlank { null } ?: "Verified click"
    } else {
        chatDetails.otherUser.name ?: "Unknown"
    }
    val user = chatDetails.otherUser
    val connection = chatDetails.connection
    val lastMessage = chatDetails.lastMessage
    val unreadCount = chatDetails.unreadCount
    val activityTs = lastMessage?.timeCreated ?: connection.last_message_at
    val timeText = activityTs?.let { formatTimestamp(it) } ?: "No messages"
    val showLoadingSubtitle =
        lastMessage == null && user.name == "Connection" && connection.last_message_at == null
    val previewNeedsRefresh = connection.last_message_at?.let { latestAt ->
        lastMessage == null || lastMessage.timeCreated < latestAt
    } ?: false

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
        // Avatar (stacked for verified clicks)
        if (isGroup) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (chatDetails.groupMemberUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    GroupChatListAvatarStack(
                        memberUsers = chatDetails.groupMemberUsers,
                        avatarSize = 40.dp,
                    )
                }
            }
        } else {
            AvatarWithOnlineIndicator(
                isOnline = showOnlineIndicator,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 24.dp),
                        onClick = onAvatarClick,
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    headline,
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
                    val previewText = when {
                        previewNeedsRefresh -> "New message"
                        lastMessage != null -> lastMessage.previewLabel()
                        connection.last_message_at != null -> "New message"
                        else -> "Start a conversation"
                    }
                    Text(
                        previewText,
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
        }

        if (!isGroup) {
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
        } else {
            Spacer(modifier = Modifier.size(36.dp))
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
fun ChatView(
    viewModel: ChatViewModel,
    chatId: String,
    onBackPressed: () -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
) {
    val chatMessagesState by viewModel.chatMessagesState.collectAsState()
    val messageInput by viewModel.messageInput.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val isPeerOnline by viewModel.isPeerOnline.collectAsState()
    val chatListState by viewModel.chatListState.collectAsState()
    val archivedConnectionIds by viewModel.archivedConnectionIds.collectAsState()
    val hiddenConnectionIds by viewModel.hiddenConnectionIds.collectAsState()
    val editingMessageId by viewModel.editingMessageId.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val messageSendError by viewModel.messageSendError.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val onlineUsers by AppDataManager.onlineUsers.collectAsState()

    // Icebreaker prompts state
    val icebreakerPrompts by viewModel.icebreakerPrompts.collectAsState()
    val showIcebreakerPanel by viewModel.showIcebreakerPanel.collectAsState()

    // Fresh scroll state per chat so opening a thread doesn't keep the previous scroll offset
    val listState = remember(chatId) { LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sendButtonAbsorbInteraction = remember { MutableInteractionSource() }

    // Connection action sheet (archive, delete, report, block)
    var showConnectionSheet by remember { mutableStateOf(false) }
    var showRenameGroupDialog by remember { mutableStateOf(false) }
    var renameGroupDraft by remember { mutableStateOf("") }
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    val reactionsMap by viewModel.messageReactions.collectAsState()
                    val isGroupChat = chatDetails.groupClique != null
                    val typingPeerLabel = remember(chatDetails.otherUser.name, isGroupChat) {
                        if (isGroupChat) "Someone is typing"
                        else "${chatDetails.otherUser.name ?: "Someone"} is typing"
                    }
                    val groupTitle = chatDetails.groupClique?.name?.trim()?.ifBlank { null }
                        ?: "Verified click"
                    val memberSummaryLine = remember(chatDetails.groupClique, chatDetails.groupMemberUsers, currentUser) {
                        val gc = chatDetails.groupClique ?: return@remember null
                        val self = currentUser
                        val nameParts = buildList {
                            val byId = (chatDetails.groupMemberUsers + listOfNotNull(self))
                                .distinctBy { it.id }
                                .associateBy { it.id }
                            gc.memberUserIds.sorted().forEach { id ->
                                val u = byId[id]
                                val part = u?.firstName?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: u?.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
                                    ?: "Member"
                                add(part)
                            }
                        }
                        "${gc.memberUserIds.size} members: ${nameParts.joinToString(", ")}"
                    }
                    val mediaPickers = rememberChatMediaPickers(
                        onImagePicked = { bytes, mime -> viewModel.sendChatImage(bytes, mime) },
                        onAudioPicked = { bytes, mime, dur -> viewModel.sendChatAudio(bytes, mime, dur?.toInt()) },
                    )
                    var attachmentMenuExpanded by remember { mutableStateOf(false) }

                    val messageContentModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()

                    // Match ConnectionsListView: list rows + composer are full width; only the top bar uses 20.dp gutters.
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, top = topInset, end = 20.dp)
                        ) {
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

                                if (isGroupChat) {
                                    Box(
                                        modifier = Modifier.size(40.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        GroupChatListAvatarStack(
                                            memberUsers = chatDetails.groupMemberUsers,
                                            avatarSize = 34.dp,
                                        )
                                    }
                                } else {
                                    AvatarWithOnlineIndicator(
                                        isOnline = chatDetails.otherUser.id in onlineUsers || isPeerOnline,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(bounded = false, radius = 22.dp),
                                                onClick = { onOpenUserProfile(chatDetails.otherUser.id) },
                                            ),
                                        indicatorSize = 9.dp,
                                        indicatorBorder = 1.25.dp
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
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
                                                color = LightBlue.copy(alpha = 0.96f),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isGroupChat) groupTitle else (chatDetails.otherUser.name ?: "Unknown"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isGroupChat && memberSummaryLine != null) {
                                        Text(
                                            text = memberSummaryLine,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else if (!isGroupChat) {
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
                                }

                                if (isGroupChat) {
                                    IconButton(
                                        onClick = {
                                            renameGroupDraft = groupTitle
                                            showRenameGroupDialog = true
                                        },
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "Rename group",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        )
                                    }
                                }

                                if (!isGroupChat) {
                                Box(modifier = Modifier.size(48.dp)) {
                                    IconButton(
                                        onClick = { showCallMenu = true },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Icon(
                                            Icons.Filled.Call,
                                            contentDescription = "Call options",
                                            tint = PrimaryBlue.copy(alpha = 0.85f)
                                        )
                                    }
                                    val menuStyle = LocalPlatformStyle.current
                                    val density = LocalDensity.current
                                    if (menuStyle.isIOS && showCallMenu) {
                                        Popup(
                                            alignment = Alignment.TopStart,
                                            offset = IntOffset(0, with(density) { 48.dp.roundToPx() }),
                                            onDismissRequest = { showCallMenu = false },
                                            properties = PopupProperties(
                                                focusable = true,
                                                dismissOnBackPress = true,
                                                dismissOnClickOutside = true,
                                            ),
                                        ) {
                                            ChatCallOptionsIosSurface(
                                                onVoice = {
                                                    showCallMenu = false
                                                    CallSessionManager.startOutgoingCall(
                                                        connectionId = chatDetails.connection.id,
                                                        otherUserId = chatDetails.otherUser.id,
                                                        otherUserName = chatDetails.otherUser.name ?: "Connection",
                                                        videoEnabled = false
                                                    )
                                                },
                                                onVideo = {
                                                    showCallMenu = false
                                                    CallSessionManager.startOutgoingCall(
                                                        connectionId = chatDetails.connection.id,
                                                        otherUserId = chatDetails.otherUser.id,
                                                        otherUserName = chatDetails.otherUser.name ?: "Connection",
                                                        videoEnabled = true
                                                    )
                                                },
                                            )
                                        }
                                    } else if (!menuStyle.isIOS) {
                                        DropdownMenu(
                                            expanded = showCallMenu,
                                            onDismissRequest = { showCallMenu = false },
                                            shape = RoundedCornerShape(22.dp),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            tonalElevation = 8.dp,
                                            shadowElevation = 16.dp
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
                                        if (isGroupChat) {
                                            "Everyone here is in a verified click — say hello to the group."
                                        } else {
                                            "Say hi to ${chatDetails.otherUser.name}!"
                                        },
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
                                                val bubble: @Composable () -> Unit = {
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
                                                        onLongPress = { contextMenuMessage = messageWithUser },
                                                        onSwipeReply = {
                                                            viewModel.startReplyTo(it)
                                                        },
                                                    )
                                                }
                                                if (messageWithUser.message.messageType == "call_log") {
                                                    bubble()
                                                } else {
                                                    AnimatedVisibilityChatBubble(
                                                        messageId = messageWithUser.message.id,
                                                        isSent = messageWithUser.isSent,
                                                        content = bubble
                                                    )
                                                }
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
                            archivedConnectionIds = archivedConnectionIds,
                            hiddenConnectionIds = hiddenConnectionIds,
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
                    val replyBannerVisible = replyingTo != null && editingMessageId == null
                    // iOS: ~44dp matches comfortable body text + cursor; Android unchanged.
                    val auxButtonSize = if (composerStyle.isIOS) 44.dp else 52.dp
                    val composerRowVPad = if (composerStyle.isIOS) 6.dp else 8.dp
                    val composerRowHPad = 8.dp
                    val attachIconSize = if (composerStyle.isIOS) 24.dp else 26.dp
                    val sendIconSize = if (composerStyle.isIOS) 22.dp else 20.dp
                    val fieldCorner = if (composerStyle.isIOS) 20.dp else 12.dp
                    val replyShape = RoundedCornerShape(if (composerStyle.isIOS) 12.dp else 14.dp)
                    val composerStripInteraction = remember { MutableInteractionSource() }
                    val composerStripBg = MaterialTheme.colorScheme.background
                    // Match ChatMessageBubble / ChatLinkifyText (bodyMedium).
                    val composerInputTextStyle = MaterialTheme.typography.bodyMedium
                    // Full-bleed hit target behind composer so gaps between + / field / send do not
                    // pass touches through to ConnectionsListView (iOS overlay stack).
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(composerStripBg)
                                .clickable(
                                    indication = null,
                                    interactionSource = composerStripInteraction,
                                ) {},
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = effectiveImePadding)
                                .padding(horizontal = composerRowHPad, vertical = composerRowVPad)
                        ) {
                        Crossfade(
                            targetState = replyBannerVisible,
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            modifier = Modifier.fillMaxWidth(),
                            label = "replyComposerBanner",
                        ) { showBanner ->
                            if (!showBanner) {
                                Spacer(Modifier.height(0.dp).fillMaxWidth())
                            } else {
                                val rt = replyingTo
                                if (rt == null) {
                                    Spacer(Modifier.height(0.dp).fillMaxWidth())
                                } else {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = replyShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = if (composerStyle.isIOS) 0.45f else 0.55f,
                                        ),
                                        border = if (composerStyle.isIOS) {
                                            BorderStroke(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                            )
                                        } else {
                                            null
                                        },
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Reply,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Replying to ${rt.user.name ?: "message"}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    replySnippetForMetadata(rt.message.content, maxLen = 100),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.clearReplyTarget() },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "Cancel reply",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (replyBannerVisible) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        val composerGap = if (composerStyle.isIOS) 6.dp else 8.dp
                        val fieldSideInset = auxButtonSize + composerGap
                        val attachTint = PrimaryBlue.copy(alpha = if (isSending) 0.35f else 0.92f)
                        val attachInteraction = remember { MutableInteractionSource() }
                        val canSend = messageInput.trim().isNotEmpty() && !isSending
                        val sendGradient = Brush.linearGradient(
                            colors = if (canSend) listOf(PrimaryBlue, LightBlue)
                            else listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        // Box layout: outlined field is inset between buttons; overlay circles stay on top for hits.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(auxButtonSize)
                        ) {
                            val composerFieldInteraction = remember { MutableInteractionSource() }
                            val fieldColors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue.copy(alpha = if (composerStyle.isIOS) 0.50f else 0.65f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (composerStyle.isIOS) 0.08f else 0.12f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (composerStyle.isIOS) 0.30f else 0.4f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (composerStyle.isIOS) 0.18f else 0.25f)
                            )
                            val fieldShape = RoundedCornerShape(fieldCorner)
                            // Vertically center single-line cap / placeholder within the fixed bar height.
                            val composerTextStyleCentered = composerInputTextStyle.merge(
                                TextStyle(
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                                ),
                            )
                            val approxLineBodyDp = 24.dp
                            val innerVerticalPad =
                                ((auxButtonSize - approxLineBodyDp) / 2).coerceIn(6.dp, 12.dp)
                            val innerHorizontalPad = 12.dp
                            val fieldDecorPadding = PaddingValues(
                                start = innerHorizontalPad,
                                end = innerHorizontalPad,
                                top = innerVerticalPad,
                                bottom = innerVerticalPad,
                            )
                            // Horizontal Modifier.padding shrinks the outlined region so it does not wrap the
                            // attach/send buttons; DecorationBox contentPadding is inner text only.
                            BasicTextField(
                                value = messageInput,
                                onValueChange = { viewModel.updateMessageInput(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = fieldSideInset, end = fieldSideInset)
                                    .height(auxButtonSize)
                                    .align(Alignment.Center),
                                enabled = !isSending,
                                textStyle = composerTextStyleCentered.merge(
                                    TextStyle(color = MaterialTheme.colorScheme.onSurface),
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.None
                                ),
                                singleLine = false,
                                maxLines = 5,
                                interactionSource = composerFieldInteraction,
                                cursorBrush = SolidColor(PrimaryBlue),
                                decorationBox = { innerTextField ->
                                    OutlinedTextFieldDefaults.DecorationBox(
                                        value = messageInput,
                                        innerTextField = innerTextField,
                                        enabled = !isSending,
                                        singleLine = false,
                                        visualTransformation = VisualTransformation.None,
                                        interactionSource = composerFieldInteraction,
                                        placeholder = {
                                            Text(
                                                when {
                                                    editingMessageId != null -> "Edit message…"
                                                    isGroupChat -> "Message the group…"
                                                    else -> "Message ${chatDetails.otherUser.name}…"
                                                },
                                                style = composerTextStyleCentered,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            )
                                        },
                                        colors = fieldColors,
                                        contentPadding = fieldDecorPadding,
                                        container = {
                                            OutlinedTextFieldDefaults.Container(
                                                enabled = !isSending,
                                                isError = false,
                                                interactionSource = composerFieldInteraction,
                                                modifier = Modifier,
                                                colors = fieldColors,
                                                shape = fieldShape,
                                            )
                                        }
                                    )
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(auxButtonSize)
                                    .zIndex(4f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(PrimaryBlue.copy(alpha = if (isSending) 0.06f else 0.12f))
                                        .clickable(
                                            interactionSource = attachInteraction,
                                            indication = if (composerStyle.useRipple) {
                                                ripple(bounded = true)
                                            } else {
                                                null
                                            },
                                            enabled = !isSending,
                                            onClick = { attachmentMenuExpanded = true },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Attach",
                                        tint = attachTint,
                                        modifier = Modifier.size(attachIconSize),
                                    )
                                }
                                DropdownMenu(
                                    expanded = attachmentMenuExpanded,
                                    onDismissRequest = { attachmentMenuExpanded = false },
                                    shape = RoundedCornerShape(if (composerStyle.isIOS) 14.dp else 12.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = if (composerStyle.isIOS) {
                                        BorderStroke(
                                            0.5.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                        )
                                    } else {
                                        null
                                    },
                                    tonalElevation = if (composerStyle.isIOS) 0.dp else 4.dp,
                                    shadowElevation = if (composerStyle.isIOS) 0.dp else 8.dp,
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Photo library",
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Image,
                                                contentDescription = null,
                                                tint = PrimaryBlue.copy(alpha = 0.9f),
                                            )
                                        },
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            mediaPickers.openPhotoLibrary()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Take photo",
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.PhotoCamera,
                                                contentDescription = null,
                                                tint = PrimaryBlue.copy(alpha = 0.9f),
                                            )
                                        },
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            mediaPickers.openCamera()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Voice message",
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Mic,
                                                contentDescription = null,
                                                tint = PrimaryBlue.copy(alpha = 0.9f),
                                            )
                                        },
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            mediaPickers.openVoiceRecorder()
                                        },
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(auxButtonSize)
                                    .zIndex(4f)
                                    .clip(if (composerStyle.isIOS) CircleShape else RoundedCornerShape(fieldCorner))
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
                                    modifier = Modifier.size(sendIconSize)
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
        val sheetConn = successState?.chatDetails?.connection
        ConnectionActionSheet(
            chatDetails = successState?.chatDetails,
            currentUserId = currentUserId,
            isArchived = sheetConn != null && sheetConn.id in archivedConnectionIds,
            isServerLifecycleArchived = sheetConn?.isServerLifecycleArchived() == true,
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
            onUnarchive = {
                val connId = (chatMessagesState as? ChatMessagesState.Success)?.chatDetails?.connection?.id
                if (connId != null) viewModel.unarchiveConnection(connId)
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
            },
            onLeaveGroup = {
                val gid = successState?.chatDetails?.groupClique?.groupId ?: return@ConnectionActionSheet
                viewModel.leaveVerifiedClique(gid) { ok -> if (ok) onBackPressed() }
            },
            onDeleteGroup = {
                val gid = successState?.chatDetails?.groupClique?.groupId ?: return@ConnectionActionSheet
                viewModel.deleteVerifiedClique(gid) { ok -> if (ok) onBackPressed() }
            },
        )
    }

    if (showRenameGroupDialog) {
        val gid = (chatMessagesState as? ChatMessagesState.Success)?.chatDetails?.groupClique?.groupId
        AlertDialog(
            onDismissRequest = { showRenameGroupDialog = false },
            title = { Text("Rename group") },
            text = {
                OutlinedTextField(
                    value = renameGroupDraft,
                    onValueChange = { renameGroupDraft = it },
                    singleLine = true,
                    label = { Text("Group name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (gid != null) {
                            viewModel.renameVerifiedClique(gid, renameGroupDraft) { }
                        }
                        showRenameGroupDialog = false
                    },
                    enabled = renameGroupDraft.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameGroupDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    } // End outer Box
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
    archivedConnectionIds: Set<String>,
    hiddenConnectionIds: Set<String>,
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
                            .filter {
                                it.connection.id != currentChatId &&
                                    it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                            }
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
    val enterFade = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    val enterSlide = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)
    val exitSlide = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(enterFade) +
            slideInVertically(animationSpec = enterSlide, initialOffsetY = { it / 10 }) +
            scaleIn(enterFade, initialScale = 0.97f),
        exit = fadeOut(animationSpec = tween(140)) +
            slideOutVertically(animationSpec = exitSlide, targetOffsetY = { it / 12 }) +
            scaleOut(animationSpec = tween(200), targetScale = 0.96f)
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
                Text(
                    text = formatMessageTime(message.timeCreated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

/** 0..1 — how close the swipe is to triggering reply (for hint UI). */
private fun replyDragHintProgress(rawTravelPx: Float, isSent: Boolean, thresholdPx: Float): Float {
    if (thresholdPx <= 0f) return 0f
    val directed = if (isSent) (-rawTravelPx).coerceAtLeast(0f) else rawTravelPx.coerceAtLeast(0f)
    return (directed / thresholdPx).coerceIn(0f, 1f)
}

/**
 * Maps finger travel → bubble translation: **quadratic** for the first pixels (zero slope at 0 so
 * nothing “snaps”), then **linear** to the cap, then rubber. Paired with [swipeRawTravelFromVisual]
 * for picking up mid-settle.
 */
private fun swipeVisualFromRawTravel(
    rawTravelPx: Float,
    isSent: Boolean,
    maxVisualPx: Float,
    softKneePx: Float,
    trackGain: Float,
    overflowRubberGain: Float,
): Float {
    val cap = maxVisualPx.coerceAtLeast(1f)
    val directed = if (isSent) (-rawTravelPx).coerceAtLeast(0f) else rawTravelPx.coerceAtLeast(0f)
    if (directed <= 0f) return 0f

    val d1 = softKneePx.coerceAtLeast(1f)
    val gain = trackGain.coerceIn(0.01f, 1f)
    val k = gain / (2f * d1)
    val v1 = k * d1 * d1
    val rubber = overflowRubberGain.coerceAtLeast(0.001f)
    val dReach = d1 + (cap - v1) / gain

    val magnitude = when {
        directed <= d1 -> k * directed * directed
        directed <= dReach -> v1 + gain * (directed - d1)
        else -> cap + (directed - dReach) * rubber
    }
    return if (isSent) -magnitude else magnitude
}

/** Inverse of [swipeVisualFromRawTravel]. */
private fun swipeRawTravelFromVisual(
    visualPx: Float,
    isSent: Boolean,
    maxVisualPx: Float,
    softKneePx: Float,
    trackGain: Float,
    overflowRubberGain: Float,
): Float {
    val cap = maxVisualPx.coerceAtLeast(1f)
    val v = if (isSent) (-visualPx).coerceAtLeast(0f) else visualPx.coerceAtLeast(0f)
    if (v <= 0f) return 0f

    val d1 = softKneePx.coerceAtLeast(1f)
    val gain = trackGain.coerceIn(0.01f, 1f)
    val k = gain / (2f * d1)
    val v1 = k * d1 * d1
    val rubber = overflowRubberGain.coerceAtLeast(0.001f)
    val dReach = d1 + (cap - v1) / gain

    val directedRaw = when {
        v <= v1 -> sqrt((v / k).coerceAtLeast(0f))
        v <= cap -> d1 + (v - v1) / gain
        else -> dReach + (v - cap) / rubber
    }
    return if (isSent) -directedRaw else directedRaw
}

/** iOS: Material [DropdownMenu] uses a platform popup that can show white bands in dark mode; this is fully themed. */
@Composable
private fun ChatCallOptionsIosSurface(
    onVoice: () -> Unit,
    onVideo: () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Surface(
        modifier = Modifier.widthIn(min = 200.dp),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(0.5.dp, outline),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onVoice,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = onSurface)
                Spacer(Modifier.width(12.dp))
                Text("Voice call", style = MaterialTheme.typography.bodyLarge, color = onSurface)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onVideo,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = onSurface)
                Spacer(Modifier.width(12.dp))
                Text("Video call", style = MaterialTheme.typography.bodyLarge, color = onSurface)
            }
        }
    }
}

/** Reply affordance drawn **behind** the bubble; uncovered as the bubble slides (no layout gutter). */
@Composable
private fun ReplySwipeSideIcon(
    hintProgress: Float,
    hintAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val t = hintProgress.coerceIn(0f, 1f)
    val smooth = t * t * (3f - 2f * t)
    val scale = 0.82f + 0.18f * smooth
    val visibility = smooth * (0.28f + 0.72f * smooth).coerceIn(0f, 1f)
    val a = (visibility * hintAlpha).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = a
                scaleX = scale
                scaleY = scale
            }
            .size(40.dp)
            .clip(CircleShape)
            .background(LightBlue.copy(alpha = (0.18f + 0.22f * smooth) * a)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = "Reply",
            tint = LightBlue.copy(alpha = a),
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun formatChatAudioDuration(durationMs: Long, fallbackSec: Int?): String {
    val totalSec = when {
        durationMs > 0 -> (durationMs / 1000).toInt()
        fallbackSec != null && fallbackSec > 0 -> fallbackSec
        else -> 0
    }
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun formatChatAudioPositionMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

@Composable
private fun ChatAudioBubbleRow(
    mediaUrl: String,
    durationSeconds: Int?,
    contentColor: Color,
    accentColor: Color,
) {
    val hintMs = remember(durationSeconds) {
        durationSeconds?.takeIf { it > 0 }?.times(1000L) ?: 0L
    }
    val player = rememberChatAudioPlayer(mediaUrl, durationHintMs = hintMs)
    val durationMs = remember(player.durationMs, hintMs) {
        when {
            player.durationMs > 0 -> player.durationMs
            hintMs > 0 -> hintMs
            else -> 1L
        }
    }
    var draggingSlider by remember(mediaUrl) { mutableStateOf(false) }
    var sliderValue by remember(mediaUrl) { mutableFloatStateOf(0f) }
    LaunchedEffect(player.positionMs, durationMs, player.isPlaying, draggingSlider) {
        if (!draggingSlider && durationMs > 0) {
            sliderValue = (player.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
    }
    val positionDisplayMs = if (draggingSlider) {
        (sliderValue * durationMs).toLong()
    } else {
        player.positionMs
    }
    val timeLabel = "${formatChatAudioPositionMs(positionDisplayMs)} / ${formatChatAudioDuration(durationMs, durationSeconds)}"
    Row(
        modifier = Modifier.widthIn(min = 0.dp, max = 280.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = { player.togglePlayPause() },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (player.isPlaying) "Pause" else "Play",
                tint = accentColor,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Slider(
                value = sliderValue,
                onValueChange = {
                    draggingSlider = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    player.seekTo((sliderValue * durationMs).toLong().coerceIn(0L, durationMs))
                    draggingSlider = false
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = contentColor.copy(alpha = 0.28f),
                ),
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun ChatMessageOverflowButton(
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(30.dp),
    ) {
        Icon(
            Icons.Outlined.MoreVert,
            contentDescription = "Message actions",
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun ChatMessageBubble(
    messageWithUser: MessageWithUser,
    currentUserId: String?,
    reactions: List<compose.project.click.click.data.models.MessageReaction> = emptyList(),
    onToggleReaction: (String) -> Unit = {},
    onForward: (String) -> Unit,
    onLongPress: (MessageWithUser) -> Unit = {},
    /** Horizontal swipe toward the center of the screen starts a reply (same idea as drag L→R on incoming). */
    onSwipeReply: (MessageWithUser) -> Unit = {},
) {
    val message = messageWithUser.message
    if (message.messageType == "call_log") {
        CallLogSystemRow(message = message)
        return
    }
    val isSent = messageWithUser.isSent
    val replyRef = message.replyRef()
    val mt = message.messageType.lowercase()
    val mediaUrl = message.mediaUrlOrNull()
    val audioDurSec = message.parsedMediaMetadata()?.durationSeconds
    val isImageMessage = mt == ChatMessageType.IMAGE && mediaUrl != null

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

    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 52.dp.toPx() } }
    val maxSwipeVisualPx = remember(density) { with(density) { 42.dp.toPx() } }
    val swipeSoftKneePx = remember(density) { with(density) { 5.dp.toPx() } }
    val swipeTrackGain = remember { 0.56f }
    val swipeOverflowRubberGain = remember { 0.12f }
    var rawSwipeTravelPx by remember(message.id) { mutableFloatStateOf(0f) }
    var displayVisualPx by remember(message.id) { mutableFloatStateOf(0f) }
    var swipeDragging by remember(message.id) { mutableStateOf(false) }
    val onSwipeReplyState = rememberUpdatedState(onSwipeReply)
    val messageWithUserState = rememberUpdatedState(messageWithUser)
    val scope = rememberCoroutineScope()
    var swipeSettleJob by remember(message.id) { mutableStateOf<Job?>(null) }

    val draggableState = rememberDraggableState { delta ->
        swipeSettleJob?.cancel()
        swipeSettleJob = null
        rawSwipeTravelPx = (rawSwipeTravelPx + delta).coerceIn(-340f, 340f)
        displayVisualPx = swipeVisualFromRawTravel(
            rawTravelPx = rawSwipeTravelPx,
            isSent = isSent,
            maxVisualPx = maxSwipeVisualPx,
            softKneePx = swipeSoftKneePx,
            trackGain = swipeTrackGain,
            overflowRubberGain = swipeOverflowRubberGain,
        )
    }

    val swipeDragModifier = Modifier.draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = {
            swipeSettleJob?.cancel()
            swipeSettleJob = null
            swipeDragging = true
            if (displayVisualPx != 0f) {
                rawSwipeTravelPx = swipeRawTravelFromVisual(
                    visualPx = displayVisualPx,
                    isSent = isSent,
                    maxVisualPx = maxSwipeVisualPx,
                    softKneePx = swipeSoftKneePx,
                    trackGain = swipeTrackGain,
                    overflowRubberGain = swipeOverflowRubberGain,
                ).coerceIn(-340f, 340f)
            }
        },
        onDragStopped = {
            swipeDragging = false
            val raw = rawSwipeTravelPx
            val shouldReply = if (isSent) raw <= -swipeThresholdPx else raw >= swipeThresholdPx
            if (shouldReply) {
                onSwipeReplyState.value(messageWithUserState.value)
            }
            rawSwipeTravelPx = 0f
            swipeSettleJob = scope.launch {
                try {
                    animate(
                        initialValue = displayVisualPx,
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 480,
                            easing = CubicBezierEasing(0.17f, 0.88f, 0.24f, 1f),
                        ),
                    ) { v, _ ->
                        displayVisualPx = v
                    }
                } finally {
                    swipeSettleJob = null
                }
            }
        },
    )

    val dragging = swipeDragging
    val rawHintP = replyDragHintProgress(rawSwipeTravelPx, isSent, swipeThresholdPx)
    val visualHintP = (abs(displayVisualPx) / maxSwipeVisualPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val hintProgress = if (dragging) maxOf(rawHintP, visualHintP) else visualHintP
    val hintAlpha = if (dragging) {
        (0.52f + 0.48f * (hintProgress * hintProgress)).coerceIn(0f, 1f)
    } else {
        (0.38f + 0.5f * (hintProgress * hintProgress)).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (!isSent) {
                    ReplySwipeSideIcon(
                        hintProgress = hintProgress,
                        hintAlpha = hintAlpha,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .zIndex(0f),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
                ) {
                    Column(
                        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
                        modifier = Modifier
                            .graphicsLayer { translationX = displayVisualPx }
                            .then(swipeDragModifier),
                    ) {
                if (isSent) {
                    if (isImageMessage) {
                        Column(
                            modifier = Modifier.widthIn(max = 280.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = mediaUrl,
                                    contentDescription = "Photo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.35f))
                                            .align(Alignment.Center),
                                    )
                                    ChatMessageOverflowButton(
                                        onClick = { onLongPress(messageWithUser) },
                                        tint = Color.White.copy(alpha = 0.92f),
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                }
                            }
                            val capImg = message.content.trim()
                            if (capImg.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectionContainer {
                                    ChatLinkifyText(
                                        text = capImg,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        linkColor = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMessageTime(message.timeCreated),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.align(Alignment.End),
                            )
                            Text(
                                text = if (message.isRead) "Read" else "Sent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    } else {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(sentShape)
                            .background(sentGradient)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        ChatMessageOverflowButton(
                            onClick = { onLongPress(messageWithUser) },
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        Column(
                            modifier = Modifier.padding(end = 22.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.55f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            when {
                                mt == ChatMessageType.AUDIO && mediaUrl != null -> {
                                    ChatAudioBubbleRow(
                                        mediaUrl = mediaUrl,
                                        durationSeconds = audioDurSec,
                                        contentColor = Color.White,
                                        accentColor = Color.White,
                                    )
                                    val cap = message.content.trim()
                                    if (cap.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        SelectionContainer {
                                            ChatLinkifyText(
                                                text = cap,
                                                color = Color.White,
                                                linkColor = Color(0xFFB7E0FF),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    SelectionContainer {
                                        Column {
                                            if (message.content.isNotBlank()) {
                                                ChatLinkifyText(
                                                    text = message.content,
                                                    color = Color.White,
                                                    linkColor = Color(0xFFB7E0FF),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMessageTime(message.timeCreated),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.End),
                            )
                            Text(
                                text = if (message.isRead) "Read" else "Sent",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                    }
                } else {
                    if (isImageMessage) {
                        Column(
                            modifier = Modifier.widthIn(max = 280.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            val onBody = MaterialTheme.colorScheme.onSurface
                            val linkC = MaterialTheme.colorScheme.primary
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = mediaUrl,
                                    contentDescription = "Photo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .border(1.dp, PrimaryBlue.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp)),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.22f))
                                            .align(Alignment.Center),
                                    )
                                    ChatMessageOverflowButton(
                                        onClick = { onLongPress(messageWithUser) },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f),
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                }
                            }
                            val capRx = message.content.trim()
                            if (capRx.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectionContainer {
                                    ChatLinkifyText(
                                        text = capRx,
                                        color = onBody,
                                        linkColor = linkC,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMessageTime(message.timeCreated),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    } else {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .border(width = 1.dp, color = PrimaryBlue.copy(alpha = 0.18f), shape = receivedShape)
                            .clip(receivedShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        ChatMessageOverflowButton(
                            onClick = { onLongPress(messageWithUser) },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        Column(
                            modifier = Modifier.padding(end = 22.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            val onBody = MaterialTheme.colorScheme.onSurface
                            val linkC = MaterialTheme.colorScheme.primary
                            when {
                                mt == ChatMessageType.AUDIO && mediaUrl != null -> {
                                    ChatAudioBubbleRow(
                                        mediaUrl = mediaUrl,
                                        durationSeconds = audioDurSec,
                                        contentColor = onBody,
                                        accentColor = linkC,
                                    )
                                    val cap = message.content.trim()
                                    if (cap.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        SelectionContainer {
                                            ChatLinkifyText(
                                                text = cap,
                                                color = onBody,
                                                linkColor = linkC,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    SelectionContainer {
                                        Column {
                                            if (message.content.isNotBlank()) {
                                                ChatLinkifyText(
                                                    text = message.content,
                                                    color = onBody,
                                                    linkColor = linkC,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMessageTime(message.timeCreated),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                    }
                }

                if (reactionGroups.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        reactionGroups.forEach { (emoji, count) ->
                            val isOwnReaction = reactions.any { it.reactionType == emoji && it.userId == currentUserId }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isOwnReaction) PrimaryBlue.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f),
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isOwnReaction) PrimaryBlue.copy(alpha = 0.5f)
                                        else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable { onToggleReaction(emoji) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = if (count > 1) "$emoji $count" else emoji,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
                }
                }
                if (isSent) {
                    ReplySwipeSideIcon(
                        hintProgress = hintProgress,
                        hintAlpha = hintAlpha,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .zIndex(0f),
                    )
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showDeleteMessageConfirm by remember { mutableStateOf(false) }
    var showDeleteMessageFinalConfirm by remember { mutableStateOf(false) }
    var emojiPickMode by remember { mutableStateOf(false) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // Material ModalBottomSheet (not Calf): avoids native iOS sheet chrome / white bands in in-app dark mode.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        val sheetBg = MaterialTheme.colorScheme.surfaceContainerHigh
        val onSurface = MaterialTheme.colorScheme.onSurface
        val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(sheetBg)
                .padding(bottom = 32.dp)
        ) {
            if (emojiPickMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { emojiPickMode = false }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = onSurface
                        )
                    }
                    Text(
                        "Choose emoji",
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(44.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(EmojiCatalog.all, key = { it }) { em ->
                        Text(
                            text = em,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clickable {
                                    viewModel.addReaction(message.id, em)
                                    dismiss()
                                }
                                .padding(8.dp)
                        )
                    }
                }
            } else {
            // ── Reply ─────────────────────────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text("Reply", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply",
                        tint = PrimaryBlue
                    )
                },
                modifier = Modifier.clickable {
                    if (message.messageType != "call_log") {
                        viewModel.startReplyTo(messageWithUser)
                        dismiss()
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

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

            TextButton(
                onClick = { emojiPickMode = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("More emojis…", color = PrimaryBlue)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

            val imageUrl = message.mediaUrlOrNull()
            if (message.messageType.lowercase() == ChatMessageType.IMAGE && imageUrl != null) {
                ListItem(
                    headlineContent = {
                        Text("Download image", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = "Download image",
                            tint = PrimaryBlue
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch {
                            saveChatImageToGallery(imageUrl).onSuccess { dismiss() }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // ── Copy action ───────────────────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text(
                        if (message.messageType.lowercase() == ChatMessageType.IMAGE) "Copy caption & link"
                        else "Copy",
                        color = onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = onVariant
                    )
                },
                modifier = Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(message.copyableText()))
                    dismiss()
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // ── Edit action (sent messages only) ──────────────────────────────
            if (isSent) {
                ListItem(
                    headlineContent = {
                        Text("Edit", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit message",
                            tint = PrimaryBlue
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
    }

    // ── Delete message confirmation dialog ──────────────────────────────────
    if (showDeleteMessageConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteMessageConfirm = false },
            title = { Text("Delete Message?") },
            text = { Text("This message will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteMessageConfirm = false
                        showDeleteMessageFinalConfirm = true
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMessageConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Final delete confirmation dialog ───────────────────────────────────
    if (showDeleteMessageFinalConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteMessageFinalConfirm = false },
            title = { Text("Delete Message Permanently?") },
            text = { Text("This action is permanent and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessage(message.id)
                        showDeleteMessageFinalConfirm = false
                        dismiss()
                    }
                ) {
                    Text("Yes, Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMessageFinalConfirm = false }) {
                    Text("Cancel")
                }
            }
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
    currentUserId: String?,
    isArchived: Boolean = false,
    isServerLifecycleArchived: Boolean = false,
    onDismiss: () -> Unit,
    onNudge: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onArchive: () -> Unit = {},
    onUnarchive: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReport: (String) -> Unit = {},
    onBlock: () -> Unit = {},
    onLeaveGroup: () -> Unit = {},
    onDeleteGroup: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val isGroup = chatDetails?.groupClique != null
    val uid = currentUserId.orEmpty()
    val isGroupCreator = isGroup && uid.isNotBlank() && chatDetails?.groupClique?.createdByUserId == uid
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
    var finalConfirmButtonColor by remember { mutableStateOf(PrimaryBlue) }
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

    // Material ModalBottomSheet (not Calf): native iOS page sheet was forcing system chrome / white bands
    // while the app uses in-app dark mode; this stays in Compose and follows MaterialTheme.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        val sheetBg = MaterialTheme.colorScheme.surfaceContainerHigh
        val onSurface = MaterialTheme.colorScheme.onSurface
        val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(sheetBg)
                .padding(bottom = 32.dp)
        ) {
            // Connection / group header
            chatDetails?.let { details ->
                val title = if (isGroup) {
                    details.groupClique?.name?.trim()?.ifBlank { null } ?: "Verified click"
                } else {
                    details.otherUser.name ?: "Connection"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurface,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .align(Alignment.CenterHorizontally)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            }

            if (!isGroup) {
                // ── Nudge (1:1 only) ───────────────────────────────────────────
                ListItem(
                    headlineContent = {
                        Text("Nudge 👋", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                    },
                    supportingContent = {
                        Text("Send a quick ping", color = onVariant,
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
                            Text("Unarchive", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                if (isServerLifecycleArchived) {
                                    "Remove from your Archived tab (server-archived connections stay read-only)"
                                } else {
                                    "Move this connection back to Active"
                                },
                                color = onVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Unarchive, contentDescription = null,
                                tint = onVariant)
                        },
                        modifier = Modifier.clickable {
                            showUnarchiveConfirm = true
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                } else if (!isServerLifecycleArchived) {
                    ListItem(
                        headlineContent = {
                            Text("Archive", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text("Hide this connection (recoverable)", color = onVariant,
                                style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Archive, contentDescription = null,
                                tint = onVariant)
                        },
                        modifier = Modifier.clickable {
                            showArchiveConfirm = true
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

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
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                ListItem(
                    headlineContent = {
                        Text("Leave Group", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = onVariant)
                    },
                    modifier = Modifier.clickable {
                        openFinalConfirm(
                            title = "Leave group?",
                            body = "You will lose access to this verified click and its messages.",
                            buttonLabel = "Leave",
                            buttonColor = Color(0xFFFF4444)
                        ) {
                            onLeaveGroup()
                            dismiss()
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (isGroupCreator) {
                    ListItem(
                        headlineContent = {
                            Text("Delete Group", color = Color(0xFFFF4444),
                                style = MaterialTheme.typography.bodyLarge)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Delete, contentDescription = null,
                                tint = Color(0xFFFF4444))
                        },
                        modifier = Modifier.clickable {
                            openFinalConfirm(
                                title = "Delete group?",
                                body = "Permanently deletes this verified click for everyone. This cannot be undone.",
                                buttonLabel = "Delete",
                                buttonColor = Color(0xFFFF4444)
                            ) {
                                onDeleteGroup()
                                dismiss()
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
            )
        }
    }

    if (showUnarchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showUnarchiveConfirm = false },
            title = { Text("Unarchive Connection?") },
            text = { Text("This connection will return to your Active list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnarchiveConfirm = false
                        openFinalConfirm(
                            title = "Confirm Unarchive",
                            body = "Move this connection back to Active now?",
                            buttonLabel = "Yes, Unarchive",
                            buttonColor = PrimaryBlue
                        ) {
                            onUnarchive()
                        }
                    }
                ) {
                    Text("Unarchive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Archive confirmation dialog ────────────────────────────────────────────
    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive Connection?") },
            text = { Text("This connection will be hidden from your list. You can recover it later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveConfirm = false
                        openFinalConfirm(
                            title = "Confirm Archive",
                            body = "Archive this connection now? You can unarchive it later.",
                            buttonLabel = "Yes, Archive",
                            buttonColor = PrimaryBlue
                        ) {
                            onArchive()
                        }
                    }
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Block confirmation dialog ──────────────────────────────────────────────
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block User?") },
            text = {
                Text("They won't be able to contact you and this connection will be removed. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockConfirm = false
                        openFinalConfirm(
                            title = "Confirm Block",
                            body = "Block this user and remove this connection? This cannot be undone.",
                            buttonLabel = "Yes, Block",
                            buttonColor = Color(0xFFFF4444)
                        ) {
                            onBlock()
                        }
                    }
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Connection?") },
            text = {
                Text("This will permanently remove this connection and all messages. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        openFinalConfirm(
                            title = "Confirm Remove",
                            body = "Permanently remove this connection and all messages? This cannot be undone.",
                            buttonLabel = "Yes, Remove",
                            buttonColor = Color(0xFFFF4444)
                        ) {
                            onDelete()
                        }
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Report reason dialog ───────────────────────────────────────────────────
    if (showReportDialog) {
        val reportOn = MaterialTheme.colorScheme.onSurface
        val reportVariant = MaterialTheme.colorScheme.onSurfaceVariant
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report User", color = reportOn) },
            text = {
                Column {
                    Text(
                        "Please describe the issue:",
                        color = reportVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        placeholder = { Text("Reason for report...", color = reportVariant.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = reportOn,
                            unfocusedTextColor = reportOn,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
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
                    Text("Cancel", color = PrimaryBlue)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── Final destructive-action confirmation dialog ────────────────────────
    if (showFinalConfirm) {
        AlertDialog(
            onDismissRequest = {
                showFinalConfirm = false
                finalConfirmAction = null
            },
            title = { Text(finalConfirmTitle) },
            text = { Text(finalConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        finalConfirmAction?.invoke()
                        showFinalConfirm = false
                        finalConfirmAction = null
                        dismiss()
                    }
                ) {
                    Text(finalConfirmButtonLabel, color = finalConfirmButtonColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFinalConfirm = false
                        finalConfirmAction = null
                    }
                ) {
                    Text("Cancel")
                }
            }
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
