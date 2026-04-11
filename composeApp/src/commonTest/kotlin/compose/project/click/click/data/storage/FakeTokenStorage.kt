package compose.project.click.click.data.storage

/**
 * In-memory [TokenStorage] for tests (avoids Android [createTokenStorage] init requirements).
 */
class FakeTokenStorage : TokenStorage {
    override suspend fun saveTokens(jwt: String, refreshToken: String, expiresAt: Long?, tokenType: String?) {}
    override suspend fun getJwt(): String? = null
    override suspend fun getRefreshToken(): String? = null
    override suspend fun getExpiresAt(): Long? = null
    override suspend fun getTokenType(): String? = null
    override suspend fun clearTokens() {}
    override suspend fun saveFreeThisWeek(isFree: Boolean) {}
    override suspend fun getFreeThisWeek(): Boolean? = null
    override suspend fun saveTagsInitialized(initialized: Boolean) {}
    override suspend fun getTagsInitialized(): Boolean? = null
    override suspend fun saveDarkModeEnabled(isDarkMode: Boolean) {}
    override suspend fun getDarkModeEnabled(): Boolean? = null
    override suspend fun saveMessageNotificationsEnabled(enabled: Boolean) {}
    override suspend fun getMessageNotificationsEnabled(): Boolean? = null
    override suspend fun saveCallNotificationsEnabled(enabled: Boolean) {}
    override suspend fun getCallNotificationsEnabled(): Boolean? = null
    override suspend fun saveAmbientNoiseOptIn(enabled: Boolean) {}
    override suspend fun getAmbientNoiseOptIn(): Boolean? = null
    override suspend fun saveBarometricContextOptIn(enabled: Boolean) {}
    override suspend fun getBarometricContextOptIn(): Boolean? = null
    override suspend fun saveLocationExplainerSeen(seen: Boolean) {}
    override suspend fun getLocationExplainerSeen(): Boolean? = null
    override suspend fun saveOnboardingState(state: String?) {}
    override suspend fun getOnboardingState(): String? = null
    override suspend fun saveCachedAppSnapshot(snapshot: String?) {}
    override suspend fun getCachedAppSnapshot(): String? = null
    override suspend fun savePendingConnectionQueue(queue: String?) {}
    override suspend fun getPendingConnectionQueue(): String? = null
    override suspend fun savePendingProximityHandshakeQueue(queue: String?) {}
    override suspend fun getPendingProximityHandshakeQueue(): String? = null
    override suspend fun clearSessionData() {}
}
