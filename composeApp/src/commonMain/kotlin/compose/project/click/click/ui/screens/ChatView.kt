@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset // pragma: allowlist secret
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.Edit // pragma: allowlist secret
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
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
import compose.project.click.click.ui.chat.CHAT_SEARCH_FOCUS_HOLD_MS // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatBeaconDetailSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChannelLoadingView // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChromeHorizontalPadding // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChromeMotion // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatComposerStripReserve // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatExpandedPhotoPreview // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatGlassHeaderPlateTestTag // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatHeaderIconButton // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMessageTimeline // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatTypingDots // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatWarmLoadingView // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionChatMessageComposer // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionMenuAction // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialog // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialogs // pragma: allowlist secret
import compose.project.click.click.ui.chat.ForwardDialog // pragma: allowlist secret
import compose.project.click.click.ui.chat.GroupMembersPickerContext // pragma: allowlist secret
import compose.project.click.click.ui.chat.IcebreakerPanel // pragma: allowlist secret
import compose.project.click.click.ui.chat.MessageActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.applyTimestampPeekDragStep // pragma: allowlist secret
import compose.project.click.click.ui.chat.buildChatTimelineEntriesNewestFirst // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatBubbleReplySnippetStyle // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatBubbleScaledDp // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatDismissKeyboardAfterScrollConnection // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatPeerStatusSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimelineFollowUsesAnimation // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimelineShouldFollowInbound // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimestampPeekOnSwipeLeft // pragma: allowlist secret
import compose.project.click.click.ui.chat.groupMembersPickerContextFrom // pragma: allowlist secret
import compose.project.click.click.ui.chat.indexOfMessageId // pragma: allowlist secret
import compose.project.click.click.ui.chat.isTimestampPeekRevealed // pragma: allowlist secret
import compose.project.click.click.ui.chat.launchTimestampPeekReplyStyleSettle // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatNativeKeyboardInsets // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekRevealPx // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekSoftKneePx // pragma: allowlist secret
import compose.project.click.click.ui.chat.restoreTimestampPeekRawFromDisplay // pragma: allowlist secret
import compose.project.click.click.ui.chat.scrollChatTimelineToLatest // pragma: allowlist secret
import compose.project.click.click.ui.chat.scrollChatTimelineToMessage // pragma: allowlist secret
import compose.project.click.click.ui.components.AvatarWithOnlineIndicator // pragma: allowlist secret
import compose.project.click.click.ui.components.BindPlatformNativeNavigationBar // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.CoreConnectionAvatarFrame // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.GroupAvatar // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeChromeAction // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeChromeIdentity // pragma: allowlist secret
import compose.project.click.click.ui.components.TetherCompassToast // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedPopupFormDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.chatThreadKeyboardDock // pragma: allowlist secret
import compose.project.click.click.ui.components.groupAvatarClusterWidth // pragma: allowlist secret
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    var showConnectionSheet by remember { mutableStateOf(false) }
    var showRenameGroupDialog by remember { mutableStateOf(false) }
    var renameGroupDraft by remember { mutableStateOf("") }
    // Message context sheet (reactions, edit, delete, copy)
    var contextMenuMessage by remember { mutableStateOf<MessageWithUser?>(null) }
    var forwardMessageId by remember { mutableStateOf<String?>(null) }
    var expandedPhotoTarget by remember { mutableStateOf<MessageWithUser?>(null) }
    var openBeaconDetailId by remember { mutableStateOf<String?>(null) }
    var openBeaconDetailFallback by remember { mutableStateOf<compose.project.click.click.data.models.MapBeacon?>(null) }
    var openBeaconDetailMetadata by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    var openBeaconDetailContent by remember { mutableStateOf<String?>(null) }
    val toastState = rememberGlassToastState()
    val tetherPayload by EncounterTetherManager.activeTetherPayload.collectAsState()
    var tetherToastMessage by remember { mutableStateOf<String?>(null) }
    var tetherSenderAck by remember { mutableStateOf<String?>(null) }
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
    if (nativeNavChrome) {
        BindPlatformNativeNavigationBar(
            title = bindTitle,
            subtitle = bindStatusSubtitle,
            presenceOnline = bindOnline,
            identity =
                NativeChromeIdentity(
                    displayName = bindTitle,
                    email =
                        successChat
                            ?.chatDetails
                            ?.otherUser
                            ?.email
                            ?: hintedChatRow?.otherUser?.email,
                    avatarUrl = bindAvatarUrl,
                    userId =
                        successChat
                            ?.chatDetails
                            ?.groupClique
                            ?.groupId
                            ?: successChat
                                ?.chatDetails
                                ?.otherUser
                                ?.id
                            ?: hintedChatRow?.groupClique?.groupId
                            ?: hintedChatRow?.otherUser?.id
                            ?: chatId,
                    onClick = {
                        if (bindIsGroup) {
                            successChat?.chatDetails?.let { details ->
                                groupMembersPickerContextFrom(details)?.let(onOpenGroupMembersPicker)
                            }
                        } else {
                            val peerId =
                                successChat
                                    ?.chatDetails
                                    ?.otherUser
                                    ?.id
                                    ?: hintedChatRow?.otherUser?.id
                            if (peerId != null) onOpenUserProfile(peerId)
                        }
                    },
                ),
            onNavigateBack = onBackPressed,
            nativeTrailingActions =
                buildList {
                    if (bindIsGroup) {
                        add(
                            NativeChromeAction(
                                sfSymbol = "pencil",
                                contentDescription = "Rename group",
                                onClick = {
                                    renameGroupDraft =
                                        successChat
                                            ?.chatDetails
                                            ?.groupClique
                                            ?.name
                                            .orEmpty()
                                            .ifBlank { bindTitle }
                                    showRenameGroupDialog = true
                                },
                            ),
                        )
                    }
                    add(
                        NativeChromeAction(
                            sfSymbol = "ellipsis.circle",
                            contentDescription = "More options",
                            onClick = { showConnectionSheet = true },
                        ),
                    )
                },
            collapseFraction = 1f,
        )
    }

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
    var initialTimelineScrollDone by remember(chatId) { mutableStateOf(false) }
    var focusedSearchMessageId by remember(chatId) { mutableStateOf<String?>(null) }

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
                    var icebreakerPanelHeightPx by remember { mutableIntStateOf(0) }
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
                            if (nativeNavChrome) {
                                Spacer(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(chatNativeClearance)
                                            .testTag(ChatGlassHeaderPlateTestTag),
                                )
                            } else {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = topInset)
                                            .heightIn(min = 56.dp)
                                            .padding(horizontal = ChatChromeHorizontalPadding, vertical = 6.dp)
                                            .testTag(ChatGlassHeaderPlateTestTag),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ChatHeaderIconButton(
                                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            onClick = onBackPressed,
                                            showBorder = true,
                                        )

                                        if (isGroupChat) {
                                            val chatHeaderGroupAvatarSize = 34.dp
                                            val groupAvatarUrl =
                                                chatDetails.groupClique
                                                    ?.avatarUrl
                                                    ?.trim()
                                                    ?.takeIf { it.isNotEmpty() }
                                            val groupClusterWidth =
                                                if (groupAvatarUrl != null) {
                                                    chatHeaderGroupAvatarSize
                                                } else {
                                                    groupAvatarClusterWidth(
                                                        chatDetails.groupMemberUsers.size,
                                                        chatHeaderGroupAvatarSize,
                                                    )
                                                }
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .width(groupClusterWidth)
                                                        .heightIn(min = 40.dp)
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = ripple(bounded = false, radius = 22.dp),
                                                            onClick = {
                                                                groupMembersPickerContextFrom(chatDetails)
                                                                    ?.let(onOpenGroupMembersPicker)
                                                            },
                                                        ),
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                GroupAvatar(
                                                    members = chatDetails.groupMemberUsers,
                                                    avatarSize = chatHeaderGroupAvatarSize,
                                                    avatarUrl = groupAvatarUrl,
                                                )
                                            }
                                        } else {
                                            val isPeerCore = chatDetails.connection.id in coreConnectionIds
                                            val peerOnline =
                                                chatDetails.otherUser.id in onlineUsers || isPeerOnline
                                            AvatarWithOnlineIndicator(
                                                isOnline = peerOnline,
                                                indicatorSize = 9.dp,
                                                indicatorBorder = 1.25.dp,
                                            ) {
                                                CoreConnectionAvatarFrame(
                                                    isCore = isPeerCore,
                                                    avatarSize = 36.dp,
                                                    onClick = { onOpenUserProfile(chatDetails.otherUser.id) },
                                                ) {
                                                    ConnectionListUserAvatarFace(
                                                        displayName = chatDetails.otherUser.name,
                                                        email = chatDetails.otherUser.email,
                                                        avatarUrl = chatDetails.otherUser.image,
                                                        userId = chatDetails.otherUser.id,
                                                        modifier = Modifier.fillMaxSize(),
                                                        useCompactTypography = true,
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement =
                                                Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                                        ) {
                                            Text(
                                                text = if (isGroupChat) groupTitle else (chatDetails.otherUser.name ?: "Unknown"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
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
                                                val subtitleOnline =
                                                    chatDetails.otherUser.id in onlineUsers || isPeerOnline
                                                val statusText =
                                                    chatPeerStatusSubtitle(
                                                        isTyping = isPeerTyping,
                                                        isOnline = subtitleOnline,
                                                    )
                                                val showOnlineDot = subtitleOnline && !isPeerTyping
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    AnimatedVisibility(
                                                        visible = showOnlineDot,
                                                        enter = fadeIn() + expandVertically(),
                                                        exit = fadeOut() + shrinkVertically(),
                                                    ) {
                                                        Box(
                                                            modifier =
                                                                Modifier
                                                                    .size(8.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFF22C55E)),
                                                        )
                                                    }
                                                    AnimatedContent(
                                                        targetState = statusText,
                                                        transitionSpec = {
                                                            fadeIn(
                                                                animationSpec =
                                                                    spring(
                                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                                        stiffness = Spring.StiffnessMedium,
                                                                    ),
                                                            ) togetherWith
                                                                fadeOut(
                                                                    animationSpec =
                                                                        spring(
                                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                                            stiffness = Spring.StiffnessMedium,
                                                                        ),
                                                                )
                                                        },
                                                        label = "peer_presence_subtitle",
                                                    ) { label ->
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color =
                                                                if (label == "Online") {
                                                                    Color(0xFF16A34A)
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                                                },
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (!isGroupChat && chatHasIntentOverlap) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Filled.Bolt,
                                                contentDescription = "Shared availability",
                                                tint = Color(0xFFFBBF24),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }

                                        if (isGroupChat) {
                                            ChatHeaderIconButton(
                                                icon = Icons.Outlined.Edit,
                                                contentDescription = "Rename group",
                                                onClick = {
                                                    renameGroupDraft = groupTitle
                                                    showRenameGroupDialog = true
                                                },
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                            )
                                        }

                                        // Overflow / connection options
                                        ChatHeaderIconButton(
                                            icon = Icons.Filled.MoreVert,
                                            contentDescription = "More options",
                                            onClick = { showConnectionSheet = true },
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clipToBounds(),
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .chatThreadKeyboardDock(
                                                nativeKeyboardLiftPxState = nativeKeyboardInsets.liftPxState,
                                                clearNativeTabBar = true,
                                            ),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (showIcebreaker) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .align(Alignment.TopCenter)
                                                            .fillMaxWidth()
                                                            .onSizeChanged { icebreakerPanelHeightPx = it.height }
                                                            .zIndex(2f),
                                                ) {
                                                    IcebreakerPanel(
                                                        prompts = icebreakerPrompts,
                                                        onPromptClick = { prompt -> viewModel.useIcebreakerPrompt(prompt) },
                                                        onRefresh = { viewModel.refreshIcebreakerPrompts() },
                                                        onDismiss = { viewModel.dismissIcebreakerPanel() },
                                                        cooldownRemainingSec = icebreakerCooldownRemainingSec,
                                                    )
                                                }
                                            }

                                            // Messages
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize()
                                                        .padding(top = icebreakerTimelineTopReserve)
                                                        .clipToBounds()
                                                        .zIndex(1f),
                                            ) {
                                                if (state.isLoadingMessages && messages.isEmpty()) {
                                                    Box(
                                                        modifier =
                                                            messageContentModifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 8.dp, vertical = 24.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(36.dp),
                                                                color = PrimaryBlue,
                                                                strokeWidth = 3.dp,
                                                            )
                                                            Spacer(modifier = Modifier.height(12.dp))
                                                            Text(
                                                                "Loading messages…",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                } else if (messages.isEmpty()) {
                                                    Box(
                                                        modifier =
                                                            messageContentModifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 8.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        GlassCard(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            usePrimaryBorder = true,
                                                            contentPadding = 28.dp,
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.ChatBubbleOutline,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(48.dp),
                                                                    tint = PrimaryBlue.copy(alpha = 0.85f),
                                                                )
                                                                Spacer(modifier = Modifier.height(16.dp))
                                                                Text(
                                                                    "No messages yet",
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    style = MaterialTheme.typography.titleMedium,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    textAlign = TextAlign.Center,
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
                                                                    textAlign = TextAlign.Center,
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    val timelineEntries =
                                                        remember(messages) {
                                                            buildChatTimelineEntriesNewestFirst(messages)
                                                        }
                                                    LaunchedEffect(chatId, targetMessageId, timelineEntries) {
                                                        val id =
                                                            targetMessageId
                                                                ?.trim()
                                                                ?.takeIf { it.isNotEmpty() }
                                                                ?: return@LaunchedEffect
                                                        val index = timelineEntries.indexOfMessageId(id)
                                                        if (index < 0) {
                                                            val found = viewModel.ensureTargetMessageLoaded(id)
                                                            if (!found) {
                                                                initialTimelineScrollDone = true
                                                            }
                                                            return@LaunchedEffect
                                                        }
                                                        initialTimelineScrollDone = true
                                                        scrollChatTimelineToMessage(
                                                            listState = listState,
                                                            suppressKeyboardDismiss =
                                                            suppressKeyboardDismissWhileProgrammaticTimelineScroll,
                                                            index = index,
                                                        )
                                                        focusedSearchMessageId = id
                                                        delay(CHAT_SEARCH_FOCUS_HOLD_MS)
                                                        if (focusedSearchMessageId == id) {
                                                            focusedSearchMessageId = null
                                                        }
                                                    }
                                                    val rawTimestampPeekTravelPx = remember { mutableFloatStateOf(0f) }
                                                    val displayTimestampPeekVisualPx = remember { mutableFloatStateOf(0f) }
                                                    val timestampPeekSettleJob = remember { mutableStateOf<Job?>(null) }
                                                    val peekRevealPx = rememberTimestampPeekRevealPx()
                                                    val timestampPeekSoftKneePx = rememberTimestampPeekSoftKneePx()
                                                    DisposableEffect(
                                                        integrateTimestampPeekWithSwipeBackContainer,
                                                        peekRevealPx,
                                                        timestampPeekSoftKneePx,
                                                        coroutineScope,
                                                    ) {
                                                        val integrate = integrateTimestampPeekWithSwipeBackContainer
                                                        if (integrate) {
                                                            val integration =
                                                                InteractiveSwipeBackRightToLeftPeek(
                                                                    onGestureStart = {
                                                                        timestampPeekSettleJob.value?.cancel()
                                                                        timestampPeekSettleJob.value = null
                                                                        restoreTimestampPeekRawFromDisplay(
                                                                            rawLeftPx = rawTimestampPeekTravelPx,
                                                                            displayVisualPx = displayTimestampPeekVisualPx,
                                                                            maxRevealPx = peekRevealPx,
                                                                            softKneePx = timestampPeekSoftKneePx,
                                                                        )
                                                                    },
                                                                    onLeftDragDelta = { dLeft ->
                                                                        applyTimestampPeekDragStep(
                                                                            rawLeftPx = rawTimestampPeekTravelPx,
                                                                            displayVisualPx = displayTimestampPeekVisualPx,
                                                                            maxRevealPx = peekRevealPx,
                                                                            softKneePx = timestampPeekSoftKneePx,
                                                                            dLeftPx = dLeft,
                                                                        )
                                                                    },
                                                                    onLeftDragEnd = {
                                                                        coroutineScope.launchTimestampPeekReplyStyleSettle(
                                                                            rawLeftPx = rawTimestampPeekTravelPx,
                                                                            displayVisualPx = displayTimestampPeekVisualPx,
                                                                            settleJobHolder = timestampPeekSettleJob,
                                                                        )
                                                                    },
                                                                    isPeekRevealed = {
                                                                        isTimestampPeekRevealed(displayTimestampPeekVisualPx.floatValue)
                                                                    },
                                                                    onRightDragDelta = { dRight ->
                                                                        applyTimestampPeekDragStep(
                                                                            rawLeftPx = rawTimestampPeekTravelPx,
                                                                            displayVisualPx = displayTimestampPeekVisualPx,
                                                                            maxRevealPx = peekRevealPx,
                                                                            softKneePx = timestampPeekSoftKneePx,
                                                                            dLeftPx = -dRight,
                                                                        )
                                                                    },
                                                                    onRightDragFromRest = {
                                                                        timestampPeekSettleJob.value?.cancel()
                                                                        timestampPeekSettleJob.value = null
                                                                        rawTimestampPeekTravelPx.floatValue = 0f
                                                                        displayTimestampPeekVisualPx.floatValue = 0f
                                                                    },
                                                                )
                                                            onRegisterSwipeBackRightToLeftPeek(integration)
                                                        }
                                                        onDispose {
                                                            timestampPeekSettleJob.value?.cancel()
                                                            timestampPeekSettleJob.value = null
                                                            if (integrate) {
                                                                onRegisterSwipeBackRightToLeftPeek(null)
                                                            }
                                                        }
                                                    }
                                                    val newestSentMessage =
                                                        remember(messages) {
                                                            messages
                                                                .asSequence()
                                                                .filter {
                                                                    it.isSent
                                                                }.maxByOrNull { it.message.timeCreated }
                                                        }
                                                    ChatMessageTimeline(
                                                        timelineEntries = timelineEntries,
                                                        listState = listState,
                                                        newestSentMessage = newestSentMessage,
                                                        listBottomPadding =
                                                            PaddingValues(
                                                                start = 12.dp,
                                                                end = 12.dp,
                                                                top = 24.dp + reverseListNewestEdgePad,
                                                                bottom = 8.dp + ChatComposerStripReserve,
                                                            ),
                                                        dismissKeyboardOnUserMessageScroll = dismissKeyboardOnUserMessageScroll,
                                                        displayTimestampPeekVisualPx = displayTimestampPeekVisualPx,
                                                        peekRevealPx = peekRevealPx,
                                                        meshConnection = chatDetails.connection,
                                                        useHubNeutralMesh = isGroupChat,
                                                        isGroupChat = isGroupChat,
                                                        currentUserId = currentUserId,
                                                        reactionsMap = reactionsMap,
                                                        secureMediaHost = viewModel,
                                                        activeChatId = activeApiChatId ?: chatDetails.chat.id,
                                                        onToggleReaction = { messageId, reaction ->
                                                            viewModel.toggleReaction(messageId, reaction)
                                                        },
                                                        onForward = { msgId -> forwardMessageId = msgId },
                                                        onLongPress = { contextMenuMessage = it },
                                                        onSwipeReply = { viewModel.startReplyTo(it) },
                                                        onDownloadAttachment = { mwu, env ->
                                                            viewModel.downloadChatAttachment(mwu.message.id, env)
                                                        },
                                                        onExpandPhoto = { expandedPhotoTarget = it },
                                                        onOpenBeacon = { msg ->
                                                            val beaconId =
                                                                compose.project.click.click.data.models
                                                                    .beaconIdFromMetadata(msg.metadata)
                                                                    ?.trim()
                                                                    .orEmpty()
                                                            if (beaconId.isNotEmpty()) {
                                                                openBeaconDetailId = beaconId
                                                                val meta = msg.metadata as? kotlinx.serialization.json.JsonObject
                                                                openBeaconDetailMetadata = meta
                                                                openBeaconDetailContent = msg.content
                                                                openBeaconDetailFallback =
                                                                    compose.project.click.click.data.models.mapBeaconFromChatMetadata(
                                                                        beaconId = beaconId,
                                                                        metadata = meta,
                                                                        contentFallback = msg.content,
                                                                    )
                                                            }
                                                        },
                                                        isLoadingOlderMessages = isLoadingOlderMessages,
                                                        highlightedMessageId = focusedSearchMessageId,
                                                        modifier =
                                                            messageContentModifier
                                                                .padding(horizontal = 4.dp)
                                                                .then(
                                                                    if (!integrateTimestampPeekWithSwipeBackContainer) {
                                                                        Modifier.chatTimestampPeekOnSwipeLeft(
                                                                            maxRevealPx = peekRevealPx,
                                                                            softKneePx = timestampPeekSoftKneePx,
                                                                            rawLeftPx = rawTimestampPeekTravelPx,
                                                                            displayVisualPx = displayTimestampPeekVisualPx,
                                                                            scope = coroutineScope,
                                                                            settleJobHolder = timestampPeekSettleJob,
                                                                        )
                                                                    } else {
                                                                        Modifier
                                                                    },
                                                                ),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        // Typing indicator — label + bouncing dots (Realtime Broadcast)
                                        AnimatedVisibility(
                                            visible = isPeerTyping,
                                            enter =
                                                fadeIn(ChatChromeMotion.ShortFade) +
                                                    slideInVertically(
                                                        animationSpec = ChatChromeMotion.ShortSlide,
                                                        initialOffsetY = { it / 4 },
                                                    ),
                                            exit =
                                                fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                                                    slideOutVertically(
                                                        animationSpec = ChatChromeMotion.ShortSlide,
                                                        targetOffsetY = { it / 4 },
                                                    ),
                                        ) {
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 16.dp, end = 80.dp, bottom = 4.dp),
                                                horizontalArrangement = Arrangement.Start,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .border(
                                                                width = 1.dp,
                                                                color = PrimaryBlue.copy(alpha = 0.15f),
                                                                shape =
                                                                    RoundedCornerShape(
                                                                        topStart = chatBubbleScaledDp(6f),
                                                                        topEnd = chatBubbleScaledDp(21f),
                                                                        bottomStart = chatBubbleScaledDp(21f),
                                                                        bottomEnd = chatBubbleScaledDp(21f),
                                                                    ),
                                                            ).clip(
                                                                RoundedCornerShape(
                                                                    topStart = chatBubbleScaledDp(6f),
                                                                    topEnd = chatBubbleScaledDp(21f),
                                                                    bottomStart = chatBubbleScaledDp(21f),
                                                                    bottomEnd = chatBubbleScaledDp(21f),
                                                                ),
                                                            ).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                                                            .padding(
                                                                horizontal = chatBubbleScaledDp(18f),
                                                                vertical = chatBubbleScaledDp(12f),
                                                            ),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(chatBubbleScaledDp(9f)),
                                                    ) {
                                                        Text(
                                                            text = typingPeerLabel,
                                                            style = chatBubbleReplySnippetStyle(),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontStyle = FontStyle.Italic,
                                                        )
                                                        ChatTypingDots()
                                                    }
                                                }
                                            }
                                        }

                                        // Edit mode indicator strip
                                        if (editingMessageId != null) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = PrimaryBlue.copy(alpha = 0.12f),
                                            ) {
                                                Row(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Edit,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = PrimaryBlue,
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Editing message",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = PrimaryBlue,
                                                    )
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = { viewModel.cancelEditMessage() },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Close,
                                                            contentDescription = "Cancel edit",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = PrimaryBlue,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            ConnectionChatMessageComposer(
                                                viewModel = viewModel,
                                                chatDetails = chatDetails,
                                                isGroupChat = isGroupChat,
                                                editingMessageId = editingMessageId,
                                                replyingTo = replyingTo,
                                                mediaPickers = mediaPickers,
                                                onOpenDisposableRoll = {
                                                    if (isGroupChat) {
                                                        chatDetails.chat.id
                                                            ?.trim()
                                                            ?.takeIf { it.isNotEmpty() }
                                                            ?.let { onOpenDisposableRollForChat?.invoke(it) }
                                                    } else {
                                                        onOpenDisposableRoll?.invoke(chatDetails.connection.id)
                                                    }
                                                },
                                                tetherPingEnabled = tetherChannelId.isNotBlank() && !currentUserId.isNullOrBlank(),
                                                pingTetherLoading = tetherSenderAck != null,
                                                onPingTether = {
                                                    tetherSenderAck = "Ping tether sent"
                                                    EncounterTetherManager.pingTether(
                                                        encounterId = tetherChannelId,
                                                        senderId = currentUserId!!,
                                                    )
                                                },
                                                shareableBeacons = shareableBeacons,
                                                onRefreshShareableBeacons = {
                                                    mapViewModel?.refreshDiscoveryFeed()
                                                },
                                            )
                                        }
                                    }
                                }
                            }

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

        TetherCompassToast(
            message = tetherToastMessage,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(60f)
                    .padding(top = topInset + 64.dp),
            onDismissed = { tetherToastMessage = null },
        )

        TetherCompassToast(
            message = tetherSenderAck,
            visibleDurationMs = 2_400L,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(61f)
                    .padding(top = topInset + 64.dp),
            onDismissed = { tetherSenderAck = null },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            ChatExpandedPhotoPreview(
                target = expandedPhotoTarget,
                secureMediaHost = viewModel,
                onDismiss = { expandedPhotoTarget = null },
            )
        }

        val beaconDetailId = openBeaconDetailId
        val mapVm = mapViewModel
        if (beaconDetailId != null) {
            if (mapVm != null) {
                ChatBeaconDetailSheet(
                    beaconId = beaconDetailId,
                    mapViewModel = mapVm,
                    knownBeacons = shareableBeacons,
                    onDismissRequest = {
                        openBeaconDetailId = null
                        openBeaconDetailFallback = null
                        openBeaconDetailMetadata = null
                        openBeaconDetailContent = null
                    },
                    onShareBeaconToChats = onShareBeaconToChats,
                    messageFallback = openBeaconDetailFallback,
                    messageMetadata = openBeaconDetailMetadata,
                    messageContent = openBeaconDetailContent,
                )
            } else {
                LaunchedEffect(beaconDetailId) {
                    compose.project.click.click.deeplink.EventDeepLinkRouter
                        .setPendingBeaconId(beaconDetailId)
                    openBeaconDetailId = null
                }
            }
        }

        GlassToastHost(
            state = toastState,
            opaque = true,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(50f)
                    .padding(end = 20.dp, bottom = edgeBottomInset + 16.dp),
        )

        // Message long-press context sheet
        if (contextMenuMessage != null) {
            MessageActionSheet(
                messageWithUser = contextMenuMessage!!,
                viewModel = viewModel,
                onDismiss = { contextMenuMessage = null },
            )
        }

        var pendingConnectionDialog by remember { mutableStateOf<ConnectionSheetDialog?>(null) }
        var dialogGroupId by remember { mutableStateOf<String?>(null) }

        // Connection action sheet
        if (showConnectionSheet) {
            val successState = chatMessagesState as? ChatMessagesState.Success
            val sheetConn = successState?.chatDetails?.connection
            ConnectionActionSheet(
                chatDetails = successState?.chatDetails,
                currentUserId = currentUserId,
                isArchived = sheetConn != null && sheetConn.id in archivedConnectionIds,
                isServerLifecycleArchived = sheetConn?.isServerLifecycleArchived() == true,
                isCore = sheetConn != null && sheetConn.id in coreConnectionIds,
                onDismiss = { showConnectionSheet = false },
                onMenuAction = { action ->
                    val details = successState?.chatDetails
                    val connId = sheetConn?.id
                    when (action) {
                        ConnectionMenuAction.Nudge -> viewModel.sendNudge()
                        ConnectionMenuAction.Archive -> {
                            viewModel.archiveConnection { success ->
                                if (success) onBackPressed()
                            }
                        }
                        ConnectionMenuAction.Unarchive -> {
                            if (connId != null) viewModel.unarchiveConnection(connId)
                        }
                        ConnectionMenuAction.AddToCore -> {
                            if (connId != null) viewModel.addConnectionToCore(connId)
                        }
                        ConnectionMenuAction.RemoveFromCore -> {
                            if (connId != null) viewModel.removeConnectionFromCore(connId)
                        }
                        ConnectionMenuAction.MarkUnread -> {
                            if (connId != null) viewModel.markConversationUnread(connId)
                        }
                        ConnectionMenuAction.RequestRemove -> {
                            pendingConnectionDialog = ConnectionSheetDialog.Remove
                        }
                        ConnectionMenuAction.RequestReport -> {
                            pendingConnectionDialog = ConnectionSheetDialog.Report()
                        }
                        ConnectionMenuAction.RequestBlock -> {
                            pendingConnectionDialog = ConnectionSheetDialog.Block
                        }
                        ConnectionMenuAction.RequestLeaveGroup -> {
                            dialogGroupId = details?.groupClique?.groupId
                            pendingConnectionDialog = ConnectionSheetDialog.LeaveGroup
                        }
                        ConnectionMenuAction.RequestDeleteGroup -> {
                            dialogGroupId = details?.groupClique?.groupId
                            pendingConnectionDialog = ConnectionSheetDialog.DeleteGroup
                        }
                    }
                },
            )
        }

        ConnectionSheetDialogs(
            dialog = pendingConnectionDialog,
            onDismiss = {
                pendingConnectionDialog = null
                dialogGroupId = null
            },
            onConfirmRemove = {
                viewModel.deleteConnectionPermanently { success ->
                    if (success) onBackPressed()
                }
            },
            onConfirmBlock = {
                viewModel.blockUser { success ->
                    if (success) onBackPressed()
                }
            },
            onConfirmReport = { reason ->
                viewModel.reportConnection(reason) { }
            },
            onConfirmLeaveGroup = {
                dialogGroupId?.let { gid ->
                    viewModel.leaveVerifiedClique(gid) { ok -> if (ok) onBackPressed() }
                }
            },
            onConfirmDeleteGroup = {
                dialogGroupId?.let { gid ->
                    viewModel.deleteVerifiedClique(gid) { ok -> if (ok) onBackPressed() }
                }
            },
        )

        val renameGroupId = (chatMessagesState as? ChatMessagesState.Success)?.chatDetails?.groupClique?.groupId
        UnifiedPopupFormDialog(
            visible = showRenameGroupDialog,
            onDismissRequest = { showRenameGroupDialog = false },
            title = "Rename group",
            confirmLabel = "Save",
            onConfirm = {
                if (renameGroupDraft.isBlank()) return@UnifiedPopupFormDialog
                renameGroupId?.let { gid ->
                    viewModel.renameVerifiedClique(gid, renameGroupDraft) { }
                }
                showRenameGroupDialog = false
            },
            body = {
                ClickOutlinedTextField(
                    value = renameGroupDraft,
                    onValueChange = { renameGroupDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Group name", color = GlassSheetTokens.OnOledMuted()) },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GlassSheetTokens.OnOled(),
                            unfocusedTextColor = GlassSheetTokens.OnOled(),
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = GlassSheetTokens.GlassBorder(),
                            cursorColor = PrimaryBlue,
                            focusedLabelColor = GlassSheetTokens.OnOledMuted(),
                            unfocusedLabelColor = GlassSheetTokens.OnOledMuted(),
                        ),
                )
            },
        )
    } // End outer Box
}
