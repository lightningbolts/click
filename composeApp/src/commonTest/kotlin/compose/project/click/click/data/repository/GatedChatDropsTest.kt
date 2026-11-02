package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.api.DropDevelopResultDto
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.disposableRollRevealAtIso
import compose.project.click.click.data.models.isDisposableRollLocked
import compose.project.click.click.data.models.isGatedDrop
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GatedChatDropsTest {
    private fun message(
        metadataJson: String,
        type: String = "image",
        id: String = "m1",
    ) = Message(
        id = id,
        user_id = "sender",
        content = "",
        timeCreated = 0,
        messageType = type,
        metadata = Json.parseToJsonElement(metadataJson),
    )

    // Stored metadata of an iOS gated drop after click-web strips drop_original_path
    // (lib/server/drops/chatDrops.ts) and stamps reveal_at.
    private val gatedV2 =
        """
        {"disposable_roll":true,"drop_gated":true,
         "collaboration_ttl":"2026-11-02T10:00:00Z","reveal_at":"2026-11-02T09:00:00Z",
         "media_url":"https://x/preview.jpg","is_encrypted_media":true,
         "drop_original":{"epoch":3,"sender_device_id":"ios-device-1",
           "client_message_id":"cmid-1.original","media_ciphertext_sha256":"abc123"}}
        """.trimIndent()

    @AfterTest
    fun reset() = GatedDropOriginals.clear()

    @Test
    fun recognizesGatedDrop() {
        assertTrue(message(gatedV2).isGatedDrop())
        assertFalse(message("""{"disposable_roll":true,"media_url":"u"}""").isGatedDrop(), "legacy drop")
        assertFalse(message("""{"drop_gated":true}""").isGatedDrop(), "not a Click Drop")
        assertFalse(message(gatedV2, type = "audio").isGatedDrop(), "only photos are gated")
        assertFalse(message("""{"disposable_roll":true,"drop_gated":{"x":1}}""").isGatedDrop(), "malformed flag")
    }

    @Test
    fun revealAtWinsOverCollaborationTtl() {
        assertEquals("2026-11-02T09:00:00Z", message(gatedV2).disposableRollRevealAtIso())
        assertEquals(
            "2026-11-02T10:00:00Z",
            message("""{"disposable_roll":true,"collaboration_ttl":"2026-11-02T10:00:00Z"}""").disposableRollRevealAtIso(),
        )
    }

    @Test
    fun lockFollowsRevealAt() {
        val m = message(gatedV2)
        assertTrue(m.isDisposableRollLocked(Instant.parse("2026-11-02T08:59:59Z")))
        assertFalse(m.isDisposableRollLocked(Instant.parse("2026-11-02T09:00:00Z")))
    }

    @Test
    fun parsesDropOriginalV2Metadata() {
        val meta = assertNotNull(message(gatedV2).dropOriginalV2MetadataOrNull("chat-1"))
        assertEquals("chat-1", meta.chatId)
        assertEquals(3, meta.epoch)
        assertEquals("ios-device-1", meta.senderDeviceId)
        assertEquals("cmid-1.original", meta.clientMessageId)
        assertEquals("abc123", meta.mediaCiphertextSha256)
    }

    @Test
    fun v1GatedDropHasNoV2Metadata() {
        val v1 = message("""{"disposable_roll":true,"drop_gated":true,"reveal_at":"2026-11-02T09:00:00Z"}""")
        assertTrue(v1.isGatedDrop())
        assertNull(v1.dropOriginalV2MetadataOrNull("chat-1"))
        assertNull(message("""{"drop_original":{"epoch":"x"}}""").dropOriginalV2MetadataOrNull("c"))
    }

    @Test
    fun previewIsHeldBackUntilOriginalIsOnScreen() {
        val m = message(gatedV2)
        assertTrue(m.isGatedDropAwaitingOriginal())
        GatedDropOriginals.markLoaded("m1")
        assertFalse(m.isGatedDropAwaitingOriginal())
        assertFalse(message("""{"disposable_roll":true}""", id = "m2").isGatedDropAwaitingOriginal())
    }

    @Test
    fun originalVaultKeyMatchesIos() {
        assertEquals("m1-original", gatedDropOriginalVaultKey("m1"))
    }

    @Test
    fun decodesDevelopResponse() {
        val rows =
            Json { ignoreUnknownKeys = true }.decodeFromString(
                ListSerializer(DropDevelopResultDto.serializer()),
                """
                [{"kind":"chat","id":"m1","status":"developed","developed_at":"2026-11-02T09:01:00Z","url":"https://signed"},
                 {"kind":"chat","id":"m2","status":"pending","reveal_at":"2026-11-02T09:00:00Z"},
                 {"kind":"chat","id":"m3","status":"not_found"}]
                """.trimIndent(),
            )
        assertEquals("https://signed", rows[0].url)
        assertEquals("pending", rows[1].status)
        assertNull(rows[2].url)
    }
}
