package compose.project.click.click.collaboration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Active re-engagement window after existing-friend proximity bumps. */
data class CollaborationSession(
    val encounterId: String,
    val connectionId: String,
    val collaborationTtlIso: String,
    val createdAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
) {
    fun collaborationTtlInstant(): Instant? =
        runCatching { Instant.parse(collaborationTtlIso) }.getOrNull()

    /** Disposable Roll capture + icon while before reveal TTL. */
    fun isRollActive(now: Instant = Clock.System.now()): Boolean {
        val ttl = collaborationTtlInstant() ?: return false
        return now < ttl
    }

    /** Squad map drop multiplier window (15 minutes from bump). */
    fun isMapDropEligible(now: Instant = Clock.System.now()): Boolean {
        if (!isRollActive(now)) return false
        val ageMs = now.toEpochMilliseconds() - createdAtEpochMs
        return ageMs in 0 until MAP_DROP_WINDOW_MS
    }

    companion object {
        const val MAP_DROP_WINDOW_MS: Long = 15L * 60L * 1000L
    }
}

/**
 * In-memory store of collaboration sessions keyed by [CollaborationSession.connectionId].
 * Populated from bind-proximity-connection when `is_new_connection == false`.
 */
object CollaborationSessionManager {
    private val _sessions = MutableStateFlow<Map<String, CollaborationSession>>(emptyMap())
    val sessions: StateFlow<Map<String, CollaborationSession>> = _sessions.asStateFlow()

    fun activate(session: CollaborationSession) {
        if (session.encounterId.isBlank() || session.connectionId.isBlank()) return
        _sessions.update { cur -> cur + (session.connectionId to session) }
    }

    fun forConnection(connectionId: String): CollaborationSession? =
        _sessions.value[connectionId]

    fun activeMapDropSession(): CollaborationSession? {
        val now = Clock.System.now()
        return _sessions.value.values.firstOrNull { it.isMapDropEligible(now) }
    }

    fun clear(connectionId: String) {
        _sessions.update { cur -> cur - connectionId }
    }
}
