@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.viewmodel.SecureChatMediaHost
import kotlinx.coroutines.delay
import kotlin.math.abs

internal fun chatTimelineShouldFollowInbound(
    firstVisibleItemIndex: Int,
    initialTimelineScrollDone: Boolean,
): Boolean = initialTimelineScrollDone && firstVisibleItemIndex <= 2

internal fun chatTimelineFollowUsesAnimation(initialTimelineScrollDone: Boolean): Boolean = initialTimelineScrollDone

/**
 * Shared reverse-layout snap used only for initial paint and near-bottom inbound messages.
 * Initial paint uses [scrollToItem]; inbound follow uses [animateScrollToItem] to stay smooth
 * without placement animation on history rows.
 */
internal suspend fun scrollChatTimelineToLatest(
    listState: LazyListState,
    suppressKeyboardDismiss: MutableState<Boolean>,
    animated: Boolean = false,
) {
    // Never interrupt an in-flight user fling — that is what feels like stutter/jump
    // when heavy attachment rows are still measuring.
    if (listState.isScrollInProgress) return
    repeat(12) {
        if (listState.isScrollInProgress) return
        if (listState.layoutInfo.totalItemsCount > 0) {
            suppressKeyboardDismiss.value = true
            try {
                if (animated) {
                    listState.animateScrollToItem(0)
                } else {
                    listState.scrollToItem(0)
                }
                delay(48)
            } finally {
                suppressKeyboardDismiss.value = false
            }
            return
        }
        delay(16)
    }
}

/**
 * Dismiss IME only after the user's fling has finished.
 *
 * Clearing focus (and shrinking keyboard insets / timeline padding) mid-drag or mid-coast
 * resizes the LazyColumn while velocity is still applied — that is what made chat scroll
 * feel like it abruptly stopped. Mark dismiss during drag; run it in [onPostFling].
 */
internal fun chatDismissKeyboardAfterScrollConnection(
    thresholdPx: Float,
    isSuppressed: () -> Boolean,
    onDismiss: () -> Unit,
): NestedScrollConnection =
    object : NestedScrollConnection {
        private var dismissAfterFling = false

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (isSuppressed()) return Offset.Zero
            if (source == NestedScrollSource.UserInput && abs(consumed.y) > thresholdPx) {
                dismissAfterFling = true
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity,
        ): Velocity {
            if (dismissAfterFling && !isSuppressed()) {
                dismissAfterFling = false
                onDismiss()
            } else {
                dismissAfterFling = false
            }
            return Velocity.Zero
        }
    }

/**
 * Isolated message list for chat screens. Kept separate from [compose.project.click.click.ui.screens.ChatView]
 * so IME-driven layout passes on the thread dock do not recompose this subtree.
 */
