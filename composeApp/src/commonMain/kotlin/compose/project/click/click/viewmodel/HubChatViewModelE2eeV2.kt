package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.crypto.MessageCryptoV2 // pragma: allowlist secret
import compose.project.click.click.crypto.PlatformCrypto // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebChatDeviceDto // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebHubEpochWriteEnvelope // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Client-held hub epoch keys. The server only stores opaque key envelopes. */
internal data class HubE2eeV2Session(
    val epoch: Int,
    val epochKeys: Map<Int, ByteArray>,
    val senderDeviceId: String,
    val identity: compose.project.click.click.crypto.DeviceIdentity,
    val membershipFingerprint: String,
    val replayGuard: MessageCryptoV2.ReplayGuard = MessageCryptoV2.ReplayGuard(),
) {
    val epochKey: ByteArray
        get() = epochKeys[epoch] ?: error("Current hub E2EE v2 key is unavailable")

    fun keyForEpoch(value: Int): ByteArray? = epochKeys[value]
}

internal const val HUB_E2EE_V2_UNAVAILABLE_MESSAGE = "Encrypted hub message unavailable"

/** Resolve, initialize, or rotate the hub epoch without ever sending a legacy write after upgrade. */
internal suspend fun HubChatViewModel.ensureHubE2eeV2Session(
    participantUserIds: Set<String>,
): HubE2eeV2Session? {
    val token = tokenStorage.requireFreshHubJwt()
    val identity = runCatching { MessageCryptoV2.loadOrCreateDeviceIdentity() }
        .getOrElse { throw IllegalStateException("E2EE v2 device identity is unavailable") }
    // Registration is idempotent from the caller's perspective: an existing identity returns 409.
    chatApi.registerE2eeV2Device(identity.info.deviceId, identity.info.publicKeySpkiBase64, token)
    var devices = chatApi.discoverHubE2eeV2Devices(hubId, token).getOrElse { throw it }
    val own = devices.firstOrNull { it.deviceId == identity.info.deviceId }
        ?: throw IllegalStateException("The current E2EE v2 device is not registered in this hub")
    var state = chatApi.getHubE2eeV2State(hubId, token, identity.info.deviceId).getOrElse { throw it }

    if (state.currentEpoch == null) {
        val allParticipantsHaveV2 =
            participantUserIds.isNotEmpty() &&
                participantUserIds.all { userId -> devices.any { device -> device.userId == userId } }
        if (!allParticipantsHaveV2) return null
        state = createHubE2eeV2EpochWithFreshKey(
            identity = identity,
            devices = devices,
            epoch = 1,
            membershipFingerprint = membershipFingerprintForHubDevices(devices),
            authToken = token,
        )
    } else {
        // The service RPC independently verifies that every current participant has a v2 device.
        // That keeps privacy-preserving guest-list mode safe while still rotating when a device
        // is added or revoked and the participant ids are intentionally hidden from this client.
        val fingerprint = membershipFingerprintForHubDevices(devices)
        if (state.membershipFingerprint != fingerprint) {
            state = createHubE2eeV2EpochWithFreshKey(
                identity = identity,
                devices = devices,
                epoch = state.currentEpoch + 1,
                membershipFingerprint = fingerprint,
                authToken = token,
            )
            devices = chatApi.discoverHubE2eeV2Devices(hubId, token).getOrElse { throw it }
        }
    }

    val currentEpoch = state.currentEpoch ?: return null
    if (state.hubId != hubId || state.deviceId != identity.info.deviceId || currentEpoch <= 0) {
        throw IllegalStateException("Hub E2EE v2 epoch response identity mismatch")
    }
    val keys = linkedMapOf<Int, ByteArray>()
    state.envelopes
        .filter { it.hubId == hubId && it.recipientDeviceId == own.id }
        .forEach { envelope ->
            val key = runCatching {
                MessageCryptoV2.unwrapEpochKey(
                    metadata = MessageCryptoV2.EpochKeyWrapMetadata(
                        chatId = hubId,
                        epoch = envelope.epoch,
                        senderDeviceId = envelope.senderDeviceId,
                        recipientDeviceId = identity.info.deviceId,
                    ),
                    recipientIdentity = identity,
                    envelope = envelope.envelope,
                )
            }.getOrNull()
            if (key != null) keys[envelope.epoch] = key
        }
    if (!keys.containsKey(currentEpoch)) {
        throw IllegalStateException("This device is not approved for the current hub E2EE v2 epoch")
    }
    return HubE2eeV2Session(
        epoch = currentEpoch,
        epochKeys = keys,
        senderDeviceId = identity.info.deviceId,
        identity = identity,
        membershipFingerprint = state.membershipFingerprint ?: membershipFingerprintForHubDevices(devices),
    ).also { hubE2eeV2Session = it }
}

