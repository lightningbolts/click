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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
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
 * iOS TokenStorage stores credentials exclusively in a single, versioned Keychain record.
 *
 * Older releases wrote credential fields to [NSUserDefaults]. On the first secure read we atomically
 * copy a complete legacy session into Keychain, verify that write, then purge every plaintext and
 * per-field Keychain legacy value. Preferences continue using UserDefaults; credentials do not.
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
        private const val KEY_SECURE_SESSION_V2 = "session_v2"
        private const val SECURE_SESSION_VERSION = 2
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
    private val sessionJson =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private var legacyCredentialStoragePurged = false

    @Serializable
    private data class SecureSession(
        val version: Int = SECURE_SESSION_VERSION,
        val jwt: String,
        val refreshToken: String,
        val expiresAt: Long? = null,
        val tokenType: String? = null,
        val userId: String? = null,
    )

    override suspend fun saveTokens(
        jwt: String,
        refreshToken: String,
        expiresAt: Long?,
        tokenType: String?,
    ) {
        val userId = LocalSessionCache.parseIdentityFromJwt(jwt)?.userId
        val session =
            SecureSession(
                jwt = jwt,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                tokenType = tokenType,
                userId = userId,
            )
        if (writeSecureSession(session)) {
            purgeLegacyCredentialStorage()
        } else {
            // Preserve any previously valid secure item when replacing it fails.
            purgeLegacyCredentialStorage()
            println("IosTokenStorage: Keychain session write failed; credentials were not persisted")
        }
    }

    override suspend fun getJwt(): String? = secureSessionOrMigrate()?.jwt?.takeIf { it.isNotBlank() }

    override suspend fun getRefreshToken(): String? = secureSessionOrMigrate()?.refreshToken?.takeIf { it.isNotBlank() }

    override suspend fun getExpiresAt(): Long? = secureSessionOrMigrate()?.expiresAt

    override suspend fun getTokenType(): String? = secureSessionOrMigrate()?.tokenType?.takeIf { it.isNotBlank() }

    override suspend fun getUserId(): String? = secureSessionOrMigrate()?.userId?.takeIf { it.isNotBlank() }

    override suspend fun clearTokens() {
        deleteKeychainItem(KEY_SECURE_SESSION_V2)
        purgeLegacyCredentialStorage()
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

        deleteKeychainItem(KEY_SECURE_SESSION_V2)
        purgeLegacyCredentialStorage()
    }

    /** Returns a complete v2 record, migrating a complete legacy record once when necessary. */
    private fun secureSessionOrMigrate(): SecureSession? {
        readSecureSession()?.let {
            // A previous app version can leave duplicate credential copies behind; remove them now.
            purgeLegacyCredentialStorage()
            return it
        }

        val jwt = userDefaults.stringForKey(KEY_JWT)?.trim().orEmpty()
        val refreshToken = userDefaults.stringForKey(KEY_REFRESH_TOKEN)?.trim().orEmpty()
        if (jwt.isEmpty() || refreshToken.isEmpty()) {
            // Incomplete legacy credentials must never be used to construct a session.
            purgeLegacyCredentialStorage()
            return null
        }
        val expiry = userDefaults.doubleForKey(KEY_EXPIRES_AT).takeIf { it > 0 }?.toLong()
        val legacy =
            SecureSession(
                jwt = jwt,
                refreshToken = refreshToken,
                expiresAt = expiry,
                tokenType = userDefaults.stringForKey(KEY_TOKEN_TYPE)?.trim()?.takeIf { it.isNotEmpty() },
                userId =
                    userDefaults.stringForKey(KEY_USER_ID)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: LocalSessionCache.parseIdentityFromJwt(jwt)?.userId,
            )
        return if (writeSecureSession(legacy)) {
            purgeLegacyCredentialStorage()
            legacy
        } else {
            // Credentials must not continue to live in plaintext when Keychain is unavailable.
            // The user will authenticate again after the device can persist a secure session.
            purgeLegacyCredentialStorage()
            println("IosTokenStorage: Legacy credential migration could not write Keychain")
            null
        }
    }

    private fun readSecureSession(): SecureSession? {
        val raw = getKeychainItem(KEY_SECURE_SESSION_V2)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val session = runCatching { sessionJson.decodeFromString(SecureSession.serializer(), raw) }.getOrNull()
        if (session == null || session.version != SECURE_SESSION_VERSION || session.jwt.isBlank() || session.refreshToken.isBlank()) {
            deleteKeychainItem(KEY_SECURE_SESSION_V2)
            return null
        }
        return session
    }

    private fun writeSecureSession(session: SecureSession): Boolean {
        if (session.jwt.isBlank() || session.refreshToken.isBlank()) return false
        return setKeychainItem(KEY_SECURE_SESSION_V2, sessionJson.encodeToString(SecureSession.serializer(), session))
    }

    /** Removes every historical plaintext/per-field credential copy after a verified Keychain write. */
    private fun purgeLegacyCredentialStorage() {
        if (legacyCredentialStoragePurged) return
        val purgeSucceeded =
            listOf(KEY_JWT, KEY_REFRESH_TOKEN, KEY_EXPIRES_AT, KEY_TOKEN_TYPE, KEY_USER_ID).all {
                userDefaults.removeObjectForKey(it)
                deleteKeychainItem(it)
            }
        userDefaults.synchronize()
        if (purgeSucceeded) legacyCredentialStoragePurged = true
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
            val nsString = NSString.create(string = value)
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
                            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
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
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
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
