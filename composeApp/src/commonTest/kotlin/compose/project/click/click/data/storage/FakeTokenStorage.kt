package compose.project.click.click.data.storage

/**
 * In-memory [TokenStorage] for tests (avoids Android [createTokenStorage] init requirements).
 */
class FakeTokenStorage(
    private val jwt: String? = null,
    private val refreshToken: String? = "test-refresh-token",
    private val expiresAtEpochMs: Long? = null,
) : TokenStorage {
    private var activeHubsJson: String? = null
    private var pendingEncounterQueueJson: String? = null

    override suspend fun saveTokens(jwt: String, refreshToken: String, expiresAt: Long?, tokenType: String?) {}
    override suspend fun getJwt(): String? = jwt
    override suspend fun getRefreshToken(): String? = refreshToken
    override suspend fun getExpiresAt(): Long? = expiresAtEpochMs
    override suspend fun getTokenType(): String? = "bearer"
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
    override suspend fun saveHasCompletedOnboarding(completed: Boolean) {}
    override suspend fun getHasCompletedOnboarding(): Boolean? = null
    override suspend fun saveCachedAppSnapshot(snapshot: String?) {}
    override suspend fun getCachedAppSnapshot(): String? = null
    override suspend fun savePendingConnectionQueue(queue: String?) {}
    override suspend fun getPendingConnectionQueue(): String? = null
    override suspend fun savePendingProximityHandshakeQueue(queue: String?) {
        pendingEncounterQueueJson = queue
    }
    override suspend fun getPendingProximityHandshakeQueue(): String? = pendingEncounterQueueJson

    override suspend fun saveActiveHubs(json: String?) {
        activeHubsJson = json
    }

    override suspend fun getActiveHubs(): String? = activeHubsJson

    private var beaconRsvpSnapshot: String? = null

    override suspend fun saveBeaconRsvpSnapshot(snapshot: String?) {
        beaconRsvpSnapshot = snapshot
    }

    override suspend fun getBeaconRsvpSnapshot(): String? = beaconRsvpSnapshot

    override suspend fun clearSessionData() {
        beaconRsvpSnapshot = null
        pendingEncounterQueueJson = null
    }
}
