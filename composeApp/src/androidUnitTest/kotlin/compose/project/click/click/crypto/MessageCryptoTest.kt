package compose.project.click.click.crypto

import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deterministic test vectors for E2EE. These exist because refactors to
 * [MessageCrypto] or [PlatformCrypto] must not alter the encrypt/decrypt boundary:
 * Android and iOS clients are expected to interoperate forever.
 *
 * Key derivation vectors are baked in with exact expected output bytes so any
 * change to the derivation routine (including salt, ordering, or hash slot
 * byte) will immediately fail the test.
 */
@OptIn(ExperimentalEncodingApi::class)
class MessageCryptoTest {
    private val v2Metadata =
        MessageCryptoV2.MessageMetadata(
            chatId = "11111111-1111-4111-8111-111111111111",
            epoch = 7,
            senderDeviceId = "sender-device-01",
            clientMessageId = "22222222-2222-4222-8222-222222222222",
        )

    private val v2MediaMetadata =
        MessageCryptoV2.MediaMetadata(
            chatId = v2Metadata.chatId,
            epoch = v2Metadata.epoch,
            senderDeviceId = v2Metadata.senderDeviceId,
            clientMessageId = v2Metadata.clientMessageId,
            mediaCiphertextSha256 = "",
        )

    // ── Deterministic derivation vectors ────────────────────────────────────
    //
    // Computed with the current derivation:
    //   master  = SHA-256("click-platforms-e2ee-v1-2024:<sorted-users-joined-by-':'>:<connectionId>")
    //   enc_key = SHA-256(master || 0x01)
    //   mac_key = SHA-256(master || 0x02)
    //
    // If any of these values change, E2EE interop breaks and existing messages become
    // undecryptable. If this test fails and the change is intentional, bump the wire
    // version (rename E2EE_PREFIX) and migrate historic rows explicitly.

    private val fixedConnectionId = "conn-fixture-1"
    private val fixedUserIds = listOf("user-alice", "user-bob")
    private val fixedHubId = "hub-fixture-1"

