package compose.project.click.click.data.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.NSUserDefaults
import platform.Security.*

/**
 * iOS TokenStorage that uses BOTH NSUserDefaults AND Keychain for redundancy.
 * 
 * Strategy:
 * - WRITE: Save to both NSUserDefaults and Keychain
 * - READ: Try Keychain first, fall back to NSUserDefaults
 * 
 * This ensures:
 * - NSUserDefaults: Works reliably for normal app lifecycle (app switcher removal)
 * - Keychain: Persists across app updates/reinstalls (TestFlight, App Store)
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
        private const val KEY_FREE_THIS_WEEK = "free_this_week"
    }

    // NSUserDefaults - reliable for normal app lifecycle
    private val userDefaults = NSUserDefaults(suiteName = PREFS_SUITE_NAME) ?: NSUserDefaults.standardUserDefaults

    override suspend fun saveTokens(jwt: String, refreshToken: String, expiresAt: Long?, tokenType: String?) {
        println("IosTokenStorage: Saving tokens...")
        
        // Save to NSUserDefaults (primary - always works)
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
        userDefaults.synchronize()
        println("IosTokenStorage: Saved to NSUserDefaults")
        
        // Also save to Keychain (for update persistence)
        val jwtSaved = setKeychainItem(KEY_JWT, jwt)
        val refreshSaved = setKeychainItem(KEY_REFRESH_TOKEN, refreshToken)
        println("IosTokenStorage: Keychain save - jwt: $jwtSaved, refresh: $refreshSaved")
        
        if (expiresAt != null) {
            setKeychainItem(KEY_EXPIRES_AT, expiresAt.toString())
        }
        if (tokenType != null) {
            setKeychainItem(KEY_TOKEN_TYPE, tokenType)
        }
    }

    override suspend fun getJwt(): String? {
        // Try Keychain first (survives updates), then NSUserDefaults
        val keychainValue = getKeychainItem(KEY_JWT)
        if (!keychainValue.isNullOrBlank()) {
            println("IosTokenStorage: Got JWT from Keychain")
            return keychainValue
        }
        
        val defaultsValue = userDefaults.stringForKey(KEY_JWT)
        if (!defaultsValue.isNullOrBlank()) {
            println("IosTokenStorage: Got JWT from NSUserDefaults")
            // Sync to Keychain for future updates
            setKeychainItem(KEY_JWT, defaultsValue)
        }
        return defaultsValue
    }

    override suspend fun getRefreshToken(): String? {
        val keychainValue = getKeychainItem(KEY_REFRESH_TOKEN)
        if (!keychainValue.isNullOrBlank()) {
            println("IosTokenStorage: Got refresh token from Keychain")
            return keychainValue
        }
        
        val defaultsValue = userDefaults.stringForKey(KEY_REFRESH_TOKEN)
        if (!defaultsValue.isNullOrBlank()) {
            println("IosTokenStorage: Got refresh token from NSUserDefaults")
            setKeychainItem(KEY_REFRESH_TOKEN, defaultsValue)
        }
        return defaultsValue
    }

    override suspend fun getExpiresAt(): Long? {
        val keychainValue = getKeychainItem(KEY_EXPIRES_AT)?.toLongOrNull()
        if (keychainValue != null) {
            return keychainValue
        }
        
        val expiry = userDefaults.doubleForKey(KEY_EXPIRES_AT)
        return if (expiry > 0) expiry.toLong() else null
    }

    override suspend fun getTokenType(): String? {
        val keychainValue = getKeychainItem(KEY_TOKEN_TYPE)
        if (!keychainValue.isNullOrBlank()) {
            return keychainValue
        }
        return userDefaults.stringForKey(KEY_TOKEN_TYPE)
    }

    override suspend fun clearTokens() {
        println("IosTokenStorage: Clearing tokens...")
        
        // Clear NSUserDefaults
        userDefaults.removeObjectForKey(KEY_JWT)
        userDefaults.removeObjectForKey(KEY_REFRESH_TOKEN)
        userDefaults.removeObjectForKey(KEY_EXPIRES_AT)
        userDefaults.removeObjectForKey(KEY_TOKEN_TYPE)
        userDefaults.synchronize()
        
        // Clear Keychain
        deleteKeychainItem(KEY_JWT)
        deleteKeychainItem(KEY_REFRESH_TOKEN)
        deleteKeychainItem(KEY_EXPIRES_AT)
        deleteKeychainItem(KEY_TOKEN_TYPE)
        
        println("IosTokenStorage: Tokens cleared from both storages")
    }

    override suspend fun saveFreeThisWeek(isFree: Boolean) {
        userDefaults.setBool(isFree, KEY_FREE_THIS_WEEK)
        userDefaults.synchronize()
    }

    override suspend fun getFreeThisWeek(): Boolean? {
        return if (userDefaults.objectForKey(KEY_FREE_THIS_WEEK) != null) {
            userDefaults.boolForKey(KEY_FREE_THIS_WEEK)
        } else {
            null
        }
    }

    // ============ Keychain Helpers ============

    private fun setKeychainItem(key: String, value: String): Boolean {
        // First delete any existing item
        deleteKeychainItem(key)

        val nsString = NSString.create(string = value)
        val valueData = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: run {
            println("IosTokenStorage: Failed to encode value for key '$key'")
            return false
        }

        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to key,
            kSecValueData to valueData,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        )

        @Suppress("UNCHECKED_CAST")
        val cfQuery = CFBridgingRetain(query) as CFDictionaryRef

        val status = SecItemAdd(cfQuery, null)
        CFBridgingRelease(cfQuery)

        if (status != errSecSuccess) {
            println("IosTokenStorage: Keychain set failed for '$key', status: $status")
        }
        return status == errSecSuccess
    }

    private fun getKeychainItem(key: String): String? = memScoped {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to key,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne
        )

        @Suppress("UNCHECKED_CAST")
        val cfQuery = CFBridgingRetain(query) as CFDictionaryRef

        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(cfQuery, result.ptr)
        CFBridgingRelease(cfQuery)

        if (status == errSecSuccess) {
            val data = CFBridgingRelease(result.value) as? NSData
            val stringValue = data?.let {
                NSString.create(data = it, encoding = NSUTF8StringEncoding) as? String
            }
            stringValue
        } else {
            null
        }
    }

    private fun deleteKeychainItem(key: String): Boolean {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to key
        )

        @Suppress("UNCHECKED_CAST")
        val cfQuery = CFBridgingRetain(query) as CFDictionaryRef

        val status = SecItemDelete(cfQuery)
        CFBridgingRelease(cfQuery)

        return status == errSecSuccess || status == errSecItemNotFound
    }
}

actual fun createTokenStorage(): TokenStorage = IosTokenStorage()
