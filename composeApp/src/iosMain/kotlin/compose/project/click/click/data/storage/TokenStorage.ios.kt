package compose.project.click.click.data.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.*

/**
 * iOS Keychain-backed TokenStorage for persistence across app updates and reinstalls.
 * Keychain data survives:
 * - App updates (TestFlight, App Store)
 * - App reinstalls (data persists if same team ID)
 * 
 * This is critical for maintaining user sessions across TestFlight updates.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class IosKeychainTokenStorage : TokenStorage {

    companion object {
        private const val SERVICE_NAME = "com.click.auth"
        private const val KEY_JWT = "jwt"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_FREE_THIS_WEEK = "free_this_week"
    }

    override suspend fun saveTokens(jwt: String, refreshToken: String, expiresAt: Long?, tokenType: String?) {
        setKeychainItem(KEY_JWT, jwt)
        setKeychainItem(KEY_REFRESH_TOKEN, refreshToken)
        if (expiresAt != null) {
            setKeychainItem(KEY_EXPIRES_AT, expiresAt.toString())
        } else {
            deleteKeychainItem(KEY_EXPIRES_AT)
        }
        if (tokenType != null) {
            setKeychainItem(KEY_TOKEN_TYPE, tokenType)
        } else {
            deleteKeychainItem(KEY_TOKEN_TYPE)
        }
    }

    override suspend fun getJwt(): String? = getKeychainItem(KEY_JWT)

    override suspend fun getRefreshToken(): String? = getKeychainItem(KEY_REFRESH_TOKEN)

    override suspend fun getExpiresAt(): Long? = getKeychainItem(KEY_EXPIRES_AT)?.toLongOrNull()

    override suspend fun getTokenType(): String? = getKeychainItem(KEY_TOKEN_TYPE)

    override suspend fun clearTokens() {
        deleteKeychainItem(KEY_JWT)
        deleteKeychainItem(KEY_REFRESH_TOKEN)
        deleteKeychainItem(KEY_EXPIRES_AT)
        deleteKeychainItem(KEY_TOKEN_TYPE)
        // Note: We intentionally do NOT clear free_this_week preference on logout
    }

    override suspend fun saveFreeThisWeek(isFree: Boolean) {
        setKeychainItem(KEY_FREE_THIS_WEEK, if (isFree) "true" else "false")
    }

    override suspend fun getFreeThisWeek(): Boolean? {
        return getKeychainItem(KEY_FREE_THIS_WEEK)?.let { it == "true" }
    }

    /**
     * Store a string value in Keychain
     */
    private fun setKeychainItem(key: String, value: String): Boolean {
        // First attempt to delete existing item
        deleteKeychainItem(key)

        val valueData = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false

        val query = mapOf(
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

        return status == errSecSuccess
    }

    /**
     * Retrieve a string value from Keychain
     */
    private fun getKeychainItem(key: String): String? = memScoped {
        val query = mapOf(
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
            data?.let {
                NSString.create(data = it, encoding = NSUTF8StringEncoding) as? String
            }
        } else {
            null
        }
    }

    /**
     * Delete an item from Keychain
     */
    private fun deleteKeychainItem(key: String): Boolean {
        val query = mapOf(
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

actual fun createTokenStorage(): TokenStorage = IosKeychainTokenStorage()
