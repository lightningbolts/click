package compose.project.click.click

import compose.project.click.click.chat.deviceApprovalWrapMetadata
import compose.project.click.click.chat.encodeDeviceApprovalProof
import compose.project.click.click.crypto.DeviceIdentityStorage
import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.api.DeviceApprovalChallengeDto
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * The server wraps the approval nonce exactly like an epoch key (`createApprovalChallenge`); this
 * device unwraps it with its identity key and sends it back as the proof.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceApprovalChallengeRoundTripTest {
    private val requestId = "4f1c2a3b-5d6e-4f70-8a9b-0c1d2e3f4a5b"

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun wrappedChallengeUnwrapsToTheProof() {
        val approver = DeviceIdentityStorage.generateEphemeral()
        val nonce = MessageCryptoV2.generateEpochKey()
        val serverMetadata = MessageCryptoV2.EpochKeyWrapMetadata("approval:$requestId", 1, "click-device-approval", approver.info.deviceId)
        val envelope = MessageCryptoV2.wrapEpochKey(serverMetadata, nonce, approver.info.publicKeySpkiBase64)
        val challenge = DeviceApprovalChallengeDto("c1", envelope, "approval:$requestId", 1, "click-device-approval")

        val metadata = deviceApprovalWrapMetadata(requestId, approver.info.deviceId, challenge)
        val proof = encodeDeviceApprovalProof(MessageCryptoV2.unwrapEpochKey(metadata, approver, challenge.envelope))
        assertEquals(Base64.encode(nonce), proof)

        // Bound to the request: the same envelope never unwraps for another one.
        val other =
            deviceApprovalWrapMetadata("00000000-0000-4000-8000-000000000000", approver.info.deviceId, challenge.copy(chatId = null))
        assertFails { MessageCryptoV2.unwrapEpochKey(other, approver, challenge.envelope) }
    }
}