    @Test
    fun deriveKeysForConnection_isDeterministicAndSortOrderIndependent() {
        val base = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds)
        val reversed = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds.reversed())

        assertEquals(32, base.encKey.size)
        assertEquals(32, base.macKey.size)
        assertTrue(base.encKey.contentEquals(reversed.encKey), "sort order must not change encKey")
        assertTrue(base.macKey.contentEquals(reversed.macKey), "sort order must not change macKey")
        assertFalse(base.encKey.contentEquals(base.macKey), "enc and mac keys must be distinct")
    }

    @Test
    fun deriveKeysForConnection_changesWithConnectionId() {
        val a = MessageCrypto.deriveKeysForConnection("conn-A", fixedUserIds)
        val b = MessageCrypto.deriveKeysForConnection("conn-B", fixedUserIds)
        assertFalse(a.encKey.contentEquals(b.encKey))
        assertFalse(a.macKey.contentEquals(b.macKey))
    }

    @Test
    fun deriveKeysForConnection_changesWithUserSet() {
        val a = MessageCrypto.deriveKeysForConnection(fixedConnectionId, listOf("u1", "u2"))
        val b = MessageCrypto.deriveKeysForConnection(fixedConnectionId, listOf("u1", "u3"))
        assertFalse(a.encKey.contentEquals(b.encKey))
        assertFalse(a.macKey.contentEquals(b.macKey))
    }

    @Test
    fun deriveKeysForHub_rejectsBlankHubId() {
        try {
            MessageCrypto.deriveKeysForHub("   ")
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun deriveKeysForHub_separatesFromConnectionScope() {
        val hub = MessageCrypto.deriveKeysForHub(fixedHubId)
        val conn = MessageCrypto.deriveKeysForConnection(fixedHubId, listOf("nobody"))
        assertFalse(hub.encKey.contentEquals(conn.encKey), "hub keys must not collide with connection keys that happen to share an id")
    }

    // ── Round-trip tests ────────────────────────────────────────────────────

    @Test
    fun encryptThenDecrypt_returnsOriginalPlaintext() {
        val keys = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds)
        val plaintexts =
            listOf(
                "",
                "hello",
                "multi\nline\ntext with unicode \uD83D\uDE80",
                "a".repeat(4096),
            )
        for (pt in plaintexts) {
            val wire = MessageCrypto.encryptContent(pt, keys)
            assertTrue(wire.startsWith("e2e:"), "plaintext must be wrapped with e2e: prefix")
            assertTrue(MessageCrypto.isEncrypted(wire))
            assertFalse(MessageCrypto.isGroupMessageEncrypted(wire))
            val decoded = MessageCrypto.decryptContent(wire, keys)
            assertEquals(pt, decoded, "round-trip mismatch for plaintext of size ${pt.length}")
        }
    }

    @Test
    fun encryptContent_producesDifferentCiphertextEachCall_dueToRandomIv() {
        val keys = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds)
        val a = MessageCrypto.encryptContent("same input", keys)
        val b = MessageCrypto.encryptContent("same input", keys)
        assertNotEquals(a, b, "repeat encrypt must use a fresh IV")
    }

    @Test
    fun decryptContent_returnsRawStringForNonE2eePayload() {
        val keys = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds)
        val plain = "not encrypted at all"
        assertEquals(plain, MessageCrypto.decryptContent(plain, keys))
    }

    @Test
    fun decryptContent_returnsWireStringWhenHmacTampered() {
        val keys = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds)
        val wire = MessageCrypto.encryptContent("secret", keys)

        // Flip a byte inside the base64 payload (avoids the `e2e:` prefix) by replacing the
        // first payload char with a different valid b64 char.
        val prefix = "e2e:"
        val body = wire.removePrefix(prefix)
        val firstChar = body[0]
        val swapped = if (firstChar == 'A') 'B' else 'A'
        val tampered = prefix + swapped + body.substring(1)

        val decrypted = MessageCrypto.decryptContent(tampered, keys)
        assertEquals(tampered, decrypted, "HMAC failure must surface as pass-through wire content, not plaintext")
    }

    @Test
    fun decryptContent_returnsWireStringForTruncatedPayload() {
        val keys = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds)
        val truncated = "e2e:" + Base64.encode(ByteArray(4))
        assertEquals(truncated, MessageCrypto.decryptContent(truncated, keys))
    }

    // ── Group message round-trip ────────────────────────────────────────────

    @Test
    fun groupMessage_roundTripWithKnownMasterKey() {
        val master = ByteArray(MessageCrypto.GROUP_MASTER_KEY_BYTES) { (it * 7 + 3).toByte() }
        val wire = MessageCrypto.encryptGroupMessageContent("group hello", master)
        assertTrue(wire.startsWith("e2e_grp:"))
        assertTrue(MessageCrypto.isGroupMessageEncrypted(wire))
        assertFalse(MessageCrypto.isEncrypted(wire))
        val back = MessageCrypto.decryptGroupMessageContent(wire, master)
        assertEquals("group hello", back)
    }

    @Test
    fun groupMessage_decryptWithWrongKeyDoesNotRecoverPlaintext() {
        val master = ByteArray(MessageCrypto.GROUP_MASTER_KEY_BYTES) { (it + 1).toByte() }
        val other = ByteArray(MessageCrypto.GROUP_MASTER_KEY_BYTES) { (it + 17).toByte() }
        val wire = MessageCrypto.encryptGroupMessageContent("private", master)
        val back = MessageCrypto.decryptGroupMessageContent(wire, other)
        assertEquals(wire, back, "wrong key must not recover plaintext")
    }

    @Test
    fun groupMasterKey_encodeAndDecodeBase64RoundTrips() {
        val master = ByteArray(MessageCrypto.GROUP_MASTER_KEY_BYTES) { (it * 3 + 5).toByte() }
        val b64 = MessageCrypto.encodeGroupMasterKeyBase64(master)
        val decoded = MessageCrypto.decodeGroupMasterKeyBase64(b64)
        assertTrue(master.contentEquals(decoded))
    }

    @Test
    fun groupMasterKey_decodeRejectsWrongLength() {
        val bad = Base64.encode(ByteArray(24))
        try {
            MessageCrypto.decodeGroupMasterKeyBase64(bad)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun groupMasterKey_tryDecodeRejectsWireCiphertextWithoutThrowing() {
        assertNull(MessageCrypto.tryDecodeGroupMasterKeyBase64("e2e:not-valid-base64:payload"))
        assertNull(MessageCrypto.tryDecodeGroupMasterKeyBase64("e2e_grp:abc"))
        assertNull(MessageCrypto.tryDecodeGroupMasterKeyBase64(""))
        assertNull(MessageCrypto.tryDecodeGroupMasterKeyBase64(Base64.encode(ByteArray(24))))
        val master = ByteArray(MessageCrypto.GROUP_MASTER_KEY_BYTES) { it.toByte() }
        val ok = MessageCrypto.encodeGroupMasterKeyBase64(master)
        assertTrue(master.contentEquals(MessageCrypto.tryDecodeGroupMasterKeyBase64(ok)!!))
    }

    // ── Media (binary) round-trip ───────────────────────────────────────────

    @Test
    fun mediaBytes_roundTripWithConnectionKeys() {
        val keys = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds)
        val original = ByteArray(128) { it.toByte() }
        val cipher = MessageCrypto.encryptMediaBytes(original, keys)
        val plain = MessageCrypto.decryptMediaBytes(cipher, keys)
        assertTrue(original.contentEquals(plain))
    }

    @Test
    fun mediaBytes_roundTripWithGroupMasterKey() {
        val master = ByteArray(MessageCrypto.GROUP_MASTER_KEY_BYTES) { (it * 11 + 2).toByte() }
        val original = ByteArray(256) { ((it * 5) % 251).toByte() }
        val cipher = MessageCrypto.encryptMediaBytes(original, master)
        val plain = MessageCrypto.decryptMediaBytes(cipher, master)
        assertTrue(original.contentEquals(plain))
    }

    @Test
    fun mediaBytes_decryptRejectsTamperedHmac() {
        val keys = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds)
        val cipher = MessageCrypto.encryptMediaBytes(byteArrayOf(1, 2, 3, 4), keys)
        // Flip a byte inside the ciphertext region.
        cipher[cipher.size - 1] = (cipher[cipher.size - 1].toInt() xor 0x01).toByte()
        try {
            MessageCrypto.decryptMediaBytes(cipher, keys)
            error("expected MessageEncryptionException")
        } catch (_: MessageCrypto.MessageEncryptionException) {
            // expected
        }
    }

    @Test
    fun mediaBytes_decryptRejectsTooShortBlob() {
        val keys = MessageCrypto.deriveKeysForConnection(fixedConnectionId, fixedUserIds)
        try {
            MessageCrypto.decryptMediaBytes(ByteArray(16), keys)
            error("expected MessageEncryptionException")
        } catch (_: MessageCrypto.MessageEncryptionException) {
            // expected
        }
    }

    // ── Wire-format classification ──────────────────────────────────────────

    @Test
    fun classifiers_returnExpectedBooleans() {
        assertFalse(MessageCrypto.isEncrypted("plain text"))
        assertFalse(MessageCrypto.isGroupMessageEncrypted("plain text"))
        assertFalse(MessageCrypto.isAnyE2eeWireContent("plain text"))

        assertTrue(MessageCrypto.isEncrypted("e2e:AAAAAAAA"))
        assertTrue(MessageCrypto.isAnyE2eeWireContent("e2e:AAAAAAAA"))

        assertTrue(MessageCrypto.isGroupMessageEncrypted("e2e_grp:AAAAAAAA"))
        assertTrue(MessageCrypto.isAnyE2eeWireContent("e2e_grp:AAAAAAAA"))
        assertFalse(MessageCrypto.isEncrypted("e2e_grp:AAAAAAAA"))
    }

    @Test
    fun v2_roundTripAuthenticatesMetadataAndUsesFreshNonce() {
        val key = ByteArray(MessageCryptoV2.EPOCH_KEY_BYTES) { (it + 9).toByte() }
        val first = MessageCryptoV2.encryptMessage(v2Metadata, key, "secret")
        val second = MessageCryptoV2.encryptMessage(v2Metadata, key, "secret")
        assertTrue(first.startsWith(MessageCryptoV2.PREFIX))
        assertNotEquals(first, second)
        assertEquals("secret", MessageCryptoV2.decryptMessage(v2Metadata, key, first))
        assertFailsWith<IllegalStateException> {
            MessageCryptoV2.decryptMessage(v2Metadata.copy(epoch = 8), key, first)
        }
    }

    @Test
    fun v2_tamperingAndReplayFailClosed() {
        val key = ByteArray(MessageCryptoV2.EPOCH_KEY_BYTES) { it.toByte() }
        val wire = MessageCryptoV2.encryptMessage(v2Metadata, key, "secret")
        val body = wire.removePrefix(MessageCryptoV2.PREFIX)
        val changed = if (body[0] == 'A') 'B' else 'A'
        val tampered = MessageCryptoV2.PREFIX + changed + body.substring(1)
        assertFailsWith<IllegalStateException> { MessageCryptoV2.decryptMessage(v2Metadata, key, tampered) }

        val guard = MessageCryptoV2.ReplayGuard()
        assertEquals("secret", MessageCryptoV2.decryptMessage(v2Metadata, key, wire, guard))
        assertEquals("secret", MessageCryptoV2.decryptMessage(v2Metadata, key, wire, guard))
        val nonceGuard = MessageCryptoV2.ReplayGuard()
        nonceGuard.reserve("same-nonce", "first-envelope")
        assertFailsWith<IllegalStateException> { nonceGuard.reserve("same-nonce", "different-envelope") }
    }

    @Test
    fun v2_parserRejectsWrongNonceLengthAndExtraFields() {
        val key = ByteArray(MessageCryptoV2.EPOCH_KEY_BYTES) { 1 }
        val wire = MessageCryptoV2.encryptMessage(v2Metadata, key, "x")
        val parsedJson = Base64.decode(wire.removePrefix(MessageCryptoV2.PREFIX)).decodeToString()
        val nonceShort = parsedJson.replace(Regex("\"nonce\":\"[^\"]+\""), "\"nonce\":\"AAAA\"")
        val shortWire = MessageCryptoV2.PREFIX + Base64.encode(nonceShort.encodeToByteArray())
        assertFailsWith<IllegalArgumentException> { MessageCryptoV2.parseE2eeV2Envelope(shortWire) }
        val extraWire = MessageCryptoV2.PREFIX + Base64.encode((parsedJson.dropLast(1) + ",\"extra\":1}").encodeToByteArray())
        assertFailsWith<IllegalArgumentException> { MessageCryptoV2.parseE2eeV2Envelope(extraWire) }
    }

    @Test
    fun v2_legacyCompatibilityLeavesV1Opaque() {
        val legacy = "e2e:legacy-v1-wire"
        assertEquals(legacy, MessageCryptoV2.decryptContentCompatible(legacy, v2Metadata, ByteArray(32)))
        assertFalse(MessageCryptoV2.isEncrypted(legacy))
    }

    @Test
    fun v2_rejectsInvalidKeyAndIdentifierBoundaries() {
        assertFailsWith<IllegalArgumentException> {
            MessageCryptoV2.encryptMessage(v2Metadata, ByteArray(31), "x")
        }
        assertFailsWith<IllegalArgumentException> {
            MessageCryptoV2.encryptMessage(v2Metadata.copy(chatId = "bad id"), ByteArray(32), "x")
        }
        assertFailsWith<IllegalArgumentException> {
            MessageCryptoV2.encryptMessage(v2Metadata.copy(epoch = 0), ByteArray(32), "x")
        }
    }

    @Test
    fun v2_mediaRoundTripUsesUploadedBytesDigestAndAuthorizationEnvelope() {
        val key = ByteArray(MessageCryptoV2.EPOCH_KEY_BYTES) { (it * 3 + 1).toByte() }
        val plaintext = "opaque media payload".encodeToByteArray()
        val encrypted = MessageCryptoV2.encryptMedia(v2MediaMetadata, key, plaintext)

        assertEquals(MessageCryptoV2.NONCE_BYTES + plaintext.size + MessageCryptoV2.GCM_TAG_BYTES, encrypted.uploadedBytes.size)
        assertEquals(
            plaintext.toList(),
            MessageCryptoV2
                .decryptMedia(
                    v2MediaMetadata.copy(mediaCiphertextSha256 = encrypted.mediaCiphertextSha256),
                    key,
                    encrypted.uploadedBytes,
                ).toList(),
        )
        val authorization =
            MessageCryptoV2.parseE2eeV2Envelope(encrypted.authorizationEnvelope)
                as MessageCryptoV2.MediaAuthorizationEnvelope
        assertEquals(encrypted.mediaCiphertextSha256, authorization.mediaCiphertextSha256)
        MessageCryptoV2.verifyMediaAuthorization(
            v2MediaMetadata.copy(mediaCiphertextSha256 = encrypted.mediaCiphertextSha256),
            key,
            encrypted.authorizationEnvelope,
        )
    }

    @Test
    fun v2_mediaTamperingWrongDigestAndShortPayloadFailClosed() {
        val key = ByteArray(MessageCryptoV2.EPOCH_KEY_BYTES) { it.toByte() }
        val encrypted = MessageCryptoV2.encryptMedia(v2MediaMetadata, key, byteArrayOf(1, 2, 3, 4))
        val tampered = encrypted.uploadedBytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFailsWith<IllegalStateException> {
            MessageCryptoV2.decryptMedia(
                v2MediaMetadata.copy(mediaCiphertextSha256 = encrypted.mediaCiphertextSha256),
                key,
                tampered,
            )
        }
        assertFailsWith<IllegalStateException> {
            MessageCryptoV2.decryptMedia(
                v2MediaMetadata.copy(mediaCiphertextSha256 = Base64.encode(ByteArray(32) { 1 })),
                key,
                encrypted.uploadedBytes,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MessageCryptoV2.decryptMedia(v2MediaMetadata.copy(mediaCiphertextSha256 = "bad"), key, encrypted.uploadedBytes)
        }
        assertFailsWith<IllegalArgumentException> {
            MessageCryptoV2.decryptMedia(
                v2MediaMetadata.copy(mediaCiphertextSha256 = encrypted.mediaCiphertextSha256),
                key,
                ByteArray(MessageCryptoV2.NONCE_BYTES + MessageCryptoV2.GCM_TAG_BYTES - 1),
            )
        }
    }

    @Test
    fun pureAesGcm_matchesNistEmptyPlaintextVector() {
        val result = PureKotlinAesGcm.encrypt(ByteArray(32), ByteArray(12), ByteArray(0), ByteArray(0))
        assertEquals("530f8afbc74536b9a963b4f1c4cb738b", result.toHex())
        assertTrue(PureKotlinAesGcm.decrypt(ByteArray(32), ByteArray(12), ByteArray(0), result).isEmpty())

        val block = PureKotlinAesGcm.encrypt(ByteArray(32), ByteArray(12), ByteArray(0), ByteArray(16))
        assertEquals("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919", block.toHex())
        assertTrue(PureKotlinAesGcm.decrypt(ByteArray(32), ByteArray(12), ByteArray(0), block).contentEquals(ByteArray(16)))
    }

    @Test
    fun pureX25519_matchesRfc7748AlicePublicKeyVector() {
        val alicePrivate = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hexToBytes()
        assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", X25519.publicKey(alicePrivate).toHex())
    }

    private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte ->
            val value = byte.toInt() and 0xff
            "0123456789abcdef"[value ushr 4].toString() + "0123456789abcdef"[value and 0x0f]
        }
}
