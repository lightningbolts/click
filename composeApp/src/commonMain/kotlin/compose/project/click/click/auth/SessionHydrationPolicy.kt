package compose.project.click.click.auth // pragma: allowlist secret

/**
 * Dual-store session rules: GoTrue [SettingsSessionManager] is the live session;
 * app [TokenStorage] is a mirror. Never import TokenStorage over a live SDK session.
 */
object SessionHydrationPolicy {
    /** Import TokenStorage into GoTrue only when the SDK has no live session. */
    fun shouldImportStoredSession(sdkHasSession: Boolean): Boolean = !sdkHasSession

    /**
     * Copy SDK/Settings tokens into TokenStorage when storage is empty or the refresh
     * token differs (stale TokenStorage must not win).
     */
    fun shouldSyncSdkTokensToStorage(
        storedRefresh: String?,
        sdkRefresh: String?,
    ): Boolean {
        val sdk = sdkRefresh?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val stored = storedRefresh?.trim().orEmpty()
        return stored.isEmpty() || stored != sdk
    }
}
