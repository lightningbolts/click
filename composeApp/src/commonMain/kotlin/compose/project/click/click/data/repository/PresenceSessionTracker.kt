package compose.project.click.click.data.repository

/** Tracks sessions, not users: closing one device must not mark another device offline. */
internal class PresenceSessionTracker {
    private val usersBySession = mutableMapOf<String, String>()

    fun update(
        joins: Map<String, String>,
        leftSessionIds: Set<String>,
    ): Set<String> {
        leftSessionIds.forEach(usersBySession::remove)
        // Presence refresh can leave and rejoin in the same event. The new state wins.
        usersBySession.putAll(joins)
        return usersBySession.values.toSet()
    }
}
