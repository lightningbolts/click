package compose.project.click.click.chat.attachments

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.crypto.PlatformCrypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Per-file E2EE pipeline for chat attachments (Phase 2 — B3).
 *
 * Design choices (locked by approval 2026-04-16):
 *   * AES-256-CBC + HMAC-SHA256 (encrypt-then-MAC) — strict parity with [MessageCrypto].
 *   * Each attachment gets a **fresh 32-byte random master** (never reused). From that master we
 *     derive enc/mac exactly like [MessageCrypto.deriveMessageKeysFromGroupMaster], so tampering
 *     with one attachment reveals nothing about any chat key.
 *   * The per-file master travels inside the normal E2EE message payload as a JSON envelope
 *     prefixed with [ENVELOPE_PREFIX]. Everything before encryption is plain text; everything
 *     after decryption must start with [ENVELOPE_PREFIX] to be treated as an attachment.
 *   * Integrity is protected twice: HMAC over the ciphertext, and a SHA-256 over the *plaintext*
 *     in the envelope so the receiver can detect a swapped object at download time.
 */
object AttachmentCrypto {

    /**
     * Envelope prefix used inside the decrypted E2EE message body. Only messages that begin with
     * this prefix are attachments; everything else is a normal text message. The version tag lets
     * us evolve the envelope without breaking old clients.
     */
    const val ENVELOPE_PREFIX: String = "ccx:v1:"

    private const val FILE_MASTER_KEY_BYTES = 32

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Envelope(
        val v: Int = 1,
        val type: String = "file",
        val name: String,
        val mime: String,
        val size: Long,
        val path: String,
        val key: String,
        val sha256: String,
    )

    /** Fresh 32-byte per-file master key. Caller must never reuse this across attachments. */
    fun generateFileMasterKey(): ByteArray = PlatformCrypto.secureRandomBytes(FILE_MASTER_KEY_BYTES)

    /**
     * Encrypt attachment bytes. Wire format is identical to [MessageCrypto.encryptMediaBytes]:
     * `IV[16] || HMAC[32] || ciphertext`.
     */
    fun encryptFileBytes(plain: ByteArray, fileMasterKey32: ByteArray): ByteArray {
        require(fileMasterKey32.size == FILE_MASTER_KEY_BYTES) {
            "File master key must be $FILE_MASTER_KEY_BYTES bytes"
        }
        return MessageCrypto.encryptMediaBytes(plain, fileMasterKey32)
    }

    /** Decrypt attachment bytes. Throws [MessageCrypto.MessageEncryptionException] on HMAC failure. */
    fun decryptFileBytes(blob: ByteArray, fileMasterKey32: ByteArray): ByteArray {
        require(fileMasterKey32.size == FILE_MASTER_KEY_BYTES) {
            "File master key must be $FILE_MASTER_KEY_BYTES bytes"
        }
        return MessageCrypto.decryptMediaBytes(blob, fileMasterKey32)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeFileMasterKeyBase64(key: ByteArray): String {
        require(key.size == FILE_MASTER_KEY_BYTES) { "File master key must be 32 bytes" }
        return Base64.encode(key)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeFileMasterKeyBase64(b64: String): ByteArray {
        val raw = Base64.decode(b64.trim())
        require(raw.size == FILE_MASTER_KEY_BYTES) { "Decoded file master key must be 32 bytes" }
        return raw
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun sha256Base64(bytes: ByteArray): String = Base64.encode(PlatformCrypto.sha256(bytes))

    /** Serialise an envelope to the wire form `ccx:v1:<json>`. */
    fun encodeEnvelope(env: Envelope): String = ENVELOPE_PREFIX + json.encodeToString(env)

    /**
     * Try to parse an envelope. Returns `null` if [content] is not an attachment payload — the
     * caller should then treat it as a plain text message (backwards-compatible fallback).
     */
    fun tryDecodeEnvelope(content: String): Envelope? {
        if (!content.startsWith(ENVELOPE_PREFIX)) return null
        val body = content.substring(ENVELOPE_PREFIX.length)
        return try {
            json.decodeFromString<Envelope>(body)
        } catch (_: Throwable) {
            null
        }
    }

    fun isAttachmentEnvelope(content: String): Boolean = content.startsWith(ENVELOPE_PREFIX)
}
