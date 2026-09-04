package compose.project.click.click.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Protocol-compatible v2 message and epoch-key cryptography. */
object MessageCryptoV2 {
    const val CRYPTO_VERSION: Int = 2
    const val PREFIX: String = "e2e2:"
    const val NONCE_BYTES: Int = 12
    const val EPOCH_KEY_BYTES: Int = 32
    const val GCM_TAG_BYTES: Int = 16

    private const val HKDF_SALT = "click-platforms-e2ee-v2-hkdf-sha256"
    private val identifierPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val json =
        Json {
            isLenient = false
            ignoreUnknownKeys = false
        }

    data class MessageMetadata(
        val chatId: String,
        val epoch: Int,
        val senderDeviceId: String,
        val clientMessageId: String,
    )

    data class MediaMetadata(
        val chatId: String,
        val epoch: Int,
        val senderDeviceId: String,
        val clientMessageId: String,
        val mediaCiphertextSha256: String,
    )

    data class EpochKeyWrapMetadata(
        val chatId: String,
        val epoch: Int,
        val senderDeviceId: String,
        val recipientDeviceId: String,
    )

    sealed interface Envelope {
        val chatId: String
        val epoch: Int
        val senderDeviceId: String
        val nonce: String
        val ciphertext: String
    }

    data class MessageEnvelope(
        override val chatId: String,
        override val epoch: Int,
        override val senderDeviceId: String,
        val clientMessageId: String,
        override val nonce: String,
        override val ciphertext: String,
    ) : Envelope

    data class MediaAuthorizationEnvelope(
        override val chatId: String,
        override val epoch: Int,
        override val senderDeviceId: String,
        val clientMessageId: String,
        val mediaCiphertextSha256: String,
        override val nonce: String,
        override val ciphertext: String,
    ) : Envelope

    data class EpochKeyWrapEnvelope(
        override val chatId: String,
        override val epoch: Int,
        override val senderDeviceId: String,
        val recipientDeviceId: String,
        val ephemeralPublicKey: String,
        override val nonce: String,
        override val ciphertext: String,
    ) : Envelope

    class ReplayGuard {
        private val nonces = mutableSetOf<String>()
        private val envelopeIdentities = mutableSetOf<String>()

        val size: Int get() = envelopeIdentities.size

        fun reserve(
            nonce: String,
            envelopeIdentity: String,
        ) {
            if (envelopeIdentities.contains(envelopeIdentity)) {
                check(nonces.contains(nonce)) { "E2EE v2 replay identity mismatch" }
                return
            }
            check(nonces.add(nonce)) { "E2EE v2 replay or nonce reuse detected" }
            envelopeIdentities.add(envelopeIdentity)
        }

        fun hasSeenNonce(nonce: String): Boolean = nonce in nonces
    }

    fun generateEpochKey(): ByteArray = PlatformCrypto.secureRandomBytes(EPOCH_KEY_BYTES)

    fun generateClientMessageId(): String = PlatformCrypto.secureRandomBytes(16).toUuidString()

    fun loadOrCreateDeviceIdentity(): DeviceIdentity = DeviceIdentityStorage.loadOrCreate()

    fun encryptMessage(
        metadata: MessageMetadata,
        epochKey: ByteArray,
        plaintext: String,
        replayGuard: ReplayGuard? = null,
    ): String {
        val checked = validateMessageMetadata(metadata)
        require(epochKey.size == EPOCH_KEY_BYTES) { "epochKey must be exactly $EPOCH_KEY_BYTES bytes" }
        val nonce = PlatformCrypto.secureRandomBytes(NONCE_BYTES)
        val nonceB64 = nonce.toB64()
        replayGuard?.reserve(nonceB64, messageIdentity(checked, nonceB64))
        val ciphertext = PlatformCrypto.aesGcmEncrypt(epochKey, nonce, canonicalMessageMetadata(checked), plaintext.encodeToByteArray())
        return encodeEnvelope(
            buildJsonObject {
                put("v", CRYPTO_VERSION)
                put("type", "message")
                put("chatId", checked.chatId)
                put("epoch", checked.epoch)
                put("senderDeviceId", checked.senderDeviceId)
                put("cryptoVersion", CRYPTO_VERSION)
                put("clientMessageId", checked.clientMessageId)
                put("nonce", nonceB64)
                put("ciphertext", ciphertext.toB64())
            },
        )
    }

