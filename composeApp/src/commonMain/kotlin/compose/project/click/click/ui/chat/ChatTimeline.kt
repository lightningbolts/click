package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.MessageWithUser

/**
 * Stable Compose/LazyColumn identity for a row so an optimistic outbound bubble
 * (`temp-…` id) and the same row after the server assigns a UUID do not remount
 * (avoids replaying the bubble enter animation and keeps reactions map aligned
 * until ids converge where needed).
 *
 * Inbound rows keep [Message.id] as the key. Outbound rows prefer
 * `local_sent_at` + sender id when present (mirrors click-web insert payload).
 */
internal fun chatBubbleStableRowKey(mwu: MessageWithUser): String {
    val m = mwu.message
    if (!mwu.isSent) return m.id
    val stamp = m.localSentAt ?: return m.id
    return "out-${m.user_id}-$stamp"
}

/**
 * Entries fed into the chat LazyColumn: either a day separator or a
 * message row. Each has a stable [key] for Compose item reuse.
 */
internal sealed interface ChatTimelineEntry {
    val key: String

    data class DaySeparator(
        override val key: String,
        val label: String,
    ) : ChatTimelineEntry

    data class MessageEntry(
        override val key: String,
        val messageWithUser: MessageWithUser,
    ) : ChatTimelineEntry
}

/**
 * Classic oldest-first timeline with a day separator prepended whenever
 * the local calendar day changes walking forward.
 *
 * Currently unused in production (the chat LazyColumn uses reverseLayout
 * and [buildChatTimelineEntriesNewestFirst]) — kept for API parity and
 * for test coverage of the simpler shape.
 */
internal fun buildChatTimelineEntries(messages: List<MessageWithUser>): List<ChatTimelineEntry> {
    if (messages.isEmpty()) return emptyList()

    val timeline = mutableListOf<ChatTimelineEntry>()
    var previousDayKey: String? = null
    messages.forEach { messageWithUser ->
        val dayKey = messageDayKey(messageWithUser.message.timeCreated)
        if (dayKey != previousDayKey) {
            timeline += ChatTimelineEntry.DaySeparator(
                key = "separator-$dayKey-${messageWithUser.message.id}",
                label = formatConversationDayLabel(messageWithUser.message.timeCreated),
            )
            previousDayKey = dayKey
        }
        timeline += ChatTimelineEntry.MessageEntry(
            key = chatBubbleStableRowKey(messageWithUser),
            messageWithUser = messageWithUser,
        )
    }
    return timeline
}

/**
 * Timeline for `reverseLayout` chat: newest message is **first** in the
 * list (index 0) so it sits next to the composer. Day separators are
 * inserted when the day changes walking newest → oldest, with a final
 * trailing separator for the oldest day in the window.
 */
internal fun buildChatTimelineEntriesNewestFirst(messages: List<MessageWithUser>): List<ChatTimelineEntry> {
    if (messages.isEmpty()) return emptyList()
    val newestFirst = messages.asReversed()
    val out = mutableListOf<ChatTimelineEntry>()
    var currentDayKey: String? = null
    var currentDayTimestamp = 0L

    newestFirst.forEach { messageWithUser ->
        val dayKey = messageDayKey(messageWithUser.message.timeCreated)
        if (currentDayKey != null && dayKey != currentDayKey) {
            out += ChatTimelineEntry.DaySeparator(
                key = "separator-nf-$currentDayKey",
                label = formatConversationDayLabel(currentDayTimestamp),
            )
        }
        if (dayKey != currentDayKey) {
            currentDayTimestamp = messageWithUser.message.timeCreated
        }
        out += ChatTimelineEntry.MessageEntry(
            key = chatBubbleStableRowKey(messageWithUser),
            messageWithUser = messageWithUser,
        )
        currentDayKey = dayKey
    }

    if (currentDayKey != null) {
        out += ChatTimelineEntry.DaySeparator(
            key = "separator-nf-tail-$currentDayKey",
            label = formatConversationDayLabel(currentDayTimestamp),
        )
    }

    return out
}

// Ambient mesh color seeding for chat lives in [ChatAmbientColorSeeds]; this file keeps timeline keys only.
