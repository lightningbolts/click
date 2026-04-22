package compose.project.click.click.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSRecursiveLock

private fun key(viewerUserId: String, peerUserId: String): String = "$viewerUserId|$peerUserId"

@OptIn(ExperimentalForeignApi::class)
actual object AvailabilityOverlapCache {
    private val lock = NSRecursiveLock()
    private val hasOverlapByPair = mutableMapOf<String, Boolean>()

    actual fun get(viewerUserId: String, peerUserId: String): Boolean? {
        lock.lock()
        return try {
            hasOverlapByPair[key(viewerUserId, peerUserId)]
        } finally {
            lock.unlock()
        }
    }

    actual fun put(viewerUserId: String, peerUserId: String, hasOverlap: Boolean) {
        lock.lock()
        try {
            hasOverlapByPair[key(viewerUserId, peerUserId)] = hasOverlap
        } finally {
            lock.unlock()
        }
    }

    actual fun clear() {
        lock.lock()
        try {
            hasOverlapByPair.clear()
        } finally {
            lock.unlock()
        }
    }
}
