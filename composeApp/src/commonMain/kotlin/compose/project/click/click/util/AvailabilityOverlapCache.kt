package compose.project.click.click.util

/**
 * Last-known availability-intent overlap between [viewerUserId] and [peerUserId].
 * Used so chat / connection rows can show the bolt immediately after navigation without a null flash.
 */
expect object AvailabilityOverlapCache {
    fun get(viewerUserId: String, peerUserId: String): Boolean?
    fun put(viewerUserId: String, peerUserId: String, hasOverlap: Boolean)
    fun clear()
}
