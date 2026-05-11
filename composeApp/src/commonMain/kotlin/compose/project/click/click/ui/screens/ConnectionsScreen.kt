package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.models.User
import compose.project.click.click.getPlatform
import compose.project.click.click.ui.chat.GroupMembersPickerSheet
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer
import compose.project.click.click.ui.components.InteractiveSwipeBackParallaxPeekRatio
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek
import compose.project.click.click.ui.components.PlatformBackHandler
import compose.project.click.click.ui.components.TabbedUserProfileSheet
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
    onChatDismissed: (() -> Unit)? = null,
    onChatOpenStateChanged: (Boolean) -> Unit = {},
    onNavigateToLocationSettings: (() -> Unit)? = null,
    onHubSelected: ((compose.project.click.click.data.ActiveHubEntry) -> Unit)? = null,
    viewModel: ChatViewModel = viewModel { ChatViewModel() },
    verifiedCliqueProximityAutofill: VerifiedCliqueProximityIntent? = null,
    onVerifiedCliqueProximityAutofillConsumed: () -> Unit = {},
) {
    var selectedChatId by remember { mutableStateOf(initialChatId) }
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    /** Shared with [InteractiveSwipeBackContainer] so the persistent list mirrors layer-1 parallax. */
    val iosChatSwipeDragPx = remember { mutableFloatStateOf(0f) }
    var iosChatSwipeBehindLayers by remember { mutableStateOf(false) }
    var iosChatRightToLeftPeek by remember { mutableStateOf<InteractiveSwipeBackRightToLeftPeek?>(null) }
    var chatTransitionMode by remember { mutableStateOf(ChatTransitionMode.Tap) }
    var isTapCloseInFlight by remember { mutableStateOf(false) }
    val screenScope = rememberCoroutineScope()
    var closeCleanupJob by remember { mutableStateOf<Job?>(null) }
    var profileUserId by remember { mutableStateOf<String?>(null) }
    var groupMemberPickerUsers by remember { mutableStateOf<List<User>?>(null) }
    /** Last opened thread id so iOS overlay exit animation still composes [ChatView] after [selectedChatId] clears. */
    var lastOpenChatIdForIosOverlay by remember { mutableStateOf<String?>(initialChatId) }

    LaunchedEffect(selectedChatId) {
        if (selectedChatId != null) {
            lastOpenChatIdForIosOverlay = selectedChatId
        }
    }

    fun finalizeChatClose(leaveChatClearsMessageSurface: Boolean = true) {
        viewModel.leaveChatRoom(clearMessageSurface = leaveChatClearsMessageSurface)
        // Forced reload clears local inbox caches and can repaint the list; skip that on the iOS
        // gesture-dismiss path where we already avoided flashing the message surface.
        if (leaveChatClearsMessageSurface) {
            viewModel.loadChats()
        } else {
            viewModel.loadChats(isForced = false)
        }
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
                // iOS gesture: avoid forcing ChatMessagesState.Loading while ChatView can still
                // paint for a frame (causes a full-screen spinner flash over the list).
                finalizeChatClose(
                    leaveChatClearsMessageSurface = !(isIOS && mode == ChatTransitionMode.Gesture),
                )
            }
        }
    }

    LaunchedEffect(userId, initialChatId) {
        viewModel.setCurrentUser(userId)
        if (initialChatId != null) {
            selectedChatId = initialChatId
            viewModel.loadChats(isForced = true)
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

    // Runs after recomposition when the overlay is gone — never clear swipe offset while the
    // chat sheet can still draw (that would snap translationX to 0 and flash the chat full-screen).
    LaunchedEffect(selectedChatId, isIOS) {
        if (isIOS && selectedChatId == null) {
            iosChatSwipeDragPx.floatValue = 0f
            iosChatSwipeBehindLayers = false
            iosChatRightToLeftPeek = null
        }
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
        // ConnectionsListView stays in this tree (not duplicated in swipe previousContent).
        // [InteractiveSwipeBackContainer] uses an empty previousContent; drag offset + behind-layer
        // visibility are mirrored onto this Box so the list receives the same parallax as layer 1.
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (!iosChatSwipeBehindLayers) {
                            translationX = 0f
                            return@graphicsLayer
                        }
                        val w = size.width.coerceAtLeast(1f)
                        val o = iosChatSwipeDragPx.floatValue.coerceIn(0f, w)
                        val progress = (o / w).coerceIn(0f, 1f)
                        translationX =
                            -(size.width * InteractiveSwipeBackParallaxPeekRatio) * (1f - progress)
                    },
            ) {
                ConnectionsListView(
                    viewModel = viewModel,
                    searchQuery = searchQuery,
                    onChatSelected = { chatId -> openChat(chatId) },
                    onHubSelected = onHubSelected,
                    onNavigateToLocationSettings = onNavigateToLocationSettings,
                    onUserProfileClick = { profileUserId = it },
                    onGroupMembersPicker = { groupMemberPickerUsers = it },
                    verifiedCliqueProximityAutofill = verifiedCliqueProximityAutofill,
                    onVerifiedCliqueProximityAutofillConsumed = onVerifiedCliqueProximityAutofillConsumed,
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
                        }
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
                enter = slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) +
                    fadeIn(animationSpec = fadeSpec),
                exit = if (chatTransitionMode == ChatTransitionMode.Tap) {
                    slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { it }) +
                        fadeOut(animationSpec = fadeSpec)
                } else {
                    ExitTransition.None
                },
                label = "ios_chat_overlay",
            ) {
                val activeChatId = lastOpenChatIdForIosOverlay
                if (activeChatId != null) {
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    InteractiveSwipeBackContainer(
                        enabled = true,
                        onBack = {
                            // After the swipe commits: resign focus first so UIKit owns the keyboard
                            // dismiss curve. On iOS, `SoftwareKeyboardController.hide()` tends to fight
                            // that animation on later presentations; Android still needs an explicit hide.
                            focusManager.clearFocus()
                            if (!isIOS) {
                                keyboardController?.hide()
                            }
                            closeActiveChat(ChatTransitionMode.Gesture)
                        },
                        opaquePreviousBackground = false,
                        externalDragOffsetPx = iosChatSwipeDragPx,
                        onBehindLayersVisibleChanged = { iosChatSwipeBehindLayers = it },
                        rightToLeftPeek = iosChatRightToLeftPeek,
                        previousContent = {},
                        currentContent = {
                            ChatView(
                                viewModel = viewModel,
                                chatId = activeChatId,
                                onBackPressed = { closeActiveChat(ChatTransitionMode.Tap) },
                                onOpenUserProfile = { profileUserId = it },
                                onOpenGroupMembersPicker = { groupMemberPickerUsers = it },
                                integrateTimestampPeekWithSwipeBackContainer = true,
                                onRegisterSwipeBackRightToLeftPeek = { iosChatRightToLeftPeek = it },
                                parentInteractiveBackSwipePx = iosChatSwipeDragPx,
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
                    onHubSelected = onHubSelected,
                    onNavigateToLocationSettings = onNavigateToLocationSettings,
                    onUserProfileClick = { profileUserId = it },
                    onGroupMembersPicker = { groupMemberPickerUsers = it },
                    verifiedCliqueProximityAutofill = verifiedCliqueProximityAutofill,
                    onVerifiedCliqueProximityAutofillConsumed = onVerifiedCliqueProximityAutofillConsumed,
                )
            } else {
                ChatView(
                    viewModel = viewModel,
                    chatId = activeChatId,
                    onBackPressed = { closeActiveChat(ChatTransitionMode.Tap) },
                    onOpenUserProfile = { profileUserId = it },
                    onOpenGroupMembersPicker = { groupMemberPickerUsers = it },
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
                val conn = compose.project.click.click.data.AppDataManager.connections.value
                    .firstOrNull { c -> pid in c.user_ids && c.user_ids.contains(userId) }
                if (conn != null) {
                    profileUserId = null
                    openChat(conn.id)
                }
            }
        },
        localMessages = viewModel.currentChatLocalMessages(),
    )
    if (groupMemberPickerUsers != null) {
        GroupMembersPickerSheet(
            members = groupMemberPickerUsers!!,
            onDismiss = { groupMemberPickerUsers = null },
            onMemberClick = { id ->
                groupMemberPickerUsers = null
                profileUserId = id
            },
        )
    }
    }
}

internal enum class ChatTransitionMode {
    Tap,
    Gesture
}

internal const val CHAT_TRANSITION_DURATION_MS = 300L
