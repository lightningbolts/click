package compose.project.click.click.data.storage

import platform.Foundation.NSUserDefaults

class IosTokenStorage : TokenStorage {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    override suspend fun saveTokens(jwt: String, refreshToken: String) {
        userDefaults.setObject(jwt, KEY_JWT)
        userDefaults.setObject(refreshToken, KEY_REFRESH_TOKEN)
        userDefaults.synchronize()
    }

    override suspend fun getJwt(): String? {
        return userDefaults.stringForKey(KEY_JWT)
    }

    override suspend fun getRefreshToken(): String? {
        return userDefaults.stringForKey(KEY_REFRESH_TOKEN)
    }

    override suspend fun clearTokens() {
        userDefaults.removeObjectForKey(KEY_JWT)
        userDefaults.removeObjectForKey(KEY_REFRESH_TOKEN)
        userDefaults.synchronize()
    }

    companion object {
        private const val KEY_JWT = "jwt"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}

actual fun createTokenStorage(): TokenStorage = IosTokenStorage()

