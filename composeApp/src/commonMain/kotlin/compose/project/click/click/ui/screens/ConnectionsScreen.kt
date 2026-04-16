package compose.project.click.click.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.AnnotatedString
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.getPlatform // pragma: allowlist secret
import compose.project.click.click.calls.CallSessionManager // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.notifications.NotificationRuntimeState // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveCard // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackParallaxPeekRatio // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformBackHandler // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveSurface // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.ripple
import compose.project.click.click.ui.components.AvatarWithOnlineIndicator // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.GroupAvatar // pragma: allowlist secret
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
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
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.originalMimeTypeOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.ui.chat.AnimatedVisibilityChatBubble
import compose.project.click.click.ui.chat.CallLogSystemRow
import compose.project.click.click.ui.chat.ChatAudioBubbleRow
import compose.project.click.click.ui.chat.ChatBubblePhotoContent
import compose.project.click.click.ui.chat.ChatChannelLoadingView
import compose.project.click.click.ui.chat.ChatWarmLoadingView
import compose.project.click.click.ui.chat.ConnectionItem
import compose.project.click.click.ui.chat.ForwardDialog
import compose.project.click.click.ui.chat.IcebreakerPanel
import compose.project.click.click.ui.chat.VibeCheckBanner
import compose.project.click.click.ui.chat.GroupMembersPickerSheet
import compose.project.click.click.ui.chat.LocationGapNudge
import compose.project.click.click.ui.chat.orderedGroupMembersForPicker
import compose.project.click.click.ui.chat.connectionHasNoGeo
import compose.project.click.click.ui.chat.connectionListActivityTs
import compose.project.click.click.ui.chat.ChatCallOptionsIosSurface
import compose.project.click.click.ui.chat.ChatMessageOverflowButton
import compose.project.click.click.ui.chat.ConnectionChatMessageComposer
import compose.project.click.click.ui.chat.ChatTimelineEntry
import compose.project.click.click.ui.chat.ChatTypingDots
import compose.project.click.click.ui.chat.ConversationDaySeparator
import compose.project.click.click.ui.chat.LoadingSubtitlePlaceholder
import compose.project.click.click.ui.chat.ReplySwipeSideIcon
import compose.project.click.click.ui.chat.buildChatTimelineEntriesNewestFirst
import compose.project.click.click.ui.chat.callLogLabel
import compose.project.click.click.ui.chat.formatCallDurationForLog
import compose.project.click.click.ui.chat.formatChatAudioDuration
import compose.project.click.click.ui.chat.formatChatAudioPositionMs
import compose.project.click.click.ui.chat.formatConnectionListTimestamp
import compose.project.click.click.ui.chat.formatConversationDayLabel
import compose.project.click.click.ui.chat.formatMessageTime
import compose.project.click.click.ui.chat.formatVibeCheckTime
import compose.project.click.click.ui.chat.messageDayKey
import compose.project.click.click.ui.chat.replyDragHintProgress
import compose.project.click.click.ui.chat.swipeRawTravelFromVisual
import compose.project.click.click.ui.chat.swipeVisualFromRawTravel
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
import compose.project.click.click.viewmodel.VerifiedCliqueProximityIntent // pragma: allowlist secret
import compose.project.click.click.viewmodel.SecureChatMediaHost // pragma: allowlist secret
import compose.project.click.click.viewmodel.SecureChatMediaLoadState // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.util.AvailabilityOverlapCache // pragma: allowlist secret
import compose.project.click.click.util.hasActiveAvailabilityIntentOverlap // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers // pragma: allowlist secret
import kotlinx.coroutines.withContext // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatListState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret
import compose.project.click.click.ui.chat.saveChatImageToGallery // pragma: allowlist secret
import compose.project.click.click.utils.toImageBitmap // pragma: allowlist secret
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
import compose.project.click.click.media.rememberChatAudioPlayer // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatLinkifyText // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMediaPickerHandles // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.util.LruMemoryCache // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret

