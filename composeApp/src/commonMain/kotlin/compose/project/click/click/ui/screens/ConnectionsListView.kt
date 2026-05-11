package compose.project.click.click.ui.screens // pragma: allowlist secret

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
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import compose.project.click.click.ui.components.GlassAdaptiveBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
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
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.originalMimeTypeOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.ui.chat.CallLogSystemRow // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatBubblePhotoContent // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChannelLoadingView // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatWarmLoadingView // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionItem // pragma: allowlist secret
import compose.project.click.click.ui.chat.ForwardDialog // pragma: allowlist secret
import compose.project.click.click.ui.chat.VibeCheckBanner // pragma: allowlist secret
import compose.project.click.click.ui.chat.GroupMembersPickerSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.LocationGapNudge // pragma: allowlist secret
import compose.project.click.click.ui.chat.MessageActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.orderedGroupMembersForPicker // pragma: allowlist secret
import compose.project.click.click.ui.chat.connectionHasNoGeo // pragma: allowlist secret
import compose.project.click.click.ui.chat.connectionListActivityTs // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatCallOptionsIosSurface // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionChatMessageComposer // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatTimelineEntry // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatTypingDots // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConversationDaySeparator // pragma: allowlist secret
import compose.project.click.click.ui.chat.LoadingSubtitlePlaceholder // pragma: allowlist secret
import compose.project.click.click.ui.chat.ReplySwipeSideIcon // pragma: allowlist secret
import compose.project.click.click.ui.chat.buildChatTimelineEntriesNewestFirst // pragma: allowlist secret
import compose.project.click.click.ui.chat.callLogLabel // pragma: allowlist secret
import compose.project.click.click.ui.chat.formatCallDurationForLog // pragma: allowlist secret
import compose.project.click.click.ui.chat.formatConnectionListTimestamp // pragma: allowlist secret
import compose.project.click.click.ui.chat.formatConversationDayLabel // pragma: allowlist secret
import compose.project.click.click.ui.chat.formatVibeCheckTime // pragma: allowlist secret
import compose.project.click.click.ui.chat.messageDayKey // pragma: allowlist secret
import compose.project.click.click.ui.chat.replyDragHintProgress // pragma: allowlist secret
import compose.project.click.click.ui.chat.swipeRawTravelFromVisual // pragma: allowlist secret
import compose.project.click.click.ui.chat.swipeVisualFromRawTravel // pragma: allowlist secret
import compose.project.click.click.data.models.copyableText // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.previewLabel // pragma: allowlist secret
import compose.project.click.click.data.models.parsedMediaMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.toUserProfile // pragma: allowlist secret
import coil3.compose.AsyncImage // pragma: allowlist secret
import androidx.compose.foundation.layout.offset // pragma: allowlist secret
import androidx.compose.material.icons.outlined.Edit // pragma: allowlist secret
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
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.absoluteValue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.ui.chat.ChatLinkifyText // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMessageBubble // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMediaPickerHandles // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.util.LruMemoryCache // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret

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
                        .fillMaxWidth()
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
                            .weight(1f)
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
                            .weight(1f)
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
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 18.dp,
                                bottom = 20.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
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
        GlassAdaptiveBottomSheet(
            onDismissRequest = { dismissVerifiedCliqueSheet() },
            adaptiveSheetState = cliqueSheetState,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(GlassSheetTokens.OledBlack),
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
