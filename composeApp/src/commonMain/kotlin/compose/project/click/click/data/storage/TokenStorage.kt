package compose.project.click.click.data.storage

interface TokenStorage {
    suspend fun saveTokens(jwt: String, refreshToken: String, expiresAt: Long? = null, tokenType: String? = null)
    suspend fun getJwt(): String?
    suspend fun getRefreshToken(): String?
    suspend fun getExpiresAt(): Long?
    suspend fun getTokenType(): String?
    suspend fun clearTokens()
    
    // Availability persistence for immediate local storage
    suspend fun saveFreeThisWeek(isFree: Boolean)
    suspend fun getFreeThisWeek(): Boolean?

    // Tags/onboarding local cache to avoid re-showing the tagging screen on app resume
    suspend fun saveTagsInitialized(initialized: Boolean)
    suspend fun getTagsInitialized(): Boolean?

    // Theme preference persistence (true = dark mode, false = light mode)
    suspend fun saveDarkModeEnabled(isDarkMode: Boolean)
    suspend fun getDarkModeEnabled(): Boolean?

    // Push notification preferences
    suspend fun saveMessageNotificationsEnabled(enabled: Boolean)
    suspend fun getMessageNotificationsEnabled(): Boolean?
    suspend fun saveCallNotificationsEnabled(enabled: Boolean)
    suspend fun getCallNotificationsEnabled(): Boolean?

    // One-time opt-in for ambient noise enrichment at connection time
    suspend fun saveAmbientNoiseOptIn(enabled: Boolean)
    suspend fun getAmbientNoiseOptIn(): Boolean?

    // Barometric / elevation context at connection time (height category only; no continuous health tracking)
    suspend fun saveBarometricContextOptIn(enabled: Boolean)
    suspend fun getBarometricContextOptIn(): Boolean?

    // Location onboarding: has the user seen the pre-permission explainer (Build my map / Not now)
    suspend fun saveLocationExplainerSeen(seen: Boolean)
    suspend fun getLocationExplainerSeen(): Boolean?

    // Serialized local state for onboarding, offline cache, and deferred sync
    suspend fun saveOnboardingState(state: String?)
    suspend fun getOnboardingState(): String?
    suspend fun saveCachedAppSnapshot(snapshot: String?)
    suspend fun getCachedAppSnapshot(): String?
    suspend fun savePendingConnectionQueue(queue: String?)
    suspend fun getPendingConnectionQueue(): String?

    /** JSON array of [compose.project.click.click.data.models.PendingHandshake] for offline tri-factor bind replay. */
    suspend fun savePendingProximityHandshakeQueue(queue: String?)
    suspend fun getPendingProximityHandshakeQueue(): String?

    /**
     * Clear all user-specific session data on sign-out.
     * Preserves only device-level preferences (e.g. dark mode).
     */
    suspend fun clearSessionData()
}

expect fun createTokenStorage(): TokenStorage

