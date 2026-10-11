package compose.project.click.click.core

import android.content.SharedPreferences
import compose.project.click.click.data.storage.androidStorageContextOrThrow
import compose.project.click.click.data.storage.createEncryptedSharedPreferences

private const val FEATURE_FLAGS_PREFS = "feature_flags_prefs"
private const val KEY_LAST_USER = "features.lastUser"

private fun userKey(userId: String) = "features.$userId"

/** Encrypted, per-user flag storage. Falls back to memory before the storage context is set (tests). */
private class AndroidFeatureFlagsPersistence(
    private val prefs: SharedPreferences,
) : FeatureFlagsPersistence {
    override fun read(userId: String): String? = prefs.getString(userKey(userId), null)

    override fun write(
        userId: String,
        json: String,
    ) {
        prefs.edit().putString(userKey(userId), json).apply()
    }

    override fun delete(userId: String) {
        prefs.edit().remove(userKey(userId)).apply()
    }

    override fun lastUserId(): String? = prefs.getString(KEY_LAST_USER, null)

    override fun setLastUserId(userId: String?) {
        prefs.edit().apply { if (userId != null) putString(KEY_LAST_USER, userId) else remove(KEY_LAST_USER) }.apply()
    }
}

private val fallback = InMemoryFeatureFlagsPersistence()
private var resolved: FeatureFlagsPersistence? = null

actual fun platformFeatureFlagsPersistence(): FeatureFlagsPersistence {
    resolved?.let { return it }
    val context = runCatching { androidStorageContextOrThrow() }.getOrNull() ?: return fallback
    val created =
        runCatching { AndroidFeatureFlagsPersistence(createEncryptedSharedPreferences(context, FEATURE_FLAGS_PREFS)) }
            .getOrNull() ?: return fallback
    resolved = created
    return created
}
