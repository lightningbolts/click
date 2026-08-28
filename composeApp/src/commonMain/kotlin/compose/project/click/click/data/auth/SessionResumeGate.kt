package compose.project.click.click.data.auth // pragma: allowlist secret

import kotlin.concurrent.Volatile

/**
 * After idle/offline boot the UI may look signed-in from disk while GoTrue
 * still has a dead access token. Network writes wait until the first
 * refreshSession attempt finishes (success or hard fail).
 */
object SessionResumeGate {
    @Volatile
    private var completed: Boolean = false

    fun isCompleted(): Boolean = completed

    fun markCompleted() {
        completed = true
    }

    fun resetForTests() {
        completed = false
    }
}
