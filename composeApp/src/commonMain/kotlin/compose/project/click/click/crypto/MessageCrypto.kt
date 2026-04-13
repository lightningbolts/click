package compose.project.click.click.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * High-level message encryption utilities for Click E2EE.
 *
 * 1:1 chats:
 *   AES-256-CBC + HMAC-SHA256 (encrypt-then-MAC)
 *   Wire format:  "e2e:" + Base64( IV[16] || HMAC[32] || ciphertext )
 *
 * Group clique messages (shared symmetric group master key):
 *   Same CBC+HMAC primitive with a distinct prefix so clients route decryption correctly:
 *   "e2e_grp:" + Base64( IV[16] || HMAC[32] || ciphertext )
 *
 * The group master key (32 random bytes) is distributed by sealing it per member over existing
 * 1:1 channel keys ([deriveKeysForConnection] + [encryptContent] on a Base64 payload).
 *
 * Key derivation per connection:
 *   master  = SHA-256( SALT || sorted_user_id_1 || sorted_user_id_2 || connection_id )
 *   enc_key = SHA-256( master || 0x01 )   — 32 bytes for AES-256
 *   mac_key = SHA-256( master || 0x02 )   — 32 bytes for HMAC-SHA256
 */
object MessageCrypto {

    private const val E2EE_PREFIX = "e2e:"
    private const val E2EE_GROUP_MSG_PREFIX = "e2e_grp:"
    private const val IV_LENGTH = 16
    private const val HMAC_LENGTH = 32

    private const val E2EE_SALT = "click-platforms-e2ee-v1-2024"

    const val GROUP_MASTER_KEY_BYTES: Int = 32

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

    /**
     * Ephemeral hub broadcast key: derived from [hubId] only. Anyone who can read hub_messages
     * (and knows [hubId]) can derive the same key; geofence / gatekeeper limits who may post.
     * Server never receives plaintext media — only ciphertext blobs.
     */
    fun deriveKeysForHub(hubId: String): DerivedKeys {
        val trimmed = hubId.trim()
        require(trimmed.isNotEmpty()) { "hubId must be non-empty" }
        val input = "$E2EE_SALT:hub-broadcast:$trimmed"
        val master = PlatformCrypto.sha256(input.encodeToByteArray())
        val encKey = PlatformCrypto.sha256(master + byteArrayOf(0x01))
        val macKey = PlatformCrypto.sha256(master + byteArrayOf(0x02))
        return DerivedKeys(encKey = encKey, macKey = macKey)
    }

    /** Derives per-message AES/HMAC keys from the 32-byte group master key (not the wire format). */
    fun deriveMessageKeysFromGroupMaster(groupMasterKey32: ByteArray): DerivedKeys {
        require(groupMasterKey32.size == GROUP_MASTER_KEY_BYTES) {
            "Group master key must be $GROUP_MASTER_KEY_BYTES bytes"
        }
        val encKey = PlatformCrypto.sha256(groupMasterKey32 + byteArrayOf(0x01))
        val macKey = PlatformCrypto.sha256(groupMasterKey32 + byteArrayOf(0x02))
        return DerivedKeys(encKey = encKey, macKey = macKey)
    }