    /** Result of encrypting a media payload and authorizing its exact uploaded bytes. */
    data class EncryptedMedia(
        val uploadedBytes: ByteArray,
        val mediaCiphertextSha256: String,
        val authorizationEnvelope: String,
    )

    /**
     * Encrypts media as `nonce || ciphertext+tag` and authorizes those exact bytes with the
     * opaque `type=media` e2e2 envelope used by click-web.
     */
    fun encryptMedia(
        metadata: MediaMetadata,
        epochKey: ByteArray,
        plaintext: ByteArray,
        replayGuard: ReplayGuard? = null,
    ): EncryptedMedia {
        val checkedBase =
            validateMessageMetadata(
                MessageMetadata(metadata.chatId, metadata.epoch, metadata.senderDeviceId, metadata.clientMessageId),
            )
        require(epochKey.size == EPOCH_KEY_BYTES) { "epochKey must be exactly $EPOCH_KEY_BYTES bytes" }
        val nonce = PlatformCrypto.secureRandomBytes(NONCE_BYTES)
        val nonceB64 = nonce.toB64()
        val ciphertext =
            PlatformCrypto.aesGcmEncrypt(
                epochKey,
                nonce,
                canonicalMediaPayloadMetadata(checkedBase),
                plaintext,
            )
        val uploadedBytes = nonce + ciphertext
        val digest = uploadedBytes.sha256B64()
        val checked = validateMediaMetadata(metadata.copy(mediaCiphertextSha256 = digest))
        val authorization = authorizeMedia(checked, epochKey, replayGuard)
        replayGuard?.reserve(nonceB64, mediaPayloadIdentity(checked, nonceB64))
        return EncryptedMedia(uploadedBytes, digest, authorization)
    }

    /** Decrypts and authenticates a `nonce || ciphertext+tag` v2 media upload. */
    fun decryptMedia(
        metadata: MediaMetadata,
        epochKey: ByteArray,
        uploadedBytes: ByteArray,
        replayGuard: ReplayGuard? = null,
    ): ByteArray {
        val checked = validateMediaMetadata(metadata)
        require(epochKey.size == EPOCH_KEY_BYTES) { "epochKey must be exactly $EPOCH_KEY_BYTES bytes" }
        require(uploadedBytes.size >= NONCE_BYTES + GCM_TAG_BYTES) {
            "E2EE v2 media payload is too short"
        }
        val expectedDigest = checked.mediaCiphertextSha256.decodeB64("media ciphertext digest")
        val actualDigest = PlatformCrypto.sha256(uploadedBytes)
        check(constantTimeEquals(expectedDigest, actualDigest)) {
            "E2EE v2 media ciphertext digest mismatch"
        }
        val nonce = uploadedBytes.copyOfRange(0, NONCE_BYTES)
        val ciphertext = uploadedBytes.copyOfRange(NONCE_BYTES, uploadedBytes.size)
        val plaintext =
            try {
                PlatformCrypto.aesGcmDecrypt(
                    epochKey,
                    nonce,
                    canonicalMediaPayloadMetadata(
                        MessageMetadata(checked.chatId, checked.epoch, checked.senderDeviceId, checked.clientMessageId),
                    ),
                    ciphertext,
                )
            } catch (error: Exception) {
                throw IllegalStateException("E2EE v2 media authentication failed", error)
            }
        replayGuard?.reserve(nonce.toB64(), mediaPayloadIdentity(checked, nonce.toB64()))
        return plaintext
    }

