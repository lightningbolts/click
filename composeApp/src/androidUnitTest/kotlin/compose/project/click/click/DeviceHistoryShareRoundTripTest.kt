package compose.project.click.click

import compose.project.click.click.crypto.DeviceIdentityStorage
import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.api.HistoryBackfillItemDto
import compose.project.click.click.data.repository.buildHistoryEnvelopes
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertTrue

/** X25519 needs android.util.Base64, so this round trip runs under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceHistoryShareRoundTripTest {
    private val item =
        HistoryBackfillItemDto(
            requestId = "req-1",
            recipientDeviceId = "new-device",
            recipientPublicKey = "recipient-spki",
            chatId = "chat-1",
            epochs = listOf(1),
        )

    @Test
    fun wrappedHistoryKeyUnwrapsOnTheRecipientDevice() {
        val recipient =
            DeviceIdentityStorage.generateEphemeral()
        val epochKey = MessageCryptoV2.generateEpochKey()
        val envelope =
            buildHistoryEnvelopes(
                item = item.copy(recipientDeviceId = recipient.info.deviceId, recipientPublicKey = recipient.info.publicKeySpkiBase64),
                senderDeviceId = "old-device",
                epochKeys = mapOf(1 to epochKey),
            ).single()

        val unwrapped =
            MessageCryptoV2.unwrapEpochKey(
                metadata = MessageCryptoV2.EpochKeyWrapMetadata("chat-1", 1, "old-device", recipient.info.deviceId),
                recipientIdentity = recipient,
                envelope = envelope.envelope,
            )
        assertTrue(unwrapped.contentEquals(epochKey))
    }
}
