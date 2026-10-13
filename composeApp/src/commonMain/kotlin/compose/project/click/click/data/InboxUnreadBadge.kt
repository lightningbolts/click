package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret

/**
 * The unread badge on the Clicks tab (sheet 11 B2; iOS `RootGateView` `.badge(conversations.unreadTotal)`).
 *
 * iOS `ConversationListModel.unreadTotal` sums the Active 1:1 chats and the groups. Muted chats still
 * count (iOS doesn't exclude them); archived and hidden chats don't, since they aren't in Active.
 */
object InboxUnreadBadge {
    /** Above this the badge reads "99+". */
    const val MAX_SHOWN = 99

    fun total(
        chats: List<ChatWithDetails>,
        archivedIds: Set<String>,
        hiddenIds: Set<String>,
    ): Int =
        chats.sumOf { chat ->
            val counts = chat.groupClique != null || chat.connection.isActiveForUser(archivedIds, hiddenIds)
            if (counts) chat.unreadCount.coerceAtLeast(0) else 0
        }

    /** The badge text, or null for no badge. */
    fun label(count: Int): String? =
        when {
            count <= 0 -> null
            count > MAX_SHOWN -> "$MAX_SHOWN+"
            else -> count.toString()
        }

    /** TalkBack for the Clicks tab: "Clicks, {n} unread". */
    fun contentDescription(
        title: String,
        count: Int,
    ): String = if (count > 0) "$title, $count unread" else title
}