    /** Creates the opaque media authorization envelope required by `/api/chat/media` and attachments. */
    fun authorizeMedia(
        metadata: MediaMetadata,
        epochKey: ByteArray,
        replayGuard: ReplayGuard? = null,
    ): String {
        val checked = validateMediaMetadata(metadata)
        require(epochKey.size == EPOCH_KEY_BYTES) { "epochKey must be exactly $EPOCH_KEY_BYTES bytes" }
        val nonce = PlatformCrypto.secureRandomBytes(NONCE_BYTES)
        val nonceB64 = nonce.toB64()
        val ciphertext =
            PlatformCrypto.aesGcmEncrypt(
                epochKey,
                nonce,
                canonicalMediaAuthorizationMetadata(checked),
                MEDIA_AUTHORIZATION_PLAINTEXT.encodeToByteArray(),
            )
        replayGuard?.reserve(nonceB64, mediaAuthorizationIdentity(checked, nonceB64))
        return encodeEnvelope(
            buildJsonObject {
                put("v", CRYPTO_VERSION)
                put("type", "media")
                put("chatId", checked.chatId)
                put("epoch", checked.epoch)
                put("senderDeviceId", checked.senderDeviceId)
                put("cryptoVersion", CRYPTO_VERSION)
                put("clientMessageId", checked.clientMessageId)
                put("mediaCiphertextSha256", checked.mediaCiphertextSha256)
                put("nonce", nonceB64)
                put("ciphertext", ciphertext.toB64())
            },
        )
    }

    /** Verifies the fixed plaintext in an opaque authorization envelope. */
    fun verifyMediaAuthorization(
        metadata: MediaMetadata,
        epochKey: ByteArray,
        envelope: String,
        replayGuard: ReplayGuard? = null,
    ) {
        val expected = validateMediaMetadata(metadata)
        val parsed =
            try {
                parseMediaAuthorizationEnvelope(envelope)
            } catch (error: IllegalArgumentException) {
                throw IllegalStateException("E2EE v2 media authorization envelope is invalid", error)
            }
        check(
            parsed.chatId == expected.chatId &&
                parsed.epoch == expected.epoch &&
                parsed.senderDeviceId == expected.senderDeviceId &&
                parsed.clientMessageId == expected.clientMessageId &&
                parsed.mediaCiphertextSha256 == expected.mediaCiphertextSha256,
        ) { "E2EE v2 media authorization metadata mismatch" }
        require(epochKey.size == EPOCH_KEY_BYTES) { "epochKey must be exactly $EPOCH_KEY_BYTES bytes" }
        try {
            val plaintext =
                PlatformCrypto
                    .aesGcmDecrypt(
                        epochKey,
                        parsed.nonce.decodeB64("nonce"),
                        canonicalMediaAuthorizationMetadata(expected),
                        parsed.ciphertext.decodeB64("ciphertext"),
                    ).decodeToString()
            check(plaintext == MEDIA_AUTHORIZATION_PLAINTEXT) {
                "E2EE v2 media authorization plaintext mismatch"
            }
        } catch (error: IllegalStateException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("E2EE v2 media authorization authentication failed", error)
        }
        replayGuard?.reserve(parsed.nonce, mediaAuthorizationIdentity(expected, parsed.nonce))
    }

    fun decryptMessage(
        metadata: MessageMetadata,
        epochKey: ByteArray,
        envelope: String,
        replayGuard: ReplayGuard? = null,
    ): String {
        val expected = validateMessageMetadata(metadata)
        val parsed =
            try {
                parseMessageEnvelope(envelope)
            } catch (error: IllegalArgumentException) {
                throw IllegalStateException("E2EE v2 message envelope is invalid", error)
            }
        check(
            parsed.chatId == expected.chatId &&
                parsed.epoch == expected.epoch &&
                parsed.senderDeviceId == expected.senderDeviceId &&
                parsed.clientMessageId == expected.clientMessageId,
        ) {
            "E2EE v2 authenticated metadata mismatch"
        }
        require(epochKey.size == EPOCH_KEY_BYTES) { "epochKey must be exactly $EPOCH_KEY_BYTES bytes" }
        val plaintext =
            try {
                PlatformCrypto
                    .aesGcmDecrypt(
                        epochKey,
                        parsed.nonce.decodeB64("nonce"),
                        canonicalMessageMetadata(expected),
                        parsed.ciphertext.decodeB64("ciphertext"),
                    ).decodeToString()
            } catch (_: Exception) {
                throw IllegalStateException("E2EE v2 message authentication failed")
            }
        replayGuard?.reserve(parsed.nonce, messageIdentity(expected, parsed.nonce))
        return plaintext
    }

