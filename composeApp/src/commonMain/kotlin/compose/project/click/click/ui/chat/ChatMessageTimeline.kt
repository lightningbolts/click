package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.viewmodel.SecureChatMediaLoadState
import compose.project.click.click.viewmodel.SecureChatMediaHost

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
    secureMediaLoadMap: Map<String, SecureChatMediaLoadState>,
    secureMediaHost: SecureChatMediaHost,
    activeChatId: String?,
    onToggleReaction: (messageId: String, reaction: String) -> Unit,
    onForward: (messageId: String) -> Unit,
    onLongPress: (MessageWithUser) -> Unit,
    onSwipeReply: (MessageWithUser) -> Unit,
    onDownloadAttachment: suspend (MessageWithUser, compose.project.click.click.chat.attachments.AttachmentCrypto.Envelope) -> ChatAttachmentDownloadOutcome,
    modifier: Modifier = Modifier,
) {
    val onToggleReactionState = rememberUpdatedState(onToggleReaction)
    val onForwardState = rememberUpdatedState(onForward)
    val onLongPressState = rememberUpdatedState(onLongPress)
    val onSwipeReplyState = rememberUpdatedState(onSwipeReply)
    val onDownloadAttachmentState = rememberUpdatedState(onDownloadAttachment)

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(dismissKeyboardOnUserMessageScroll),
            reverseLayout = true,
            contentPadding = listBottomPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
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
                contentType = { timelineEntries[it].timelineContentType() },
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
                        val msgReactions = reactionsMap[messageWithUser.message.id] ?: emptyList()
                        val isCallLog = messageWithUser.message.messageType == "call_log"
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
                                        secureMediaState = secureMediaLoadMap[messageWithUser.message.id],
                                        activeChatId = activeChatId,
                                        onDownloadAttachment = { mwu, env ->
                                            onDownloadAttachmentState.value(mwu, env)
                                        },
                                    )
                                }
                                if (isCallLog) {
                                    bubble()
                                } else {
                                    AnimatedVisibilityChatBubble(
                                        bubbleStabilityKey = chatBubbleStableRowKey(messageWithUser),
                                        isSent = messageWithUser.isSent,
                                        content = bubble,
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
