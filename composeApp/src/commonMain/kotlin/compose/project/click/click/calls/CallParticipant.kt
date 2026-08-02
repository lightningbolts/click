package compose.project.click.click.calls

/**
 * One person in the LiveKit room, including the local user.
 * Video tracks are bound separately via [CallVideoSurface] using [identity].
 */
data class CallParticipant(
    val identity: String,
    val displayName: String,
    val isLocal: Boolean,
    val isMuted: Boolean,
    val isSpeaking: Boolean,
    val cameraEnabled: Boolean,
    val hasVideo: Boolean,
)

enum class CallLayoutMode {
    Grid,
    Speaker,
}

/**
 * Pure rules for Grid vs Speaker layout selection.
 *
 * - Default: Speaker when [participantCount] <= 3, Grid when >= 4
 * - Manual override sticks until participant count crosses the threshold
 *   relative to when the override was set (or until end of call / clear)
 */
object CallLayoutPolicy {
    const val SPEAKER_MAX_PARTICIPANTS = 3

    fun defaultMode(participantCount: Int): CallLayoutMode =
        if (participantCount <= SPEAKER_MAX_PARTICIPANTS) {
            CallLayoutMode.Speaker
        } else {
            CallLayoutMode.Grid
        }

    /**
     * @param participantCount current roster size (local + remotes)
     * @param manualOverride user-selected mode, or null for auto
     * @param overrideAtCount participant count when [manualOverride] was set (ignored if override null)
     */
    fun resolveMode(
        participantCount: Int,
        manualOverride: CallLayoutMode?,
        overrideAtCount: Int = 0,
    ): CallLayoutMode {
        if (manualOverride == null) return defaultMode(participantCount)
        val wasSpeakerSide = overrideAtCount <= SPEAKER_MAX_PARTICIPANTS
        val isSpeakerSide = participantCount <= SPEAKER_MAX_PARTICIPANTS
        // Threshold crossed → drop override and follow auto rules
        if (wasSpeakerSide != isSpeakerSide) return defaultMode(participantCount)
        return manualOverride
    }

    /**
     * Active speaker: first speaking remote, else first remote with video, else first remote, else local.
     */
    fun pickActiveSpeaker(participants: List<CallParticipant>): CallParticipant? {
        if (participants.isEmpty()) return null
        val remotes = participants.filter { !it.isLocal }
        remotes.firstOrNull { it.isSpeaking }?.let { return it }
        remotes.firstOrNull { it.hasVideo }?.let { return it }
        remotes.firstOrNull()?.let { return it }
        return participants.firstOrNull { it.isLocal } ?: participants.firstOrNull()
    }

    fun initialsFor(displayName: String): String {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }

    fun selfLabel(displayName: String): String = "You ($displayName)"

    fun formatDuration(elapsedMs: Long): String {
        val totalSec = (elapsedMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
