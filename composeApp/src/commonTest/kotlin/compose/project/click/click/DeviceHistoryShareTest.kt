package compose.project.click.click

import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.api.HistoryBackfillItemDto
import compose.project.click.click.data.repository.buildHistoryEnvelopes
import compose.project.click.click.data.repository.missingV2Epochs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceHistoryShareTest {
    private val item =
        HistoryBackfillItemDto(
            requestId = "req-1",
            recipientDeviceId = "new-device",
            recipientPublicKey = "recipient-spki",
            chatId = "chat-1",
            epochs = listOf(2, 1, 2, 5),
        )

    @Test
    fun wrapsOnlyHeldEpochsForTheRecipientInOrder() {
        val wrapped = mutableListOf<MessageCryptoV2.EpochKeyWrapMetadata>()
        val envelopes =
            buildHistoryEnvelopes(
                item = item,
                senderDeviceId = "old-device",
                epochKeys = mapOf(1 to ByteArray(32) { 1 }, 2 to ByteArray(32) { 2 }),
            ) { metadata, _, recipientKey ->
                assertEquals("recipient-spki", recipientKey)
                wrapped += metadata
                "e2e2:epoch-${metadata.epoch}"
            }

        assertEquals(listOf(1, 2), envelopes.map { it.epoch })
        assertTrue(envelopes.all { it.recipientDeviceId == "new-device" && it.senderDeviceId == "old-device" })
        assertEquals(listOf("e2e2:epoch-1", "e2e2:epoch-2"), envelopes.map { it.envelope })
        assertEquals(
            MessageCryptoV2.EpochKeyWrapMetadata("chat-1", 1, "old-device", "new-device"),
            wrapped.first(),
        )
    }

    @Test
    fun missingEpochsListsOnlyUnheldV2Epochs() {
        fun wire(epoch: Int) =
            MessageCryptoV2.encryptMessage(
                metadata = MessageCryptoV2.MessageMetadata("chat-1", epoch, "device-1", MessageCryptoV2.generateClientMessageId()),
                epochKey = MessageCryptoV2.generateEpochKey(),
                plaintext = "hi",
            )
        val contents = listOf(wire(1), wire(2), wire(3), "plain text", "e2e:legacy")
        assertEquals(setOf(1, 2), missingV2Epochs(contents, held = setOf(3)))
        assertEquals(emptySet(), missingV2Epochs(contents, held = setOf(1, 2, 3)))
    }
}
