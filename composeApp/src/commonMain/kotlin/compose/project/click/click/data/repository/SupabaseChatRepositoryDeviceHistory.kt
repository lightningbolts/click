package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.crypto.MessageCryptoV2 // pragma: allowlist secret
import compose.project.click.click.data.api.HistoryBackfillItemDto // pragma: allowlist secret
import compose.project.click.click.data.api.KeyTransferBody // pragma: allowlist secret
import compose.project.click.click.data.api.KeyTransferEnvelopeDto // pragma: allowlist secret
import compose.project.click.click.data.api.getHistoryBackfill // pragma: allowlist secret
import compose.project.click.click.data.api.postKeyTransfer // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.CancellationException

/*
 * Email-approved history for the user's newer devices. Signing in registers this device right away
 * (so click-web can email the account a magic link when it's an additional device). Every older
 * device then, on launch and on foreground, wraps the historical epoch keys its approved newer
 * devices lack and uploads them via /api/chat/key-transfer. The server only relays envelopes and
 * enforces own-devices + email approval (`approve_chat_key_transfer`).
 */

/** Registers this device's E2EE v2 identity now (a repeat registration answers 409 and is fine). */
internal suspend fun SupabaseChatRepository.registerThisDeviceImpl(): Boolean {
    val token = ensureFreshJwtForChat() ?: return false
    val identity = runCatching { MessageCryptoV2.loadOrCreateDeviceIdentity() }.getOrNull() ?: return false
    val result = apiClient.registerE2eeV2Device(identity.info.deviceId, identity.info.publicKeySpkiBase64, token)
    return result.isSuccess || result.exceptionOrNull()?.message?.contains("already registered", ignoreCase = true) == true
}

/**
 * Wraps [item]'s epochs for its recipient with [epochKeys] (the keys this device holds). Pure apart
 * from the random ephemeral key inside [wrap]; epochs this device doesn't hold are skipped.
 */
internal fun buildHistoryEnvelopes(
    item: HistoryBackfillItemDto,
    senderDeviceId: String,
    epochKeys: Map<Int, ByteArray>,
    wrap: (MessageCryptoV2.EpochKeyWrapMetadata, ByteArray, String) -> String = { metadata, key, recipientKey ->
        MessageCryptoV2.wrapEpochKey(metadata, key, recipientKey)
    },
): List<KeyTransferEnvelopeDto> =
    item.epochs.distinct().sorted().mapNotNull { epoch ->
        val key = epochKeys[epoch] ?: return@mapNotNull null
        val metadata =
            MessageCryptoV2.EpochKeyWrapMetadata(
                chatId = item.chatId,
                epoch = epoch,
                senderDeviceId = senderDeviceId,
                recipientDeviceId = item.recipientDeviceId,
            )
        KeyTransferEnvelopeDto(
            epoch = epoch,
            recipientDeviceId = item.recipientDeviceId,
            senderDeviceId = senderDeviceId,
            envelope = wrap(metadata, key, item.recipientPublicKey),
        )
    }

/**
 * Shares this device's historical chat keys with the account's email-approved newer devices.
 * Returns how many chats were shared. Safe to call often: nothing to do costs one GET.
 */
internal suspend fun SupabaseChatRepository.shareHistoryWithApprovedDevicesImpl(viewerUserId: String): Int {
    if (viewerUserId.isBlank()) return 0
    val token = ensureFreshJwtForChat() ?: return 0
    val identity = runCatching { MessageCryptoV2.loadOrCreateDeviceIdentity() }.getOrNull() ?: return 0
    val myDeviceId = identity.info.deviceId
    val items =
        apiClient.getHistoryBackfill(myDeviceId, token).getOrElse { e ->
            println("ChatRepository: history backfill lookup failed: ${e.redactedRestMessage()}")
            return 0
        }
    var shared = 0
    for (item in items) {
        if (item.recipientDeviceId == myDeviceId || item.epochs.isEmpty()) continue
        try {
            val session = resolveE2eeV2ChatCrypto(item.chatId, viewerUserId, forceRefresh = true) ?: continue
            val envelopes = buildHistoryEnvelopes(item, myDeviceId, session.epochKeys)
            if (envelopes.isEmpty()) continue
            apiClient
                .postKeyTransfer(
                    KeyTransferBody(
                        chatId = item.chatId,
                        approvingDeviceId = myDeviceId,
                        recipientDeviceId = item.recipientDeviceId,
                        historicalEnvelopes = envelopes,
                    ),
                    token,
                ).onSuccess { shared++ }
                .onFailure { e ->
                    println("ChatRepository: history share failed chatId=${item.chatId}: ${e.redactedRestMessage()}")
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ChatRepository: history share skipped chatId=${item.chatId}: ${e.redactedRestMessage()}")
        }
    }
    if (shared > 0) println("ChatRepository: shared history for $shared chat(s) with approved devices")
    return shared
}
