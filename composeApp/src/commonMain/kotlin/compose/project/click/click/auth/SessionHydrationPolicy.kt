package compose.project.click.click.auth // pragma: allowlist secret

/**
 * Dual-store session rules: GoTrue [io.github.jan.supabase.auth.SettingsSessionManager] is the
 * live session; app [compose.project.click.click.data.storage.TokenStorage] is a mirror.
 *
 * Import TokenStorage over a live SDK session **only** when storage clearly won a later
 * rotation (newer access `exp`). Importing a stale refresh token causes
 * "Invalid Refresh Token: Already Used".
 */
object SessionHydrationPolicy {
    /**
     * @param sdkHasSession true when GoTrue currently has any session (including expired).
     * @param sdkRefresh SDK refresh token; ignored when [sdkHasSession] is false.
     * @param storedRefresh TokenStorage refresh token.
     * @param sdkAccessExpMs SDK access-token expiry; later exp means a more recent rotation.
     * @param storedAccessExpMs TokenStorage access-token expiry.
     */
    fun shouldImportStoredSession(
        sdkHasSession: Boolean,
        sdkRefresh: String? = null,
        storedRefresh: String? = null,
        sdkAccessExpMs: Long? = null,
        storedAccessExpMs: Long? = null,
    ): Boolean {
        if (!sdkHasSession) return true
        val stored = storedRefresh?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val sdk = sdkRefresh?.trim().orEmpty()
        if (sdk.isEmpty()) return true
        if (stored == sdk) return false
        val storedExp = storedAccessExpMs ?: return false
        val sdkExp = sdkAccessExpMs ?: return true
        return storedExp > sdkExp + 1_000L
    }

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