private suspend fun HubChatViewModel.createHubE2eeV2EpochWithFreshKey(
    identity: compose.project.click.click.crypto.DeviceIdentity,
    devices: List<ClickWebChatDeviceDto>,
    epoch: Int,
    membershipFingerprint: String,
    authToken: String,
): compose.project.click.click.data.api.ClickWebHubE2eeV2StateEnvelope {
    val epochKey = MessageCryptoV2.generateEpochKey()
    val envelopes = devices.map { recipient ->
        ClickWebHubEpochWriteEnvelope(
            recipientDeviceId = recipient.deviceId,
            envelope = MessageCryptoV2.wrapEpochKey(
                metadata = MessageCryptoV2.EpochKeyWrapMetadata(
                    chatId = hubId,
                    epoch = epoch,
                    senderDeviceId = identity.info.deviceId,
                    recipientDeviceId = recipient.deviceId,
                ),
                epochKey = epochKey,
                recipientPublicKeySpkiBase64 = recipient.identityPublicKey,
            ),
        )
    }
    val write = chatApi.createHubE2eeV2Epoch(
        hubId = hubId,
        epoch = epoch,
        senderDeviceId = identity.info.deviceId,
        membershipFingerprint = membershipFingerprint,
        envelopes = envelopes,
        authToken = authToken,
    )
    if (write.isFailure) {
        val concurrent = chatApi.getHubE2eeV2State(hubId, authToken, identity.info.deviceId).getOrNull()
        if (concurrent?.currentEpoch == epoch && concurrent.membershipFingerprint == membershipFingerprint) return concurrent
        throw write.exceptionOrNull() ?: IllegalStateException("Hub E2EE v2 epoch operation failed")
    }
    return chatApi.getHubE2eeV2State(hubId, authToken, identity.info.deviceId).getOrElse { throw it }
}

private fun membershipFingerprintForHubDevices(devices: List<ClickWebChatDeviceDto>): String {
    val canonical = devices.map { "${it.userId.orEmpty()}:${it.deviceId}" }.sorted().joinToString("|")
    return PlatformCrypto.sha256(canonical.encodeToByteArray()).toHexString()
}

private fun ByteArray.toHexString(): String = buildString(size * 2) {
    for (value in this@toHexString) {
        val byte = value.toInt() and 0xff
        append("0123456789abcdef"[byte ushr 4])
        append("0123456789abcdef"[byte and 0x0f])
    }
}

internal fun HubChatViewModel.decryptHubBody(row: HubMessageRow): String {
    if (!MessageCryptoV2.isEncrypted(row.body)) return row.body
    val session = hubE2eeV2Session ?: return HUB_E2EE_V2_UNAVAILABLE_MESSAGE
    val envelope = runCatching { MessageCryptoV2.parseE2eeV2Envelope(row.body) }
        .getOrNull() as? MessageCryptoV2.MessageEnvelope
        ?: return HUB_E2EE_V2_UNAVAILABLE_MESSAGE
    val key = session.keyForEpoch(envelope.epoch) ?: return HUB_E2EE_V2_UNAVAILABLE_MESSAGE
    return runCatching {
        MessageCryptoV2.decryptMessage(
            metadata = MessageCryptoV2.MessageMetadata(
                chatId = envelope.chatId,
                epoch = envelope.epoch,
                senderDeviceId = envelope.senderDeviceId,
                clientMessageId = envelope.clientMessageId,
            ),
            epochKey = key,
            envelope = row.body,
        )
    }.getOrDefault(HUB_E2EE_V2_UNAVAILABLE_MESSAGE)
}

internal fun Message.hubE2eeV2MediaMetadataOrNull(): MessageCryptoV2.MediaMetadata? {
    val root = metadata as? JsonObject ?: return null
    fun string(vararg names: String): String? = names.asSequence()
        .mapNotNull { root[it]?.jsonPrimitive?.contentOrNull }
        .firstOrNull { it.isNotBlank() }
    fun int(vararg names: String): Int? = names.asSequence()
        .mapNotNull { root[it]?.jsonPrimitive?.intOrNull }
        .firstOrNull()
    val chatId = string("media_chat_id", "mediaChatId") ?: return null
    val epoch = int("media_epoch", "mediaEpoch") ?: return null
    val sender = string("media_sender_device_id", "mediaSenderDeviceId") ?: return null
    val client = string("media_client_message_id", "mediaClientMessageId") ?: return null
    val digest = string("media_ciphertext_sha256", "mediaCiphertextSha256") ?: return null
    return runCatching {
        MessageCryptoV2.MediaMetadata(chatId, epoch, sender, client, digest)
    }.getOrNull()
}