    fun generateGroupMasterKey(): ByteArray = PlatformCrypto.secureRandomBytes(GROUP_MASTER_KEY_BYTES)

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeGroupMasterKeyBase64(key: ByteArray): String {
        require(key.size == GROUP_MASTER_KEY_BYTES)
        return Base64.encode(key)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeGroupMasterKeyBase64(b64: String): ByteArray {
        val raw = Base64.decode(b64.trim())
        require(raw.size == GROUP_MASTER_KEY_BYTES) { "Decoded group key must be 32 bytes" }
        return raw
    }

    // ── Encrypt / Decrypt ───────────────────────────────────────────────────

    @OptIn(ExperimentalEncodingApi::class)
    private fun encryptWithPrefix(plaintext: String, keys: DerivedKeys, prefix: String): String {
        val iv = PlatformCrypto.secureRandomBytes(IV_LENGTH)
        val ciphertext = PlatformCrypto.aesCbcEncrypt(keys.encKey, iv, plaintext.encodeToByteArray())
        val hmac = PlatformCrypto.hmacSha256(keys.macKey, iv + ciphertext)
        val payload = iv + hmac + ciphertext
        return prefix + Base64.encode(payload)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decryptWithPrefix(content: String, keys: DerivedKeys, prefix: String): String {
        if (!content.startsWith(prefix)) return content
        return try {
            val payload = Base64.decode(content.removePrefix(prefix))
            if (payload.size < IV_LENGTH + HMAC_LENGTH + 1) return content

            val iv = payload.copyOfRange(0, IV_LENGTH)
            val storedHmac = payload.copyOfRange(IV_LENGTH, IV_LENGTH + HMAC_LENGTH)
            val ciphertext = payload.copyOfRange(IV_LENGTH + HMAC_LENGTH, payload.size)

            val computedHmac = PlatformCrypto.hmacSha256(keys.macKey, iv + ciphertext)
            if (!computedHmac.contentEquals(storedHmac)) {
                println("MessageCrypto: HMAC verification failed — message may be tampered")
                return content
            }

            PlatformCrypto.aesCbcDecrypt(keys.encKey, iv, ciphertext).decodeToString()
        } catch (e: Exception) {
            println("MessageCrypto: Decryption failed: ${e.message}")
            content
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encryptContent(plaintext: String, keys: DerivedKeys): String =
        encryptWithPrefix(plaintext, keys, E2EE_PREFIX)

    fun encryptGroupMessageContent(plaintext: String, groupMasterKey32: ByteArray): String {
        val keys = deriveMessageKeysFromGroupMaster(groupMasterKey32)
        return encryptWithPrefix(plaintext, keys, E2EE_GROUP_MSG_PREFIX)
    }

    class MessageEncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

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

    fun decryptGroupMessageContent(content: String, groupMasterKey32: ByteArray): String {
        val keys = deriveMessageKeysFromGroupMaster(groupMasterKey32)
        return decryptWithPrefix(content, keys, E2EE_GROUP_MSG_PREFIX)
    }

    fun isEncrypted(content: String): Boolean = content.startsWith(E2EE_PREFIX)

    fun isGroupMessageEncrypted(content: String): Boolean = content.startsWith(E2EE_GROUP_MSG_PREFIX)

    fun isAnyE2eeWireContent(content: String): Boolean = isEncrypted(content) || isGroupMessageEncrypted(content)

    // ── Binary media (AES-256-CBC + HMAC-SHA256, same primitive as text; wire is raw bytes: IV||HMAC||ciphertext) ──

    fun encryptMediaBytes(plain: ByteArray, keys: DerivedKeys): ByteArray {
        val iv = PlatformCrypto.secureRandomBytes(IV_LENGTH)
        val ciphertext = PlatformCrypto.aesCbcEncrypt(keys.encKey, iv, plain)
        val hmac = PlatformCrypto.hmacSha256(keys.macKey, iv + ciphertext)
        return iv + hmac + ciphertext
    }

    fun encryptMediaBytes(plain: ByteArray, groupMasterKey32: ByteArray): ByteArray =
        encryptMediaBytes(plain, deriveMessageKeysFromGroupMaster(groupMasterKey32))

    fun decryptMediaBytes(blob: ByteArray, keys: DerivedKeys): ByteArray {
        if (blob.size < IV_LENGTH + HMAC_LENGTH + 1) {
            throw MessageEncryptionException("Encrypted media blob too short")
        }
        val iv = blob.copyOfRange(0, IV_LENGTH)
        val storedHmac = blob.copyOfRange(IV_LENGTH, IV_LENGTH + HMAC_LENGTH)
        val ciphertext = blob.copyOfRange(IV_LENGTH + HMAC_LENGTH, blob.size)
        val computedHmac = PlatformCrypto.hmacSha256(keys.macKey, iv + ciphertext)
        if (!computedHmac.contentEquals(storedHmac)) {
            throw MessageEncryptionException("Encrypted media HMAC verification failed")
        }
        return PlatformCrypto.aesCbcDecrypt(keys.encKey, iv, ciphertext)
    }

    fun decryptMediaBytes(blob: ByteArray, groupMasterKey32: ByteArray): ByteArray =
        decryptMediaBytes(blob, deriveMessageKeysFromGroupMaster(groupMasterKey32))
}
