package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.api.ChatMediaUploadJsonBody
import compose.project.click.click.viewmodel.gatesClickDropOriginal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The sender half of a gated drop, against click-web `extractGatedChatDrop` and iOS `sendMedia(gated)`. */
class GatedChatDropsSendTest {
    private val base =
        buildJsonObject {
            put("media_url", "https://preview")
            put("disposable_roll", true)
            put("collaboration_ttl", "2026-10-02T10:00:00Z")
        }

    @Test
    fun v1DropsNameOnlyTheOriginalPath() {
        val meta = gatedClickDropMetadata(base, ClickDropOriginalUpload(path = "chat/c1/u1/original.jpg", v2 = null))
        assertEquals("chat/c1/u1/original.jpg", meta["drop_original_path"]?.jsonPrimitive?.content)
        assertFalse("drop_original" in meta)
        // Everything the legacy drop carried stays.
        assertEquals("https://preview", meta["media_url"]?.jsonPrimitive?.content)
        assertEquals("true", meta["disposable_roll"]?.jsonPrimitive?.content)
    }

    @Test
    fun v2DropsAlsoCarryHowToDecryptTheOriginal() {
        val v2 =
            MessageCryptoV2.MediaMetadata(
                chatId = "c1",
                epoch = 4,
                senderDeviceId = "dev-1",
                clientMessageId = clickDropOriginalClientMessageId("msg-1"),
                mediaCiphertextSha256 = "abc=",
            )
        val meta = gatedClickDropMetadata(base, ClickDropOriginalUpload(path = "p", v2 = v2))
        val original = meta["drop_original"]!!.jsonObject
        assertEquals(4, original["epoch"]?.jsonPrimitive?.int)
        assertEquals("dev-1", original["sender_device_id"]?.jsonPrimitive?.content)
        assertEquals("msg-1.original", original["client_message_id"]?.jsonPrimitive?.content)
        assertEquals("abc=", original["media_ciphertext_sha256"]?.jsonPrimitive?.content)
        // The receive side reads back exactly what the sender wrote.
        val message =
            compose.project.click.click.data.models.Message(
                id = "m1",
                user_id = "u1",
                content = " ",
                timeCreated = 0L,
                messageType = "image",
                metadata = meta,
            )
        assertEquals(v2, message.dropOriginalV2MetadataOrNull("c1"))
    }

    @Test
    fun onlyPhotosGoOutGatedAndOnlyWithTheFlagOn() {
        assertTrue(gatesClickDropOriginal("image/jpeg", flagEnabled = true))
        assertTrue(gatesClickDropOriginal(" IMAGE/PNG ", flagEnabled = true))
        assertFalse(gatesClickDropOriginal("image/jpeg", flagEnabled = false))
        assertFalse(gatesClickDropOriginal("video/mp4", flagEnabled = true))
    }

    @Test
    fun theUploadAsksForPrivateStorageOnlyForOriginals() {
        val json = Json { explicitNulls = false }
        val plain =
            json.parseToJsonElement(
                json.encodeToString(ChatMediaUploadJsonBody(chatId = "c", mimeType = "image/jpeg", fileBase64 = "AA==")),
            ) as JsonObject
        assertFalse("drop_original" in plain)
        val original =
            json.parseToJsonElement(
                json.encodeToString(
                    ChatMediaUploadJsonBody(chatId = "c", mimeType = "image/jpeg", fileBase64 = "AA==", dropOriginal = true),
                ),
            ) as JsonObject
        assertEquals("true", original["drop_original"]?.jsonPrimitive?.content)
    }
}
