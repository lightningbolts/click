@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit // pragma: allowlist secret
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.HangoutPlan
import compose.project.click.click.data.models.IcebreakerPrompt // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.PlanResponses
import compose.project.click.click.data.models.seenByPlacement
import compose.project.click.click.data.models.withTombstonePlaceholders
import compose.project.click.click.encounter.EncounterTetherManager // pragma: allowlist secret
import compose.project.click.click.ui.chat.CHAT_SEARCH_FOCUS_HOLD_MS // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChromeMotion // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatComposerStripReserve // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMediaPickerHandles // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMessageTimeline // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatSearchBar
import compose.project.click.click.ui.chat.ChatTypingDots // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionChatMessageComposer // pragma: allowlist secret
import compose.project.click.click.ui.chat.IcebreakerPanel // pragma: allowlist secret
import compose.project.click.click.ui.chat.JumpToLatestButton
import compose.project.click.click.ui.chat.PlanResponsesSheet
import compose.project.click.click.ui.chat.ReactorsSheet
import compose.project.click.click.ui.chat.applyTimestampPeekDragStep // pragma: allowlist secret
import compose.project.click.click.ui.chat.buildChatTimelineEntriesNewestFirst // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatBubbleReplySnippetStyle // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatBubbleScaledDp // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatComposerKeyboardMotion // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatSearchMatches
import compose.project.click.click.ui.chat.chatTimelineKeyboardViewport // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimelineShouldFollowKeyboard // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimestampPeekOnSwipeLeft // pragma: allowlist secret
import compose.project.click.click.ui.chat.firstUnreadMessageId
import compose.project.click.click.ui.chat.indexOfMessageId // pragma: allowlist secret
import compose.project.click.click.ui.chat.isTimestampPeekRevealed // pragma: allowlist secret
import compose.project.click.click.ui.chat.launchTimestampPeekReplyStyleSettle // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatTimelineKeyboardFollow // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekRevealPx // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekSoftKneePx // pragma: allowlist secret
import compose.project.click.click.ui.chat.restoreTimestampPeekRawFromDisplay // pragma: allowlist secret
import compose.project.click.click.ui.chat.scrollChatTimelineToMessage // pragma: allowlist secret
import compose.project.click.click.ui.chat.withUnreadDivider
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.ChatViewTimelinePane(
    viewModel: ChatViewModel,
    chatId: String,
    targetMessageId: String?,
    state: ChatMessagesState.Success,
    chatDetails: ChatWithDetails,
    messages: List<MessageWithUser>,
    isGroupChat: Boolean,
    currentUserId: String?,
    activeApiChatId: String?,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    dismissKeyboardOnUserMessageScroll: NestedScrollConnection,
    suppressKeyboardDismissWhileProgrammaticTimelineScroll: MutableState<Boolean>,
    initialTimelineScrollDoneState: MutableState<Boolean>,
    focusedSearchMessageIdState: MutableState<String?>,
    integrateTimestampPeekWithSwipeBackContainer: Boolean,
    onRegisterSwipeBackRightToLeftPeek: (InteractiveSwipeBackRightToLeftPeek?) -> Unit,
    reverseListNewestEdgePad: Dp,
    messageContentModifier: Modifier,
    showIcebreaker: Boolean,
    icebreakerPrompts: List<IcebreakerPrompt>,
    icebreakerCooldownRemainingSec: Int,
    icebreakerPanelHeightPxState: MutableIntState,
    icebreakerTimelineTopReserve: Dp,
    isLoadingOlderMessages: Boolean,
    typingPeerLabel: String,
    editingMessageId: String?,
    replyingTo: MessageWithUser?,
    mediaPickers: ChatMediaPickerHandles,
    tetherChannelId: String,
    tetherSenderAckState: MutableState<String?>,
    onOpenUserProfile: (String) -> Unit,
    onOpenDisposableRoll: ((connectionId: String) -> Unit)?,
    onOpenDisposableRollForChat: ((chatId: String) -> Unit)?,
    shareableBeacons: List<compose.project.click.click.data.models.MapBeacon>,
    mapViewModel: compose.project.click.click.viewmodel.MapViewModel?,
    contextMenuMessageState: MutableState<MessageWithUser?>,
    expandedPhotoTargetState: MutableState<MessageWithUser?>,
    openBeaconDetailIdState: MutableState<String?>,
    openBeaconDetailFallbackState: MutableState<compose.project.click.click.data.models.MapBeacon?>,
    openBeaconDetailMetadataState: MutableState<kotlinx.serialization.json.JsonObject?>,
    openBeaconDetailContentState: MutableState<String?>,
) {
    var initialTimelineScrollDone by initialTimelineScrollDoneState
    var focusedSearchMessageId by focusedSearchMessageIdState
    var icebreakerPanelHeightPx by icebreakerPanelHeightPxState
    var tetherSenderAck by tetherSenderAckState
    var contextMenuMessage by contextMenuMessageState
    var expandedPhotoTarget by expandedPhotoTargetState
    var openBeaconDetailId by openBeaconDetailIdState
    var openBeaconDetailFallback by openBeaconDetailFallbackState
    var openBeaconDetailMetadata by openBeaconDetailMetadataState
    var openBeaconDetailContent by openBeaconDetailContentState
    var planResponsesTarget by remember { mutableStateOf<HangoutPlan?>(null) }
    var reactionsForMessageId by remember { mutableStateOf<String?>(null) }
    var planResponsesMessageId by remember { mutableStateOf<String?>(null) }
    val timelineFollowsKeyboardState =
        rememberChatTimelineKeyboardFollow(
            shouldFollowOnKeyboardOpen = {
                chatTimelineShouldFollowKeyboard(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    initialTimelineScrollDone = initialTimelineScrollDoneState.value,
                    userScrollInProgress = listState.isScrollInProgress,
                )
            },
        )
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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

                    // Messages. The keyboard only moves this viewport for sessions that began at
                    // latest; history remains visually stationary while the composer follows IME.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = icebreakerTimelineTopReserve)
                                .clipToBounds()
                                .chatTimelineKeyboardViewport(
                                    followKeyboard = { timelineFollowsKeyboardState.value },
                                ).zIndex(1f),
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
                                    usePrimaryBorder = false,
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
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            val tombstones by viewModel.tombstones.collectAsState()
                            // Captured once per open: where "New messages" starts.
                            var firstUnreadId by remember(chatId) { mutableStateOf<String?>(null) }
                            var unreadCaptured by remember(chatId) { mutableStateOf(false) }
                            if (!unreadCaptured && messages.isNotEmpty() && !state.isLoadingMessages) {
                                unreadCaptured = true
                                firstUnreadId = firstUnreadMessageId(messages)
                            }
                            val timelineEntries =
                                remember(messages, tombstones, firstUnreadId) {
                                    val usersById = messages.associate { it.user.id to it.user }
                                    withUnreadDivider(
                                        buildChatTimelineEntriesNewestFirst(
                                            withTombstonePlaceholders(
                                                messages = messages,
                                                tombstones = tombstones.values,
                                                userFor = { id ->
                                                    usersById[id] ?: compose.project.click.click.data.models
                                                        .User(id = id)
                                                },
                                                viewerUserId = currentUserId,
                                            ),
                                        ),
                                        firstUnreadId,
                                    )
                                }
                            val readCursors by viewModel.readCursors.collectAsState()
                            val seenBy =
                                remember(messages, readCursors, isGroupChat, currentUserId) {
                                    if (!isGroupChat || readCursors.isEmpty()) {
                                        emptyMap()
                                    } else {
                                        val users =
                                            buildMap {
                                                chatDetails.groupMemberUsers.forEach { put(it.id, it) }
                                                messages.forEach { putIfAbsent(it.user.id, it.user) }
                                            }
                                        seenByPlacement(messages.map { it.message }, readCursors, currentUserId)
                                            .mapValues { (_, ids) -> ids.mapNotNull { users[it] } }
                                            .filterValues { it.isNotEmpty() }
                                    }
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
                                reactionsMap = emptyMap(),
                                reactionsFlow = viewModel.messageReactions,
                                secureMediaHost = viewModel,
                                activeChatId = activeApiChatId ?: chatDetails.chat.id,
                                onToggleReaction = { messageId, reaction ->
                                    viewModel.toggleReaction(messageId, reaction)
                                },
                                onLongPress = { contextMenuMessage = it },
                                onSwipeReply = { viewModel.startReplyTo(it) },
                                onPeerAvatarClick = onOpenUserProfile,
                                onDownloadAttachment = { mwu, env ->
                                    viewModel.downloadChatAttachment(mwu.message.id, env, mwu.message)
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
                                onPlanRsvp = { id, rsvp -> viewModel.setPlanRsvp(id, rsvp) },
                                onOpenReactions = { mwu -> reactionsForMessageId = mwu.message.id },
                                seenBy = seenBy,
                                onShowPlanResponses = { mwu, plan ->
                                    planResponsesMessageId = mwu.message.id
                                    planResponsesTarget = plan
                                },
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
                            // Jump to latest, with how many incoming messages arrived while scrolled up.
                            val scrolledAway by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 6 } }
                            var awayAnchorMs by remember(chatId) { mutableStateOf<Long?>(null) }
                            LaunchedEffect(scrolledAway) {
                                awayAnchorMs = if (scrolledAway) messages.maxOfOrNull { it.message.timeCreated } else null
                            }
                            val unseen =
                                awayAnchorMs?.let { anchor -> messages.count { !it.isSent && it.message.timeCreated > anchor } } ?: 0
                            if (scrolledAway) {
                                JumpToLatestButton(
                                    unseenCount = unseen,
                                    onClick = {
                                        focusedSearchMessageId = null
                                        coroutineScope.launch { listState.animateScrollToItem(0) }
                                    },
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 16.dp, bottom = ChatComposerStripReserve + 16.dp),
                                )
                            }
                            // In-conversation search over loaded (decrypted) messages.
                            val searchOpen by viewModel.chatSearchOpen.collectAsState()
                            if (searchOpen) {
                                var query by remember(chatId) { mutableStateOf("") }
                                var matchIndex by remember(chatId) { mutableStateOf(0) }
                                val matches = remember(messages, query) { chatSearchMatches(messages, query) }
                                LaunchedEffect(matches, matchIndex) {
                                    val id = matches.getOrNull(matchIndex) ?: return@LaunchedEffect
                                    focusedSearchMessageId = id
                                    val index = timelineEntries.indexOfMessageId(id)
                                    if (index >= 0) listState.animateScrollToItem(index)
                                }
                                ChatSearchBar(
                                    query = query,
                                    onQueryChange = {
                                        query = it
                                        matchIndex = 0
                                    },
                                    position = if (matches.isEmpty()) 0 else matchIndex + 1,
                                    total = matches.size,
                                    onOlder = { if (matchIndex < matches.lastIndex) matchIndex++ },
                                    onNewer = { if (matchIndex > 0) matchIndex-- },
                                    onClose = {
                                        focusedSearchMessageId = null
                                        viewModel.closeChatSearch()
                                    },
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                            }
                        }
                    }
                }
            }

            // Composer/accessory chrome tracks the keyboard independently from the timeline.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .chatComposerKeyboardMotion(),
            ) {
                ChatTypingIndicator(viewModel = viewModel, typingPeerLabel = typingPeerLabel)

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
    reactionsForMessageId?.let { messageId ->
        val reactions by viewModel.messageReactions.collectAsState()
        val chatMessagesState by viewModel.chatMessagesState.collectAsState()
        val successMessages = (chatMessagesState as? ChatMessagesState.Success)?.messages.orEmpty()
        val forMessage = reactions[messageId].orEmpty()
        LaunchedEffect(forMessage.isEmpty()) { if (forMessage.isEmpty()) reactionsForMessageId = null }
        val names =
            remember(successMessages, chatDetails) {
                buildMap {
                    put(chatDetails.otherUser.id, chatDetails.otherUser.name ?: "Someone")
                    chatDetails.groupMemberUsers.forEach { u -> u.name?.let { put(u.id, it) } }
                    successMessages.forEach { mwu -> mwu.user.name?.let { put(mwu.user.id, it) } }
                }
            }
        ReactorsSheet(
            reactions = forMessage,
            viewerUserId = currentUserId,
            nameFor = { id -> names[id] ?: "Someone" },
            onRemoveMine = { emoji -> viewModel.toggleReaction(messageId, emoji) },
            onAddReaction = {
                reactionsForMessageId = null
                successMessages.firstOrNull { it.message.id == messageId }?.let { contextMenuMessage = it }
            },
            onDismiss = { reactionsForMessageId = null },
        )
    }
    val responsesPlan = planResponsesTarget
    val responsesMessageId = planResponsesMessageId
    if (responsesPlan != null && responsesMessageId != null) {
        val reactions by viewModel.messageReactions.collectAsState()
        val chatMessagesState by viewModel.chatMessagesState.collectAsState()
        val names =
            remember(chatMessagesState, chatDetails) {
                buildMap {
                    put(chatDetails.otherUser.id, chatDetails.otherUser.name ?: "Someone")
                    (chatMessagesState as? ChatMessagesState.Success)?.messages?.forEach { mwu ->
                        mwu.user.name?.let { put(mwu.user.id, it) }
                    }
                }
            }
        PlanResponsesSheet(
            plan = responsesPlan,
            responses = PlanResponses.from(reactions[responsesMessageId].orEmpty()),
            nameFor = { id -> if (id == currentUserId) "You" else names[id] ?: "Someone" },
            onDismiss = {
                planResponsesTarget = null
                planResponsesMessageId = null
            },
        )
    }
}

@Composable
private fun ChatTypingIndicator(
    viewModel: ChatViewModel,
    typingPeerLabel: String,
) {
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
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
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 80.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bubbleShape =
                RoundedCornerShape(
                    topStart = chatBubbleScaledDp(6f),
                    topEnd = chatBubbleScaledDp(21f),
                    bottomStart = chatBubbleScaledDp(21f),
                    bottomEnd = chatBubbleScaledDp(21f),
                )
            Box(
                modifier =
                    Modifier
                        .border(width = 1.dp, color = PrimaryBlue.copy(alpha = 0.15f), shape = bubbleShape)
                        .clip(bubbleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .padding(horizontal = chatBubbleScaledDp(18f), vertical = chatBubbleScaledDp(12f)),
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
}
