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
import compose.project.click.click.ui.chat.MessageActionSheet
import compose.project.click.click.ui.chat.orderedGroupMembersForPicker
import compose.project.click.click.ui.chat.connectionHasNoGeo
import compose.project.click.click.ui.chat.connectionListActivityTs
import compose.project.click.click.ui.chat.ChatCallOptionsIosSurface
import compose.project.click.click.ui.chat.ConnectionActionSheet
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
import compose.project.click.click.ui.chat.ChatMessageBubble
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







