package compose.project.click.click.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatApiClientHubTest {
    @Test
    fun urlOnlyMediaResponse_decodesWithoutPath() {
        val response =
            Json.decodeFromString<ChatMediaUploadUrlResponse>(
                """{"url":"https://example.test/signed-audio"}""",
            )

        assertEquals("https://example.test/signed-audio", response.trimmedUrlOrNull())
    }
    @Test
    fun eventHubAccessDeniedMessage_preservesMarkerAndHumanMessage() {
        val message = eventHubAccessDeniedMessage()

        assertTrue(message.contains(EVENT_HUB_ACCESS_DENIED_MARKER))
        assertTrue(message.contains(HUB_ACCESS_DENIED_USER_MESSAGE))
    }

    @Test
    fun v2MediaUploadBodiesCarryEnvelopeDigestAndBindingMetadata() {
        val request = E2eeV2MediaUploadRequest(
            envelope = "e2e2:authorization",
            mediaCiphertextSha256 = "digest",
            epoch = 9,
            senderDeviceId = "device-1",
            clientMessageId = "client-1",
        )
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val media = json.encodeToString(
            ChatMediaUploadJsonBody(
                chatId = "chat-1",
                mimeType = "image/jpeg",
                fileBase64 = "ciphertext",
                e2eeV2Envelope = request.envelope,
                mediaCiphertextSha256 = request.mediaCiphertextSha256,
                epoch = request.epoch,
                senderDeviceId = request.senderDeviceId,
                clientMessageId = request.clientMessageId,
            ),
        )
        val attachment = json.encodeToString(
            ChatAttachmentUploadJsonBody(
                chatId = "chat-1",
                mimeType = "application/pdf",
                fileName = "file.pdf",
                fileBase64 = "ciphertext",
                e2eeV2Envelope = request.envelope,
                mediaCiphertextSha256 = request.mediaCiphertextSha256,
                epoch = request.epoch,
                senderDeviceId = request.senderDeviceId,
                clientMessageId = request.clientMessageId,
            ),
        )
        for (body in listOf(media, attachment)) {
            assertTrue(body.contains("\"e2ee_v2_envelope\":\"e2e2:authorization\""))
            assertTrue(body.contains("\"media_ciphertext_sha256\":\"digest\""))
            assertTrue(body.contains("\"epoch\":9"))
            assertTrue(body.contains("\"sender_device_id\":\"device-1\""))
            assertTrue(body.contains("\"client_message_id\":\"client-1\""))
        }
    }
}
