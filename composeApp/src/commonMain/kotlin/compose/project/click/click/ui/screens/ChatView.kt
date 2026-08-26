@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset // pragma: allowlist secret
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.collaboration.CollaborationSessionManager // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.encounter.EncounterTetherManager // pragma: allowlist secret
import compose.project.click.click.encounter.EncounterTetherWidgetBridge // pragma: allowlist secret
import compose.project.click.click.encounter.recentEncounterId // pragma: allowlist secret
import compose.project.click.click.encounter.tetherCompassMessage // pragma: allowlist secret
import compose.project.click.click.notifications.NotificationRuntimeState // pragma: allowlist secret
import compose.project.click.click.platform.KeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.platform.rememberKeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChannelLoadingView // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatHeaderIconButton // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatWarmLoadingView // pragma: allowlist secret
import compose.project.click.click.ui.chat.ForwardDialog // pragma: allowlist secret
import compose.project.click.click.ui.chat.GroupMembersPickerContext // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatDismissKeyboardAfterScrollConnection // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatPeerStatusSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimelineFollowUsesAnimation // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimelineShouldFollowInbound // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatNativeKeyboardInsets // pragma: allowlist secret
import compose.project.click.click.ui.chat.scrollChatTimelineToLatest // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.ui.components.platformNativeHeaderClearance // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberEdgeToEdgeBottomPadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberGlassToastState // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.util.AvailabilityOverlapCache // pragma: allowlist secret
import compose.project.click.click.util.ViewerAvailabilityBubblesCache // pragma: allowlist secret
import compose.project.click.click.util.hasActiveAvailabilityIntentOverlap // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatListState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers // pragma: allowlist secret
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext // pragma: allowlist secret
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    viewModel: ChatViewModel,
    chatId: String,
    targetMessageId: String? = null,
    onBackPressed: () -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    onOpenGroupMembersPicker: (GroupMembersPickerContext) -> Unit = {},
    onOpenDisposableRoll: ((connectionId: String) -> Unit)? = null,
    onOpenDisposableRollForChat: ((chatId: String) -> Unit)? = null,
    shareableBeacons: List<compose.project.click.click.data.models.MapBeacon> = emptyList(),
    mapViewModel: compose.project.click.click.viewmodel.MapViewModel? = null,
    onShareBeaconToChats: (
        (
            beacon: compose.project.click.click.data.models.MapBeacon,
            chatIds: List<String>,
            openConnectionId: String?,
        ) -> Unit
    )? = null,
    /**
     * When true, timestamp peek is driven by the parent `InteractiveSwipeBackContainer` horizontal
     * drag (register callbacks with [onRegisterSwipeBackRightToLeftPeek]). When false, the chat
     * surface uses a local full-width left-drag handler instead.
     */
    integrateTimestampPeekWithSwipeBackContainer: Boolean = false,
    onRegisterSwipeBackRightToLeftPeek: (InteractiveSwipeBackRightToLeftPeek?) -> Unit = {},
    /**
     * When set (iOS chat overlay), matches [InteractiveSwipeBackContainer]'s drag pixels so the IME
     * can hide after a short rightward threshold while the container's [graphicsLayer] carries the
     * horizontal slide — avoid stacking a redundant [Modifier.offset] for the same translation.
     */
    parentInteractiveBackSwipePx: MutableFloatState? = null,
    keyboardHeightProvider: KeyboardHeightProvider = rememberKeyboardHeightProvider(),
) {
    val chatMessagesState by viewModel.chatMessagesState.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val isPeerOnline by viewModel.isPeerOnline.collectAsState()
    val chatListState by viewModel.chatListState.collectAsState()
    val archivedConnectionIds by viewModel.archivedConnectionIds.collectAsState()
    val coreConnectionIds by AppDataManager.coreConnectionIds.collectAsState()
    val hiddenConnectionIds by viewModel.hiddenConnectionIds.collectAsState()
    val edgeBottomInset = rememberEdgeToEdgeBottomPadding()
    val editingMessageId by viewModel.editingMessageId.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val collaborationSessions by CollaborationSessionManager.sessions.collectAsState()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val onlineUsers by AppDataManager.onlineUsers.collectAsState()

    // Icebreaker prompts state
    val icebreakerPrompts by viewModel.icebreakerPrompts.collectAsState()
    val showIcebreakerPanel by viewModel.showIcebreakerPanel.collectAsState()
    val icebreakerCooldownRemainingSec by viewModel.icebreakerCooldownRemainingSec.collectAsState()
    val hasMoreOlderMessages by viewModel.hasMoreOlderMessages.collectAsState()
    val isLoadingOlderMessages by viewModel.isLoadingOlderMessages.collectAsState()

    // Fresh scroll state per chat so opening a thread doesn't keep the previous scroll offset
    val listState = remember(chatId) { LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val platformStyle = LocalPlatformStyle.current
    val nativeKeyboardInsets =
        rememberChatNativeKeyboardInsets(
            keyboardHeightProvider = keyboardHeightProvider,
            subtractTabBarOverlay = true,
        )
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val focusManager = LocalFocusManager.current
    val focusManagerState = rememberUpdatedState(focusManager)
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardControllerState = rememberUpdatedState(keyboardController)

    /** Skips IME dismiss while [listState.scrollToItem] snaps the newest-first timeline (not user-driven). */
    val suppressKeyboardDismissWhileProgrammaticTimelineScroll = remember { mutableStateOf(false) }

    /**
     * Dismisses the IME after the user finishes a scroll gesture — never mid-drag / mid-fling.
     * Clearing focus while coasting resizes keyboard insets and kills LazyColumn fling physics.
     */
    val keyboardDismissScrollThresholdPx = remember(density) { with(density) { 16.dp.toPx() } }
    val dismissKeyboardOnUserMessageScroll =
        remember(keyboardDismissScrollThresholdPx) {
            chatDismissKeyboardAfterScrollConnection(
                thresholdPx = keyboardDismissScrollThresholdPx,
                isSuppressed = { suppressKeyboardDismissWhileProgrammaticTimelineScroll.value },
                onDismiss = { focusManagerState.value.clearFocus() },
            )
        }

    var imeClearedForInteractiveBackSwipe = false
    LaunchedEffect(parentInteractiveBackSwipePx) {
        val ref = parentInteractiveBackSwipePx ?: return@LaunchedEffect
        // Defer IME teardown until the swipe is well underway (~28% width). Hiding the
        // keyboard at 8px resized the LazyColumn mid-gesture and tanked interactive-back fps.
        // Use a plain flag (not mutableState) — writing Compose state here recomposed the
        // entire ChatView (all image bubbles) every threshold cross and made backswipe laggy.
        snapshotFlow { ref.floatValue }.collect { offset ->
            val commitPx = with(density) { 120.dp.toPx() }
            when {
                offset > commitPx && !imeClearedForInteractiveBackSwipe -> {
                    imeClearedForInteractiveBackSwipe = true
                    keyboardControllerState.value?.hide()
                    focusManagerState.value.clearFocus()
                }
                offset <= 0f -> imeClearedForInteractiveBackSwipe = false
            }
        }
    }

    // Connection action sheet (archive, delete, report, block)
    val showConnectionSheetState = remember { mutableStateOf(false) }
    var showConnectionSheet by showConnectionSheetState
    val showRenameGroupDialogState = remember { mutableStateOf(false) }
    var showRenameGroupDialog by showRenameGroupDialogState
    val renameGroupDraftState = remember { mutableStateOf("") }
    var renameGroupDraft by renameGroupDraftState
    // Message context sheet (reactions, edit, delete, copy)
    val contextMenuMessageState = remember { mutableStateOf<MessageWithUser?>(null) }
    var contextMenuMessage by contextMenuMessageState
    val forwardMessageIdState = remember { mutableStateOf<String?>(null) }
    var forwardMessageId by forwardMessageIdState
    val expandedPhotoTargetState = remember { mutableStateOf<MessageWithUser?>(null) }
    var expandedPhotoTarget by expandedPhotoTargetState
    val openBeaconDetailIdState = remember { mutableStateOf<String?>(null) }
    var openBeaconDetailId by openBeaconDetailIdState
    val openBeaconDetailFallbackState = remember { mutableStateOf<compose.project.click.click.data.models.MapBeacon?>(null) }
    var openBeaconDetailFallback by openBeaconDetailFallbackState
    val openBeaconDetailMetadataState = remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    var openBeaconDetailMetadata by openBeaconDetailMetadataState
    val openBeaconDetailContentState = remember { mutableStateOf<String?>(null) }
    var openBeaconDetailContent by openBeaconDetailContentState
    val toastState = rememberGlassToastState()
    val tetherPayload by EncounterTetherManager.activeTetherPayload.collectAsState()
    val tetherToastMessageState = remember { mutableStateOf<String?>(null) }
    var tetherToastMessage by tetherToastMessageState
    val tetherSenderAckState = remember { mutableStateOf<String?>(null) }
    var tetherSenderAck by tetherSenderAckState
    val nativeNavChrome = LocalPlatformStyle.current.isIOS
    val hintedChatRow =
        (chatListState as? ChatListState.Success)
            ?.chats
            ?.firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
    val successChat =
        (chatMessagesState as? ChatMessagesState.Success)?.takeIf { state ->
            state.chatDetails.connection.id == chatId || state.chatDetails.chat.id == chatId
        }
    val bindIsGroup =
        successChat?.chatDetails?.groupClique != null ||
            hintedChatRow?.groupClique != null
    val bindAvatarUrl =
        successChat?.let { details ->
            details.chatDetails.groupClique
                ?.avatarUrl
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: details.chatDetails.otherUser.image
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
        } ?: hintedChatRow
            ?.groupClique
            ?.avatarUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: hintedChatRow
                ?.otherUser
                ?.image
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
    val bindTitle =
        successChat?.let { details ->
            if (details.chatDetails.groupClique != null) {
                details.chatDetails.groupClique
                    ?.name
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Group" }
            } else {
                details.chatDetails.otherUser.name ?: "Chat"
            }
        } ?: hintedChatRow
            ?.groupClique
            ?.name
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: hintedChatRow
                ?.otherUser
                ?.name
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: "Chat"
    val bindOnline =
        if (bindIsGroup) {
            null
        } else {
            val peerId =
                successChat
                    ?.chatDetails
                    ?.otherUser
                    ?.id ?: hintedChatRow?.otherUser?.id
            peerId?.let { it in onlineUsers || isPeerOnline }
        }
    val bindStatusSubtitle =
        if (bindIsGroup) {
            null
        } else if (bindOnline != null || successChat != null || hintedChatRow != null) {
            chatPeerStatusSubtitle(isTyping = isPeerTyping, isOnline = bindOnline == true)
        } else {
            null
        }
    val chatNativeHasStackedSubtitle = bindStatusSubtitle != null
    val chatNativeClearance =
        platformNativeHeaderClearance(
            statusBarTop = topInset,
            collapseFraction = 1f,
            hasSubtitle = chatNativeHasStackedSubtitle,
            stackSubtitle = chatNativeHasStackedSubtitle,
        )
    ChatViewNativeNavBinding(
        nativeNavChrome = nativeNavChrome,
        chatId = chatId,
        bindTitle = bindTitle,
        bindStatusSubtitle = bindStatusSubtitle,
        bindOnline = bindOnline,
        bindIsGroup = bindIsGroup,
        bindAvatarUrl = bindAvatarUrl,
        successChat = successChat,
        hintedChatRow = hintedChatRow,
        onOpenUserProfile = onOpenUserProfile,
        onOpenGroupMembersPicker = onOpenGroupMembersPicker,
        onBackPressed = onBackPressed,
        showConnectionSheetState = showConnectionSheetState,
        showRenameGroupDialogState = showRenameGroupDialogState,
        renameGroupDraftState = renameGroupDraftState,
    )

    LaunchedEffect(nudgeResult) {
        val r = nudgeResult ?: return@LaunchedEffect
        viewModel.clearNudgeResult()
        toastState.show(coroutineScope, r)
    }

    LaunchedEffect(chatId, currentUserId) {
        if (currentUserId.isNullOrBlank()) return@LaunchedEffect
        viewModel.loadChatMessages(chatId)
    }

    val activeApiChatId = (chatMessagesState as? ChatMessagesState.Success)?.chatDetails?.chat?.id
    DisposableEffect(activeApiChatId) {
        NotificationRuntimeState.setActiveChatId(activeApiChatId)
        onDispose {
            NotificationRuntimeState.setActiveChatId(null)
        }
    }

    // Newest-first + reverseLayout pins latest messages next to the composer.
    // Snap only on open and when a peer message arrives while already near the bottom —
    // never on every size change (load-older / prefetch merge), which caused lag + teleports.
    val successMessages = (chatMessagesState as? ChatMessagesState.Success)?.messages.orEmpty()
    val peerNewestMessageId =
        successMessages
            .lastOrNull()
            ?.takeIf { !it.isSent }
            ?.message
            ?.id
    val initialTimelineScrollDoneState = remember(chatId) { mutableStateOf(false) }
    var initialTimelineScrollDone by initialTimelineScrollDoneState
    val focusedSearchMessageIdState = remember(chatId) { mutableStateOf<String?>(null) }
    var focusedSearchMessageId by focusedSearchMessageIdState

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val maxVisibleIndex = info.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            Triple(maxVisibleIndex, total, listState.firstVisibleItemIndex)
        }.collect { (maxVisibleIndex, total, _) ->
            if (total > 0 &&
                hasMoreOlderMessages &&
                !isLoadingOlderMessages &&
                maxVisibleIndex >= total - 3
            ) {
                viewModel.loadOlderMessages()
            }
        }
    }

    LaunchedEffect(chatId, successMessages.isNotEmpty(), targetMessageId) {
        if (successMessages.isEmpty() || initialTimelineScrollDone) return@LaunchedEffect
        if (!targetMessageId.isNullOrBlank()) {
            val found = viewModel.ensureTargetMessageLoaded(targetMessageId)
            if (!found) {
                initialTimelineScrollDone = true
                scrollChatTimelineToLatest(
                    listState = listState,
                    suppressKeyboardDismiss = suppressKeyboardDismissWhileProgrammaticTimelineScroll,
                )
            }
            return@LaunchedEffect
        }
        initialTimelineScrollDone = true
        scrollChatTimelineToLatest(
            listState = listState,
            suppressKeyboardDismiss = suppressKeyboardDismissWhileProgrammaticTimelineScroll,
        )
    }

    LaunchedEffect(peerNewestMessageId) {
        if (peerNewestMessageId == null) return@LaunchedEffect
        if (chatTimelineShouldFollowInbound(listState.firstVisibleItemIndex, initialTimelineScrollDone)) {
            scrollChatTimelineToLatest(
                listState = listState,
                suppressKeyboardDismiss = suppressKeyboardDismissWhileProgrammaticTimelineScroll,
                animated = chatTimelineFollowUsesAnimation(initialTimelineScrollDone),
            )
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        val successForMesh = chatMessagesState as? ChatMessagesState.Success
        if (successForMesh != null) {
            ChatAmbientMeshBackground(
                connection = successForMesh.chatDetails.connection,
                isHubNeutral = successForMesh.chatDetails.groupClique != null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            when (val state = chatMessagesState) {
                is ChatMessagesState.Loading -> {
                    val hintedRow =
                        (chatListState as? ChatListState.Success)
                            ?.chats
                            ?.firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
                    if (hintedRow != null &&
                        (
                            hintedRow.lastMessage != null ||
                                hintedRow.chat.messages.isNotEmpty()
                        )
                    ) {
                        ChatWarmLoadingView(
                            topInset = topInset,
                            onBackPressed = onBackPressed,
                            chatRow = hintedRow,
                            composeHeader = !nativeNavChrome,
                        )
                    } else {
                        ChatChannelLoadingView(
                            topInset = topInset,
                            onBackPressed = onBackPressed,
                            composeHeader = !nativeNavChrome,
                        )
                    }
                }
                is ChatMessagesState.Error -> {
                    if (nativeNavChrome) {
                        Spacer(modifier = Modifier.fillMaxWidth().height(chatNativeClearance))
                    } else {
                        Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ChatHeaderIconButton(
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    onClick = onBackPressed,
                                    showBorder = true,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Chat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Error loading chat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                is ChatMessagesState.Success -> {
                    val stateMatchesThread =
                        state.chatDetails.connection.id == chatId ||
                            (
                                !state.chatDetails.chat.id
                                    .isNullOrBlank() &&
                                    state.chatDetails.chat.id == chatId
                            )
                    if (!stateMatchesThread) {
                        val hintedRow =
                            (chatListState as? ChatListState.Success)
                                ?.chats
                                ?.firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
                        if (hintedRow != null) {
                            ChatWarmLoadingView(
                                topInset = topInset,
                                onBackPressed = onBackPressed,
                                chatRow = hintedRow,
                                composeHeader = !nativeNavChrome,
                            )
                        } else {
                            ChatChannelLoadingView(
                                topInset = topInset,
                                onBackPressed = onBackPressed,
                                composeHeader = !nativeNavChrome,
                            )
                        }
                        return@Column
                    }
                    val chatDetails = state.chatDetails
                    val messages = state.messages
                    val reactionsMap by viewModel.messageReactions.collectAsState()
                    // R1.1: hoist secure media load state above the LazyColumn so each item doesn't
                    // subscribe to the full map.
                    val isGroupChat = chatDetails.groupClique != null
                    val overlapRepo = remember { SupabaseRepository() }
                    val chatPeerId = chatDetails.otherUser.id
                    val tetherChannelId =
                        remember(
                            chatDetails.connection.id,
                            collaborationSessions,
                        ) {
                            collaborationSessions[chatDetails.connection.id]?.encounterId?.takeIf { it.isNotBlank() }
                                ?: chatDetails.connection.recentEncounterId()?.takeIf { it.isNotBlank() }
                                ?: chatDetails.connection.id
                        }
                    val tetherMemberNamesById =
                        remember(chatDetails.groupMemberUsers, currentUser, chatDetails.otherUser) {
                            (chatDetails.groupMemberUsers + listOfNotNull(currentUser, chatDetails.otherUser))
                                .distinctBy { it.id }
                                .associate { user ->
                                    user.id to (user.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Friend")
                                }
                        }
                    val peerDisplayName = chatDetails.otherUser.name ?: "Friend"
                    LaunchedEffect(tetherChannelId, peerDisplayName) {
                        EncounterTetherWidgetBridge.updateRecentEncounter(tetherChannelId, peerDisplayName)
                    }
                    LaunchedEffect(tetherChannelId, currentUserId, chatPeerId, peerDisplayName, tetherMemberNamesById) {
                        val self = currentUserId ?: return@LaunchedEffect
                        val resolver: (String) -> String = { senderId ->
                            if (isGroupChat) {
                                tetherMemberNamesById[senderId] ?: "Someone"
                            } else if (senderId == chatPeerId) {
                                peerDisplayName
                            } else {
                                "Friend"
                            }
                        }
                        EncounterTetherManager.setPeerNameResolver(resolver)
                        EncounterTetherManager.subscribe(tetherChannelId, self, resolver)
                    }
                    LaunchedEffect(tetherPayload, chatPeerId, currentUserId, isGroupChat) {
                        val ping =
                            tetherPayload ?: run {
                                tetherToastMessage = null
                                return@LaunchedEffect
                            }
                        if (ping.senderId == currentUserId) return@LaunchedEffect
                        if (!isGroupChat && ping.senderId != chatPeerId) return@LaunchedEffect
                        val receiver = LocationService().getCurrentLocation()
                        tetherToastMessage =
                            if (receiver != null) {
                                tetherCompassMessage(
                                    senderName = ping.senderName,
                                    receiverLat = receiver.latitude,
                                    receiverLng = receiver.longitude,
                                    senderLat = ping.latitude,
                                    senderLng = ping.longitude,
                                )
                            } else {
                                "${ping.senderName} pinged their tether"
                            }
                    }
                    var chatHasIntentOverlap by remember(chatDetails.otherUser.id, currentUserId, isGroupChat) {
                        val v = currentUserId
                        val cached =
                            if (!isGroupChat && !v.isNullOrBlank()) {
                                AvailabilityOverlapCache.get(v, chatPeerId)
                            } else {
                                null
                            }
                        mutableStateOf(cached == true)
                    }
                    LaunchedEffect(chatDetails.otherUser.id, currentUserId, isGroupChat) {
                        if (isGroupChat) {
                            chatHasIntentOverlap = false
                            return@LaunchedEffect
                        }
                        val v =
                            currentUserId ?: run {
                                chatHasIntentOverlap = false
                                return@LaunchedEffect
                            }
                        val peer = chatDetails.otherUser.id
                        AvailabilityOverlapCache.get(v, peer)?.let { cached ->
                            chatHasIntentOverlap = cached
                            return@LaunchedEffect
                        }
                        val mine =
                            ViewerAvailabilityBubblesCache.get(v)
                                ?: overlapRepo.fetchPeerProfileAvailabilityBubbles(v, v).also {
                                    ViewerAvailabilityBubblesCache.put(v, it)
                                }
                        val result =
                            withContext(Dispatchers.Default) {
                                val theirs = overlapRepo.fetchPeerProfileAvailabilityBubbles(v, peer)
                                hasActiveAvailabilityIntentOverlap(mine, theirs)
                            }
                        AvailabilityOverlapCache.put(v, peer, result)
                        chatHasIntentOverlap = result
                    }
                    val typingPeerLabel =
                        remember(chatDetails.otherUser.name, isGroupChat) {
                            if (isGroupChat) {
                                "Someone is typing"
                            } else {
                                "${chatDetails.otherUser.name ?: "Someone"} is typing"
                            }
                        }
                    val groupTitle =
                        chatDetails.groupClique
                            ?.name
                            ?.trim()
                            ?.ifBlank { null }
                            ?: "Verified click"
                    val memberSummaryLine =
                        remember(chatDetails.groupClique, chatDetails.groupMemberUsers, currentUser) {
                            val gc = chatDetails.groupClique ?: return@remember null
                            val self = currentUser
                            val nameParts =
                                buildList {
                                    val byId =
                                        (chatDetails.groupMemberUsers + listOfNotNull(self))
                                            .distinctBy { it.id }
                                            .associateBy { it.id }
                                    gc.memberUserIds.sorted().forEach { id ->
                                        val u = byId[id]
                                        val part =
                                            u?.firstName?.trim()?.takeIf { it.isNotEmpty() }
                                                ?: u
                                                    ?.name
                                                    ?.trim()
                                                    ?.split(Regex("\\s+"))
                                                    ?.firstOrNull()
                                                    ?.takeIf { it.isNotEmpty() }
                                                ?: "Member"
                                        add(part)
                                    }
                                }
                            "${gc.memberUserIds.size} members: ${nameParts.joinToString(", ")}"
                        }
                    val mediaPickers =
                        rememberChatMediaPickers(
                            onImagePicked = { bytes, mime -> viewModel.stageMediaForUpload(bytes, mime) },
                            onAudioPicked = { bytes, mime, dur -> viewModel.sendChatAudio(bytes, mime, dur?.toInt()) },
                            onFilePicked = { picked ->
                                viewModel.sendChatFile(picked.bytes, picked.mimeType, picked.fileName)
                            },
                            onMediaAccessBlocked = { msg ->
                                toastState.show(coroutineScope, msg)
                            },
                        )

                    /**
                     * Full-screen ambient mesh behind header + thread. Top padding uses
                     * [WindowInsets.statusBars] only so opening the IME does not push the header past the
                     * top via [WindowInsets.safeDrawing] / display cutout coupling.
                     *
                     * iOS keeps the timeline scrollable by padding its bottom edge while the composer
                     * follows the native keyboard on a graphics layer.
                     */
                    val reverseListNewestEdgePad = 6.dp
                    val showIcebreaker = showIcebreakerPanel && icebreakerPrompts.isNotEmpty() && messages.size < 5
                    val icebreakerPanelHeightPxState = remember { mutableIntStateOf(0) }
                    var icebreakerPanelHeightPx by icebreakerPanelHeightPxState
                    val icebreakerTimelineTopReserve =
                        if (showIcebreaker) {
                            val measured = with(density) { icebreakerPanelHeightPx.toDp() }
                            if (measured > 0.dp) measured + 8.dp else 228.dp
                        } else {
                            0.dp
                        }
                    val messageContentModifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()

                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            ChatViewSuccessHeader(
                                nativeNavChrome = nativeNavChrome,
                                chatNativeClearance = chatNativeClearance,
                                topInset = topInset,
                                chatDetails = chatDetails,
                                isGroupChat = isGroupChat,
                                groupTitle = groupTitle,
                                memberSummaryLine = memberSummaryLine,
                                isPeerTyping = isPeerTyping,
                                isPeerOnline = isPeerOnline,
                                onlineUsers = onlineUsers,
                                coreConnectionIds = coreConnectionIds,
                                chatHasIntentOverlap = chatHasIntentOverlap,
                                onBackPressed = onBackPressed,
                                onOpenUserProfile = onOpenUserProfile,
                                onOpenGroupMembersPicker = onOpenGroupMembersPicker,
                                showConnectionSheetState = showConnectionSheetState,
                                showRenameGroupDialogState = showRenameGroupDialogState,
                                renameGroupDraftState = renameGroupDraftState,
                            )

                            ChatViewTimelinePane(
                                viewModel = viewModel,
                                chatId = chatId,
                                targetMessageId = targetMessageId,
                                state = state,
                                chatDetails = chatDetails,
                                messages = messages,
                                isGroupChat = isGroupChat,
                                currentUserId = currentUserId,
                                activeApiChatId = activeApiChatId,
                                listState = listState,
                                coroutineScope = coroutineScope,
                                nativeKeyboardInsets = nativeKeyboardInsets,
                                dismissKeyboardOnUserMessageScroll = dismissKeyboardOnUserMessageScroll,
                                suppressKeyboardDismissWhileProgrammaticTimelineScroll = suppressKeyboardDismissWhileProgrammaticTimelineScroll,
                                initialTimelineScrollDoneState = initialTimelineScrollDoneState,
                                focusedSearchMessageIdState = focusedSearchMessageIdState,
                                integrateTimestampPeekWithSwipeBackContainer = integrateTimestampPeekWithSwipeBackContainer,
                                onRegisterSwipeBackRightToLeftPeek = onRegisterSwipeBackRightToLeftPeek,
                                reverseListNewestEdgePad = reverseListNewestEdgePad,
                                messageContentModifier = messageContentModifier,
                                showIcebreaker = showIcebreaker,
                                icebreakerPrompts = icebreakerPrompts,
                                icebreakerCooldownRemainingSec = icebreakerCooldownRemainingSec,
                                icebreakerPanelHeightPxState = icebreakerPanelHeightPxState,
                                icebreakerTimelineTopReserve = icebreakerTimelineTopReserve,
                                reactionsMap = reactionsMap,
                                isLoadingOlderMessages = isLoadingOlderMessages,
                                isPeerTyping = isPeerTyping,
                                typingPeerLabel = typingPeerLabel,
                                editingMessageId = editingMessageId,
                                replyingTo = replyingTo,
                                mediaPickers = mediaPickers,
                                tetherChannelId = tetherChannelId,
                                tetherSenderAckState = tetherSenderAckState,
                                onOpenDisposableRoll = onOpenDisposableRoll,
                                onOpenDisposableRollForChat = onOpenDisposableRollForChat,
                                shareableBeacons = shareableBeacons,
                                mapViewModel = mapViewModel,
                                forwardMessageIdState = forwardMessageIdState,
                                contextMenuMessageState = contextMenuMessageState,
                                expandedPhotoTargetState = expandedPhotoTargetState,
                                openBeaconDetailIdState = openBeaconDetailIdState,
                                openBeaconDetailFallbackState = openBeaconDetailFallbackState,
                                openBeaconDetailMetadataState = openBeaconDetailMetadataState,
                                openBeaconDetailContentState = openBeaconDetailContentState,
                            )

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
                                    onDismiss = { forwardMessageId = null },
                                )
                            }
                        }
                    }
                }
            }
        }

        ChatViewOverlays(
            viewModel = viewModel,
            chatMessagesState = chatMessagesState,
            currentUserId = currentUserId,
            archivedConnectionIds = archivedConnectionIds,
            coreConnectionIds = coreConnectionIds,
            topInset = topInset,
            edgeBottomInset = edgeBottomInset,
            toastState = toastState,
            shareableBeacons = shareableBeacons,
            mapViewModel = mapViewModel,
            onShareBeaconToChats = onShareBeaconToChats,
            onBackPressed = onBackPressed,
            tetherToastMessageState = tetherToastMessageState,
            tetherSenderAckState = tetherSenderAckState,
            expandedPhotoTargetState = expandedPhotoTargetState,
            openBeaconDetailIdState = openBeaconDetailIdState,
            openBeaconDetailFallbackState = openBeaconDetailFallbackState,
            openBeaconDetailMetadataState = openBeaconDetailMetadataState,
            openBeaconDetailContentState = openBeaconDetailContentState,
            contextMenuMessageState = contextMenuMessageState,
            showConnectionSheetState = showConnectionSheetState,
            showRenameGroupDialogState = showRenameGroupDialogState,
            renameGroupDraftState = renameGroupDraftState,
        )
    } // End outer Box
}
