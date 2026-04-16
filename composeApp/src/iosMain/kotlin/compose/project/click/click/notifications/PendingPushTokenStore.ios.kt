package compose.project.click.click.notifications

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
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS pending push token store backed by the Keychain (R0.7).
 *
 * Previously stored in NSUserDefaults in plaintext. That persisted through
 * iTunes/Finder backups and unencrypted at rest. Push tokens by themselves
 * are not full credentials but paired with the account they identify the
 * device and can enable push-replay; we treat them as sensitive.
 *
 * Layout:
 * - service = [PUSH_KEYCHAIN_SERVICE]
 * - one (account) entry per tokenType, value = "<platform>\n<token>"
 * - accessible = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
 *   (matches auth token storage so background refresh works).
 *
 * One-shot migration: any pre-existing NSUserDefaults slot is copied into
 * Keychain on the first consume() call and the plaintext slot is wiped.
 */

private const val PUSH_KEYCHAIN_SERVICE = "com.click.push"
private val SUPPORTED_TOKEN_TYPES: List<String> = listOf("standard", "voip")

private fun legacyPendingPushTokenKey(tokenType: String): String =
    "pending_push_token_$tokenType"

private fun legacyPendingPushPlatformKey(tokenType: String): String =
    "pending_push_platform_$tokenType"

actual fun savePendingPushToken(token: String, platform: String, tokenType: String) {
    // Layer platform and token into one Keychain value to keep the schema flat.
    setKeychainPushEntry(tokenType, "$platform\n$token")
}

actual fun consumePendingPushTokens(): List<PendingPushToken> {
    migrateLegacyPendingPushTokensIfNeeded()
    val out = ArrayList<PendingPushToken>(SUPPORTED_TOKEN_TYPES.size)
    for (type in SUPPORTED_TOKEN_TYPES) {
        val raw = getKeychainPushEntry(type) ?: continue
        val idx = raw.indexOf('\n')
        if (idx <= 0 || idx == raw.length - 1) {
            deleteKeychainPushEntry(type)
            continue
        }
        val platform = raw.substring(0, idx)
        val token = raw.substring(idx + 1)
        out += PendingPushToken(token = token, platform = platform, tokenType = type)
        deleteKeychainPushEntry(type)
    }
    return out
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private fun setKeychainPushEntry(tokenType: String, value: String): Boolean {
    deleteKeychainPushEntry(tokenType)
    val nsString = NSString.create(string = value)
    val valueData = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return false

    val query = mapOf<Any?, Any?>(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to PUSH_KEYCHAIN_SERVICE,
        kSecAttrAccount to tokenType,
        kSecValueData to valueData,
        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
    )
    @Suppress("UNCHECKED_CAST")
    val cfQuery = CFBridgingRetain(query) as CFDictionaryRef
    val status = SecItemAdd(cfQuery, null)
    CFBridgingRelease(cfQuery)
    return status == errSecSuccess
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private fun getKeychainPushEntry(tokenType: String): String? = memScoped {
    val query = mapOf<Any?, Any?>(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to PUSH_KEYCHAIN_SERVICE,
        kSecAttrAccount to tokenType,
        kSecReturnData to true,
        kSecMatchLimit to kSecMatchLimitOne,
    )
    @Suppress("UNCHECKED_CAST")
    val cfQuery = CFBridgingRetain(query) as CFDictionaryRef
    val result = alloc<CFTypeRefVar>()
    val status = SecItemCopyMatching(cfQuery, result.ptr)
    CFBridgingRelease(cfQuery)
    if (status != errSecSuccess) return@memScoped null
    val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
    NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
}

@OptIn(ExperimentalForeignApi::class)
private fun deleteKeychainPushEntry(tokenType: String): Boolean {
    val query = mapOf<Any?, Any?>(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to PUSH_KEYCHAIN_SERVICE,
        kSecAttrAccount to tokenType,
    )
    @Suppress("UNCHECKED_CAST")
    val cfQuery = CFBridgingRetain(query) as CFDictionaryRef
    val status = SecItemDelete(cfQuery)
    CFBridgingRelease(cfQuery)
    return status == errSecSuccess || status == errSecItemNotFound
}

private fun migrateLegacyPendingPushTokensIfNeeded() {
    val defaults = NSUserDefaults.standardUserDefaults
    for (type in SUPPORTED_TOKEN_TYPES) {
        val legacyToken = defaults.stringForKey(legacyPendingPushTokenKey(type)) ?: continue
        val legacyPlatform = defaults.stringForKey(legacyPendingPushPlatformKey(type))
        if (legacyPlatform != null) {
            setKeychainPushEntry(type, "$legacyPlatform\n$legacyToken")
        }
        defaults.removeObjectForKey(legacyPendingPushTokenKey(type))
        defaults.removeObjectForKey(legacyPendingPushPlatformKey(type))
    }
}
