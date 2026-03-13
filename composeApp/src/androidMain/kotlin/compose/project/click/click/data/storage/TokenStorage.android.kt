package compose.project.click.click.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidTokenStorage(private val context: Context) : TokenStorage {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun saveTokens(jwt: String, refreshToken: String, expiresAt: Long?, tokenType: String?) {
        sharedPreferences.edit().apply {
            putString(KEY_JWT, jwt)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            if (expiresAt != null) putLong(KEY_EXPIRES_AT, expiresAt) else remove(KEY_EXPIRES_AT)
            if (tokenType != null) putString(KEY_TOKEN_TYPE, tokenType) else remove(KEY_TOKEN_TYPE)
            apply()
        }
    }

    override suspend fun getJwt(): String? {
        return sharedPreferences.getString(KEY_JWT, null)
    }

    override suspend fun getRefreshToken(): String? {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    override suspend fun getExpiresAt(): Long? {
        val expiry = sharedPreferences.getLong(KEY_EXPIRES_AT, -1L)
        return if (expiry != -1L) expiry else null
    }

    override suspend fun getTokenType(): String? {
        return sharedPreferences.getString(KEY_TOKEN_TYPE, null)
    }

    override suspend fun clearTokens() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_JWT = "jwt"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_FREE_THIS_WEEK = "free_this_week"
        private const val KEY_TAGS_INITIALIZED = "tags_initialized"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        private const val KEY_MESSAGE_NOTIFICATIONS_ENABLED = "message_notifications_enabled"
        private const val KEY_CALL_NOTIFICATIONS_ENABLED = "call_notifications_enabled"
        private const val KEY_AMBIENT_NOISE_OPT_IN = "ambient_noise_opt_in"
        private const val KEY_LOCATION_EXPLAINER_SEEN = "location_explainer_seen"
    }
    
    override suspend fun saveFreeThisWeek(isFree: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_FREE_THIS_WEEK, isFree)
            apply()
        }
    }
    
    override suspend fun getFreeThisWeek(): Boolean? {
        return if (sharedPreferences.contains(KEY_FREE_THIS_WEEK)) {
            sharedPreferences.getBoolean(KEY_FREE_THIS_WEEK, false)
        } else {
            null
        }
    }

    override suspend fun saveTagsInitialized(initialized: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_TAGS_INITIALIZED, initialized)
            apply()
        }
    }

    override suspend fun getTagsInitialized(): Boolean? {
        return if (sharedPreferences.contains(KEY_TAGS_INITIALIZED)) {
            sharedPreferences.getBoolean(KEY_TAGS_INITIALIZED, false)
        } else {
            null
        }
    }

    override suspend fun saveDarkModeEnabled(isDarkMode: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_DARK_MODE_ENABLED, isDarkMode)
            apply()
        }
    }

    override suspend fun getDarkModeEnabled(): Boolean? {
        return if (sharedPreferences.contains(KEY_DARK_MODE_ENABLED)) {
            sharedPreferences.getBoolean(KEY_DARK_MODE_ENABLED, true)
        } else {
            null
        }
    }

    override suspend fun saveMessageNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_MESSAGE_NOTIFICATIONS_ENABLED, enabled)
            apply()
        }
    }

    override suspend fun getMessageNotificationsEnabled(): Boolean? {
        return if (sharedPreferences.contains(KEY_MESSAGE_NOTIFICATIONS_ENABLED)) {
            sharedPreferences.getBoolean(KEY_MESSAGE_NOTIFICATIONS_ENABLED, true)
        } else {
            null
        }
    }

    override suspend fun saveCallNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_CALL_NOTIFICATIONS_ENABLED, enabled)
            apply()
        }
    }

    override suspend fun getCallNotificationsEnabled(): Boolean? {
        return if (sharedPreferences.contains(KEY_CALL_NOTIFICATIONS_ENABLED)) {
            sharedPreferences.getBoolean(KEY_CALL_NOTIFICATIONS_ENABLED, true)
        } else {
            null
        }
    }

    override suspend fun saveAmbientNoiseOptIn(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_AMBIENT_NOISE_OPT_IN, enabled)
            apply()
        }
    }

    override suspend fun getAmbientNoiseOptIn(): Boolean? {
        return if (sharedPreferences.contains(KEY_AMBIENT_NOISE_OPT_IN)) {
            sharedPreferences.getBoolean(KEY_AMBIENT_NOISE_OPT_IN, false)
        } else {
            null
        }
    }

    override suspend fun saveLocationExplainerSeen(seen: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_LOCATION_EXPLAINER_SEEN, seen)
            apply()
        }
    }

    override suspend fun getLocationExplainerSeen(): Boolean? {
        return if (sharedPreferences.contains(KEY_LOCATION_EXPLAINER_SEEN)) {
            sharedPreferences.getBoolean(KEY_LOCATION_EXPLAINER_SEEN, false)
        } else {
            null
        }
    }
}

// We need to pass context from the composable level
private var contextInstance: Context? = null

fun initTokenStorage(context: Context) {
    contextInstance = context
}

actual fun createTokenStorage(): TokenStorage {
    val context = contextInstance ?: throw IllegalStateException(
        "TokenStorage not initialized. Call initTokenStorage() from MainActivity first."
    )
    return AndroidTokenStorage(context)
}

