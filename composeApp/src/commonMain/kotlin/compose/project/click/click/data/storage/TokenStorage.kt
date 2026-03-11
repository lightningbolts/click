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
}

expect fun createTokenStorage(): TokenStorage

