package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.ChatMessageType
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
    if (!mwu.isSent) return "msg-${m.id}"
    val stamp = m.localSentAt ?: return "msg-${m.id}"
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

/** LazyColumn [contentType] so keyboard resize reuses row nodes instead of remounting bubbles. */
internal fun ChatTimelineEntry.timelineContentType(): Any = when (this) {
    is ChatTimelineEntry.DaySeparator -> "day_separator"
    is ChatTimelineEntry.MessageEntry -> {
        val mt = messageWithUser.message.messageType?.takeIf { it.isNotBlank() } ?: ChatMessageType.TEXT
        // Subtype keeps heavy attachment rows from recycling into text bubbles mid-fling
        // (height mismatch = visible jump).
        when (mt.lowercase()) {
            ChatMessageType.IMAGE -> "image"
            ChatMessageType.AUDIO -> "audio"
            ChatMessageType.BEACON -> "beacon"
            ChatMessageType.CALL_LOG -> "call_log"
            else -> mt
        }
    }
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
    // Always walk chronological order so day separators cannot oscillate.
    messages
        .sortedWith(compareBy({ it.message.timeCreated }, { it.message.id }))
        .forEach { messageWithUser ->
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
    return ensureUniqueTimelineKeys(timeline)
}

/**
 * Timeline for `reverseLayout` chat: newest message is **first** in the
 * list (index 0) so it sits next to the composer. Day separators are
 * inserted when the day changes walking newest → oldest, with a final
 * trailing separator for the oldest day in the window.
 */
internal fun buildChatTimelineEntriesNewestFirst(messages: List<MessageWithUser>): List<ChatTimelineEntry> {
    if (messages.isEmpty()) return emptyList()
    // Sort first — callers sometimes feed unsorted hot-cache / merge output.
    // Walking unsorted list order makes day separators oscillate (Apr 22 → Mar 6 → Apr 22).
    val newestFirst = messages
        .sortedWith(compareBy({ it.message.timeCreated }, { it.message.id }))
        .asReversed()
    val out = mutableListOf<ChatTimelineEntry>()
    var currentDayKey: String? = null
    var currentDayTimestamp = 0L
    var separatorSeq = 0

    newestFirst.forEach { messageWithUser ->
        val dayKey = messageDayKey(messageWithUser.message.timeCreated)
        if (currentDayKey != null && dayKey != currentDayKey) {
            // Include a monotonic seq so residual day revisits cannot collide keys.
            out += ChatTimelineEntry.DaySeparator(
                key = "separator-nf-${separatorSeq++}-$currentDayKey",
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

    return ensureUniqueTimelineKeys(out)
}

/**
 * Last-resort uniqueness for LazyColumn item keys. Day oscillation, duplicate
 * outbound `localSentAt` stamps, or optimistic/delivered pairs that have not yet
 * converged can otherwise crash Compose with "Key was already used".
 */
internal fun ensureUniqueTimelineKeys(entries: List<ChatTimelineEntry>): List<ChatTimelineEntry> {
    if (entries.isEmpty()) return entries
    val seen = HashSet<String>(entries.size)
    return entries.mapIndexed { index, entry ->
        var key = entry.key
        if (!seen.add(key)) {
            key = "${entry.key}#$index"
            seen.add(key)
        }
        when (entry) {
            is ChatTimelineEntry.DaySeparator -> entry.copy(key = key)
            is ChatTimelineEntry.MessageEntry -> entry.copy(key = key)
        }
    }
}

// Ambient mesh color seeding for chat lives in [ChatAmbientColorSeeds]; this file keeps timeline keys only.
