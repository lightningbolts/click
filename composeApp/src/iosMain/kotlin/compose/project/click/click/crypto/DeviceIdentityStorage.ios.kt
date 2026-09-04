package compose.project.click.click.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
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
    actual fun loadOrCreate(): DeviceIdentity {
        readPrivateKey()?.let { return identityFromPrivate(it, persistent = true) }
        val identity = identityFromPrivate(X25519.generatePrivateKey(), persistent = true)
        check(writePrivateKey(identity.privateKey)) { "Unable to persist iOS E2EE v2 identity" }
        return identity
    }

    actual fun generateEphemeral(): DeviceIdentity = identityFromPrivate(X25519.generatePrivateKey(), persistent = false)

    actual fun deriveSharedSecret(identity: DeviceIdentity, peerPublicKeySpkiBase64: String): ByteArray {
        val spki = runCatching { Base64.decode(peerPublicKeySpkiBase64) }.getOrElse { error("Invalid X25519 public key") }
        require(spki.size == 44 && spki.copyOfRange(0, 12).contentEquals(SPKI_PREFIX)) { "Invalid X25519 public key" }
        return X25519.sharedSecret(identity.privateKey, spki.copyOfRange(12, 44))
    }

    actual fun destroyEphemeral(identity: DeviceIdentity) {
        if (!identity.persistent) identity.privateKey.fill(0)
    }

    private fun identityFromPrivate(privateKey: ByteArray, persistent: Boolean): DeviceIdentity {
        val spki = SPKI_PREFIX + X25519.publicKey(privateKey)
        val deviceId = PlatformCrypto.sha256(spki).toHex()
        return DeviceIdentity(privateKey, DeviceIdentityInfo(deviceId, Base64.encode(spki)), persistent)
    }

    private fun readPrivateKey(): ByteArray? = memScoped {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to ACCOUNT,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val cfQuery = CFBridgingRetain(query) as CFDictionaryRef
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(cfQuery, result.ptr)
        CFBridgingRelease(cfQuery)
        if (status != errSecSuccess) return@memScoped null
        (CFBridgingRelease(result.value) as? NSData)?.toByteArray()?.takeIf { it.size == 32 }
    }

    private fun writePrivateKey(privateKey: ByteArray): Boolean = memScoped {
        val data = privateKey.toNSData()
        val base = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to ACCOUNT,
        )
        val add = base + mapOf<Any?, Any?>(
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        )
        val cfBase = CFBridgingRetain(base) as CFDictionaryRef
        val cfAdd = CFBridgingRetain(add) as CFDictionaryRef
        var status = SecItemUpdate(cfBase, CFBridgingRetain(mapOf<Any?, Any?>(kSecValueData to data)) as CFDictionaryRef)
        if (status == errSecItemNotFound) status = SecItemAdd(cfAdd, null)
        if (status == errSecDuplicateItem) status = SecItemUpdate(cfBase, CFBridgingRetain(mapOf<Any?, Any?>(kSecValueData to data)) as CFDictionaryRef)
        CFBridgingRelease(cfBase)
        CFBridgingRelease(cfAdd)
        status == errSecSuccess
    }

    private fun NSData.toByteArray(): ByteArray {
        val output = ByteArray(length.toInt())
        val source = bytes ?: return output
        if (output.isNotEmpty()) output.usePinned { pinned -> memcpy(pinned.addressOf(0), source, output.size.toULong()) }
        return output
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        val output = NSMutableData()
        output.setLength(size.toULong())
        if (isNotEmpty()) output.mutableBytes?.let { memcpy(it, pinned.addressOf(0), size.toULong()) }
        output
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) append("0123456789abcdef"[(byte.toInt() ushr 4) and 15]).append("0123456789abcdef"[byte.toInt() and 15])
    }

    private val SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00)
}
