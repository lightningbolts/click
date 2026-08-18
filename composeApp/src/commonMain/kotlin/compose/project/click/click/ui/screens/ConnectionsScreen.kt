@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.getPlatform
import compose.project.click.click.notifications.ChatNotificationDismisser
import compose.project.click.click.ui.chat.ConnectionMemberPickerSheet
import compose.project.click.click.ui.chat.ConnectionSheetDialog
import compose.project.click.click.ui.chat.ConnectionSheetDialogs
import compose.project.click.click.ui.chat.GroupMembersPickerContext
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek
import compose.project.click.click.ui.components.PlatformBackHandler
import compose.project.click.click.ui.components.PlatformNativeNavigationBarSwipeReveal
import compose.project.click.click.ui.components.TabbedGroupProfileSheet
import compose.project.click.click.ui.components.TabbedUserProfileSheet
import compose.project.click.click.ui.components.interactiveSwipeBackUnderlay
import compose.project.click.click.ui.components.rememberInteractiveBackHostState
import compose.project.click.click.viewmodel.ChatViewModel
import compose.project.click.click.viewmodel.VerifiedCliqueProximityIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(
    userId: String,
    searchQuery: String = "",
    initialChatId: String? = null,
    initialTargetMessageId: String? = null,
    onChatDismissed: (() -> Unit)? = null,
    onChatOpenStateChanged: (Boolean) -> Unit = {},
    /**
     * When true, the main tab bar is fully alpha-hidden so chat owns the bottom edge (required on
     * iOS where UITabBar sits above Compose). Stays true until dismiss settles — do not peel the
     * native Liquid Glass bar mid-gesture (masking it every frame causes shimmer + drag jank).
     */
    onChatSuppressesTabBarChanged: (Boolean) -> Unit = {},
    onNavigateToLocationSettings: (() -> Unit)? = null,
    onHubSelected: ((compose.project.click.click.data.ActiveHubEntry) -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onOpenDisposableRoll: ((String) -> Unit)? = null,
    onOpenDisposableRollForChat: ((String) -> Unit)? = null,
    shareableBeacons: List<compose.project.click.click.data.models.MapBeacon> = emptyList(),
    mapViewModel: compose.project.click.click.viewmodel.MapViewModel? = null,
    onShareBeaconToChats: (
        (
            beacon: compose.project.click.click.data.models.MapBeacon,
            chatIds: List<String>,
            openConnectionId: String?,
        ) -> Unit
    )? = null,
    viewModel: ChatViewModel = viewModel { ChatViewModel() },
    verifiedCliqueProximityAutofill: VerifiedCliqueProximityIntent? = null,
    onVerifiedCliqueProximityAutofillConsumed: () -> Unit = {},
) {
    var selectedChatId by remember { mutableStateOf(initialChatId) }
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }

    /** Shared with [InteractiveSwipeBackContainer] so the persistent list mirrors layer-1 parallax. */
    val chatBackHost = rememberInteractiveBackHostState()
    val iosChatSwipeDragPx = chatBackHost.dragOffsetPx
    PlatformNativeNavigationBarSwipeReveal(iosChatSwipeDragPx)
    var iosChatRightToLeftPeek by remember { mutableStateOf<InteractiveSwipeBackRightToLeftPeek?>(null) }
    var chatTransitionMode by remember { mutableStateOf(ChatTransitionMode.Tap) }
    var isTapCloseInFlight by remember { mutableStateOf(false) }
    val screenScope = rememberCoroutineScope()
    var closeCleanupJob by remember { mutableStateOf<Job?>(null) }
    var profileUserId by remember { mutableStateOf<String?>(null) }
    var groupMembersPickerContext by remember { mutableStateOf<GroupMembersPickerContext?>(null) }
    var showGroupMembersSheet by remember { mutableStateOf(false) }
    var showGroupAddMemberPicker by remember { mutableStateOf(false) }
    var pendingRemoveGroupMember by remember { mutableStateOf<ConnectionSheetDialog.RemoveGroupMember?>(null) }
    var selectedAddMemberIds by remember { mutableStateOf(setOf<String>()) }
    var addMemberEligibilityMask by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var addMemberEligibilityReady by remember { mutableStateOf(false) }

    /** Last opened thread id so iOS overlay exit animation still composes [ChatView] after [selectedChatId] clears. */
    var lastOpenChatIdForIosOverlay by remember { mutableStateOf<String?>(initialChatId) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(selectedChatId) {
        if (selectedChatId != null) {
            lastOpenChatIdForIosOverlay = selectedChatId
        }
    }

    fun finalizeChatClose(leaveChatClearsMessageSurface: Boolean = true) {
        viewModel.leaveChatRoom(clearMessageSurface = leaveChatClearsMessageSurface)
        // Do not loadChats here — refreshing the inbox on the same frame as chrome restore
        // makes the list look like it remounted with the tab bar.
        chatBackHost.reset()
        iosChatRightToLeftPeek = null
        onChatDismissed?.invoke()
        onChatOpenStateChanged(false)
        // Bring the already-warm UITabBar to front (alpha stayed 1 while it was behind Compose).
        onChatSuppressesTabBarChanged(false)
    }

    fun closeActiveChat(mode: ChatTransitionMode = ChatTransitionMode.Tap) {
        if (selectedChatId != null) {
            closeCleanupJob?.cancel()
            chatTransitionMode = mode
            isTapCloseInFlight = mode == ChatTransitionMode.Tap
            if (isIOS) {
                focusManager.clearFocus()
            }
            selectedChatId = null
            // Keep the bar behind Compose until settle. It stays at alpha 1 the whole time
            // (sendSubviewToBack while suppressed), so bring-to-front on finalize does not
            // rematerialize Liquid Glass. Do not un-suppress mid-slide — that covers the chat.
            closeCleanupJob =
                screenScope.launch {
                    val settleMs =
                        if (mode == ChatTransitionMode.Tap) {
                            CHAT_TRANSITION_DURATION_MS
                        } else {
                            CHAT_GESTURE_CLOSE_SETTLE_MS
                        }
                    delay(settleMs)
                    if (selectedChatId == null) {
                        finalizeChatClose(
                            leaveChatClearsMessageSurface = mode != ChatTransitionMode.Gesture,
                        )
                    }
                    isTapCloseInFlight = false
                    closeCleanupJob = null
                }
        }
    }

    LaunchedEffect(userId) {
        viewModel.setCurrentUser(userId)
    }

    // One-shot entry only — clearing pendingChatId on dismiss must not re-run setCurrentUser
    // (that restarts global realtime and flickers the inbox).
    LaunchedEffect(initialChatId) {
        val id = initialChatId ?: return@LaunchedEffect
        lastOpenChatIdForIosOverlay = id
        viewModel.loadChatMessages(id)
        selectedChatId = id
        viewModel.loadChats(isForced = false)
    }

    DisposableEffect(Unit) {
        onDispose {
            closeCleanupJob?.cancel()
            isTapCloseInFlight = false
            onChatOpenStateChanged(false)
            onChatSuppressesTabBarChanged(false)
            viewModel.leaveChatRoom()
        }
    }

    LaunchedEffect(selectedChatId) {
        // Session + full tab-bar suppress while a thread is active. Native bar restores on settle.
        if (selectedChatId != null) {
            onChatOpenStateChanged(true)
            onChatSuppressesTabBarChanged(true)
        }
    }

    PlatformBackHandler(
        enabled = selectedChatId != null && !isIOS,
        onBack = { closeActiveChat(ChatTransitionMode.Tap) },
    )

    fun openChat(chatId: String) {
        closeCleanupJob?.cancel()
        closeCleanupJob = null
        isTapCloseInFlight = false
        chatTransitionMode = ChatTransitionMode.Tap
        // If a deferred close was cancelled, keep session + tab-bar suppress for the new thread.
        onChatOpenStateChanged(true)
        onChatSuppressesTabBarChanged(true)
        ChatNotificationDismisser.dismissForThread(chatId, chatId)
        lastOpenChatIdForIosOverlay = chatId
        selectedChatId = chatId
        viewModel.loadChatMessages(chatId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Persistent base layer + chat overlay (iOS + Android).
        // ConnectionsListView stays in this tree (not duplicated in swipe previousContent).
        // [InteractiveSwipeBackContainer] uses an empty previousContent; drag offset + behind-layer
        // visibility are mirrored onto this Box so the list receives the same parallax as layer 1.
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .interactiveSwipeBackUnderlay(chatBackHost),
            ) {
                ConnectionsListView(
                    viewModel = viewModel,
                    searchQuery = searchQuery,
                    onOpenSearch = onOpenSearch,
                    onChatSelected = { chatId -> openChat(chatId) },
                    onHubSelected = onHubSelected,
                    onNavigateToLocationSettings = onNavigateToLocationSettings,
                    onUserProfileClick = { profileUserId = it },
                    onGroupMembersPicker = {
                        groupMembersPickerContext = it
                        showGroupMembersSheet = true
                    },
                    verifiedCliqueProximityAutofill = verifiedCliqueProximityAutofill,
                    onVerifiedCliqueProximityAutofillConsumed = onVerifiedCliqueProximityAutofillConsumed,
                    isListObscured = selectedChatId != null,
                )
            }

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
                        },
                )
            }

            // AnimatedVisibility (not AnimatedContent) so a gesture dismiss can use ExitTransition.None:
            // the chat is already slid off-screen; an AnimatedContent "target null" transition can
            // still insert an extra layout pass. Tap-close keeps a horizontal slide + fade out.
            val slideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
            val fadeSpec = tween<Float>(220, easing = LinearOutSlowInEasing)
            AnimatedVisibility(
                visible = selectedChatId != null,
                modifier = Modifier.fillMaxSize(),
                enter =
                    slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) +
                        fadeIn(animationSpec = fadeSpec),
                exit =
                    if (chatTransitionMode == ChatTransitionMode.Tap) {
                        slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { it }) +
                            fadeOut(animationSpec = fadeSpec)
                    } else {
                        ExitTransition.None
                    },
                label = "chat_overlay",
            ) {
                val activeChatId = lastOpenChatIdForIosOverlay
                if (activeChatId != null) {
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    InteractiveSwipeBackContainer(
                        enabled = true,
                        onBack = {
                            focusManager.clearFocus()
                            if (!isIOS) {
                                keyboardController?.hide()
                            }
                            closeActiveChat(ChatTransitionMode.Gesture)
                        },
                        opaquePreviousBackground = false,
                        externalDragOffsetPx = iosChatSwipeDragPx,
                        onBehindLayersVisibleChanged = { revealing ->
                            chatBackHost.behindLayersVisible = revealing
                        },
                        rightToLeftPeek = iosChatRightToLeftPeek,
                        previousContent = {},
                        currentContent = {
                            ChatView(
                                viewModel = viewModel,
                                chatId = activeChatId,
                                targetMessageId = initialTargetMessageId,
                                onBackPressed = { closeActiveChat(ChatTransitionMode.Tap) },
                                onOpenUserProfile = { profileUserId = it },
                                onOpenGroupMembersPicker = {
                                    groupMembersPickerContext = it
                                    showGroupMembersSheet = true
                                },
                                integrateTimestampPeekWithSwipeBackContainer = true,
                                onRegisterSwipeBackRightToLeftPeek = { iosChatRightToLeftPeek = it },
                                parentInteractiveBackSwipePx = iosChatSwipeDragPx,
                                onOpenDisposableRoll = onOpenDisposableRoll,
                                onOpenDisposableRollForChat = onOpenDisposableRollForChat,
                                shareableBeacons = shareableBeacons,
                                mapViewModel = mapViewModel,
                                onShareBeaconToChats = onShareBeaconToChats,
                            )
                        },
                    )
                }
            }
        }
        // C13 directive: chat-list avatar taps must surface the new tabbed
        // ProfileBottomSheet (Timeline · Media · Links · Files), NOT the legacy
        // UserProfileBottomSheet. The Timeline subtab hydrates user_interests.tags
        // for the tapped peer via SupabaseRepository.fetchUserPublicProfile.
        TabbedUserProfileSheet(
            userId = profileUserId,
            viewerUserId = userId,
            onDismiss = { profileUserId = null },
            onMessage = {
                val pid = profileUserId
                if (pid != null) {
                    val conn =
                        compose.project.click.click.data.AppDataManager.connections.value
                            .firstOrNull { c -> pid in c.user_ids && c.user_ids.contains(userId) }
                    if (conn != null) {
                        profileUserId = null
                        openChat(conn.id)
                    }
                }
            },
            onNudge = {
                val pid = profileUserId ?: return@TabbedUserProfileSheet
                val conn =
                    AppDataManager.connections.value
                        .firstOrNull { c -> pid in c.user_ids && c.user_ids.contains(userId) }
                val peer = AppDataManager.connectedUsers.value[pid]
                if (conn != null) {
                    profileUserId = null
                    viewModel.sendNudgeToChat(conn.id, peer?.name ?: "them")
                }
            },
            onOpenDisposableRoll = onOpenDisposableRoll,
            localMessages = viewModel.currentChatLocalMessages(),
        )
        val groupPickerContext = groupMembersPickerContext
        if (showGroupMembersSheet && groupPickerContext != null) {
            val isGroupCreator = groupPickerContext.createdByUserId == userId
            TabbedGroupProfileSheet(
                groupName = groupPickerContext.groupName,
                groupId = groupPickerContext.groupId,
                chatId = groupPickerContext.chatId,
                avatarUrl = groupPickerContext.avatarUrl,
                viewerUserId = userId,
                members = groupPickerContext.members,
                groupCreatorId = groupPickerContext.createdByUserId,
                onDismiss = {
                    showGroupMembersSheet = false
                    groupMembersPickerContext = null
                },
                onMessage =
                    groupPickerContext.chatId?.let { chatId ->
                        {
                            showGroupMembersSheet = false
                            groupMembersPickerContext = null
                            openChat(chatId)
                        }
                    },
                onNudge =
                    groupPickerContext.chatId?.let { chatId ->
                        {
                            showGroupMembersSheet = false
                            groupMembersPickerContext = null
                            viewModel.sendNudgeToChat(chatId, groupPickerContext.groupName ?: "the group")
                        }
                    },
                onOpenDisposableRoll = onOpenDisposableRollForChat,
                onAddMember = {
                    showGroupMembersSheet = false
                    selectedAddMemberIds = emptySet()
                    showGroupAddMemberPicker = true
                },
                onRemoveMember =
                    if (isGroupCreator) {
                        { memberId ->
                            val memberName =
                                groupPickerContext.members
                                    .find { it.id == memberId }
                                    ?.name
                                    ?.trim()
                                    ?.ifBlank { null }
                                    ?: "This member"
                            pendingRemoveGroupMember =
                                ConnectionSheetDialog.RemoveGroupMember(
                                    memberUserId = memberId,
                                    memberName = memberName,
                                )
                        }
                    } else {
                        null
                    },
                onMemberClick = { id ->
                    showGroupMembersSheet = false
                    groupMembersPickerContext = null
                    profileUserId = id
                },
                onGroupAvatarUrlChanged = { url ->
                    groupMembersPickerContext = groupMembersPickerContext?.copy(avatarUrl = url)
                },
                localMessages = viewModel.currentChatLocalMessages(),
            )
        }
        val removeMemberContext = groupPickerContext
        ConnectionSheetDialogs(
            dialog = pendingRemoveGroupMember,
            onDismiss = { pendingRemoveGroupMember = null },
            onConfirmRemove = { },
            onConfirmBlock = { },
            onConfirmReport = { },
            onConfirmLeaveGroup = { },
            onConfirmDeleteGroup = { },
            onConfirmRemoveGroupMember = { memberId ->
                val groupId = removeMemberContext?.groupId
                if (groupId != null) {
                    viewModel.removeMemberFromVerifiedClique(groupId, memberId)
                }
                pendingRemoveGroupMember = null
            },
        )
        if (showGroupAddMemberPicker && groupPickerContext != null) {
            val memberIds = groupPickerContext.memberUserIds.toSet()
            val candidates =
                AppDataManager.connectedUsers.value.values
                    .filter { candidate ->
                        candidate.id != userId &&
                            candidate.id !in memberIds &&
                            AppDataManager.connections.value.any { conn ->
                                conn.user_ids.contains(candidate.id) &&
                                    conn.user_ids.contains(userId) &&
                                    conn.normalizedConnectionStatus() in setOf("active", "kept")
                            }
                    }.sortedBy { it.name?.lowercase().orEmpty() }
            LaunchedEffect(
                showGroupAddMemberPicker,
                groupPickerContext.groupId,
                selectedAddMemberIds,
                candidates.map { it.id },
            ) {
                if (!showGroupAddMemberPicker) {
                    addMemberEligibilityMask = emptyMap()
                    addMemberEligibilityReady = false
                    return@LaunchedEffect
                }
                addMemberEligibilityReady = false
                addMemberEligibilityMask =
                    viewModel.computeVerifiedCliqueAddableMask(
                        baseMemberUserIds = groupPickerContext.memberUserIds,
                        candidateUserIds = candidates.map { it.id },
                        selectedCandidateIds = selectedAddMemberIds,
                    )
                addMemberEligibilityReady = true
            }
            ConnectionMemberPickerSheet(
                onDismissRequest = {
                    showGroupAddMemberPicker = false
                    selectedAddMemberIds = emptySet()
                },
                title = "Add to group",
                subtitle = "Choose verified connections who are connected to everyone in this click.",
                candidates = candidates,
                selectedIds = selectedAddMemberIds,
                onSelectedIdsChange = { selectedAddMemberIds = it },
                eligibilityMask = addMemberEligibilityMask,
                eligibilityReady = addMemberEligibilityReady,
                eligibilityCheckingLabel = "Checking who can join…",
                onSelectionBlocked = { viewModel.notifyVerifiedCliqueSelectionBlocked() },
                primaryButtonLabel =
                    when (selectedAddMemberIds.size) {
                        0 -> "Add"
                        1 -> "Add"
                        else -> "Add ${selectedAddMemberIds.size}"
                    },
                primaryEnabled = addMemberEligibilityReady && selectedAddMemberIds.isNotEmpty(),
                onPrimaryClick = {
                    showGroupAddMemberPicker = false
                    viewModel.addMembersToVerifiedClique(
                        groupId = groupPickerContext.groupId,
                        newMemberUserIds = selectedAddMemberIds.toList(),
                    )
                    selectedAddMemberIds = emptySet()
                },
            )
        }
    }
}

internal enum class ChatTransitionMode {
    Tap,
    Gesture,
}

internal const val CHAT_TRANSITION_DURATION_MS = 300L

/** After interactive-back commits, wait before leaveChatRoom / tab-bar restore to avoid inbox flicker. */
internal const val CHAT_GESTURE_CLOSE_SETTLE_MS = 64L
