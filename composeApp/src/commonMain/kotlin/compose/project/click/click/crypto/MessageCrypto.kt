package compose.project.click.click.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * High-level message encryption utilities for Click E2EE.
 *
 * Scheme:
 *   AES-256-CBC  + HMAC-SHA256 (encrypt-then-MAC)
 *   Wire format:  "e2e:" + Base64( IV[16] || HMAC[32] || ciphertext )
 *
 * Key derivation per connection:
 *   master  = SHA-256( SALT || sorted_user_id_1 || sorted_user_id_2 || connection_id )
 *   enc_key = SHA-256( master || 0x01 )   — 32 bytes for AES-256
 *   mac_key = SHA-256( master || 0x02 )   — 32 bytes for HMAC-SHA256
 */
object MessageCrypto {

    private const val E2EE_PREFIX = "e2e:"
    private const val IV_LENGTH = 16
    private const val HMAC_LENGTH = 32

    private const val E2EE_SALT = "click-platforms-e2ee-v1-2024"

    // ── Key derivation ──────────────────────────────────────────────────────

    data class DerivedKeys(val encKey: ByteArray, val macKey: ByteArray)

    fun deriveKeysForConnection(connectionId: String, userIds: List<String>): DerivedKeys {
        val sorted = userIds.sorted()
        val input = "$E2EE_SALT:${sorted.joinToString(":")}:$connectionId"
        val master = PlatformCrypto.sha256(input.encodeToByteArray())
        val encKey = PlatformCrypto.sha256(master + byteArrayOf(0x01))
        val macKey = PlatformCrypto.sha256(master + byteArrayOf(0x02))
        return DerivedKeys(encKey = encKey, macKey = macKey)
    }

    // ── Encrypt / Decrypt ───────────────────────────────────────────────────

    @OptIn(ExperimentalEncodingApi::class)
    fun encryptContent(plaintext: String, keys: DerivedKeys): String {
        val iv = PlatformCrypto.secureRandomBytes(IV_LENGTH)
        val ciphertext = PlatformCrypto.aesCbcEncrypt(keys.encKey, iv, plaintext.encodeToByteArray())
        val hmac = PlatformCrypto.hmacSha256(keys.macKey, iv + ciphertext)
        val payload = iv + hmac + ciphertext
        return E2EE_PREFIX + Base64.encode(payload)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decryptContent(content: String, keys: DerivedKeys): String {
        if (!content.startsWith(E2EE_PREFIX)) return content

        return try {
            val payload = Base64.decode(content.removePrefix(E2EE_PREFIX))
            if (payload.size < IV_LENGTH + HMAC_LENGTH + 1) return content

            val iv = payload.copyOfRange(0, IV_LENGTH)
            val storedHmac = payload.copyOfRange(IV_LENGTH, IV_LENGTH + HMAC_LENGTH)
            val ciphertext = payload.copyOfRange(IV_LENGTH + HMAC_LENGTH, payload.size)

            val computedHmac = PlatformCrypto.hmacSha256(keys.macKey, iv + ciphertext)
            if (!storedHmac.contentEquals(computedHmac)) {
                println("MessageCrypto: HMAC verification failed — message may be tampered")
                return content
            }

            PlatformCrypto.aesCbcDecrypt(keys.encKey, iv, ciphertext).decodeToString()
        } catch (e: Exception) {
            println("MessageCrypto: Decryption failed: ${e.message}")
            content
        }
    }

    fun isEncrypted(content: String): Boolean = content.startsWith(E2EE_PREFIX)
}
