package compose.project.click.click.collaboration

import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.clickDropRevealAt
import compose.project.click.click.data.models.isDisposableRollLocked
import compose.project.click.click.data.models.isGatedDrop
import compose.project.click.click.data.repository.ClickDropOriginalUpload
import compose.project.click.click.data.repository.dropOriginalV2MetadataOrNull
import compose.project.click.click.data.repository.gatedClickDropMetadata
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * Golden shapes for gated Click Drops, keyed exactly as iOS writes them
 * (`ChatRepository.sendMedia(gated)`, `clickDropMetadata`) and as click-web stores them
 * (`lib/server/drops/chatDrops.ts`: `drop_original_path` → `drop_gated: true`, plus `reveal_at`).
 */
object GatedDropFixtures {
    /** What a v2 sender posts in `metadata` (iOS keys; values from iOS `parsesGatedDrop`). */
    const val V2_SENT =
        """
        {
          "media_chat_id": "chat-1",
          "media_epoch": 3,
          "media_sender_device_id": "dev-1",
          "media_client_message_id": "cm-1",
          "media_ciphertext_sha256": "prev=",
          "media_authorization_envelope": "env",
          "media_path": "chat-1/me/preview.jpg",
          "media_url": "https://signed.example/p",
          "original_mime_type": "image/jpeg",
          "is_encrypted_media": true,
          "disposable_roll": true,
          "collaboration_ttl": "2026-10-01T10:00:00Z",
          "encounter_id": "enc-1",
          "drop_original_path": "chat/chat-1/me/original.jpg",
          "drop_original": {
            "epoch": 3,
            "sender_device_id": "dev-1",
            "client_message_id": "cm-1.original",
            "media_ciphertext_sha256": "abc="
          }
        }
        """

    /** What a recipient reads back: the server swapped the path for `drop_gated` and stamped `reveal_at`. */
    const val V2_STORED =
        """
        {
          "media_url": "https://signed.example/p",
          "disposable_roll": true,
          "drop_gated": true,
          "reveal_at": "2026-10-01T10:00:00Z",
          "drop_original": {"epoch": 3, "sender_device_id": "dev-1", "client_message_id": "cm-1.original",
                            "media_ciphertext_sha256": "abc="}
        }
        """

    /** A v1 (legacy keys) gated drop: no `drop_original`. */
    const val V1_STORED =
        """
        {"media_url": "https://signed.example/p", "disposable_roll": true, "drop_gated": true,
         "collaboration_ttl": "2026-10-01T10:00:00Z", "is_encrypted_media": true}
        """
}

class GatedDropMetadataTest {
    private fun obj(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    private fun drop(metadata: String) =
        Message(id = "m1", user_id = "peer", content = " ", timeCreated = 0L, messageType = ChatMessageType.IMAGE, metadata = obj(metadata))

    @Test
    fun sendAddsTheOriginalExactlyAsIosDoes() {
        val golden = obj(GatedDropFixtures.V2_SENT)
        val base = JsonObject(golden - "drop_original_path" - "drop_original")
        val built =
            gatedClickDropMetadata(
                base,
                ClickDropOriginalUpload(
                    path = "chat/chat-1/me/original.jpg",
                    v2 = MessageCryptoV2.MediaMetadata("chat-1", 3, "dev-1", "cm-1.original", "abc="),
                ),
            )
        assertEquals(golden, built)
    }

    @Test
    fun v1SendNamesOnlyThePath() {
        val built = gatedClickDropMetadata(JsonObject(emptyMap()), ClickDropOriginalUpload("chat/c/u/o.jpg", v2 = null))
        assertEquals(obj("""{"drop_original_path":"chat/c/u/o.jpg"}"""), built)
    }

    @Test
    fun receivedGatedDropParsesItsMarkerRevealAndOriginalFields() {
        val message = drop(GatedDropFixtures.V2_STORED)
        assertTrue(message.isGatedDrop())
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), message.clickDropRevealAt())
        assertEquals(
            MessageCryptoV2.MediaMetadata("chat-1", 3, "dev-1", "cm-1.original", "abc="),
            dropOriginalV2MetadataOrNull(message.metadata, "chat-1"),
        )
    }

    @Test
    fun v1GatedDropUsesLegacyKeys() {
        val message = drop(GatedDropFixtures.V1_STORED)
        assertTrue(message.isGatedDrop())
        assertNull(dropOriginalV2MetadataOrNull(message.metadata, "chat-1"))
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), message.clickDropRevealAt())
    }

    @Test
    fun theGatedPreviewIsNeverShownAsThePhotoOutsideTheDevelopFlow() {
        val message = drop(GatedDropFixtures.V2_STORED)
        val longAfter = Instant.parse("2026-10-01T10:00:00Z") + 30.days
        assertTrue(message.isDisposableRollLocked(longAfter))
        // A legacy drop unlocks by itself at reveal.
        val legacy = drop("""{"disposable_roll":true,"collaboration_ttl":"2026-10-01T10:00:00Z"}""")
        assertFalse(legacy.isGatedDrop())
        assertFalse(legacy.isDisposableRollLocked(longAfter))
        assertTrue(legacy.isDisposableRollLocked(Instant.parse("2026-10-01T09:00:00Z")))
    }
}