    fun wrapEpochKey(
        metadata: EpochKeyWrapMetadata,
        epochKey: ByteArray,
        recipientPublicKeySpkiBase64: String,
        replayGuard: ReplayGuard? = null,
    ): String {
        val checked = validateWrapMetadata(metadata)
        require(epochKey.size == EPOCH_KEY_BYTES) { "epochKey must be exactly $EPOCH_KEY_BYTES bytes" }
        val ephemeral = DeviceIdentityStorage.generateEphemeral()
        return try {
            val sharedSecret = DeviceIdentityStorage.deriveSharedSecret(ephemeral, recipientPublicKeySpkiBase64)
            val wrappingKey = hkdfSha256(sharedSecret, canonicalWrapMetadata(checked))
            val nonce = PlatformCrypto.secureRandomBytes(NONCE_BYTES)
            val nonceB64 = nonce.toB64()
            replayGuard?.reserve(nonceB64, wrapIdentity(checked, nonceB64))
            val ciphertext = PlatformCrypto.aesGcmEncrypt(wrappingKey, nonce, canonicalWrapMetadata(checked), epochKey)
            encodeEnvelope(
                buildJsonObject {
                    put("v", CRYPTO_VERSION)
                    put("type", "epoch-key-wrap")
                    put("chatId", checked.chatId)
                    put("epoch", checked.epoch)
                    put("senderDeviceId", checked.senderDeviceId)
                    put("recipientDeviceId", checked.recipientDeviceId)
                    put("cryptoVersion", CRYPTO_VERSION)
                    put("ephemeralPublicKey", ephemeral.info.publicKeySpkiBase64)
                    put("nonce", nonceB64)
                    put("ciphertext", ciphertext.toB64())
                },
            )
        } finally {
            DeviceIdentityStorage.destroyEphemeral(ephemeral)
        }
    }

    fun unwrapEpochKey(
        metadata: EpochKeyWrapMetadata,
        recipientIdentity: DeviceIdentity,
        envelope: String,
        replayGuard: ReplayGuard? = null,
    ): ByteArray {
        val expected = validateWrapMetadata(metadata)
        val parsed = parseWrapEnvelope(envelope)
        check(
            parsed.chatId == expected.chatId &&
                parsed.epoch == expected.epoch &&
                parsed.senderDeviceId == expected.senderDeviceId &&
                parsed.recipientDeviceId == expected.recipientDeviceId,
        ) {
            "E2EE v2 epoch-key metadata mismatch"
        }
        replayGuard?.reserve(parsed.nonce, wrapIdentity(expected, parsed.nonce))
        return try {
            val sharedSecret = DeviceIdentityStorage.deriveSharedSecret(recipientIdentity, parsed.ephemeralPublicKey)
            val wrappingKey = hkdfSha256(sharedSecret, canonicalWrapMetadata(expected))
            val key =
                PlatformCrypto.aesGcmDecrypt(
                    wrappingKey,
                    parsed.nonce.decodeB64("nonce"),
                    canonicalWrapMetadata(expected),
                    parsed.ciphertext.decodeB64("ciphertext"),
                )
            require(key.size == EPOCH_KEY_BYTES) { "unwrapped epoch key must be exactly $EPOCH_KEY_BYTES bytes" }
            key
        } catch (_: Exception) {
            throw IllegalStateException("E2EE v2 epoch-key authentication failed")
        }
    }

    /** Returns v1 bytes unchanged; only e2e2 content is parsed or decrypted. */
    fun decryptContentCompatible(
        content: String,
        metadata: MessageMetadata,
        epochKey: ByteArray,
        replayGuard: ReplayGuard? = null,
    ): String = if (content.startsWith(PREFIX)) decryptMessage(metadata, epochKey, content, replayGuard) else content

