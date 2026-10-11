package compose.project.click.click.data.auth // pragma: allowlist secret

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ends a session the server ended (its refresh token rejected), the same way a sign-out does
 * (iOS F127, `SessionController.endSession`): sockets, epoch-key prefs, push preview keys, the
 * disk caches and the outbox go; the account's local message store stays.
 *
 * Runs once per ended account, however many requests saw the 401. A sign-in that lands while the
 * teardown runs (e.g. a magic link) keeps its session: accounts are compared by user id, not token.
 */
class SessionEndCoordinator(
    /** The account currently signed in on this device (token storage / SDK), or null. */
    private val currentUserId: suspend () -> String?,
    /** In-memory state, sockets and caches. Must keep the local message store. */
    private val teardown: suspend () -> Unit,
    /** Credentials and session-scoped prefs (tokens, push preview keys, epoch keys). */
    private val clearCredentials: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private var endedUserId: String? = null

    /** Returns true when this call ended [userId]'s session. */
    suspend fun endRejectedSession(userId: String?): Boolean {
        val ending = userId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        mutex.withLock {
            if (endedUserId == ending) return false
            // Another account is already signed in: the rejected token wasn't theirs.
            if (currentUserId() != ending) return false
            endedUserId = ending
        }
        runCatching { teardown() }
        // A sign-in that landed during the teardown keeps its session.
        if (currentUserId() != ending) return false
        runCatching { clearCredentials() }
        return true
    }

    /** A (re)sign-in of this account: a later rejection must end it again. */
    suspend fun onSignedIn(userId: String) {
        mutex.withLock { if (endedUserId == userId) endedUserId = null }
    }
}

/** App-wide entry point; [AuthViewModel] installs the real teardown at startup. */
object SessionEnd {
    private var coordinator: SessionEndCoordinator? = null
    private val _ended = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** User ids whose session was ended by the server (the UI returns to sign-in). */
    val ended: SharedFlow<String> = _ended.asSharedFlow()

    fun install(coordinator: SessionEndCoordinator) {
        this.coordinator = coordinator
    }

    /** A refresh was rejected (400/401 "invalid refresh token") for [userId]. */
    suspend fun onRefreshRejected(userId: String?): Boolean {
        val c = coordinator ?: return false
        val ended = c.endRejectedSession(userId)
        if (ended && userId != null) _ended.emit(userId)
        return ended
    }

    suspend fun onSignedIn(userId: String) {
        coordinator?.onSignedIn(userId)
    }
}
