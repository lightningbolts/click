package compose.project.click.click.data.storage

import platform.Foundation.NSUserDefaults

class IosTokenStorage : TokenStorage {

    private val userDefaults = NSUserDefaults(suiteName = "click_auth_prefs") ?: NSUserDefaults.standardUserDefaults

    override suspend fun saveTokens(jwt: String, refreshToken: String, expiresAt: Long?, tokenType: String?) {
        userDefaults.setObject(jwt, KEY_JWT)
        userDefaults.setObject(refreshToken, KEY_REFRESH_TOKEN)
        if (expiresAt != null) userDefaults.setDouble(expiresAt.toDouble(), KEY_EXPIRES_AT) else userDefaults.removeObjectForKey(KEY_EXPIRES_AT)
        if (tokenType != null) userDefaults.setObject(tokenType, KEY_TOKEN_TYPE) else userDefaults.removeObjectForKey(KEY_TOKEN_TYPE)
        userDefaults.synchronize()
    }

    override suspend fun getJwt(): String? {
        return userDefaults.stringForKey(KEY_JWT)
    }

    override suspend fun getRefreshToken(): String? {
        return userDefaults.stringForKey(KEY_REFRESH_TOKEN)
    }

    override suspend fun getExpiresAt(): Long? {
        val expiry = userDefaults.doubleForKey(KEY_EXPIRES_AT)
        return if (expiry > 0) expiry.toLong() else null
    }

    override suspend fun getTokenType(): String? {
        return userDefaults.stringForKey(KEY_TOKEN_TYPE)
    }

    override suspend fun clearTokens() {
        userDefaults.removeObjectForKey(KEY_JWT)
        userDefaults.removeObjectForKey(KEY_REFRESH_TOKEN)
        userDefaults.removeObjectForKey(KEY_EXPIRES_AT)
        userDefaults.removeObjectForKey(KEY_TOKEN_TYPE)
        userDefaults.synchronize()
    }

    companion object {
        private const val KEY_JWT = "jwt"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_FREE_THIS_WEEK = "free_this_week"
    }
    
    override suspend fun saveFreeThisWeek(isFree: Boolean) {
        userDefaults.setBool(isFree, KEY_FREE_THIS_WEEK)
        userDefaults.synchronize()
    }
    
    override suspend fun getFreeThisWeek(): Boolean? {
        // Check if the key exists first
        return if (userDefaults.objectForKey(KEY_FREE_THIS_WEEK) != null) {
            userDefaults.boolForKey(KEY_FREE_THIS_WEEK)
        } else {
            null
        }
    }
}

actual fun createTokenStorage(): TokenStorage = IosTokenStorage()