    fun parseE2eeV2Envelope(wire: String): Envelope {
        val value = parseEnvelopeJson(wire)
        return when (value.string("type")) {
            "message" -> parseMessageEnvelope(wire)
            "media" -> parseMediaAuthorizationEnvelope(wire)
            "epoch-key-wrap" -> parseWrapEnvelope(wire)
            else -> throw IllegalArgumentException("Malformed e2e2 envelope type")
        }
    }

    fun isEncrypted(content: String): Boolean = content.startsWith(PREFIX)

    private fun parseMessageEnvelope(wire: String): MessageEnvelope {
        val value = parseEnvelopeJson(wire)
        requireExactKeys(
            value,
            setOf("v", "type", "chatId", "epoch", "senderDeviceId", "cryptoVersion", "clientMessageId", "nonce", "ciphertext"),
        )
        validateCommon(value, "message")
        val metadata =
            validateMessageMetadata(
                MessageMetadata(
                    value.string("chatId"),
                    value.positiveEpoch(),
                    value.string("senderDeviceId"),
                    value.string("clientMessageId"),
                ),
            )
        return MessageEnvelope(
            metadata.chatId,
            metadata.epoch,
            metadata.senderDeviceId,
            metadata.clientMessageId,
            value.string("nonce"),
            value.string("ciphertext"),
        )
    }

    private fun parseMediaAuthorizationEnvelope(wire: String): MediaAuthorizationEnvelope {
        val value = parseEnvelopeJson(wire)
        requireExactKeys(
            value,
            setOf(
                "v",
                "type",
                "chatId",
                "epoch",
                "senderDeviceId",
                "cryptoVersion",
                "clientMessageId",
                "mediaCiphertextSha256",
                "nonce",
                "ciphertext",
            ),
        )
        validateCommon(value, "media")
        val metadata =
            validateMediaMetadata(
                MediaMetadata(
                    chatId = value.string("chatId"),
                    epoch = value.positiveEpoch(),
                    senderDeviceId = value.string("senderDeviceId"),
                    clientMessageId = value.string("clientMessageId"),
                    mediaCiphertextSha256 = value.string("mediaCiphertextSha256"),
                ),
            )
        return MediaAuthorizationEnvelope(
            chatId = metadata.chatId,
            epoch = metadata.epoch,
            senderDeviceId = metadata.senderDeviceId,
            clientMessageId = metadata.clientMessageId,
            mediaCiphertextSha256 = metadata.mediaCiphertextSha256,
            nonce = value.string("nonce"),
            ciphertext = value.string("ciphertext"),
        )
    }

    private fun parseWrapEnvelope(wire: String): EpochKeyWrapEnvelope {
        val value = parseEnvelopeJson(wire)
        requireExactKeys(
            value,
            setOf(
                "v",
                "type",
                "chatId",
                "epoch",
                "senderDeviceId",
                "recipientDeviceId",
                "cryptoVersion",
                "ephemeralPublicKey",
                "nonce",
                "ciphertext",
            ),
        )
        validateCommon(value, "epoch-key-wrap")
        val metadata =
            validateWrapMetadata(
                EpochKeyWrapMetadata(
                    value.string("chatId"),
                    value.positiveEpoch(),
                    value.string("senderDeviceId"),
                    value.string("recipientDeviceId"),
                ),
            )
        val publicKey = value.string("ephemeralPublicKey").decodeB64("ephemeral public key")
        require(publicKey.size == 44) { "ephemeral X25519 public key must be 44 bytes" }
        return EpochKeyWrapEnvelope(
            metadata.chatId,
            metadata.epoch,
            metadata.senderDeviceId,
            metadata.recipientDeviceId,
            value.string("ephemeralPublicKey"),
            value.string("nonce"),
            value.string("ciphertext"),
        )
    }

