package compose.project.click.click.crypto

import android.util.Base64
import compose.project.click.click.data.storage.androidStorageContextOrThrow
import compose.project.click.click.data.storage.createEncryptedSharedPreferences
import java.security.MessageDigest

private const val IDENTITY_PREFS = "click_e2ee_v2_identity"
private const val PRIVATE_KEY = "x25519_private_key"
private const val PUBLIC_KEY = "x25519_public_key"

/** Android API 24+ storage: the private X25519 seed is encrypted by Android Keystore-backed prefs. */
actual class DeviceIdentity internal constructor(
    internal val privateKey: ByteArray,
    internal val persistent: Boolean,
    actual val info: DeviceIdentityInfo,
)

actual object DeviceIdentityStorage {
    private val lock = Any()

    actual fun loadOrCreate(): DeviceIdentity = synchronized(lock) {
        val prefs = prefs()
        val privateKey = prefs.getString(PRIVATE_KEY, null)?.decodeKey()?.takeIf { it.size == 32 }
        if (privateKey != null) {
            val publicKey = X25519.publicKey(privateKey)
            val identity = identityFromPrivate(privateKey, persistent = true)
            if (prefs.getString(PUBLIC_KEY, null) != identity.info.publicKeySpkiBase64) {
                prefs.edit().putString(PUBLIC_KEY, identity.info.publicKeySpkiBase64).commit()
            }
            return@synchronized identity
        }
        val identity = identityFromPrivate(X25519.generatePrivateKey(), persistent = true)
        check(prefs.edit().putString(PRIVATE_KEY, identity.privateKey.encodeKey()).putString(PUBLIC_KEY, identity.info.publicKeySpkiBase64).commit()) {
            "Unable to persist Android E2EE v2 identity"
        }
        identity
    }

    actual fun generateEphemeral(): DeviceIdentity = identityFromPrivate(X25519.generatePrivateKey(), persistent = false)

    actual fun deriveSharedSecret(identity: DeviceIdentity, peerPublicKeySpkiBase64: String): ByteArray {
        val spki = peerPublicKeySpkiBase64.decodeKey() ?: error("Invalid X25519 public key")
        require(spki.size == 44 && spki.copyOfRange(0, 12).contentEquals(SPKI_PREFIX)) { "Invalid X25519 public key" }
        return X25519.sharedSecret(identity.privateKey, spki.copyOfRange(12, 44))
    }

    actual fun destroyEphemeral(identity: DeviceIdentity) {
        if (!identity.persistent) identity.privateKey.fill(0)
    }

    private fun identityFromPrivate(privateKey: ByteArray, persistent: Boolean): DeviceIdentity {
        require(privateKey.size == 32)
        val spki = SPKI_PREFIX + X25519.publicKey(privateKey)
        val deviceId = MessageDigest.getInstance("SHA-256").digest(spki).toHex()
        return DeviceIdentity(
            privateKey = privateKey,
            persistent = persistent,
            info = DeviceIdentityInfo(deviceId = deviceId, publicKeySpkiBase64 = Base64.encodeToString(spki, Base64.NO_WRAP)),
        )
    }

    private fun prefs() = createEncryptedSharedPreferences(androidStorageContextOrThrow(), IDENTITY_PREFS)

    private fun String.decodeKey(): ByteArray? = runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()

    private fun ByteArray.encodeKey(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) append("0123456789abcdef"[(byte.toInt() ushr 4) and 0x0f]).append("0123456789abcdef"[byte.toInt() and 0x0f])
    }

    private val SPKI_PREFIX = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00,
    )
}
