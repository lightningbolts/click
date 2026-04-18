package compose.project.click.click.data.storage

/**
 * Encrypted / platform-backed session + lightweight prefs shared by Android and iOS.
 */
interface TokenStorage {
    suspend fun saveTokens(jwt: String, refreshToken: String, expiresAt: Long?, tokenType: String?)
    suspend fun getJwt(): String?
    suspend fun getRefreshToken(): String?
    suspend fun getExpiresAt(): Long?
    suspend fun getTokenType(): String?
    suspend fun clearTokens()

    suspend fun saveFreeThisWeek(isFree: Boolean)
    suspend fun getFreeThisWeek(): Boolean?

    suspend fun saveTagsInitialized(initialized: Boolean)
    suspend fun getTagsInitialized(): Boolean?

    suspend fun saveDarkModeEnabled(isDarkMode: Boolean)
    suspend fun getDarkModeEnabled(): Boolean?

    suspend fun saveMessageNotificationsEnabled(enabled: Boolean)
    suspend fun getMessageNotificationsEnabled(): Boolean?

    suspend fun saveCallNotificationsEnabled(enabled: Boolean)
    suspend fun getCallNotificationsEnabled(): Boolean?

    suspend fun saveAmbientNoiseOptIn(enabled: Boolean)
    suspend fun getAmbientNoiseOptIn(): Boolean?

    suspend fun saveBarometricContextOptIn(enabled: Boolean)
    suspend fun getBarometricContextOptIn(): Boolean?

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

    suspend fun clearSessionData()
}

expect fun createTokenStorage(): TokenStorage