    private fun parseEnvelopeJson(wire: String): JsonObject {
        require(wire.startsWith(PREFIX)) { "Not an e2e2 envelope" }
        val decoded = wire.removePrefix(PREFIX).decodeB64("envelope").decodeToString()
        return runCatching { json.parseToJsonElement(decoded) as? JsonObject ?: error("object required") }
            .getOrElse { throw IllegalArgumentException("Malformed e2e2 envelope JSON") }
    }

    private fun validateCommon(
        value: JsonObject,
        type: String,
    ) {
        require(value.int("v") == CRYPTO_VERSION && value.string("type") == type && value.int("cryptoVersion") == CRYPTO_VERSION) {
            "Malformed e2e2 envelope"
        }
        require(value.string("nonce").decodeB64("nonce").size == NONCE_BYTES) { "Malformed nonce" }
        require(value.string("ciphertext").decodeB64("ciphertext").size >= GCM_TAG_BYTES) { "Malformed ciphertext" }
    }

    private fun validateMessageMetadata(value: MessageMetadata): MessageMetadata {
        validateIdentifier(value.chatId, "chatId")
        validateEpoch(value.epoch)
        validateIdentifier(value.senderDeviceId, "senderDeviceId")
        validateIdentifier(value.clientMessageId, "clientMessageId")
        return value
    }

    private fun validateMediaMetadata(value: MediaMetadata): MediaMetadata {
        validateMessageMetadata(
            MessageMetadata(value.chatId, value.epoch, value.senderDeviceId, value.clientMessageId),
        )
        require(value.mediaCiphertextSha256.decodeB64("media ciphertext digest").size == 32) {
            "media ciphertext digest must be SHA-256"
        }
        return value
    }

    private fun validateWrapMetadata(value: EpochKeyWrapMetadata): EpochKeyWrapMetadata {
        validateIdentifier(value.chatId, "chatId")
        validateEpoch(value.epoch)
        validateIdentifier(value.senderDeviceId, "senderDeviceId")
        validateIdentifier(value.recipientDeviceId, "recipientDeviceId")
        return value
    }

    private fun validateIdentifier(
        value: String,
        field: String,
    ) {
        require(value.trim() == value && identifierPattern.matches(value)) { "$field must be a strict identifier" }
    }

    private fun validateEpoch(value: Int) {
        require(value > 0) { "epoch must be a positive integer" }
    }

    private fun canonicalMessageMetadata(value: MessageMetadata): ByteArray =
        "{\"chatId\":\"${value.chatId}\",\"epoch\":${value.epoch},\"senderDeviceId\":\"${value.senderDeviceId}\",\"cryptoVersion\":2,\"clientMessageId\":\"${value.clientMessageId}\"}"
            .encodeToByteArray()

    private fun canonicalMediaAuthorizationMetadata(value: MediaMetadata): ByteArray =
        "{\"chatId\":\"${value.chatId}\",\"epoch\":${value.epoch},\"senderDeviceId\":\"${value.senderDeviceId}\",\"cryptoVersion\":2,\"clientMessageId\":\"${value.clientMessageId}\",\"mediaCiphertextSha256\":\"${value.mediaCiphertextSha256}\",\"purpose\":\"media-authorization\"}"
            .encodeToByteArray()

    private fun canonicalMediaPayloadMetadata(value: MessageMetadata): ByteArray =
        "{\"chatId\":\"${value.chatId}\",\"epoch\":${value.epoch},\"senderDeviceId\":\"${value.senderDeviceId}\",\"cryptoVersion\":2,\"clientMessageId\":\"${value.clientMessageId}\",\"purpose\":\"media-payload\"}"
            .encodeToByteArray()

    private fun canonicalWrapMetadata(value: EpochKeyWrapMetadata): ByteArray =
        "{\"chatId\":\"${value.chatId}\",\"epoch\":${value.epoch},\"senderDeviceId\":\"${value.senderDeviceId}\",\"recipientDeviceId\":\"${value.recipientDeviceId}\",\"cryptoVersion\":2,\"purpose\":\"epoch-key-wrap\"}"
            .encodeToByteArray()

