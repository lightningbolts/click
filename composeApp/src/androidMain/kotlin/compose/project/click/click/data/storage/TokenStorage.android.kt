package compose.project.click.click.data.storage // pragma: allowlist secret

import android.content.Context
import android.content.SharedPreferences
import compose.project.click.click.auth.LocalSessionCache // pragma: allowlist secret

class AndroidTokenStorage(
    private val context: Context,
) : TokenStorage {
    private val sharedPreferences: SharedPreferences =
        createEncryptedSharedPreferences(context, AUTH_PREFS_NAME)

    override suspend fun saveTokens(
        jwt: String,
        refreshToken: String,
        expiresAt: Long?,
        tokenType: String?,
    ) {
        val userId = LocalSessionCache.parseIdentityFromJwt(jwt)?.userId
        sharedPreferences.edit().apply {
            putString(KEY_JWT, jwt)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            if (expiresAt != null) putLong(KEY_EXPIRES_AT, expiresAt) else remove(KEY_EXPIRES_AT)
            if (tokenType != null) putString(KEY_TOKEN_TYPE, tokenType) else remove(KEY_TOKEN_TYPE)
            if (userId != null) putString(KEY_USER_ID, userId) else remove(KEY_USER_ID)
            commit()
        }
    }

    override suspend fun getJwt(): String? = sharedPreferences.getString(KEY_JWT, null)

    override suspend fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)

    override suspend fun getExpiresAt(): Long? {
        val expiry = sharedPreferences.getLong(KEY_EXPIRES_AT, -1L)
        return if (expiry != -1L) expiry else null
    }

    override suspend fun getTokenType(): String? = sharedPreferences.getString(KEY_TOKEN_TYPE, null)

    override suspend fun getUserId(): String? = sharedPreferences.getString(KEY_USER_ID, null)

    override suspend fun clearTokens() {
        sharedPreferences.edit().apply {
            remove(KEY_JWT)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_EXPIRES_AT)
            remove(KEY_TOKEN_TYPE)
            remove(KEY_USER_ID)
            commit()
        }
    }

    companion object {
        internal const val AUTH_PREFS_NAME = "auth_prefs"
        private const val KEY_JWT = "jwt"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_FREE_THIS_WEEK = "free_this_week"
        private const val KEY_TAGS_INITIALIZED = "tags_initialized"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        private const val KEY_HOME_LAYOUT_MODE = "home_layout_mode"
        private const val KEY_MESSAGE_NOTIFICATIONS_ENABLED = "message_notifications_enabled"
        private const val KEY_CALL_NOTIFICATIONS_ENABLED = "call_notifications_enabled"
        private const val KEY_AMBIENT_NOISE_OPT_IN = "ambient_noise_opt_in"
        private const val KEY_BAROMETRIC_CONTEXT_OPT_IN = "barometric_context_opt_in"
        private const val KEY_LOCATION_EXPLAINER_SEEN = "location_explainer_seen"
        private const val KEY_ONBOARDING_STATE = "onboarding_state"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_CACHED_APP_SNAPSHOT = "cached_app_snapshot"
        private const val KEY_PENDING_CONNECTION_QUEUE = "pending_connection_queue"
        private const val KEY_PENDING_PROXIMITY_HANDSHAKE_QUEUE = "pending_proximity_handshake_queue"
        private const val KEY_ACTIVE_HUBS = "active_hubs"
        private const val KEY_BEACON_RSVP_SNAPSHOT = "beacon_rsvp_snapshot"
        private const val KEY_BEACON_ENGAGEMENT_SNAPSHOT = "beacon_engagement_snapshot"
    }

    override suspend fun saveFreeThisWeek(isFree: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_FREE_THIS_WEEK, isFree)
            apply()
        }
    }

    override suspend fun getFreeThisWeek(): Boolean? =
        if (sharedPreferences.contains(KEY_FREE_THIS_WEEK)) {
            sharedPreferences.getBoolean(KEY_FREE_THIS_WEEK, false)
        } else {
            null
        }

    override suspend fun saveTagsInitialized(initialized: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_TAGS_INITIALIZED, initialized)
            apply()
        }
    }

    override suspend fun getTagsInitialized(): Boolean? =
        if (sharedPreferences.contains(KEY_TAGS_INITIALIZED)) {
            sharedPreferences.getBoolean(KEY_TAGS_INITIALIZED, false)
        } else {
            null
        }

    override suspend fun saveDarkModeEnabled(isDarkMode: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_DARK_MODE_ENABLED, isDarkMode)
            apply()
        }
    }

    override suspend fun getDarkModeEnabled(): Boolean? =
        if (sharedPreferences.contains(KEY_DARK_MODE_ENABLED)) {
            sharedPreferences.getBoolean(KEY_DARK_MODE_ENABLED, true)
        } else {
            null
        }

    override suspend fun saveHomeLayoutMode(mode: String) {
        sharedPreferences.edit().apply {
            putString(KEY_HOME_LAYOUT_MODE, mode)
            apply()
        }
    }

    override suspend fun getHomeLayoutMode(): String? = sharedPreferences.getString(KEY_HOME_LAYOUT_MODE, null)

    override suspend fun saveMessageNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_MESSAGE_NOTIFICATIONS_ENABLED, enabled)
            apply()
        }
    }

    override suspend fun getMessageNotificationsEnabled(): Boolean? =
        if (sharedPreferences.contains(KEY_MESSAGE_NOTIFICATIONS_ENABLED)) {
            sharedPreferences.getBoolean(KEY_MESSAGE_NOTIFICATIONS_ENABLED, true)
        } else {
            null
        }

    override suspend fun saveCallNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_CALL_NOTIFICATIONS_ENABLED, enabled)
            apply()
        }
    }

    override suspend fun getCallNotificationsEnabled(): Boolean? =
        if (sharedPreferences.contains(KEY_CALL_NOTIFICATIONS_ENABLED)) {
            sharedPreferences.getBoolean(KEY_CALL_NOTIFICATIONS_ENABLED, true)
        } else {
            null
        }

    override suspend fun saveAmbientNoiseOptIn(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_AMBIENT_NOISE_OPT_IN, enabled)
            apply()
        }
    }

    override suspend fun getAmbientNoiseOptIn(): Boolean? =
        if (sharedPreferences.contains(KEY_AMBIENT_NOISE_OPT_IN)) {
            sharedPreferences.getBoolean(KEY_AMBIENT_NOISE_OPT_IN, false)
        } else {
            null
        }

    override suspend fun saveBarometricContextOptIn(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_BAROMETRIC_CONTEXT_OPT_IN, enabled)
            apply()
        }
    }

    override suspend fun getBarometricContextOptIn(): Boolean? =
        if (sharedPreferences.contains(KEY_BAROMETRIC_CONTEXT_OPT_IN)) {
            sharedPreferences.getBoolean(KEY_BAROMETRIC_CONTEXT_OPT_IN, true)
        } else {
            null
        }

    override suspend fun saveLocationExplainerSeen(seen: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_LOCATION_EXPLAINER_SEEN, seen)
            apply()
        }
    }

    override suspend fun getLocationExplainerSeen(): Boolean? =
        if (sharedPreferences.contains(KEY_LOCATION_EXPLAINER_SEEN)) {
            sharedPreferences.getBoolean(KEY_LOCATION_EXPLAINER_SEEN, false)
        } else {
            null
        }

    override suspend fun saveOnboardingState(state: String?) {
        sharedPreferences.edit().apply {
            if (state == null) remove(KEY_ONBOARDING_STATE) else putString(KEY_ONBOARDING_STATE, state)
            apply()
        }
    }

    override suspend fun getOnboardingState(): String? = sharedPreferences.getString(KEY_ONBOARDING_STATE, null)

    override suspend fun saveHasCompletedOnboarding(completed: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_HAS_COMPLETED_ONBOARDING, completed)
            apply()
        }
    }

    override suspend fun getHasCompletedOnboarding(): Boolean? =
        if (sharedPreferences.contains(KEY_HAS_COMPLETED_ONBOARDING)) {
            sharedPreferences.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        } else {
            null
        }

    override suspend fun saveCachedAppSnapshot(snapshot: String?) {
        sharedPreferences.edit().apply {
            if (snapshot == null) remove(KEY_CACHED_APP_SNAPSHOT) else putString(KEY_CACHED_APP_SNAPSHOT, snapshot)
            apply()
        }
    }

    override suspend fun getCachedAppSnapshot(): String? = sharedPreferences.getString(KEY_CACHED_APP_SNAPSHOT, null)

    override suspend fun savePendingConnectionQueue(queue: String?) {
        sharedPreferences.edit().apply {
            if (queue == null) remove(KEY_PENDING_CONNECTION_QUEUE) else putString(KEY_PENDING_CONNECTION_QUEUE, queue)
            apply()
        }
    }

    override suspend fun getPendingConnectionQueue(): String? = sharedPreferences.getString(KEY_PENDING_CONNECTION_QUEUE, null)

    override suspend fun savePendingProximityHandshakeQueue(queue: String?) {
        sharedPreferences.edit().apply {
            if (queue == null) {
                remove(KEY_PENDING_PROXIMITY_HANDSHAKE_QUEUE)
            } else {
                putString(KEY_PENDING_PROXIMITY_HANDSHAKE_QUEUE, queue)
            }
            apply()
        }
    }

    override suspend fun getPendingProximityHandshakeQueue(): String? =
        sharedPreferences.getString(KEY_PENDING_PROXIMITY_HANDSHAKE_QUEUE, null)

    override suspend fun saveActiveHubs(json: String?) {
        sharedPreferences.edit().apply {
            if (json == null) remove(KEY_ACTIVE_HUBS) else putString(KEY_ACTIVE_HUBS, json)
            apply()
        }
    }

    override suspend fun getActiveHubs(): String? = sharedPreferences.getString(KEY_ACTIVE_HUBS, null)

    override suspend fun saveBeaconRsvpSnapshot(snapshot: String?) {
        sharedPreferences.edit().apply {
            if (snapshot == null) remove(KEY_BEACON_RSVP_SNAPSHOT) else putString(KEY_BEACON_RSVP_SNAPSHOT, snapshot)
            apply()
        }
    }

    override suspend fun getBeaconRsvpSnapshot(): String? = sharedPreferences.getString(KEY_BEACON_RSVP_SNAPSHOT, null)

    override suspend fun saveBeaconEngagementSnapshot(snapshot: String?) {
        sharedPreferences.edit().apply {
            if (snapshot == null) {
                remove(KEY_BEACON_ENGAGEMENT_SNAPSHOT)
            } else {
                putString(KEY_BEACON_ENGAGEMENT_SNAPSHOT, snapshot)
            }
            apply()
        }
    }

    override suspend fun getBeaconEngagementSnapshot(): String? = sharedPreferences.getString(KEY_BEACON_ENGAGEMENT_SNAPSHOT, null)

    override suspend fun clearSessionData() {
        sharedPreferences.edit().apply {
            val sessionKeys =
                listOf(
                    KEY_JWT,
                    KEY_REFRESH_TOKEN,
                    KEY_EXPIRES_AT,
                    KEY_TOKEN_TYPE,
                    KEY_USER_ID,
                    KEY_FREE_THIS_WEEK,
                    KEY_TAGS_INITIALIZED,
                    KEY_MESSAGE_NOTIFICATIONS_ENABLED,
                    KEY_CALL_NOTIFICATIONS_ENABLED,
                    KEY_AMBIENT_NOISE_OPT_IN,
                    KEY_BAROMETRIC_CONTEXT_OPT_IN,
                    KEY_LOCATION_EXPLAINER_SEEN,
                    KEY_ONBOARDING_STATE,
                    KEY_HAS_COMPLETED_ONBOARDING,
                    KEY_CACHED_APP_SNAPSHOT,
                    KEY_PENDING_CONNECTION_QUEUE,
                    KEY_PENDING_PROXIMITY_HANDSHAKE_QUEUE,
                    KEY_BEACON_RSVP_SNAPSHOT,
                    KEY_BEACON_ENGAGEMENT_SNAPSHOT,
                )
            sessionKeys.forEach { remove(it) }
            apply()
        }
    }
}

// We need to pass context from the composable level
private var contextInstance: Context? = null

fun initTokenStorage(context: Context) {
    contextInstance = context.applicationContext
}

internal fun androidStorageContextOrThrow(): Context =
    contextInstance ?: throw IllegalStateException(
        "TokenStorage not initialized. Call initTokenStorage() from MainActivity first.",
    )

actual fun createTokenStorage(): TokenStorage = AndroidTokenStorage(androidStorageContextOrThrow())
