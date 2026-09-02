@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package compose.project.click.click.data

import com.russhwolf.settings.Settings
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
import platform.Security.SecItemUpdate
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

private const val SUPABASE_AUTH_SUITE_NAME = "click_supabase_auth_settings"
private const val SUPABASE_AUTH_KEYCHAIN_SERVICE = "com.click.supabase.auth"
private const val SUPABASE_AUTH_KEYCHAIN_ACCOUNT = "settings_v1"

private sealed interface KeychainReadResult {
    data class Found(
        val value: String,
    ) : KeychainReadResult

    data object Missing : KeychainReadResult

    data object Unavailable : KeychainReadResult
}

/**
 * Keychain-backed [Settings] used by GoTrue. The legacy UserDefaults suite is consulted only for
 * a one-time migration, then erased so session JSON and refresh tokens are never stored plaintext.
 */
internal actual fun createSupabaseAuthSettings(): Settings = IosKeychainSettings()

@Serializable
private data class KeychainSettingsPayload(
    val version: Int = 1,
    val values: Map<String, String> = emptyMap(),
)

private class IosKeychainSettings : Settings {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val legacyDefaults = NSUserDefaults(suiteName = SUPABASE_AUTH_SUITE_NAME)

    override val keys: Set<String> get() = read().values.keys
    override val size: Int get() = read().values.size

    init {
        migrateLegacySettings()
    }

    override fun clear() {
        if (keychainGet() is KeychainReadResult.Unavailable) return
        write(emptyMap())
    }

    override fun remove(key: String) {
        val current = readForMutation() ?: return
        write(current.values - key)
    }

    override fun hasKey(key: String): Boolean = read().values.containsKey(key)

    override fun putInt(
        key: String,
        value: Int,
    ) = put(key, "i:$value")

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = getIntOrNull(key) ?: defaultValue

    override fun getIntOrNull(key: String): Int? = readTyped(key, "i")?.toIntOrNull()

    override fun putLong(
        key: String,
        value: Long,
    ) = put(key, "l:$value")

    override fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = getLongOrNull(key) ?: defaultValue

    override fun getLongOrNull(key: String): Long? = readTyped(key, "l")?.toLongOrNull()

    override fun putString(
        key: String,
        value: String,
    ) = put(key, "s:$value")

    override fun getString(
        key: String,
        defaultValue: String,
    ): String = getStringOrNull(key) ?: defaultValue

    override fun getStringOrNull(key: String): String? = readTyped(key, "s")

    override fun putFloat(
        key: String,
        value: Float,
    ) = put(key, "f:$value")

    override fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = getFloatOrNull(key) ?: defaultValue

    override fun getFloatOrNull(key: String): Float? = readTyped(key, "f")?.toFloatOrNull()

    override fun putDouble(
        key: String,
        value: Double,
    ) = put(key, "d:$value")

    override fun getDouble(
        key: String,
        defaultValue: Double,
    ): Double = getDoubleOrNull(key) ?: defaultValue

