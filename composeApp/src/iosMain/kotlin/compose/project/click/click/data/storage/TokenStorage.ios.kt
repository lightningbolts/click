package compose.project.click.click.data.storage // pragma: allowlist secret

import compose.project.click.click.auth.LocalSessionCache // pragma: allowlist secret
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus

/**
 * iOS TokenStorage that uses BOTH NSUserDefaults AND Keychain for redundancy.
 *
 * Strategy:
 * - WRITE: Save to NSUserDefaults first (authoritative for the active session), then best-effort Keychain
 * - READ: Prefer NSUserDefaults, then Keychain (avoids stale Keychain JWT poisoning fresh Defaults tokens
 *   when Keychain writes fail with errSecParam)
 *
 * Historical bug: Keychain-first reads + failed Keychain overwrites left an expired JWT in Keychain while
 * Defaults held a valid session → endless "JWT expired" / "Refresh Token Not Found" loops.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class IosTokenStorage : TokenStorage {
    companion object {
        private const val SERVICE_NAME = "com.click.auth"
        private const val PREFS_SUITE_NAME = "click_auth_prefs"
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

        /** OSStatus errSecParam — invalid Keychain query/value parameters. */
        private const val ERR_SEC_PARAM = -50
    }

    private val userDefaults = NSUserDefaults(suiteName = PREFS_SUITE_NAME) ?: NSUserDefaults.standardUserDefaults

    override suspend fun saveTokens(
        jwt: String,
        refreshToken: String,
        expiresAt: Long?,
        tokenType: String?,
    ) {
        // Defaults are the live session source of truth.
        val userId = LocalSessionCache.parseIdentityFromJwt(jwt)?.userId
        userDefaults.setObject(jwt, KEY_JWT)
        userDefaults.setObject(refreshToken, KEY_REFRESH_TOKEN)
        if (expiresAt != null) {
            userDefaults.setDouble(expiresAt.toDouble(), KEY_EXPIRES_AT)
        } else {
            userDefaults.removeObjectForKey(KEY_EXPIRES_AT)
        }
        if (tokenType != null) {
            userDefaults.setObject(tokenType, KEY_TOKEN_TYPE)
        } else {
            userDefaults.removeObjectForKey(KEY_TOKEN_TYPE)
        }
        if (userId != null) {
            userDefaults.setObject(userId, KEY_USER_ID)
        } else {
            userDefaults.removeObjectForKey(KEY_USER_ID)
        }
        userDefaults.synchronize()

        // Update-or-add Keychain; delete stale entries when writes fail so empty Defaults
        // cannot resurrect an expired refresh token on fallback read.
        val jwtOk = setKeychainItem(KEY_JWT, jwt)
        val refreshOk = setKeychainItem(KEY_REFRESH_TOKEN, refreshToken)
        val expiresOk =
            if (expiresAt != null) {
                setKeychainItem(KEY_EXPIRES_AT, expiresAt.toString())
            } else {
                deleteKeychainItem(KEY_EXPIRES_AT)
                true
            }
        val typeOk =
            if (tokenType != null) {
                setKeychainItem(KEY_TOKEN_TYPE, tokenType)
            } else {
                deleteKeychainItem(KEY_TOKEN_TYPE)
                true
            }
        if (!jwtOk || !refreshOk || !expiresOk || !typeOk) {
            println(
                "IosTokenStorage: NSUserDefaults saved; Keychain write failed, purging stale entries " +
                    "(jwt=$jwtOk refresh=$refreshOk expires=$expiresOk type=$typeOk)",
            )
            if (!jwtOk) deleteKeychainItem(KEY_JWT)
            if (!refreshOk) deleteKeychainItem(KEY_REFRESH_TOKEN)
            if (!expiresOk) deleteKeychainItem(KEY_EXPIRES_AT)
            if (!typeOk) deleteKeychainItem(KEY_TOKEN_TYPE)
        }
    }

    override suspend fun getJwt(): String? {
        userDefaults.stringForKey(KEY_JWT)?.takeIf { it.isNotBlank() }?.let { return it }
        return getKeychainItem(KEY_JWT)?.takeIf { it.isNotBlank() }?.also { recovered ->
            userDefaults.setObject(recovered, KEY_JWT)
            userDefaults.synchronize()
        }
    }

    override suspend fun getRefreshToken(): String? {
        userDefaults.stringForKey(KEY_REFRESH_TOKEN)?.takeIf { it.isNotBlank() }?.let { return it }
        return getKeychainItem(KEY_REFRESH_TOKEN)?.takeIf { it.isNotBlank() }?.also { recovered ->
            userDefaults.setObject(recovered, KEY_REFRESH_TOKEN)
            userDefaults.synchronize()
        }
    }

    override suspend fun getExpiresAt(): Long? {
        val expiry = userDefaults.doubleForKey(KEY_EXPIRES_AT)
        if (expiry > 0) return expiry.toLong()
        return getKeychainItem(KEY_EXPIRES_AT)?.toLongOrNull()?.also { recovered ->
            userDefaults.setDouble(recovered.toDouble(), KEY_EXPIRES_AT)
            userDefaults.synchronize()
        }
    }

    override suspend fun getTokenType(): String? {
        userDefaults.stringForKey(KEY_TOKEN_TYPE)?.takeIf { it.isNotBlank() }?.let { return it }
        return getKeychainItem(KEY_TOKEN_TYPE)?.takeIf { it.isNotBlank() }
    }

    override suspend fun getUserId(): String? = userDefaults.stringForKey(KEY_USER_ID)

    override suspend fun clearTokens() {
        userDefaults.removeObjectForKey(KEY_JWT)
        userDefaults.removeObjectForKey(KEY_REFRESH_TOKEN)
        userDefaults.removeObjectForKey(KEY_EXPIRES_AT)
        userDefaults.removeObjectForKey(KEY_TOKEN_TYPE)
        userDefaults.removeObjectForKey(KEY_USER_ID)
        userDefaults.synchronize()

        deleteKeychainItem(KEY_JWT)
        deleteKeychainItem(KEY_REFRESH_TOKEN)
        deleteKeychainItem(KEY_EXPIRES_AT)
        deleteKeychainItem(KEY_TOKEN_TYPE)
    }

    override suspend fun saveFreeThisWeek(isFree: Boolean) {
        userDefaults.setBool(isFree, KEY_FREE_THIS_WEEK)
        userDefaults.synchronize()
    }

    override suspend fun getFreeThisWeek(): Boolean? =
        if (userDefaults.objectForKey(KEY_FREE_THIS_WEEK) != null) {
            userDefaults.boolForKey(KEY_FREE_THIS_WEEK)
        } else {
            null
        }

    override suspend fun saveTagsInitialized(initialized: Boolean) {
        userDefaults.setBool(initialized, KEY_TAGS_INITIALIZED)
        userDefaults.synchronize()
    }

    override suspend fun getTagsInitialized(): Boolean? =
        if (userDefaults.objectForKey(KEY_TAGS_INITIALIZED) != null) {
            userDefaults.boolForKey(KEY_TAGS_INITIALIZED)
        } else {
            null
        }

    override suspend fun saveDarkModeEnabled(isDarkMode: Boolean) {
        userDefaults.setBool(isDarkMode, KEY_DARK_MODE_ENABLED)
        userDefaults.synchronize()
    }

    override suspend fun getDarkModeEnabled(): Boolean? =
        if (userDefaults.objectForKey(KEY_DARK_MODE_ENABLED) != null) {
            userDefaults.boolForKey(KEY_DARK_MODE_ENABLED)
        } else {
            null
        }

    override suspend fun saveHomeLayoutMode(mode: String) {
        userDefaults.setObject(mode, KEY_HOME_LAYOUT_MODE)
        userDefaults.synchronize()
    }

    override suspend fun getHomeLayoutMode(): String? = userDefaults.stringForKey(KEY_HOME_LAYOUT_MODE)

    override suspend fun saveMessageNotificationsEnabled(enabled: Boolean) {
        userDefaults.setBool(enabled, KEY_MESSAGE_NOTIFICATIONS_ENABLED)
        userDefaults.synchronize()
    }

    override suspend fun getMessageNotificationsEnabled(): Boolean? =
        if (userDefaults.objectForKey(KEY_MESSAGE_NOTIFICATIONS_ENABLED) != null) {
            userDefaults.boolForKey(KEY_MESSAGE_NOTIFICATIONS_ENABLED)
        } else {
            null
        }

    override suspend fun saveCallNotificationsEnabled(enabled: Boolean) {
        userDefaults.setBool(enabled, KEY_CALL_NOTIFICATIONS_ENABLED)
        userDefaults.synchronize()
    }

    override suspend fun getCallNotificationsEnabled(): Boolean? =
        if (userDefaults.objectForKey(KEY_CALL_NOTIFICATIONS_ENABLED) != null) {
            userDefaults.boolForKey(KEY_CALL_NOTIFICATIONS_ENABLED)
        } else {
            null
        }

    override suspend fun saveAmbientNoiseOptIn(enabled: Boolean) {
        userDefaults.setBool(enabled, KEY_AMBIENT_NOISE_OPT_IN)
        userDefaults.synchronize()
    }

    override suspend fun getAmbientNoiseOptIn(): Boolean? =
        if (userDefaults.objectForKey(KEY_AMBIENT_NOISE_OPT_IN) != null) {
            userDefaults.boolForKey(KEY_AMBIENT_NOISE_OPT_IN)
        } else {
            null
        }

    override suspend fun saveBarometricContextOptIn(enabled: Boolean) {
        userDefaults.setBool(enabled, KEY_BAROMETRIC_CONTEXT_OPT_IN)
        userDefaults.synchronize()
    }

    override suspend fun getBarometricContextOptIn(): Boolean? =
        if (userDefaults.objectForKey(KEY_BAROMETRIC_CONTEXT_OPT_IN) != null) {
            userDefaults.boolForKey(KEY_BAROMETRIC_CONTEXT_OPT_IN)
        } else {
            null
        }

    override suspend fun saveLocationExplainerSeen(seen: Boolean) {
        userDefaults.setBool(seen, KEY_LOCATION_EXPLAINER_SEEN)
        userDefaults.synchronize()
    }

    override suspend fun getLocationExplainerSeen(): Boolean? =
        if (userDefaults.objectForKey(KEY_LOCATION_EXPLAINER_SEEN) != null) {
            userDefaults.boolForKey(KEY_LOCATION_EXPLAINER_SEEN)
        } else {
            null
        }

    override suspend fun saveOnboardingState(state: String?) {
        if (state == null) {
            userDefaults.removeObjectForKey(KEY_ONBOARDING_STATE)
        } else {
            userDefaults.setObject(state, KEY_ONBOARDING_STATE)
        }
        userDefaults.synchronize()
    }

    override suspend fun getOnboardingState(): String? = userDefaults.stringForKey(KEY_ONBOARDING_STATE)

    override suspend fun saveHasCompletedOnboarding(completed: Boolean) {
        userDefaults.setBool(completed, KEY_HAS_COMPLETED_ONBOARDING)
        userDefaults.synchronize()
    }

    override suspend fun getHasCompletedOnboarding(): Boolean? =
        if (userDefaults.objectForKey(KEY_HAS_COMPLETED_ONBOARDING) != null) {
            userDefaults.boolForKey(KEY_HAS_COMPLETED_ONBOARDING)
        } else {
            null
        }

    override suspend fun saveCachedAppSnapshot(snapshot: String?) {
        if (snapshot == null) {
            userDefaults.removeObjectForKey(KEY_CACHED_APP_SNAPSHOT)
        } else {
            userDefaults.setObject(snapshot, KEY_CACHED_APP_SNAPSHOT)
        }
        userDefaults.synchronize()
    }

    override suspend fun getCachedAppSnapshot(): String? = userDefaults.stringForKey(KEY_CACHED_APP_SNAPSHOT)

    override suspend fun savePendingConnectionQueue(queue: String?) {
        if (queue == null) {
            userDefaults.removeObjectForKey(KEY_PENDING_CONNECTION_QUEUE)
        } else {
            userDefaults.setObject(queue, KEY_PENDING_CONNECTION_QUEUE)
        }
        userDefaults.synchronize()
    }

    override suspend fun getPendingConnectionQueue(): String? = userDefaults.stringForKey(KEY_PENDING_CONNECTION_QUEUE)

    override suspend fun savePendingProximityHandshakeQueue(queue: String?) {
        if (queue == null) {
            userDefaults.removeObjectForKey(KEY_PENDING_PROXIMITY_HANDSHAKE_QUEUE)
        } else {
            userDefaults.setObject(queue, KEY_PENDING_PROXIMITY_HANDSHAKE_QUEUE)
        }
        userDefaults.synchronize()
    }

    override suspend fun getPendingProximityHandshakeQueue(): String? = userDefaults.stringForKey(KEY_PENDING_PROXIMITY_HANDSHAKE_QUEUE)

    override suspend fun saveActiveHubs(json: String?) {
        if (json == null) {
            userDefaults.removeObjectForKey(KEY_ACTIVE_HUBS)
        } else {
            userDefaults.setObject(json, KEY_ACTIVE_HUBS)
        }
        userDefaults.synchronize()
    }

    override suspend fun getActiveHubs(): String? = userDefaults.stringForKey(KEY_ACTIVE_HUBS)

    override suspend fun saveBeaconRsvpSnapshot(snapshot: String?) {
        if (snapshot == null) {
            userDefaults.removeObjectForKey(KEY_BEACON_RSVP_SNAPSHOT)
        } else {
            userDefaults.setObject(snapshot, KEY_BEACON_RSVP_SNAPSHOT)
        }
        userDefaults.synchronize()
    }

    override suspend fun getBeaconRsvpSnapshot(): String? = userDefaults.stringForKey(KEY_BEACON_RSVP_SNAPSHOT)

    override suspend fun saveBeaconEngagementSnapshot(snapshot: String?) {
        if (snapshot == null) {
            userDefaults.removeObjectForKey(KEY_BEACON_ENGAGEMENT_SNAPSHOT)
        } else {
            userDefaults.setObject(snapshot, KEY_BEACON_ENGAGEMENT_SNAPSHOT)
        }
        userDefaults.synchronize()
    }

    override suspend fun getBeaconEngagementSnapshot(): String? = userDefaults.stringForKey(KEY_BEACON_ENGAGEMENT_SNAPSHOT)

    override suspend fun clearSessionData() {
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
        sessionKeys.forEach { userDefaults.removeObjectForKey(it) }
        userDefaults.synchronize()

        deleteKeychainItem(KEY_JWT)
        deleteKeychainItem(KEY_REFRESH_TOKEN)
        deleteKeychainItem(KEY_EXPIRES_AT)
        deleteKeychainItem(KEY_TOKEN_TYPE)
    }

    // ============ Keychain Helpers ============

    /**
     * Uses CFDictionaryCreate + CFBridgingRetain (same pattern as multiplatform-settings).
     * Kotlin Map → CFBridgingRetain often yields errSecParam (-50) for SecItemAdd/Update.
     */
    private fun setKeychainItem(
        key: String,
        value: String,
    ): Boolean =
        memScoped {
            if (value.isEmpty()) return false
            @Suppress("CAST_NEVER_SUCCEEDS")
            val nsString = value as NSString
            val valueData =
                nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: run {
                    println("IosTokenStorage: Failed to encode value for key '$key'")
                    return false
                }

            val cfAccount = CFBridgingRetain(key)
            val cfService = CFBridgingRetain(SERVICE_NAME)
            val cfValue = CFBridgingRetain(valueData)
            try {
                val basePairs =
                    mapOf<CFStringRef?, CFTypeRef?>(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to cfService,
                        kSecAttrAccount to cfAccount,
                    )
                val updatePairs =
                    mapOf<CFStringRef?, CFTypeRef?>(
                        kSecValueData to cfValue,
                    )
                val addPairs =
                    basePairs +
                        mapOf(
                            kSecValueData to cfValue,
                            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        )

                val cfBase = cfDictionaryOf(basePairs)
                val cfUpdate = cfDictionaryOf(updatePairs)
                var status: OSStatus = SecItemUpdate(cfBase, cfUpdate)
                CFBridgingRelease(cfUpdate)

                if (status == errSecItemNotFound) {
                    val cfAdd = cfDictionaryOf(addPairs)
                    status = SecItemAdd(cfAdd, null)
                    CFBridgingRelease(cfAdd)
                    if (status == errSecDuplicateItem || status.toInt() == ERR_SEC_PARAM) {
                        val cfRetry = cfDictionaryOf(updatePairs)
                        status = SecItemUpdate(cfBase, cfRetry)
                        CFBridgingRelease(cfRetry)
                    }
                } else if (status.toInt() == ERR_SEC_PARAM || status == errSecDuplicateItem) {
                    deleteKeychainItem(key)
                    val cfAdd = cfDictionaryOf(addPairs)
                    status = SecItemAdd(cfAdd, null)
                    CFBridgingRelease(cfAdd)
                    if (status != errSecSuccess) {
                        val cfRetry = cfDictionaryOf(updatePairs)
                        status = SecItemUpdate(cfBase, cfRetry)
                        CFBridgingRelease(cfRetry)
                    }
                }

                CFBridgingRelease(cfBase)

                if (status != errSecSuccess) {
                    println(
                        "IosTokenStorage: Keychain set failed for '$key', status: $status" +
                            if (status.toInt() == ERR_SEC_PARAM) " (errSecParam)" else "",
                    )
                }
                return status == errSecSuccess
            } finally {
                CFBridgingRelease(cfAccount)
                CFBridgingRelease(cfService)
                CFBridgingRelease(cfValue)
            }
        }

    private fun getKeychainItem(key: String): String? =
        memScoped {
            val cfAccount = CFBridgingRetain(key)
            val cfService = CFBridgingRetain(SERVICE_NAME)
            try {
                val query =
                    cfDictionaryOf(
                        mapOf(
                            kSecClass to kSecClassGenericPassword,
                            kSecAttrService to cfService,
                            kSecAttrAccount to cfAccount,
                            kSecReturnData to kCFBooleanTrue,
                            kSecMatchLimit to kSecMatchLimitOne,
                        ),
                    )
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                CFBridgingRelease(query)
                if (status != errSecSuccess) return null
                val data = CFBridgingRelease(result.value) as? NSData ?: return null
                NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
            } finally {
                CFBridgingRelease(cfAccount)
                CFBridgingRelease(cfService)
            }
        }

    private fun deleteKeychainItem(key: String): Boolean =
        memScoped {
            val cfAccount = CFBridgingRetain(key)
            val cfService = CFBridgingRetain(SERVICE_NAME)
            try {
                val query =
                    cfDictionaryOf(
                        mapOf(
                            kSecClass to kSecClassGenericPassword,
                            kSecAttrService to cfService,
                            kSecAttrAccount to cfAccount,
                        ),
                    )
                val status = SecItemDelete(query)
                CFBridgingRelease(query)
                status == errSecSuccess || status == errSecItemNotFound
            } finally {
                CFBridgingRelease(cfAccount)
                CFBridgingRelease(cfService)
            }
        }

    private fun MemScope.cfDictionaryOf(map: Map<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val size = map.size
        val keys = allocArrayOf(*map.keys.toTypedArray())
        val values = allocArrayOf(*map.values.toTypedArray())
        return CFDictionaryCreate(
            kCFAllocatorDefault,
            keys.reinterpret(),
            values.reinterpret(),
            size.convert(),
            null,
            null,
        )
    }
}

actual fun createTokenStorage(): TokenStorage = IosTokenStorage()
