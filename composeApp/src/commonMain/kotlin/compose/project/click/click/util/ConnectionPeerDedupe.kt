package compose.project.click.click.util

import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection

/**
 * Stable key for an unordered 1:1 pair. Postgres `UNIQUE(user_ids)` does not treat
 * `[A,B]` and `[B,A]` as equal, so two connection ids can exist for one peer.
 *
 * Always uses the sorted pair — does **not** depend on the viewer id (avoids failing
 * when current user is not yet loaded, or when [ChatWithDetails.otherUser] is wrong).
 */
fun oneToOnePeerPairKey(userIds: List<String>): String? {
    if (userIds.size != 2) return null
    val a = userIds[0].trim()
    val b = userIds[1].trim()
    if (a.isEmpty() || b.isEmpty()) return null
    return if (a <= b) "$a|$b" else "$b|$a"
}

/**
 * Collapses duplicate 1:1 [Connection] rows that share the same peer pair into a single winner.
 *
 * Group / multi-member rows (`user_ids.size != 2`) are left unchanged.
 * Final pass still [distinctBy] connection [Connection.id].
 *
 * [viewerUserId] is unused for keying (kept for call-site compatibility).
 */
@Suppress("UNUSED_PARAMETER")
fun dedupeOneToOneConnectionsByPeer(
    viewerUserId: String,
    connections: List<Connection>,
): List<Connection> {
    if (connections.isEmpty()) return connections

    val ones = linkedMapOf<String, Connection>()
    val groups = ArrayList<Connection>()

    for (conn in connections) {
        val key = oneToOnePeerPairKey(conn.user_ids)
        if (key == null) {
            groups.add(conn)
            continue
        }
        val existing = ones[key]
        ones[key] = if (existing == null) conn else preferOneToOneConnection(existing, conn)
    }

    return (ones.values + groups).distinctBy { it.id }
}

/**
 * Same peer collapse for inbox / connections-list rows. Group cliques pass through unchanged.
 * Keys by sorted [Connection.user_ids], not [ChatWithDetails.otherUser], so swapped-order
 * rows and wrong placeholder peers still collapse.
 */
fun dedupeOneToOneChatsByPeer(chats: List<ChatWithDetails>): List<ChatWithDetails> {
    if (chats.isEmpty()) return chats

    val ones = linkedMapOf<String, ChatWithDetails>()
    val groups = ArrayList<ChatWithDetails>()

    for (chat in chats) {
        if (chat.groupClique != null) {
            groups.add(chat)
            continue
        }
        val key = oneToOnePeerPairKey(chat.connection.user_ids)
        if (key == null) {
            groups.add(chat)
            continue
        }
        val existing = ones[key]
        ones[key] = if (existing == null) {
            chat
        } else {
            val winner = preferOneToOneConnection(existing.connection, chat.connection)
            if (winner.id == chat.connection.id) chat else existing
        }
    }

    return (ones.values + groups).distinctBy { it.connection.id }
}

/** Prefer kept > active > pending > archived/other, then newer activity. */
internal fun preferOneToOneConnection(a: Connection, b: Connection): Connection {
    val rankA = oneToOneStatusRank(a.normalizedConnectionStatus())
    val rankB = oneToOneStatusRank(b.normalizedConnectionStatus())
    if (rankA != rankB) return if (rankA > rankB) a else b

    val activityA = (a.last_message_at ?: 0L).coerceAtLeast(a.created)
    val activityB = (b.last_message_at ?: 0L).coerceAtLeast(b.created)
    if (activityA != activityB) return if (activityA > activityB) a else b

    // Stable tie-break so repeated merges don't thrash.
    return if (a.id >= b.id) a else b
}

/** Higher is better. */
private fun oneToOneStatusRank(status: String): Int = when (status) {
    "kept" -> 4
    "active" -> 3
    "pending" -> 2
    "archived" -> 1
    else -> 0
}
