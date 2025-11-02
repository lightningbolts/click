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

    override suspend fun saveTokens(jwt: String, refreshToken: String) {
        sharedPreferences.edit().apply {
            putString(KEY_JWT, jwt)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    override suspend fun getJwt(): String? {
        return sharedPreferences.getString(KEY_JWT, null)
    }

    override suspend fun getRefreshToken(): String? {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    override suspend fun clearTokens() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_JWT = "jwt"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
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

