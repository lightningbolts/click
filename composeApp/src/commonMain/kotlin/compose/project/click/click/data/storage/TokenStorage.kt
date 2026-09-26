package compose.project.click.click.data.storage // pragma: allowlist secret

/**
 * Encrypted / platform-backed session + lightweight prefs.
 */
interface TokenStorage {
    suspend fun saveTokens(
        jwt: String,
        refreshToken: String,
        expiresAt: Long?,
        tokenType: String?,
    )

    suspend fun getJwt(): String?

    suspend fun getRefreshToken(): String?

    suspend fun getExpiresAt(): Long?

    suspend fun getTokenType(): String?

    suspend fun getUserId(): String?

    suspend fun clearTokens()

    suspend fun saveFreeThisWeek(isFree: Boolean)

    suspend fun getFreeThisWeek(): Boolean?

    suspend fun saveTagsInitialized(initialized: Boolean)

    suspend fun getTagsInitialized(): Boolean?

    suspend fun saveDarkModeEnabled(isDarkMode: Boolean)

    suspend fun getDarkModeEnabled(): Boolean?

    suspend fun saveHomeLayoutMode(mode: String)

    suspend fun getHomeLayoutMode(): String?

    suspend fun saveMessageNotificationsEnabled(enabled: Boolean)

    suspend fun getMessageNotificationsEnabled(): Boolean?

    suspend fun saveCallNotificationsEnabled(enabled: Boolean)

    suspend fun getCallNotificationsEnabled(): Boolean?

    suspend fun saveAmbientNoiseOptIn(enabled: Boolean)

    suspend fun getAmbientNoiseOptIn(): Boolean?

    suspend fun saveBarometricContextOptIn(enabled: Boolean)

    suspend fun getBarometricContextOptIn(): Boolean?

    /** Custom plan ideas (JSON string array), shown first in the planner. Device-local. */
    suspend fun savePlanCustomIdeas(json: String?) {}

    suspend fun getPlanCustomIdeas(): String? = null

    /** Opt-in "Hanging out?" detection; there is no server column (iOS keeps it local too). */
    suspend fun saveHangoutDetectionOptIn(enabled: Boolean) {}

    suspend fun getHangoutDetectionOptIn(): Boolean? = null

    /** Home recap cache (JSON, one user at a time); cleared with the session. */
    suspend fun saveHomeRecapCache(json: String?) {}

    suspend fun getHomeRecapCache(): String? = null

    /** Per-chat push mutes (JSON map chatId -> muted-until ms); cleared with the session. */
    suspend fun saveChatMutesCache(json: String?) {}

    suspend fun getChatMutesCache(): String? = null

    /** Recent E2EE v2 epoch keys for on-device push previews (encrypted prefs); cleared with the session. */
    suspend fun savePushPreviewKeys(json: String?) {}

    suspend fun getPushPreviewKeys(): String? = null

    /** Recently used reaction emoji (JSON list, newest first). Device-local. */
    suspend fun saveRecentEmoji(json: String?) {}

    suspend fun getRecentEmoji(): String? = null

    suspend fun saveLocationExplainerSeen(seen: Boolean)

    suspend fun getLocationExplainerSeen(): Boolean?

    suspend fun saveOnboardingState(state: String?)

    suspend fun getOnboardingState(): String?

    suspend fun saveHasCompletedOnboarding(completed: Boolean)

    suspend fun getHasCompletedOnboarding(): Boolean?

    suspend fun saveCachedAppSnapshot(snapshot: String?)

    suspend fun getCachedAppSnapshot(): String?

    suspend fun savePendingConnectionQueue(queue: String?)

    suspend fun getPendingConnectionQueue(): String?

    suspend fun savePendingProximityHandshakeQueue(queue: String?)

    suspend fun getPendingProximityHandshakeQueue(): String?

    suspend fun saveActiveHubs(json: String?)

    suspend fun getActiveHubs(): String?

    /** JSON snapshot of per-beacon RSVP state for the signed-in user (survives process death). */
    suspend fun saveBeaconRsvpSnapshot(snapshot: String?)

    suspend fun getBeaconRsvpSnapshot(): String?

    /** JSON snapshot of bookmark + check-in flags for the signed-in user. */
    suspend fun saveBeaconEngagementSnapshot(snapshot: String?)

    suspend fun getBeaconEngagementSnapshot(): String?

    suspend fun clearSessionData()
}

expect fun createTokenStorage(): TokenStorage