    private fun messageIdentity(
        value: MessageMetadata,
        nonce: String,
    ) = "${value.chatId}|${value.epoch}|${value.senderDeviceId}|${value.clientMessageId}|$nonce"

    private fun mediaPayloadIdentity(
        value: MediaMetadata,
        nonce: String,
    ) = "media-payload|${value.chatId}|${value.epoch}|${value.senderDeviceId}|${value.clientMessageId}|$nonce"

    private fun mediaAuthorizationIdentity(
        value: MediaMetadata,
        nonce: String,
    ) = "media-authorization|${value.chatId}|${value.epoch}|${value.senderDeviceId}|${value.clientMessageId}|$nonce"

    private fun wrapIdentity(
        value: EpochKeyWrapMetadata,
        nonce: String,
    ) = "${value.chatId}|${value.epoch}|${value.senderDeviceId}|${value.recipientDeviceId}|$nonce"

    private fun ByteArray.sha256B64(): String = Base64.encode(PlatformCrypto.sha256(this))

    private fun constantTimeEquals(
        left: ByteArray,
        right: ByteArray,
    ): Boolean {
        if (left.size != right.size) return false
        var difference = 0
        for (index in left.indices) difference = difference or (left[index].toInt() xor right[index].toInt())
        return difference == 0
    }

    private const val MEDIA_AUTHORIZATION_PLAINTEXT = "click-e2ee-v2-media-authorization"

    private fun hkdfSha256(
        sharedSecret: ByteArray,
        info: ByteArray,
    ): ByteArray {
        val prk = PlatformCrypto.hmacSha256(HKDF_SALT.encodeToByteArray(), sharedSecret)
        return PlatformCrypto.hmacSha256(prk, info + byteArrayOf(1)).copyOf(EPOCH_KEY_BYTES)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeEnvelope(value: JsonElement): String =
        PREFIX + Base64.encode(json.encodeToString(JsonElement.serializer(), value).encodeToByteArray())

    @OptIn(ExperimentalEncodingApi::class)
    private fun ByteArray.toB64(): String = Base64.encode(this)

    private fun ByteArray.toUuidString(): String {
        val value = copyOf()
        value[6] = ((value[6].toInt() and 0x0f) or 0x40).toByte()
        value[8] = ((value[8].toInt() and 0x3f) or 0x80).toByte()
        return value.toHex(0, 4) + "-" + value.toHex(4, 6) + "-" + value.toHex(6, 8) + "-" + value.toHex(8, 10) + "-" + value.toHex(10, 16)
    }

    private fun ByteArray.toHex(
        from: Int,
        until: Int,
    ): String =
        buildString((until - from) * 2) {
            for (index in from until until) {
                val byte = this@toHex[index].toInt() and 0xff
                append("0123456789abcdef"[byte ushr 4])
                append("0123456789abcdef"[byte and 0x0f])
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    private fun String.decodeB64(field: String): ByteArray {
        require(isStrictBase64(this)) { "Malformed $field" }
        return runCatching { Base64.decode(this) }.getOrElse { throw IllegalArgumentException("Malformed $field") }
    }

    private fun isStrictBase64(value: String): Boolean =
        value.isNotEmpty() &&
            value.matches(Regex("^[A-Za-z0-9+/]*={0,2}$")) &&
            (
                value.indexOf('=') == -1 &&
                    value.length % 4 != 1 ||
                    value.indexOf('=') >= value.length - 2 &&
                    value.length % 4 == 0
            )

    private fun requireExactKeys(
        value: JsonObject,
        expected: Set<String>,
    ) {
        require(value.keys == expected) { "Malformed e2e2 envelope fields" }
    }

    private fun JsonObject.string(name: String): String = (this[name] as? JsonPrimitive)?.content ?: error("Malformed e2e2 envelope")

    private fun JsonObject.int(name: String): Int = (this[name] as? JsonPrimitive)?.intOrNull ?: error("Malformed e2e2 envelope")

    private fun JsonObject.positiveEpoch(): Int = int("epoch").also(::validateEpoch)
}
