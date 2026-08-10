package compose.project.click.click.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide single-flight for Supabase session refresh.
 *
 * Multiple [compose.project.click.click.data.repository.AuthRepository] instances and
 * [compose.project.click.click.data.api.ApiClient] bearer refresh used to hit `/token`
 * concurrently, causing "Request rate limit reached" and refresh-token rotation races
 * ("Invalid Refresh Token"). All network refresh paths must go through here.
 */
object SessionRefreshCoordinator {
    private val mutex = Mutex()
    private var inFlight: CompletableDeferred<Result<Unit>>? = null

    /**
     * Runs [block] once; concurrent callers await the same [Result].
     */
    suspend fun singleFlightRefresh(block: suspend () -> Result<Unit>): Result<Unit> {
        val leaderDeferred: CompletableDeferred<Result<Unit>>?
        val follower: CompletableDeferred<Result<Unit>>?
        mutex.withLock {
            val existing = inFlight
            if (existing != null) {
                leaderDeferred = null
                follower = existing
            } else {
                val created = CompletableDeferred<Result<Unit>>()
                inFlight = created
                leaderDeferred = created
                follower = null
            }
        }
        if (follower != null) {
            return follower.await()
        }
        val deferred = leaderDeferred!!
        try {
            val result = try {
                block()
            } catch (e: Exception) {
                Result.failure(e)
            }
            deferred.complete(result)
            return result
        } finally {
            mutex.withLock {
                if (inFlight === deferred) inFlight = null
            }
        }
    }

    /** Test-only: clear in-flight state between unit tests. */
    internal suspend fun resetForTests() {
        mutex.withLock {
            inFlight?.cancel()
            inFlight = null
        }
    }
}
