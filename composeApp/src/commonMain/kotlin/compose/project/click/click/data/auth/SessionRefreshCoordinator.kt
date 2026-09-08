package compose.project.click.click.data.auth

import compose.project.click.click.crypto.PlatformCrypto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

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
    private var lastSuccessfulRefreshAtMs: Long = 0L
    private var lastFailureAtMs: Long = 0L
    private var lastFailure: Result<Unit>? = null
    private var lastFailureCredential: List<Byte>? = null
    private var inFlightCredential: List<Byte>? = null
    private const val COALESCE_MS = 15_000L

    /** GoTrue `/token` rate-limit window — do not retry immediately after a failed refresh. */
    internal const val FAILURE_COOLDOWN_MS = 30_000L

    /** Burned refresh tokens stay dead; do not hammer `/token` for a quarter-hour. */
    internal const val HARD_FAILURE_COOLDOWN_MS = 15 * 60 * 1000L

    private var lastFailureCooldownMs: Long = FAILURE_COOLDOWN_MS

    /**
     * Runs [block] once for concurrent callers using the same credential.
     * After a failure, further callers reuse that [Result] until [FAILURE_COOLDOWN_MS] elapses
     * so a JWT-expired stampede cannot hammer GoTrue into a longer rate limit.
     */
    suspend fun singleFlightRefresh(
        credential: String? = null,
        block: suspend () -> Result<Unit>,
    ): Result<Unit> {
        // Retain only a digest as the cache identity, never the refresh credential itself.
        val credentialKey = credential?.let { PlatformCrypto.sha256(it.encodeToByteArray()).toList() }
        val leaderDeferred: CompletableDeferred<Result<Unit>>?
        val follower: CompletableDeferred<Result<Unit>>?
        val coalescedFailure: Result<Unit>?
        var differentCredentialInFlight = false
        mutex.withLock {
            val failed = lastFailure
            if (failed != null && lastFailureCredential == credentialKey && recentlyFailedLocked()) {
                leaderDeferred = null
                follower = null
                coalescedFailure = failed
            } else {
                coalescedFailure = null
                val existing = inFlight
                if (existing != null) {
                    leaderDeferred = null
                    follower = existing
                    differentCredentialInFlight = inFlightCredential != credentialKey
                } else {
                    val created = CompletableDeferred<Result<Unit>>()
                    inFlight = created
                    inFlightCredential = credentialKey
                    leaderDeferred = created
                    follower = null
                }
            }
        }
        if (coalescedFailure != null) {
            return coalescedFailure
        }
        if (follower != null) {
            val result = follower.await()
            // Serialize SDK refreshes, but never give a newly signed-in session the old
            // credential's result. No credential is logged or persisted here.
            return if (differentCredentialInFlight) singleFlightRefresh(credential, block) else result
        }
        val deferred = leaderDeferred!!
        try {
            var cancellation: CancellationException? = null
            val result =
                try {
                    block()
                } catch (e: CancellationException) {
                    cancellation = e
                    Result.failure<Unit>(e)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            withContext(NonCancellable) {
                mutex.withLock {
                    recordResultLocked(result, credentialKey)
                    if (inFlight === deferred) {
                        inFlight = null
                        inFlightCredential = null
                    }
                    deferred.complete(result)
                }
            }
            cancellation?.let { throw it }
            currentCoroutineContext().ensureActive()
            return result
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (inFlight === deferred) {
                        inFlight = null
                        inFlightCredential = null
                    }
                }
            }
        }
    }

    fun markSuccessfulRefresh() {
        lastSuccessfulRefreshAtMs = Clock.System.now().toEpochMilliseconds()
        lastFailureAtMs = 0L
        lastFailure = null
        lastFailureCredential = null
        lastFailureCooldownMs = FAILURE_COOLDOWN_MS
    }

    fun recentlyRefreshed(): Boolean {
        val last = lastSuccessfulRefreshAtMs
        if (last <= 0L) return false
        return Clock.System.now().toEpochMilliseconds() - last < COALESCE_MS
    }

    fun recentlyFailed(): Boolean {
        val last = lastFailureAtMs
        if (last <= 0L) return false
        return Clock.System.now().toEpochMilliseconds() - last < lastFailureCooldownMs
    }

    fun clearSuccessfulRefresh() {
        lastSuccessfulRefreshAtMs = 0L
    }

    /** Test-only: clear in-flight state between unit tests. */
    internal suspend fun resetForTests() {
        mutex.withLock {
            inFlight?.cancel()
            inFlight = null
            inFlightCredential = null
            lastSuccessfulRefreshAtMs = 0L
            lastFailureAtMs = 0L
            lastFailure = null
            lastFailureCredential = null
            lastFailureCooldownMs = FAILURE_COOLDOWN_MS
        }
    }

    private fun recentlyFailedLocked(): Boolean {
        val last = lastFailureAtMs
        if (last <= 0L) return false
        return Clock.System.now().toEpochMilliseconds() - last < lastFailureCooldownMs
    }

    private fun recordResultLocked(
        result: Result<Unit>,
        credential: List<Byte>?,
    ) {
        val exception = result.exceptionOrNull()
        if (result.isSuccess || exception is CancellationException) {
            lastFailureAtMs = 0L
            lastFailure = null
            lastFailureCredential = null
            lastFailureCooldownMs = FAILURE_COOLDOWN_MS
            return
        }

        lastFailureAtMs = Clock.System.now().toEpochMilliseconds()
        lastFailure = result
        lastFailureCredential = credential
        val msg = exception?.message.orEmpty().lowercase()
        lastFailureCooldownMs =
            if (
                msg.contains("already used") ||
                msg.contains("invalid refresh") ||
                msg.contains("refresh token not found")
            ) {
                HARD_FAILURE_COOLDOWN_MS
            } else {
                FAILURE_COOLDOWN_MS
            }
    }
}