    override fun getDoubleOrNull(key: String): Double? = readTyped(key, "d")?.toDoubleOrNull()

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) = put(key, "b:$value")

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = getBooleanOrNull(key) ?: defaultValue

    override fun getBooleanOrNull(key: String): Boolean? = readTyped(key, "b")?.toBooleanStrictOrNull()

    private fun put(
        key: String,
        value: String,
    ) {
        require(key.isNotBlank()) { "Settings key must not be blank" }
        val current = readForMutation() ?: return
        write(current.values + (key to value))
    }

    private fun readTyped(
        key: String,
        type: String,
    ): String? = read().values[key]?.takeIf { it.startsWith("$type:") }?.removePrefix("$type:")

    private fun read(): KeychainSettingsPayload = readForMutation() ?: KeychainSettingsPayload()

    /** Returns null only when Keychain is unavailable; missing means an empty mutable payload. */
    private fun readForMutation(): KeychainSettingsPayload? =
        when (val result = keychainGet()) {
            KeychainReadResult.Missing -> KeychainSettingsPayload()
            KeychainReadResult.Unavailable -> null
            is KeychainReadResult.Found ->
                runCatching { json.decodeFromString(KeychainSettingsPayload.serializer(), result.value.trim()) }
                    .getOrNull()
                    ?.takeIf { it.version == 1 }
                    ?: KeychainSettingsPayload()
        }

    private fun write(values: Map<String, String>) {
        val encoded = json.encodeToString(KeychainSettingsPayload.serializer(), KeychainSettingsPayload(values = values))
        keychainSet(encoded)
    }

    private fun migrateLegacySettings() {
        when (keychainGet()) {
            is KeychainReadResult.Found -> {
                purgeLegacySettings()
                return
            }
            KeychainReadResult.Unavailable -> return
            KeychainReadResult.Missing -> Unit
        }
        // SettingsSessionManager's documented key is `session`; copy every legacy string in its
        // dedicated suite so a future auth helper cannot strand a verifier under a derived key.
        val legacyValues =
            legacyDefaults
                .dictionaryRepresentation()
                .entries
                .mapNotNull { (key, rawValue) ->
                    val normalizedKey = key?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val value = rawValue.toString()
                    value.takeIf { it.isNotBlank() }?.let { normalizedKey to "s:$it" }
                }.toMap()
        if (legacyValues.isNotEmpty() &&
            keychainSet(json.encodeToString(KeychainSettingsPayload.serializer(), KeychainSettingsPayload(values = legacyValues)))
        ) {
            purgeLegacySettings()
        }
    }

    private fun purgeLegacySettings() {
        legacyDefaults.removePersistentDomainForName(SUPABASE_AUTH_SUITE_NAME)
        legacyDefaults.synchronize()
    }

    private fun keychainSet(value: String): Boolean =
        runCatching {
            memScoped {
                val valueData =
                    NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding)
                        ?: return@memScoped false
                val account = CFBridgingRetain(SUPABASE_AUTH_KEYCHAIN_ACCOUNT)
                val service = CFBridgingRetain(SUPABASE_AUTH_KEYCHAIN_SERVICE)
                val cfValue = CFBridgingRetain(valueData)
                try {
                    val base =
                        cfDictionaryOf(
                            mapOf(
                                kSecClass to kSecClassGenericPassword,
                                kSecAttrService to service,
                                kSecAttrAccount to account,
                            ),
                        )
                    val update = cfDictionaryOf(mapOf(kSecValueData to cfValue))
                    var status = SecItemUpdate(base, update)
                    CFBridgingRelease(update)
                    if (status == errSecItemNotFound) {
                        val add =
                            cfDictionaryOf(
                                mapOf(
                                    kSecClass to kSecClassGenericPassword,
                                    kSecAttrService to service,
                                    kSecAttrAccount to account,
                                    kSecValueData to cfValue,
                                    kSecAttrAccessible to
                                        kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                                ),
                            )
                        status = SecItemAdd(add, null)
                        CFBridgingRelease(add)
                    }
                    CFBridgingRelease(base)
                    status == errSecSuccess
                } finally {
                    CFBridgingRelease(account)
                    CFBridgingRelease(service)
                    CFBridgingRelease(cfValue)
                }
            }
        }.getOrDefault(false)

    private fun keychainGet(): KeychainReadResult =
        runCatching {
            memScoped {
                val account = CFBridgingRetain(SUPABASE_AUTH_KEYCHAIN_ACCOUNT)
                val service = CFBridgingRetain(SUPABASE_AUTH_KEYCHAIN_SERVICE)
                try {
                    val query =
                        cfDictionaryOf(
                            mapOf(
                                kSecClass to kSecClassGenericPassword,
                                kSecAttrService to service,
                                kSecAttrAccount to account,
                                kSecReturnData to kCFBooleanTrue,
                                kSecMatchLimit to kSecMatchLimitOne,
                            ),
                        )
                    val result = alloc<CFTypeRefVar>()
                    val status = SecItemCopyMatching(query, result.ptr)
                    CFBridgingRelease(query)
                    if (status == errSecItemNotFound) return@memScoped KeychainReadResult.Missing
                    if (status != errSecSuccess) return@memScoped KeychainReadResult.Unavailable
                    val data =
                        CFBridgingRelease(result.value) as? NSData
                            ?: return@memScoped KeychainReadResult.Unavailable
                    val value =
                        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                            ?: return@memScoped KeychainReadResult.Unavailable
                    KeychainReadResult.Found(value)
                } finally {
                    CFBridgingRelease(account)
                    CFBridgingRelease(service)
                }
            }
        }.getOrDefault(KeychainReadResult.Unavailable)

    private fun MemScope.cfDictionaryOf(map: Map<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val keys = allocArrayOf(*map.keys.toTypedArray())
        val values = allocArrayOf(*map.values.toTypedArray())
        return CFDictionaryCreate(kCFAllocatorDefault, keys.reinterpret(), values.reinterpret(), map.size.convert(), null, null)
    }
}
