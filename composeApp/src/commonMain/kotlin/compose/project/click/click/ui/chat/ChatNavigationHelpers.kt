package compose.project.click.click.ui.chat // pragma: allowlist secret

import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.isDeletedPlaceholder // pragma: allowlist secret

/*
 * Timeline navigation helpers (iOS ConversationModel.firstUnreadID, ChatSearchBar, typing names).
 */

/** The oldest incoming message not yet read when the chat opened, or null when all are read. */
internal fun firstUnreadMessageId(messages: List<MessageWithUser>): String? =
    messages
        .filter { !it.isSent && !it.message.isRead && it.message.readAt == null && !it.message.id.startsWith("temp-") }
        .minByOrNull { it.message.timeCreated }
        ?.message
        ?.id

/** Inserts a "New messages" divider above [firstUnreadId] in a newest-first timeline. */
internal fun withUnreadDivider(
    newestFirst: List<ChatTimelineEntry>,
    firstUnreadId: String?,
): List<ChatTimelineEntry> {
    val id = firstUnreadId ?: return newestFirst
    val index =
        newestFirst.indexOfFirst { it is ChatTimelineEntry.MessageEntry && it.messageWithUser.message.id == id }
    if (index < 0) return newestFirst
    return newestFirst.toMutableList().apply { add(index + 1, ChatTimelineEntry.UnreadDivider(key = "unread-divider-$id")) }
}

/** Ids of loaded messages whose readable text contains [query], newest first. */
internal fun chatSearchMatches(
    messages: List<MessageWithUser>,
    query: String,
): List<String> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    return messages
        .asSequence()
        .filter { !it.message.isDeletedPlaceholder() && !it.message.id.startsWith("temp-") }
        .filter {
            it.message.messageType
                .ifBlank { ChatMessageType.TEXT }
                .lowercase() == ChatMessageType.TEXT
        }.filter { it.message.content.contains(q, ignoreCase = true) }
        .sortedByDescending { it.message.timeCreated }
        .map { it.message.id }
        .toList()
}

/** "Lena is typing…", "Lena and Sam are typing…", "3 people are typing…". */
internal fun typingLabel(names: List<String>): String? =
    when (names.size) {
        0 -> null
        1 -> "${names[0]} is typing…"
        2 -> "${names[0]} and ${names[1]} are typing…"
        else -> "${names.size} people are typing…"
    }
