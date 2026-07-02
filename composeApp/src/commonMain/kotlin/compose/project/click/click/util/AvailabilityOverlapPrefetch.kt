package compose.project.click.click.util

import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val AVAILABILITY_OVERLAP_PREFETCH_CONCURRENCY = 8
/** Cap peer overlap fetches so power users with hundreds of connections do not stampede Supabase. */
const val AVAILABILITY_OVERLAP_MAX_PEERS = 48

/** Session cache for the signed-in user's availability bubbles (avoids re-fetch per row/chat). */
object ViewerAvailabilityBubblesCache {
    private var viewerUserId: String? = null
    private var bubbles: List<ProfileAvailabilityIntentBubble>? = null

    fun put(viewerUserId: String, bubbles: List<ProfileAvailabilityIntentBubble>) {
        this.viewerUserId = viewerUserId
        this.bubbles = bubbles
    }

    fun get(viewerUserId: String): List<ProfileAvailabilityIntentBubble>? {
        if (viewerUserId != this.viewerUserId) return null
        return bubbles
    }

    fun clear() {
        viewerUserId = null
        bubbles = null
    }
}

/**
 * Fetches peer availability bubbles and writes overlap results into [AvailabilityOverlapCache].
 * Skips peers already cached; returns how many peers were fetched.
 */
suspend fun prefetchAvailabilityOverlapsForPeers(
    viewerUserId: String,
    peerUserIds: List<String>,
    viewerBubbles: List<ProfileAvailabilityIntentBubble>,
    repository: SupabaseRepository = SupabaseRepository(),
    concurrency: Int = AVAILABILITY_OVERLAP_PREFETCH_CONCURRENCY,
): Int = withContext(Dispatchers.Default) {
    if (viewerUserId.isBlank()) return@withContext 0
    val uncachedPeerIds = peerUserIds
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != viewerUserId }
        .distinct()
        .take(AVAILABILITY_OVERLAP_MAX_PEERS)
        .filter { peerId -> AvailabilityOverlapCache.get(viewerUserId, peerId) == null }
        .toList()
    if (uncachedPeerIds.isEmpty()) return@withContext 0

    val limiter = Semaphore(concurrency.coerceAtLeast(1))
    coroutineScope {
        uncachedPeerIds.map { peerId ->
            async {
                limiter.withPermit {
                    val theirs = runCatching {
                        repository.fetchPeerProfileAvailabilityBubbles(viewerUserId, peerId)
                    }.getOrElse { emptyList() }
                    val hasOverlap = hasActiveAvailabilityIntentOverlap(viewerBubbles, theirs)
                    AvailabilityOverlapCache.put(viewerUserId, peerId, hasOverlap)
                }
            }
        }.awaitAll()
    }
    uncachedPeerIds.size
}
