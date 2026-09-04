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
import compose.project.click.click.data.models.IcebreakerPrompt // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.encounter.EncounterTetherManager // pragma: allowlist secret
import compose.project.click.click.ui.chat.CHAT_SEARCH_FOCUS_HOLD_MS // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChromeMotion // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatComposerStripReserve // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMediaPickerHandles // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMessageTimeline // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatNativeKeyboardInsets // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatTypingDots // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionChatMessageComposer // pragma: allowlist secret
import compose.project.click.click.ui.chat.IcebreakerPanel // pragma: allowlist secret
import compose.project.click.click.ui.chat.applyTimestampPeekDragStep // pragma: allowlist secret
import compose.project.click.click.ui.chat.buildChatTimelineEntriesNewestFirst // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatBubbleReplySnippetStyle // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatBubbleScaledDp // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimestampPeekOnSwipeLeft // pragma: allowlist secret
import compose.project.click.click.ui.chat.indexOfMessageId // pragma: allowlist secret
import compose.project.click.click.ui.chat.isTimestampPeekRevealed // pragma: allowlist secret
import compose.project.click.click.ui.chat.launchTimestampPeekReplyStyleSettle // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekRevealPx // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekSoftKneePx // pragma: allowlist secret
import compose.project.click.click.ui.chat.restoreTimestampPeekRawFromDisplay // pragma: allowlist secret
import compose.project.click.click.ui.chat.scrollChatTimelineToMessage // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.ui.components.chatThreadKeyboardDock // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    nativeKeyboardInsets: ChatNativeKeyboardInsets,
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
    reactionsMap: Map<String, List<compose.project.click.click.data.models.MessageReaction>>,
    isLoadingOlderMessages: Boolean,
    isPeerTyping: Boolean,
    typingPeerLabel: String,
    editingMessageId: String?,
    replyingTo: MessageWithUser?,
    mediaPickers: ChatMediaPickerHandles,
    tetherChannelId: String,
    tetherSenderAckState: MutableState<String?>,
    onOpenDisposableRoll: ((connectionId: String) -> Unit)?,
    onOpenDisposableRollForChat: ((chatId: String) -> Unit)?,
    shareableBeacons: List<compose.project.click.click.data.models.MapBeacon>,
    mapViewModel: compose.project.click.click.viewmodel.MapViewModel?,
    forwardMessageIdState: MutableState<String?>,
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
    var forwardMessageId by forwardMessageIdState
    var contextMenuMessage by contextMenuMessageState
    var expandedPhotoTarget by expandedPhotoTargetState
    var openBeaconDetailId by openBeaconDetailIdState
    var openBeaconDetailFallback by openBeaconDetailFallbackState
    var openBeaconDetailMetadata by openBeaconDetailMetadataState
    var openBeaconDetailContent by openBeaconDetailContentState
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
}