@Composable
internal fun ChatMessageTimeline(
    timelineEntries: List<ChatTimelineEntry>,
    listState: LazyListState,
    newestSentMessage: MessageWithUser?,
    listBottomPadding: PaddingValues,
    dismissKeyboardOnUserMessageScroll: NestedScrollConnection,
    displayTimestampPeekVisualPx: MutableFloatState,
    peekRevealPx: Float,
    meshConnection: Connection?,
    useHubNeutralMesh: Boolean,
    isGroupChat: Boolean,
    currentUserId: String?,
    reactionsMap: Map<String, List<MessageReaction>>,
    secureMediaHost: SecureChatMediaHost,
    activeChatId: String?,
    onToggleReaction: (messageId: String, reaction: String) -> Unit,
    onForward: (messageId: String) -> Unit,
    onLongPress: (MessageWithUser) -> Unit,
    onSwipeReply: (MessageWithUser) -> Unit,
    onDownloadAttachment: suspend (
        MessageWithUser,
        compose.project.click.click.chat.attachments.AttachmentCrypto.Envelope,
    ) -> ChatAttachmentDownloadOutcome,
    onExpandPhoto: (MessageWithUser) -> Unit = {},
    onOpenBeacon: (Message) -> Unit = {},
    isLoadingOlderMessages: Boolean = false,
    interMessageBaseCompact: Dp = ChatInterMessageListBaseCompact,
    enableMessageContextMenu: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val onToggleReactionState = rememberUpdatedState(onToggleReaction)
    val onForwardState = rememberUpdatedState(onForward)
    val onLongPressState = rememberUpdatedState(onLongPress)
    val onSwipeReplyState = rememberUpdatedState(onSwipeReply)
    val onDownloadAttachmentState = rememberUpdatedState(onDownloadAttachment)
    val onExpandPhotoState = rememberUpdatedState(onExpandPhoto)
    val onOpenBeaconState = rememberUpdatedState(onOpenBeacon)

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(dismissKeyboardOnUserMessageScroll),
            reverseLayout = true,
            contentPadding = listBottomPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.Bottom),
        ) {
            if (newestSentMessage != null) {
                val receiptM = newestSentMessage
                items(
                    items = listOf(receiptM),
                    key = { _ -> "outbound-delivery-receipt" },
                    contentType = { "delivery_receipt" },
                ) { mwu ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = chatDeliveryReceiptGapBeforeTimeline(interMessageBaseCompact),
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
                contentType = { timelineEntries[it].timelineContentType() },
            ) { index ->
                val entry = timelineEntries[index]
                val listGapTop =
                    chatTimelineRowTopPadding(
                        index = index,
                        timelineEntries = timelineEntries,
                        baseCompact = interMessageBaseCompact,
                    )
                when (entry) {
                    is ChatTimelineEntry.DaySeparator -> {
                        Column(Modifier.padding(top = listGapTop)) {
                            ConversationDaySeparator(entry.label)
                        }
                    }
                    is ChatTimelineEntry.MessageEntry -> {
                        val messageWithUser = entry.messageWithUser
                        val msgReactions = reactionsMap[messageWithUser.message.id] ?: emptyList()
                        val mt = messageWithUser.message.messageType.lowercase()
                        // Beacons are regular actionable messages (timestamp peek, reply swipe,
                        // long-press menu). Only call logs skip the gutter/gesture chrome.
                        val isCallLog = mt == ChatMessageType.CALL_LOG
                        Column(Modifier.padding(top = listGapTop)) {
                            ChatMessageRowWithTimestampGutter(
                                isCallLog = isCallLog,
                                isSent = messageWithUser.isSent,
                                timeCreated = messageWithUser.message.timeCreated,
                                stripVisualPx = displayTimestampPeekVisualPx,
                                maxRevealPx = peekRevealPx,
                                meshConnection = meshConnection,
                                useHubNeutralMesh = useHubNeutralMesh,
                            ) {
                                val bubble: @Composable () -> Unit = {
                                    ChatMessageBubble(
                                        messageWithUser = messageWithUser,
                                        currentUserId = currentUserId,
                                        reactions = msgReactions,
                                        onToggleReaction = { reaction ->
                                            onToggleReactionState.value(
                                                messageWithUser.message.id,
                                                reaction,
                                            )
                                        },
                                        onForward = { msgId -> onForwardState.value(msgId) },
                                        onLongPress = { onLongPressState.value(it) },
                                        onSwipeReply = { onSwipeReplyState.value(it) },
                                        showPeerAvatarInGroup = isGroupChat,
                                        secureMediaHost = secureMediaHost,
                                        activeChatId = activeChatId,
                                        enableMessageContextMenu = enableMessageContextMenu,
                                        onDownloadAttachment = { mwu, env ->
                                            onDownloadAttachmentState.value(mwu, env)
                                        },
                                        onExpandPhoto = { onExpandPhotoState.value(it) },
                                        onOpenBeacon = { onOpenBeaconState.value(it) },
                                    )
                                }
                                if (isCallLog) {
                                    bubble()
                                } else {
                                    val stabilityKey = chatBubbleStableRowKey(messageWithUser)
                                    // Optimistic outbound only — layout-stable pop-in. Never animate
                                    // recycled history rows (that kills LazyColumn fling coast).
                                    val isOptimisticOutbound =
                                        messageWithUser.isSent &&
                                            messageWithUser.message.id.startsWith("temp-")
                                    AnimatedVisibilityChatBubble(
                                        bubbleStabilityKey = stabilityKey,
                                        isSent = messageWithUser.isSent,
                                        animateEnter = isOptimisticOutbound,
                                        content = bubble,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isLoadingOlderMessages) {
                item(key = "load_older_indicator") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
