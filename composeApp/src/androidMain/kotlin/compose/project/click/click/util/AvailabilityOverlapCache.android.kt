package compose.project.click.click.util

import java.util.concurrent.ConcurrentHashMap

private fun key(viewerUserId: String, peerUserId: String): String = "$viewerUserId|$peerUserId"

actual object AvailabilityOverlapCache {
    private val hasOverlapByPair = ConcurrentHashMap<String, Boolean>()

    actual fun get(viewerUserId: String, peerUserId: String): Boolean? =
        hasOverlapByPair[key(viewerUserId, peerUserId)]

    actual fun put(viewerUserId: String, peerUserId: String, hasOverlap: Boolean) {
        hasOverlapByPair[key(viewerUserId, peerUserId)] = hasOverlap
    }

    actual fun clear() {
        hasOverlapByPair.clear()
    }
}
