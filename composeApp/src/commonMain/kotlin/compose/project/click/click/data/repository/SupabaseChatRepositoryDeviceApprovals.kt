package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.crypto.MessageCryptoV2 // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalChallengeDto // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalDecisionBody // pragma: allowlist secret
import compose.project.click.click.data.api.askForHistory // pragma: allowlist secret
import compose.project.click.click.data.api.emailDeviceApprovalLink // pragma: allowlist secret
import compose.project.click.click.data.api.getDeviceApprovalChallenge // pragma: allowlist secret
import compose.project.click.click.data.api.getDeviceApprovals // pragma: allowlist secret
import compose.project.click.click.data.api.postDeviceApprovalDecision // pragma: allowlist secret
import compose.project.click.click.data.devices.DeviceApprovals // pragma: allowlist secret
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * Approving a new device of this account from one already in use (iOS
 * `ChatRepository.decideDeviceApproval`). The server proves this device holds its identity key by
 * wrapping a one-time challenge to it like an epoch key; this device unwraps it and sends the bytes
 * back with its decision. Who may approve what is decided on the server, never here.
 */

/** The two calls an approval makes, so the flow can be tested without a network. */
internal interface DeviceApprovalGateway {
    suspend fun challenge(
        requestId: String,
        approvingDeviceId: String,
    ): Result<DeviceApprovalChallengeDto>

    suspend fun decide(
        requestId: String,
        body: DeviceApprovalDecisionBody,
    ): Result<Unit>
}

/** Unwraps [challenge] with this device's identity key; the base64 bytes are the proof. */
@OptIn(ExperimentalEncodingApi::class)
internal fun deviceApprovalProof(
    challenge: DeviceApprovalChallengeDto,
    myDeviceId: String,
    unwrap: (MessageCryptoV2.EpochKeyWrapMetadata, String) -> ByteArray,
): String {
    val metadata =
        MessageCryptoV2.EpochKeyWrapMetadata(
            chatId = challenge.chatId,
            epoch = challenge.epoch,
            senderDeviceId = challenge.senderDeviceId,
            recipientDeviceId = myDeviceId,
        )
    val bytes = unwrap(metadata, challenge.envelope)
    return try {
        Base64.encode(bytes)
    } finally {
        bytes.fill(0)
    }
}

/**
 * Fetches the challenge while [authorize] (the owner's biometric or screen lock) runs, so its
 * round trip overlaps the system prompt. If [authorize] throws, the challenge request is cancelled
 * and nothing is sent.
 */
internal suspend fun runDeviceApprovalDecision(
    requestId: String,
    approve: Boolean,
    myDeviceId: String,
    gateway: DeviceApprovalGateway,
    unwrap: (MessageCryptoV2.EpochKeyWrapMetadata, String) -> ByteArray,
    authorize: (suspend () -> Unit)?,
) {
    coroutineScope {
        val pending = async { gateway.challenge(requestId, myDeviceId) }
        authorize?.invoke()
        val challenge = pending.await().getOrThrow()
        val proof = deviceApprovalProof(challenge, myDeviceId, unwrap)
        gateway
            .decide(
                requestId,
                DeviceApprovalDecisionBody(
                    decision = if (approve) "approve" else "deny",
                    approvingDeviceId = myDeviceId,
                    challengeId = challenge.challengeId,
                    proof = proof,
                ),
            ).getOrThrow()
    }
}

internal suspend fun SupabaseChatRepository.deviceApprovalsImpl(): DeviceApprovals? {
    val token = ensureFreshJwtForChat() ?: return null
    val identity = runCatching { MessageCryptoV2.loadOrCreateDeviceIdentity() }.getOrNull() ?: return null
    return apiClient.getDeviceApprovals(identity.info.deviceId, token).getOrNull()?.let(DeviceApprovals::from)
}

/** Asks the account's other devices for history (idempotent); [reopen] asks again after a denial. */
internal suspend fun SupabaseChatRepository.askForHistoryImpl(reopen: Boolean): DeviceApprovals? {
    registerThisDeviceImpl()
    val token = ensureFreshJwtForChat() ?: return null
    val identity = runCatching { MessageCryptoV2.loadOrCreateDeviceIdentity() }.getOrNull() ?: return null
    return apiClient.askForHistory(identity.info.deviceId, reopen, token).getOrThrow().let(DeviceApprovals::from)
}

internal suspend fun SupabaseChatRepository.emailDeviceApprovalLinkImpl(requestId: String): Boolean {
    val token = ensureFreshJwtForChat() ?: error("Not signed in")
    return apiClient.emailDeviceApprovalLink(requestId, token).getOrThrow()
}

/** Throws on failure; a cancelled [authorize] propagates its own exception and sends nothing. */
internal suspend fun SupabaseChatRepository.decideDeviceApprovalImpl(
    requestId: String,
    approve: Boolean,
    authorize: (suspend () -> Unit)?,
) {
    registerThisDeviceImpl()
    val token = ensureFreshJwtForChat() ?: error("Not signed in")
    val identity = MessageCryptoV2.loadOrCreateDeviceIdentity()
    val api = apiClient
    val gateway =
        object : DeviceApprovalGateway {
            override suspend fun challenge(
                requestId: String,
                approvingDeviceId: String,
            ) = api.getDeviceApprovalChallenge(requestId, approvingDeviceId, token)

            override suspend fun decide(
                requestId: String,
                body: DeviceApprovalDecisionBody,
            ) = api.postDeviceApprovalDecision(requestId, body, token)
        }
    runDeviceApprovalDecision(
        requestId = requestId,
        approve = approve,
        myDeviceId = identity.info.deviceId,
        gateway = gateway,
        unwrap = { metadata, envelope -> MessageCryptoV2.unwrapEpochKey(metadata, identity, envelope) },
        authorize = authorize,
    )
}
