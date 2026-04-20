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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.AnnotatedString
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.calls.CallSessionManager // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.notifications.NotificationRuntimeState // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveCard // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
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
import androidx.compose.foundation.layout.ime
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
import compose.project.click.click.ui.chat.chatBubbleStableRowKey
import compose.project.click.click.ui.chat.CallLogSystemRow
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
import compose.project.click.click.ui.chat.ChatDeliveryReceiptIcon
import compose.project.click.click.ui.chat.ChatMessageRowWithTimestampGutter
import compose.project.click.click.ui.chat.applyTimestampPeekDragStep
import compose.project.click.click.ui.chat.chatTimestampPeekOnSwipeLeft
import compose.project.click.click.ui.chat.launchTimestampPeekReplyStyleSettle
import compose.project.click.click.ui.chat.rememberTimestampPeekRevealPx
import compose.project.click.click.ui.chat.rememberTimestampPeekSoftKneePx
import compose.project.click.click.ui.chat.restoreTimestampPeekRawFromDisplay
import compose.project.click.click.ui.chat.ConnectionChatMessageComposer
import compose.project.click.click.ui.chat.ChatTimelineEntry
import compose.project.click.click.ui.chat.ChatTypingDots
import compose.project.click.click.ui.chat.chatBubbleReplySnippetStyle
import compose.project.click.click.ui.chat.chatBubbleScaledDp
import compose.project.click.click.ui.chat.ChatInterMessageListBaseCompact
import compose.project.click.click.ui.chat.chatDeliveryReceiptGapBeforeTimeline
import compose.project.click.click.ui.chat.chatTimelineRowTopPadding
import compose.project.click.click.ui.chat.ConversationDaySeparator
import compose.project.click.click.ui.chat.LoadingSubtitlePlaceholder
import compose.project.click.click.ui.chat.ReplySwipeSideIcon
import compose.project.click.click.ui.chat.buildChatTimelineEntriesNewestFirst
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground
import compose.project.click.click.ui.chat.ChatGlassHeaderPlateTestTag
import compose.project.click.click.ui.chat.ChatComposerChromeFadeUnderlay
import compose.project.click.click.ui.chat.callLogLabel
import compose.project.click.click.ui.chat.formatCallDurationForLog
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
import compose.project.click.click.ui.chat.ChatLinkifyText // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMessageBubble
import compose.project.click.click.ui.chat.ChatMediaPickerHandles // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.util.LruMemoryCache // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    viewModel: ChatViewModel,
    chatId: String,
    onBackPressed: () -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    onOpenGroupMembersPicker: (List<User>) -> Unit = {},
    /**
     * When true, timestamp peek is driven by the parent `InteractiveSwipeBackContainer` horizontal
     * drag (register callbacks with [onRegisterSwipeBackRightToLeftPeek]). When false, the chat
     * surface uses a local full-width left-drag handler instead.
     */
    integrateTimestampPeekWithSwipeBackContainer: Boolean = false,
    onRegisterSwipeBackRightToLeftPeek: (InteractiveSwipeBackRightToLeftPeek?) -> Unit = {},
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
                        onFilePicked = { picked ->
                            viewModel.sendChatFile(picked.bytes, picked.mimeType, picked.fileName)
                        },
                        onMediaAccessBlocked = { msg ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )

                    val headerBlockHeight = topInset + 56.dp
                    /**
                     * With [reverseLayout] = true, [PaddingValues.top] maps to the visual bottom of the
                     * list (adjacent to the typing/composer block). Do not add [imePadding] on the
                     * composer: the parent scaffold / UIKit layer already reserves keyboard space;
                     * stacking IME insets here produced a visible gap (Android `adjustResize` + iOS).
                     */
                    val reverseListNewestEdgePad = 6.dp
                    val messageContentModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Prevent nested IME consumers (e.g. future imePadding) from double-counting
                            // insets already reflected in the surrounding layout on iOS + Android.
                            .consumeWindowInsets(WindowInsets.ime),
                    ) {
                        ChatAmbientMeshBackground(
                            connection = chatDetails.connection,
                            isHubNeutral = false,
                            modifier = Modifier.fillMaxSize(),
                        )

                    // Match ConnectionsListView: list rows + composer are full width; only the top bar uses 20.dp gutters.
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerBlockHeight)
                                .padding(horizontal = 20.dp)
                                .testTag(ChatGlassHeaderPlateTestTag),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
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
                                                    fadeIn(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMedium,
                                                        ),
                                                    ) togetherWith fadeOut(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMedium,
                                                        ),
                                                    )
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
                                    val callMenuSpring = spring<Float>(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    )
                                    val callMenuEnter =
                                        fadeIn(animationSpec = callMenuSpring) +
                                            scaleIn(
                                                initialScale = 0.92f,
                                                animationSpec = callMenuSpring,
                                            )
                                    val callMenuExit =
                                        fadeOut(animationSpec = callMenuSpring) +
                                            scaleOut(
                                                targetScale = 0.96f,
                                                animationSpec = callMenuSpring,
                                            )
                                    var keepIosCallMenuMounted by remember { mutableStateOf(false) }
                                    var iosCallMenuContentVisible by remember { mutableStateOf(false) }
                                    LaunchedEffect(showCallMenu) {
                                        if (showCallMenu) {
                                            keepIosCallMenuMounted = true
                                            iosCallMenuContentVisible = false
                                            withFrameNanos { }
                                            iosCallMenuContentVisible = true
                                        } else {
                                            iosCallMenuContentVisible = false
                                            delay(150)
                                            keepIosCallMenuMounted = false
                                        }
                                    }
                                    if (menuStyle.isIOS) {
                                        if (keepIosCallMenuMounted) {
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
                                                androidx.compose.animation.AnimatedVisibility(
                                                    visible = iosCallMenuContentVisible,
                                                    enter = callMenuEnter,
                                                    exit = callMenuExit,
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
                                            }
                                        }
                                    } else {
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
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
                                val integration = InteractiveSwipeBackRightToLeftPeek(
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
                        val newestSentMessage = remember(messages) {
                            messages.asSequence().filter { it.isSent }.maxByOrNull { it.message.timeCreated }
                        }
                        Box(
                            modifier = messageContentModifier
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
                                )
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                reverseLayout = true,
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 10.dp + reverseListNewestEdgePad,
                                    bottom = 14.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                if (newestSentMessage != null) {
                                    val receiptM = newestSentMessage
                                    items(
                                        listOf(receiptM),
                                        key = { _ -> "outbound-delivery-receipt" },
                                    ) { mwu ->
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    top = chatDeliveryReceiptGapBeforeTimeline(ChatInterMessageListBaseCompact),
                                                    end = 10.dp,
                                                ),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            ChatDeliveryReceiptIcon(
                                                messageWithUser = mwu,
                                                baseTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                readTint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                items(
                                    count = timelineEntries.size,
                                    key = { timelineEntries[it].key },
                                ) { index ->
                                    val entry = timelineEntries[index]
                                    val listGapTop = chatTimelineRowTopPadding(
                                        index = index,
                                        timelineEntries = timelineEntries,
                                        baseCompact = ChatInterMessageListBaseCompact,
                                    )
                                    when (entry) {
                                        is ChatTimelineEntry.DaySeparator -> {
                                            Column(Modifier.padding(top = listGapTop)) {
                                                ConversationDaySeparator(entry.label)
                                            }
                                        }
                                        is ChatTimelineEntry.MessageEntry -> {
                                            val messageWithUser = entry.messageWithUser
                                            val msgReactions =
                                                reactionsMap[messageWithUser.message.id] ?: emptyList()
                                            val isCallLog = messageWithUser.message.messageType == "call_log"
                                            Column(Modifier.padding(top = listGapTop)) {
                                                ChatMessageRowWithTimestampGutter(
                                                    isCallLog = isCallLog,
                                                    isSent = messageWithUser.isSent,
                                                    timeCreated = messageWithUser.message.timeCreated,
                                                    stripVisualPx = displayTimestampPeekVisualPx,
                                                    maxRevealPx = peekRevealPx,
                                                ) {
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
                                                            onDownloadAttachment = { _, env ->
                                                                viewModel.downloadChatAttachment(env)
                                                            },
                                                        )
                                                    }
                                                    if (isCallLog) {
                                                        bubble()
                                                    } else {
                                                        AnimatedVisibilityChatBubble(
                                                            bubbleStabilityKey = chatBubbleStableRowKey(messageWithUser),
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

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Typing indicator — label + bouncing dots (Realtime Broadcast)
                        AnimatedVisibility(
                            visible = isPeerTyping,
                            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                slideInVertically(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    initialOffsetY = { it / 2 },
                                ),
                            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                slideOutVertically(
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                    targetOffsetY = { it / 2 },
                                ),
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
                                            shape = RoundedCornerShape(
                                                topStart = chatBubbleScaledDp(6f),
                                                topEnd = chatBubbleScaledDp(21f),
                                                bottomStart = chatBubbleScaledDp(21f),
                                                bottomEnd = chatBubbleScaledDp(21f),
                                            )
                                        )
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = chatBubbleScaledDp(6f),
                                                topEnd = chatBubbleScaledDp(21f),
                                                bottomStart = chatBubbleScaledDp(21f),
                                                bottomEnd = chatBubbleScaledDp(21f),
                                            )
                                        )
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                                        .padding(horizontal = chatBubbleScaledDp(18f), vertical = chatBubbleScaledDp(12f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(chatBubbleScaledDp(9f))
                                    ) {
                                        Text(
                                            text = typingPeerLabel,
                                            style = chatBubbleReplySnippetStyle(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontStyle = FontStyle.Italic
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

                        Box(modifier = Modifier.fillMaxWidth()) {
                            ChatComposerChromeFadeUnderlay(modifier = Modifier.matchParentSize())
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
