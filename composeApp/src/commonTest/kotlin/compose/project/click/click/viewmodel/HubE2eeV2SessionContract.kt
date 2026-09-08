package compose.project.click.click.viewmodel

import compose.project.click.click.crypto.DeviceIdentityStorage
import compose.project.click.click.crypto.MessageCryptoV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class HubE2eeV2SessionContract {
    @Test
    fun cacheReplacementDuringUploadDoesNotChangeSendKey() =
        runTest {
            val identity = DeviceIdentityStorage.generateEphemeral()
            val recipientKey = MessageCryptoV2.generateEpochKey()
            val session = HubE2eeV2Session(1, mapOf(1 to recipientKey.copyOf()), identity.info.deviceId, identity, "members")
            val cache = session.copyWithIndependentKeys()
            val uploadStarted = CompletableDeferred<Unit>()
            val uploadComplete = CompletableDeferred<Unit>()
            val metadata = MessageCryptoV2.MessageMetadata("hub-1", 1, identity.info.deviceId, MessageCryptoV2.generateClientMessageId())
            try {
                val send =
                    async {
                        session.useForSend { owned ->
                            val mediaMetadata =
                                MessageCryptoV2.MediaMetadata(
                                    metadata.chatId,
                                    1,
                                    metadata.senderDeviceId,
                                    metadata.clientMessageId,
                                    "",
                                )
                            val media = MessageCryptoV2.encryptMedia(mediaMetadata, owned!!.epochKey, byteArrayOf(1, 2, 3))
                            uploadStarted.complete(Unit)
                            uploadComplete.await()
                            assertContentEquals(recipientKey, owned.epochKey)
                            assertContentEquals(
                                byteArrayOf(1, 2, 3),
                                MessageCryptoV2.decryptMedia(
                                    mediaMetadata.copy(mediaCiphertextSha256 = media.mediaCiphertextSha256),
                                    recipientKey,
                                    media.uploadedBytes,
                                ),
                            )
                            MessageCryptoV2.encryptMessage(metadata, owned.epochKey, "Photo")
                        }
                    }
                uploadStarted.await()
                cache.clearKeys()
                uploadComplete.complete(Unit)
                assertEquals("Photo", MessageCryptoV2.decryptMessage(metadata, recipientKey, send.await()))
                assertTrue(session.epochKey.all { it == 0.toByte() })
            } finally {
                session.clearKeys()
                cache.clearKeys()
                recipientKey.fill(0)
                DeviceIdentityStorage.destroyEphemeral(identity)
            }
        }

    @Test
    fun failureAndCancellationEraseOperationKeys() =
        runTest {
            val identity = DeviceIdentityStorage.generateEphemeral()
            try {
                for (error in listOf(IllegalStateException("upload failed"), CancellationException("cancelled"))) {
                    val session =
                        HubE2eeV2Session(1, mapOf(1 to MessageCryptoV2.generateEpochKey()), identity.info.deviceId, identity, "members")
                    assertFailsWith<Exception> { session.useForSend { throw error } }
                    assertTrue(session.epochKey.all { it == 0.toByte() })
                }
            } finally {
                DeviceIdentityStorage.destroyEphemeral(identity)
            }
        }
}
