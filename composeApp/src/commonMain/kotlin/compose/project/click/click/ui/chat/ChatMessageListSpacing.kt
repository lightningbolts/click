package compose.project.click.click.ui.chat

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.MessageWithUser

/**
 * Same-author messages use compact vertical gap only if the later one is within this many
 * milliseconds after the earlier one.
 */
internal const val CHAT_INTER_MESSAGE_SAME_AUTHOR_MAX_GAP_MS: Long = 120_000L

/** Main chat timeline: compact vertical gap between rows (loose gaps are 2× this). */
internal val ChatInterMessageListBaseCompact: Dp = 3.dp * 1.25f

/** Hub chat list: compact vertical gap between rows (loose gaps are 2× this). */
internal val ChatInterMessageHubBaseCompact: Dp = 4.dp * 1.25f

internal enum class ChatInterMessageSpacingKind {
    Compact,
    Loose,
}

/**
 * Compare two adjacent message rows (order in time does not matter).
 */
internal fun chatInterMessageSpacingBetweenNeighbors(
    neighborA: MessageWithUser,
    neighborB: MessageWithUser,
): ChatInterMessageSpacingKind {
    val (earlier, later) =
        if (neighborA.message.timeCreated <= neighborB.message.timeCreated) {
            neighborA to neighborB
        } else {
            neighborB to neighborA
        }
    if (earlier.message.user_id != later.message.user_id) {
        return ChatInterMessageSpacingKind.Loose
    }
    val dt = later.message.timeCreated - earlier.message.timeCreated
    return if (dt > CHAT_INTER_MESSAGE_SAME_AUTHOR_MAX_GAP_MS) {
        ChatInterMessageSpacingKind.Loose
    } else {
        ChatInterMessageSpacingKind.Compact
    }
}

internal fun chatInterMessageGapDp(
    kind: ChatInterMessageSpacingKind,
    baseCompact: Dp,
): Dp =
    when (kind) {
        ChatInterMessageSpacingKind.Compact -> baseCompact
        ChatInterMessageSpacingKind.Loose -> baseCompact * 2f
    }

/**
 * Vertical gap between [newerRow] (closer to composer / lower index in newest-first timeline)
 * and [olderRow] (next row toward older history).
 */
internal fun chatTimelineGapBetweenRows(
    newerRow: ChatTimelineEntry,
    olderRow: ChatTimelineEntry,
    baseCompact: Dp,
): Dp {
    if (newerRow is ChatTimelineEntry.DaySeparator || olderRow is ChatTimelineEntry.DaySeparator) {
        return chatInterMessageGapDp(ChatInterMessageSpacingKind.Loose, baseCompact)
    }
    val newer = newerRow as? ChatTimelineEntry.MessageEntry
        ?: return chatInterMessageGapDp(ChatInterMessageSpacingKind.Loose, baseCompact)
    val older = olderRow as? ChatTimelineEntry.MessageEntry
        ?: return chatInterMessageGapDp(ChatInterMessageSpacingKind.Loose, baseCompact)
    return chatInterMessageGapDp(
        chatInterMessageSpacingBetweenNeighbors(newer.messageWithUser, older.messageWithUser),
        baseCompact,
    )
}

/**
 * Vertical gap between the outbound delivery-receipt row and the newest timeline row
 * (always compact). Apply as [androidx.compose.foundation.layout.padding] **top** on the receipt row
 * in a `reverseLayout` chat list (receipt is the first lazy item, directly under the newest message).
 */
internal fun chatDeliveryReceiptGapBeforeTimeline(baseCompact: Dp): Dp =
    chatInterMessageGapDp(ChatInterMessageSpacingKind.Compact, baseCompact)

/**
 * [paddingTop] for a timeline row at [index] in [buildChatTimelineEntriesNewestFirst] order, shown in
 * a `reverseLayout` [androidx.compose.foundation.lazy.LazyColumn] (index 0 sits just above the receipt).
 * The visual neighbor **above** this row is [index + 1]; spacing uses (newer = this row, older = above).
 */
internal fun chatTimelineRowTopPadding(
    index: Int,
    timelineEntries: List<ChatTimelineEntry>,
    baseCompact: Dp,
): Dp {
    if (index >= timelineEntries.lastIndex) return 0.dp
    return chatTimelineGapBetweenRows(
        newerRow = timelineEntries[index],
        olderRow = timelineEntries[index + 1],
        baseCompact = baseCompact,
    )
}

/** [paddingTop] for hub message at [index] (oldest-first list). */
internal fun chatHubMessageRowTopPadding(
    index: Int,
    messages: List<MessageWithUser>,
    baseCompact: Dp,
): Dp {
    if (index == 0) return 0.dp
    return chatInterMessageGapDp(
        chatInterMessageSpacingBetweenNeighbors(messages[index - 1], messages[index]),
        baseCompact,
    )
}

/** [paddingTop] on the delivery-receipt row below the last hub message. */
internal fun chatHubReceiptRowTopPadding(baseCompact: Dp): Dp =
    chatInterMessageGapDp(ChatInterMessageSpacingKind.Compact, baseCompact)
