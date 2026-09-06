package compose.project.click.click.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
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
import platform.Foundation.NSLock
import platform.Foundation.NSMutableData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
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
import platform.posix.memcpy
import kotlin.io.encoding.Base64

private const val SERVICE = "com.click.e2ee.v2"
private const val ACCOUNT = "x25519_identity_private_key"

@OptIn(ExperimentalForeignApi::class)
actual class DeviceIdentity internal constructor(
    internal val privateKey: ByteArray,
    actual val info: DeviceIdentityInfo,
    internal val persistent: Boolean,
)

@OptIn(ExperimentalForeignApi::class)
actual object DeviceIdentityStorage {
    private val identityLock = NSLock()
    private var cachedPersistentIdentity: DeviceIdentity? = null

    actual fun loadOrCreate(): DeviceIdentity {
        identityLock.lock()
        try {
            cachedPersistentIdentity?.let { return it }
            val stored = readPrivateKey()
            if (stored != null) {
                val identity = identityFromPrivate(stored, persistent = true)
                cachedPersistentIdentity = identity
                return identity
            }
            val identity = identityFromPrivate(X25519.generatePrivateKey(), persistent = true)
            if (!writePrivateKey(identity.privateKey)) {
                identity.privateKey.fill(0)
                error("Unable to persist the E2EE device identity")
            }
            cachedPersistentIdentity = identity
            return identity
        } finally {
            identityLock.unlock()
        }
    }

    actual fun generateEphemeral(): DeviceIdentity = identityFromPrivate(X25519.generatePrivateKey(), persistent = false)

    actual fun deriveSharedSecret(
        identity: DeviceIdentity,
        peerPublicKeySpkiBase64: String,
    ): ByteArray {
        val spki =
            runCatching { Base64.decode(peerPublicKeySpkiBase64) }
                .getOrElse { error("Invalid X25519 public key") }
        require(
            spki.size == 44 && spki.copyOfRange(0, 12).contentEquals(SPKI_PREFIX),
        ) { "Invalid X25519 public key" }
        return X25519.sharedSecret(identity.privateKey, spki.copyOfRange(12, 44))
    }

    actual fun destroyEphemeral(identity: DeviceIdentity) {
        if (!identity.persistent) identity.privateKey.fill(0)
    }

    private fun identityFromPrivate(
        privateKey: ByteArray,
        persistent: Boolean,
    ): DeviceIdentity {
        val spki = SPKI_PREFIX + X25519.publicKey(privateKey)
        val deviceId = PlatformCrypto.sha256(spki).toHex()
        return DeviceIdentity(
            privateKey,
            DeviceIdentityInfo(deviceId, Base64.encode(spki)),
            persistent,
        )
    }

    /**
     * Same CFDictionaryCreate path as [compose.project.click.click.data.storage.IosTokenStorage].
     * Kotlin Map → CFBridgingRetain yields errSecParam (-50) and used to abort send.
     */
    private fun readPrivateKey(): ByteArray? =
        memScoped {
            val cfAccount = CFBridgingRetain(ACCOUNT)
            val cfService = CFBridgingRetain(SERVICE)
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
                when (status) {
                    errSecItemNotFound -> null
                    errSecSuccess ->
                        (CFBridgingRelease(result.value) as? NSData)
                            ?.toByteArray()
                            ?.takeIf { it.size == 32 }
                            ?: error("Stored E2EE device identity is invalid")
                    else -> error("Unable to read the E2EE device identity (status=$status)")
                }
            } finally {
                CFBridgingRelease(cfAccount)
                CFBridgingRelease(cfService)
            }
        }

    private fun writePrivateKey(privateKey: ByteArray): Boolean =
        memScoped {
            val data = privateKey.toNSData()
            val cfAccount = CFBridgingRetain(ACCOUNT)
            val cfService = CFBridgingRetain(SERVICE)
            val cfValue = CFBridgingRetain(data)
            try {
                val basePairs =
                    mapOf<CFStringRef?, CFTypeRef?>(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to cfService,
                        kSecAttrAccount to cfAccount,
                    )
                val updatePairs = mapOf<CFStringRef?, CFTypeRef?>(kSecValueData to cfValue)
                val addPairs =
                    basePairs +
                        mapOf(
                            kSecValueData to cfValue,
                            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                        )
                val cfBase = cfDictionaryOf(basePairs)
                val cfUpdate = cfDictionaryOf(updatePairs)
                var status = SecItemUpdate(cfBase, cfUpdate)
                CFBridgingRelease(cfUpdate)
                if (status == errSecItemNotFound) {
                    val cfAdd = cfDictionaryOf(addPairs)
                    status = SecItemAdd(cfAdd, null)
                    CFBridgingRelease(cfAdd)
                    if (status == errSecDuplicateItem) {
                        val cfRetry = cfDictionaryOf(updatePairs)
                        status = SecItemUpdate(cfBase, cfRetry)
                        CFBridgingRelease(cfRetry)
                    }
                }
                CFBridgingRelease(cfBase)
                if (status != errSecSuccess) {
                    println("DeviceIdentityStorage: Keychain write status=$status")
                }
                status == errSecSuccess
            } finally {
                CFBridgingRelease(cfAccount)
                CFBridgingRelease(cfService)
                CFBridgingRelease(cfValue)
            }
        }

    private fun MemScope.cfDictionaryOf(map: Map<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val keys = allocArrayOf(*map.keys.toTypedArray())
        val values = allocArrayOf(*map.values.toTypedArray())
        return CFDictionaryCreate(
            kCFAllocatorDefault,
            keys.reinterpret(),
            values.reinterpret(),
            map.size.convert(),
            null,
            null,
        )
    }

    private fun NSData.toByteArray(): ByteArray {
        val output = ByteArray(length.toInt())
        val source = bytes ?: return output
        if (output.isNotEmpty()) {
            output.usePinned { pinned ->
                memcpy(pinned.addressOf(0), source, output.size.toULong())
            }
        }
        return output
    }

    private fun ByteArray.toNSData(): NSData =
        usePinned { pinned ->
            val output = NSMutableData()
            output.setLength(size.toULong())
            if (isNotEmpty()) {
                output.mutableBytes?.let { memcpy(it, pinned.addressOf(0), size.toULong()) }
            }
            output
        }

    private fun ByteArray.toHex(): String =
        buildString(size * 2) {
            for (byte in this@toHex) {
                append("0123456789abcdef"[(byte.toInt() ushr 4) and 15])
                append("0123456789abcdef"[byte.toInt() and 15])
            }
        }

    private val SPKI_PREFIX =
        byteArrayOf(
            0x30,
            0x2a,
            0x30,
            0x05,
            0x06,
            0x03,
            0x2b,
            0x65,
            0x6e,
            0x03,
            0x21,
            0x00,
        )
}
