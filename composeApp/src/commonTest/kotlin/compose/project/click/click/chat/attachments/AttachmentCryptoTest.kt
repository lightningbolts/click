package compose.project.click.click.chat.attachments

import compose.project.click.click.crypto.MessageCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentCryptoTest {

    private val plaintext: ByteArray = "%PDF-1.7 hello world".encodeToByteArray()

    @Test
    fun generateFileMasterKey_is32BytesAndRandom() {
        val a = AttachmentCrypto.generateFileMasterKey()
        val b = AttachmentCrypto.generateFileMasterKey()
        assertEquals(32, a.size)
        assertEquals(32, b.size)
        assertTrue(!a.contentEquals(b), "two freshly generated keys must not collide")
    }

    @Test
    fun encryptThenDecrypt_roundTripsBytes() {
        val key = AttachmentCrypto.generateFileMasterKey()
        val encrypted = AttachmentCrypto.encryptFileBytes(plaintext, key)
        val decrypted = AttachmentCrypto.decryptFileBytes(encrypted, key)
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun encrypt_producesFreshIvEachCall() {
        val key = AttachmentCrypto.generateFileMasterKey()
        val a = AttachmentCrypto.encryptFileBytes(plaintext, key)
        val b = AttachmentCrypto.encryptFileBytes(plaintext, key)
        assertTrue(!a.contentEquals(b), "ciphertexts with the same key/plaintext must differ via random IV")
    }

    @Test
    fun decrypt_withWrongKey_fails() {
        val key = AttachmentCrypto.generateFileMasterKey()
        val wrong = AttachmentCrypto.generateFileMasterKey()
        val encrypted = AttachmentCrypto.encryptFileBytes(plaintext, key)
        assertFailsWith<MessageCrypto.MessageEncryptionException> {
            AttachmentCrypto.decryptFileBytes(encrypted, wrong)
        }
    }

    @Test
    fun decrypt_withTamperedByte_fails() {
        val key = AttachmentCrypto.generateFileMasterKey()
        val encrypted = AttachmentCrypto.encryptFileBytes(plaintext, key)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0x01).toByte()
        assertFailsWith<MessageCrypto.MessageEncryptionException> {
            AttachmentCrypto.decryptFileBytes(encrypted, key)
        }
    }

    @Test
    fun key_wrongLength_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            AttachmentCrypto.encryptFileBytes(plaintext, ByteArray(16))
        }
        assertFailsWith<IllegalArgumentException> {
            AttachmentCrypto.decryptFileBytes(ByteArray(128), ByteArray(31))
        }
    }

    @Test
    fun base64_keyRoundTrip() {
        val raw = AttachmentCrypto.generateFileMasterKey()
        val b64 = AttachmentCrypto.encodeFileMasterKeyBase64(raw)
        val decoded = AttachmentCrypto.decodeFileMasterKeyBase64(b64)
        assertTrue(raw.contentEquals(decoded))
    }

    @Test
    fun sha256Base64_isDeterministic() {
        val a = AttachmentCrypto.sha256Base64(plaintext)
        val b = AttachmentCrypto.sha256Base64(plaintext)
        assertEquals(a, b)
    }

    @Test
    fun envelope_encodeAndDecode_roundTrip() {
        val env = AttachmentCrypto.Envelope(
            name = "spec.pdf",
            mime = "application/pdf",
            size = 12345L,
            path = "chat-xyz/me/abc.enc",
            key = "a".repeat(44),
            sha256 = "b".repeat(44),
        )
        val wire = AttachmentCrypto.encodeEnvelope(env)
        assertTrue(wire.startsWith(AttachmentCrypto.ENVELOPE_PREFIX))
        val decoded = AttachmentCrypto.tryDecodeEnvelope(wire)
        assertNotNull(decoded)
        assertEquals(env, decoded)
    }

    @Test
    fun v2Descriptor_encodeAndDecode_roundTrip() {
        val descriptor = AttachmentCrypto.V2Descriptor(
            name = "spec.pdf",
            mime = "application/pdf",
            size = 12345L,
            path = "chat-xyz/me/abc.enc",
            mediaCiphertextSha256 = AttachmentCrypto.sha256Base64(plaintext),
        )
        val wire = AttachmentCrypto.encodeV2AttachmentDescriptor(descriptor)
        assertTrue(wire.startsWith(AttachmentCrypto.E2EE_V2_ENVELOPE_PREFIX))
        assertEquals(descriptor, AttachmentCrypto.tryDecodeV2AttachmentDescriptor(wire))
        assertEquals(2, AttachmentCrypto.resolveEnvelope(wire, null)?.v)
    }

    @Test
    fun v2Descriptor_rejectsUnknownFieldsAndUnsafePaths() {
        val digest = AttachmentCrypto.sha256Base64(plaintext)
        val unknown = "ccx:v2:{\"v\":2,\"type\":\"file\",\"name\":\"a\",\"mime\":\"text/plain\",\"size\":1,\"path\":\"a\",\"mediaCiphertextSha256\":\"$digest\",\"extra\":true}"
        val unsafe = "ccx:v2:{\"v\":2,\"type\":\"file\",\"name\":\"a\",\"mime\":\"text/plain\",\"size\":1,\"path\":\"../a\",\"mediaCiphertextSha256\":\"$digest\"}"
        assertNull(AttachmentCrypto.tryDecodeV2AttachmentDescriptor(unknown))
        assertNull(AttachmentCrypto.tryDecodeV2AttachmentDescriptor(unsafe))
    }

    @Test
    fun tryDecode_returnsNullForPlainText() {
        assertNull(AttachmentCrypto.tryDecodeEnvelope("hello, world"))
    }

    @Test
    fun tryDecode_returnsNullForMalformedJson() {
        assertNull(AttachmentCrypto.tryDecodeEnvelope("ccx:v1:{not json"))
    }

    @Test
    fun isAttachmentEnvelope_checksPrefixOnly() {
        assertTrue(AttachmentCrypto.isAttachmentEnvelope("ccx:v1:{}"))
        assertTrue(AttachmentCrypto.isAttachmentEnvelope("ccx:v2:{}"))
        assertTrue(!AttachmentCrypto.isAttachmentEnvelope("hi"))
        assertTrue(!AttachmentCrypto.isAttachmentEnvelope("e2e:xxx"))
    }
}
