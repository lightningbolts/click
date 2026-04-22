package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection

/**
 * Tiny pure helpers used across the connection-list UI (ConnectionItem,
 * ConnectionsListView, ForwardDialog). Extracted from ConnectionsScreen.kt
 * for clarity and independent testability; no behavior change.
 */

/**
 * Sort key for Clicks list: prefer server `last_message_at`, then last
 * message time, then connection creation time. Lower-is-older; callers
 * use [List.sortedByDescending] for newest-first ordering.
 */
internal fun connectionListActivityTs(chat: ChatWithDetails): Long =
    chat.connection.last_message_at
        ?: chat.lastMessage?.timeCreated
        ?: chat.connection.created

/**
 * `true` iff the connection has no usable GPS location for the map:
 * either no geo, or NaN/infinite, or (0,0) null-island.
 */
internal fun connectionHasNoGeo(connection: Connection): Boolean {
    val g = connection.connectionMapGeo() ?: return true
    return !g.lat.isFinite() || !g.lon.isFinite() || (g.lat == 0.0 && g.lon == 0.0)
}