@Composable
fun ConnectionsScreen(
    userId: String,
    searchQuery: String = "",
    initialChatId: String? = null,
    onChatDismissed: (() -> Unit)? = null,
    onChatOpenStateChanged: (Boolean) -> Unit = {},
    onNavigateToLocationSettings: (() -> Unit)? = null,
    viewModel: ChatViewModel = viewModel { ChatViewModel() },
    verifiedCliqueProximityAutofill: VerifiedCliqueProximityIntent? = null,
    onVerifiedCliqueProximityAutofillConsumed: () -> Unit = {},
) {
    var selectedChatId by remember { mutableStateOf(initialChatId) }
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    /** Shared with [InteractiveSwipeBackContainer] so the persistent list mirrors layer-1 parallax. */
    val iosChatSwipeDragPx = remember { mutableFloatStateOf(0f) }
    var iosChatSwipeBehindLayers by remember { mutableStateOf(false) }
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
                    InteractiveSwipeBackContainer(
                        enabled = true,
                        onBack = { closeActiveChat(ChatTransitionMode.Gesture) },
                        // Persistent ConnectionsListView is composed below; do not duplicate the list.
                        opaquePreviousBackground = false,
                        externalDragOffsetPx = iosChatSwipeDragPx,
                        onBehindLayersVisibleChanged = { iosChatSwipeBehindLayers = it },
                        previousContent = {},
                        currentContent = {
                            ChatView(
                                viewModel = viewModel,
                                chatId = activeChatId,
                                onBackPressed = { closeActiveChat(ChatTransitionMode.Tap) },
                                onOpenUserProfile = { profileUserId = it },
                                onOpenGroupMembersPicker = { groupMemberPickerUsers = it },
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
    UserProfileBottomSheet(
        userId = profileUserId,
        viewerUserId = userId,
        onDismiss = { profileUserId = null }
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

private enum class ChatTransitionMode {
    Tap,
    Gesture
}

private const val CHAT_TRANSITION_DURATION_MS = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsListView(
    viewModel: ChatViewModel,
    searchQuery: String = "",
    onChatSelected: (String) -> Unit,
    onNavigateToLocationSettings: (() -> Unit)? = null,
    onUserProfileClick: (String) -> Unit = {},
    onGroupMembersPicker: (List<User>) -> Unit = {},
    verifiedCliqueProximityAutofill: VerifiedCliqueProximityIntent? = null,
    onVerifiedCliqueProximityAutofillConsumed: () -> Unit = {},
) {
    val chatListState by viewModel.chatListState.collectAsState()
    val archivedConnectionIds by viewModel.archivedConnectionIds.collectAsState()
    val hiddenConnectionIds by viewModel.hiddenConnectionIds.collectAsState()
    val onlineUsers by AppDataManager.onlineUsers.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableStateOf(0) } // 0 = Active, 1 = Groups, 2 = Archived
    val tabContentOffsetX = remember { Animatable(0f) }
    val tabContentAlpha = remember { Animatable(1f) }
    var previousTabIndexForAnim by remember { mutableStateOf(selectedTabIndex) }
    var hasInitializedTabAnimation by remember { mutableStateOf(false) }
    var listBannerNow by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(60_000)
            listBannerNow = Clock.System.now().toEpochMilliseconds()
        }
    }

    LaunchedEffect(selectedTabIndex) {
        if (!hasInitializedTabAnimation) {
            hasInitializedTabAnimation = true
            previousTabIndexForAnim = selectedTabIndex
            return@LaunchedEffect
        }

        val direction = if (selectedTabIndex >= previousTabIndexForAnim) 1f else -1f
        previousTabIndexForAnim = selectedTabIndex
        tabContentOffsetX.snapTo(direction * 36f)
        tabContentAlpha.snapTo(0.88f)
        coroutineScope {
            launch {
                tabContentOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                )
            }
            launch {
                tabContentAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
                )
            }
        }
    }

    // Connection menu state: holds the chatWithDetails for which the menu is open
    var pendingMenuChat by remember { mutableStateOf<ChatWithDetails?>(null) }

    var cliqueSheetVisible by remember { mutableStateOf(false) }
    var selectedCliqueFriendIds by remember { mutableStateOf(setOf<String>()) }
    val cliqueSheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true)
    val listScope = rememberCoroutineScope()
    var proximityCliqueHintUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var cliqueProximityAutofillLoading by remember { mutableStateOf(false) }

    LaunchedEffect(verifiedCliqueProximityAutofill) {
        val intent = verifiedCliqueProximityAutofill ?: run {
            cliqueProximityAutofillLoading = false
            return@LaunchedEffect
        }
        cliqueProximityAutofillLoading = true
        proximityCliqueHintUsers =
            intent.matchedUsers.filter { it.id in intent.preselectFriendIds.toSet() }
        selectedCliqueFriendIds = intent.preselectFriendIds.toSet()
        selectedTabIndex = 0
        cliqueSheetVisible = true
        viewModel.loadChats(isForced = true)
        withTimeoutOrNull(4_500L) {
            snapshotFlow {
                val chats = when (val s = chatListState) {
                    is ChatListState.Success -> s.chats
                    else -> emptyList()
                }
                val pickable = chats.filter {
                    it.groupClique == null &&
                        it.connection.normalizedConnectionStatus() != "removed"
                }
                intent.preselectFriendIds.all { pid -> pickable.any { it.otherUser.id == pid } }
            }.first { it }
        }
        cliqueProximityAutofillLoading = false
        onVerifiedCliqueProximityAutofillConsumed()
    }

    LaunchedEffect(cliqueSheetVisible) {
        if (cliqueSheetVisible) {
            try {
                cliqueSheetState.show()
            } catch (_: Exception) {
            }
        }
    }

    fun dismissVerifiedCliqueSheet(onAfterHide: () -> Unit = {}) {
        listScope.launch {
            try {
                cliqueSheetState.hide()
            } catch (_: Exception) {
            }
        }.invokeOnCompletion {
            cliqueSheetVisible = false
            proximityCliqueHintUsers = emptyList()
            onAfterHide()
        }
    }

    val connectionsLazyListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(0, 0)
    }

    // Render only the unified inbox payload emitted by ChatViewModel.
    val effectiveChats: List<ChatWithDetails> = when (val state = chatListState) {
        is ChatListState.Success -> state.chats
        else -> emptyList()
    }

    val activeOneToOneChats = remember(effectiveChats, archivedConnectionIds, hiddenConnectionIds) {
        effectiveChats
            .filter {
                it.groupClique == null &&
                    it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
            }
            .sortedByDescending { connectionListActivityTs(it) }
    }

    /** Verified-click picker: every non-group 1:1 edge still in the inbox, including pending and archived-tab rows. */
    val verifiedCliquePickableOneToOneChats = remember(effectiveChats) {
        effectiveChats
            .filter {
                it.groupClique == null &&
                    it.connection.normalizedConnectionStatus() != "removed"
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
        verifiedCliquePickableOneToOneChats,
    ) {
        val uid = currentUserId
        if (!cliqueSheetVisible || uid.isNullOrBlank()) {
            cliqueAddableMask = emptyMap()
            cliqueCreateGraphOk = false
            cliqueSheetEligibilityReady = false
            return@LaunchedEffect
        }
        cliqueSheetEligibilityReady = false
        val others = verifiedCliquePickableOneToOneChats.map { it.otherUser.id }
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
                        it.groupClique == null &&
                        it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                    }
                    val groupChats = effectiveChats.filter {
                        it.groupClique != null &&
                            it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                    }
                    val archivedChats = effectiveChats.filter {
                        it.groupClique == null &&
                        it.connection.isArchivedChannelForUser(archivedConnectionIds, hiddenConnectionIds)
                    }
                    val tabChats = when (selectedTabIndex) {
                        0 -> activeChats
                        1 -> groupChats
                        else -> archivedChats
                    }
                    val filteredCount = if (searchQuery.isBlank()) {
                        tabChats.size
                    } else {
                        tabChats.count { chat ->
                            val groupHit =
                                chat.groupClique?.name?.contains(searchQuery, ignoreCase = true) == true
                            val userHit =
                                chat.otherUser.name?.contains(searchQuery, ignoreCase = true) == true
                            groupHit || userHit
                        }
                    }
                    val tabLabel = when (selectedTabIndex) {
                        0 -> "active"
                        1 -> "group"
                        else -> "archived"
                    }
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
                    it.groupClique == null &&
                    it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                }
                val groupCount = effectiveChats.count {
                    it.groupClique != null &&
                        it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                }
                val archivedCount = effectiveChats.count {
                    it.groupClique == null &&
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
                        .padding(4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 112.dp)
                            .clip(RoundedCornerShape(segInnerCorner))
                            .then(
                                if (selectedTabIndex == 0) Modifier
                                    .background(PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.14f else 0.18f))
                                    .border(segBorderWidth, PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.25f else 0.35f), RoundedCornerShape(segInnerCorner))
                                else Modifier
                            )
                            .clickable { selectedTabIndex = 0 }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Active ($activeCount)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTabIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTabIndex == 0) LightBlue
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(min = 112.dp)
                            .clip(RoundedCornerShape(segInnerCorner))
                            .then(
                                if (selectedTabIndex == 1) Modifier
                                    .background(PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.14f else 0.18f))
                                    .border(segBorderWidth, PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.25f else 0.35f), RoundedCornerShape(segInnerCorner))
                                else Modifier
                            )
                            .clickable { selectedTabIndex = 1 }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Groups ($groupCount)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTabIndex == 1) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTabIndex == 1) LightBlue
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(min = 112.dp)
                            .clip(RoundedCornerShape(segInnerCorner))
                            .then(
                                if (selectedTabIndex == 2) Modifier
                                    .background(PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.14f else 0.18f))
                                    .border(segBorderWidth, PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.25f else 0.35f), RoundedCornerShape(segInnerCorner))
                                else Modifier
                            )
                            .clickable { selectedTabIndex = 2 }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Archived ($archivedCount)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTabIndex == 2) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTabIndex == 2) LightBlue
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
                    it.groupClique == null &&
                    it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                }
                val groupChats = effectiveChats.filter {
                    it.groupClique != null &&
                        it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                }
                val archivedChats = effectiveChats.filter {
                    it.groupClique == null &&
                    it.connection.isArchivedChannelForUser(archivedConnectionIds, hiddenConnectionIds)
                }
                val tabChats = when (selectedTabIndex) {
                    0 -> activeChats
                    1 -> groupChats
                    else -> archivedChats
                }

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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = tabContentOffsetX.value
                            alpha = tabContentAlpha.value
                        }
                ) {
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
                                        else if (selectedTabIndex == 1) "No group chats"
                                        else if (selectedTabIndex == 2) "No archived connections"
                                        else "No connections yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        if (searchQuery.isNotBlank()) "Try a different search term"
                                        else if (selectedTabIndex == 1) "Group clicks will appear here"
                                        else if (selectedTabIndex == 2) "Archived chats will appear here"
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
                                // R1.3: key must be stable across content mutations (last message changing,
                                // typing indicator toggling, etc.) so LazyColumn can update in-place rather
                                // than dispose/recreate the row. The chat identity is the connection.
                                key = { it.connection.id }
                            ) { chatDetails ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ConnectionItem(
                                        chatDetails = chatDetails,
                                        viewerUserId = currentUserId,
                                        showOnlineIndicator = chatDetails.groupClique == null &&
                                            chatDetails.otherUser.id in onlineUsers,
                                        onAvatarClick = {
                                            if (chatDetails.groupClique == null) {
                                                onUserProfileClick(chatDetails.otherUser.id)
                                            }
                                        },
                                        onGroupMembersPicker = onGroupMembersPicker,
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
        val verifiedCliqueSheetBg = MaterialTheme.colorScheme.surfaceContainerHigh
        AdaptiveBottomSheet(
            onDismissRequest = { dismissVerifiedCliqueSheet() },
            adaptiveSheetState = cliqueSheetState,
            containerColor = verifiedCliqueSheetBg,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(verifiedCliqueSheetBg),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Create verified click",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pick friends who are pairwise connected (pending, active, kept, or archived 1:1). Friend–friend edges are checked on the server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (cliqueProximityAutofillLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AdaptiveCircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Loading your tap group in Clicks…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                val pickableIdsForHint =
                    remember(verifiedCliquePickableOneToOneChats) {
                        verifiedCliquePickableOneToOneChats.map { it.otherUser.id }.toSet()
                    }
                val supplementalHintUsers = remember(proximityCliqueHintUsers, pickableIdsForHint) {
                    proximityCliqueHintUsers.filter { it.id !in pickableIdsForHint }
                }
                if (supplementalHintUsers.isNotEmpty()) {
                    Text(
                        text = "People from your tap (profiles may still sync)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        supplementalHintUsers.forEach { u ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            ) {
                                Text(
                                    u.name?.trim()?.ifBlank { null } ?: "Friend",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 28.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val checkingVisible =
                        !cliqueSheetEligibilityReady && verifiedCliquePickableOneToOneChats.isNotEmpty()
                    Text(
                        text = "Checking who can join…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alpha(if (checkingVisible) 1f else 0f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (memberSetDuplicatesExistingClick) {
                    Text(
                        text = "You already have a verified click with this group.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (verifiedCliquePickableOneToOneChats.isEmpty()) {
                    Text(
                        text = "No eligible 1:1 connections yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(verifiedCliquePickableOneToOneChats, key = { it.connection.id }) { chatDetails ->
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
                                    ConnectionListUserAvatarFace(
                                        displayName = chatDetails.otherUser.name,
                                        email = chatDetails.otherUser.email,
                                        avatarUrl = chatDetails.otherUser.image,
                                        userId = chatDetails.otherUser.id,
                                        modifier = Modifier.fillMaxSize(),
                                        useCompactTypography = false,
                                    )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { dismissVerifiedCliqueSheet() },
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.createVerifiedClique(selectedCliqueFriendIds.toList()) { result ->
                                result.onSuccess {
                                    dismissVerifiedCliqueSheet {
                                        listScope.launch {
                                            snackbarHostState.showSnackbar("Click created")
                                        }
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    viewModel: ChatViewModel,
    chatId: String,
    onBackPressed: () -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    onOpenGroupMembersPicker: (List<User>) -> Unit = {},
) {
    val chatMessagesState by viewModel.chatMessagesState.collectAsState()
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
                    val hintedRow = (chatListState as? ChatListState.Success)
                        ?.chats
                        ?.firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
                    if (hintedRow != null && (
                            hintedRow.lastMessage != null ||
                                hintedRow.chat.messages.isNotEmpty()
                            )
                    ) {
                        ChatWarmLoadingView(
                            topInset = topInset,
                            onBackPressed = onBackPressed,
                            chatRow = hintedRow,
                        )
                    } else {
                        ChatChannelLoadingView(
                            topInset = topInset,
                            onBackPressed = onBackPressed,
                        )
                    }
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
                    // R1.1: hoist secure media load state above the LazyColumn so each item doesn't
                    // subscribe to the full map.
                    val secureMediaLoadMap by viewModel.secureChatMediaLoadState.collectAsState()
                    val isGroupChat = chatDetails.groupClique != null
                    val overlapRepo = remember { SupabaseRepository() }
                    val chatPeerId = chatDetails.otherUser.id
                    var chatHasIntentOverlap by remember(chatDetails.otherUser.id, currentUserId, isGroupChat) {
                        val v = currentUserId
                        val cached = if (!isGroupChat && !v.isNullOrBlank()) {
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
                        val v = currentUserId ?: run {
                            chatHasIntentOverlap = false
                            return@LaunchedEffect
                        }
                        val peer = chatDetails.otherUser.id
                        val result = withContext(Dispatchers.Default) {
                            val mine = overlapRepo.fetchPeerProfileAvailabilityBubbles(v, v)
                            val theirs = overlapRepo.fetchPeerProfileAvailabilityBubbles(v, peer)
                            hasActiveAvailabilityIntentOverlap(mine, theirs)
                        }
                        AvailabilityOverlapCache.put(v, peer, result)
                        chatHasIntentOverlap = result
                    }
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
                        onMediaAccessBlocked = { msg ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )

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
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(bounded = false, radius = 22.dp),
                                                onClick = {
                                                    onOpenGroupMembersPicker(
                                                        orderedGroupMembersForPicker(chatDetails),
                                                    )
                                                },
                                            ),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        GroupAvatar(
                                            members = chatDetails.groupMemberUsers,
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
                    if (state.isLoadingMessages && messages.isEmpty()) {
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
                                                        currentUserId = currentUserId,
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
                                                        showPeerAvatarInGroup = isGroupChat,
                                                        secureMediaHost = viewModel,
                                                        secureMediaState = secureMediaLoadMap[messageWithUser.message.id],
                                                        activeChatId = activeApiChatId,
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

                    ConnectionChatMessageComposer(
                        viewModel = viewModel,
                        chatDetails = chatDetails,
                        isGroupChat = isGroupChat,
                        editingMessageId = editingMessageId,
                        replyingTo = replyingTo,
                        mediaPickers = mediaPickers,
                    )
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
    /** Verified group / multi-member chat: show the sender’s face on incoming bubbles. */
    showPeerAvatarInGroup: Boolean = false,
    secureMediaHost: SecureChatMediaHost? = null,
    /**
     * Pre-resolved secure media load state for this message. Hoist the [SecureChatMediaHost.secureChatMediaLoadState]
     * collector out of LazyColumn items and pass only the relevant entry here to avoid every row observing the full map.
     * If null, falls back to collecting from [secureMediaHost] internally (legacy path).
     */
    secureMediaState: SecureChatMediaLoadState? = null,
    activeChatId: String? = null,
    /** When false, long-press context and its haptics are disabled (e.g. read-only hub preview). */
    enableMessageContextMenu: Boolean = true,
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
    val encryptedMedia = message.isEncryptedMedia()

    // Prefer the hoisted per-message state from the caller. Fallback: legacy path that collects the full map here.
    val secureSt = secureMediaState
        ?: secureMediaHost?.secureChatMediaLoadState?.collectAsState()?.value?.get(message.id)
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(message.id, mediaUrl, activeChatId, currentUserId, mt, encryptedMedia) {
        val chatId = activeChatId
        val viewer = currentUserId
        if (encryptedMedia && secureMediaHost != null && chatId != null && viewer != null) {
            when (mt) {
                ChatMessageType.IMAGE -> secureMediaHost.ensureSecureChatImageLoaded(chatId, viewer, message)
                ChatMessageType.AUDIO -> secureMediaHost.ensureSecureChatAudioLoaded(chatId, viewer, message)
            }
        }
    }

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
    val swipeThresholdPx = remember(density) { with(density) { 60.dp.toPx() } }
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
    var replyThresholdHapticFired by remember(message.id) { mutableStateOf(false) }

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
        val directed = if (isSent) (-rawSwipeTravelPx).coerceAtLeast(0f) else rawSwipeTravelPx.coerceAtLeast(0f)
        if (directed >= swipeThresholdPx && !replyThresholdHapticFired) {
            replyThresholdHapticFired = true
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val swipeDragModifier = Modifier.draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = {
            swipeSettleJob?.cancel()
            swipeSettleJob = null
            replyThresholdHapticFired = false
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

    val messageLongPressModifier =
        if (enableMessageContextMenu) {
            Modifier.pointerInput(message.id, onLongPress) {
                detectTapGestures(
                    onLongPress = {
                        PlatformHapticsPolicy.heavyImpact()
                        onLongPress(messageWithUser)
                    },
                )
            }
        } else {
            Modifier
        }

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
                    verticalAlignment = if (!isSent && showPeerAvatarInGroup) {
                        Alignment.Bottom
                    } else {
                        Alignment.CenterVertically
                    },
                    horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
                ) {
                    if (!isSent && showPeerAvatarInGroup) {
                        val peer = messageWithUser.user
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp, bottom = 2.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!peer.image.isNullOrBlank()) {
                                AsyncImage(
                                    model = peer.image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                )
                            } else {
                                Text(
                                    text = peer.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Column(
                        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
                        modifier = Modifier
                            .graphicsLayer { translationX = displayVisualPx }
                            .then(swipeDragModifier)
                            .then(messageLongPressModifier),
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
                            ChatBubblePhotoContent(
                                mediaUrl = mediaUrl,
                                message = message,
                                isEncrypted = encryptedMedia,
                                secureState = secureSt,
                                overflowTint = Color.White.copy(alpha = 0.92f),
                                onOverflow = { onLongPress(messageWithUser) },
                                useLightOverflowContrast = true,
                                borderIfReceived = false,
                            )
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
                                        localFilePathForPlayback = secureSt?.audioLocalPath,
                                        secureLoading = encryptedMedia && (secureSt == null || secureSt.loading),
                                        secureError = if (encryptedMedia) secureSt?.error else null,
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
                            ChatBubblePhotoContent(
                                mediaUrl = mediaUrl,
                                message = message,
                                isEncrypted = encryptedMedia,
                                secureState = secureSt,
                                overflowTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f),
                                onOverflow = { onLongPress(messageWithUser) },
                                useLightOverflowContrast = false,
                                borderIfReceived = true,
                            )
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
                                        localFilePathForPlayback = secureSt?.audioLocalPath,
                                        secureLoading = encryptedMedia && secureSt?.loading == true,
                                        secureError = if (encryptedMedia) secureSt?.error else null,
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
                                    .clickable {
                                        PlatformHapticsPolicy.lightImpact()
                                        onToggleReaction(emoji)
                                    }
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
        LaunchedEffect(Unit) {
            PlatformHapticsPolicy.lightImpact()
        }
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
                                    PlatformHapticsPolicy.lightImpact()
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
                                PlatformHapticsPolicy.lightImpact()
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
                            if (message.isEncryptedMedia()) {
                                val bytes = viewModel.fetchDecryptedChatMediaBytes(message)
                                if (bytes != null) {
                                    saveChatImageToGallery(
                                        imageUrl = imageUrl,
                                        decryptedImageBytes = bytes,
                                        mimeTypeHint = message.originalMimeTypeOrNull(),
                                    ).onSuccess { dismiss() }
                                }
                            } else {
                                saveChatImageToGallery(imageUrl).onSuccess { dismiss() }
                            }
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

