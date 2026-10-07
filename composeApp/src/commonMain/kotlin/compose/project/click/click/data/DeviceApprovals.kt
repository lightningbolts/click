package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.chat.deviceApprovalWrapMetadata // pragma: allowlist secret
import compose.project.click.click.chat.encodeDeviceApprovalProof // pragma: allowlist secret
import compose.project.click.click.crypto.MessageCryptoV2 // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.AskForHistoryResponseDto // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalChallengeDto // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalDecisionBody // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApproverDto // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceHistoryRequestDto // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceHistoryRequestsDto // pragma: allowlist secret
import compose.project.click.click.data.api.MyDeviceDto // pragma: allowlist secret
import compose.project.click.click.data.api.askForHistoryImpl // pragma: allowlist secret
import compose.project.click.click.data.api.emailDeviceApprovalLinkImpl // pragma: allowlist secret
import compose.project.click.click.data.api.getDeviceHistoryRequestImpl // pragma: allowlist secret
import compose.project.click.click.data.api.getDeviceHistoryRequestsImpl // pragma: allowlist secret
import compose.project.click.click.data.api.getMyDevicesImpl // pragma: allowlist secret
import compose.project.click.click.data.api.postDeviceApprovalChallengeImpl // pragma: allowlist secret
import compose.project.click.click.data.api.postDeviceApprovalDecisionImpl // pragma: allowlist secret
import compose.project.click.click.data.api.removeChatDeviceImpl // pragma: allowlist secret
import compose.project.click.click.navigation.AppRoute // pragma: allowlist secret
import compose.project.click.click.navigation.AppRouter // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The device routes the approval flow and Settings › Devices use (a fake in tests). */
interface DeviceApi {
    suspend fun historyRequests(deviceId: String): Result<DeviceHistoryRequestsDto>

    suspend fun askForHistory(
        deviceId: String,
        reopen: Boolean,
    ): Result<AskForHistoryResponseDto>

    suspend fun historyRequest(requestId: String): Result<DeviceHistoryRequestDto?>

    suspend fun challenge(
        requestId: String,
        approvingDeviceId: String,
    ): Result<DeviceApprovalChallengeDto>

    suspend fun decide(
        requestId: String,
        body: DeviceApprovalDecisionBody,
    ): Result<DeviceHistoryRequestDto?>

    suspend fun emailLink(requestId: String): Result<Boolean>

    suspend fun myDevices(): Result<List<MyDeviceDto>>

    suspend fun removeDevice(deviceId: String): Result<Unit>
}

class ClickWebDeviceApi(
    private val api: ApiClient = ApiClient(),
) : DeviceApi {
    override suspend fun historyRequests(deviceId: String) = api.getDeviceHistoryRequestsImpl(deviceId)

    override suspend fun askForHistory(
        deviceId: String,
        reopen: Boolean,
    ) = api.askForHistoryImpl(deviceId, reopen)

    override suspend fun historyRequest(requestId: String) = api.getDeviceHistoryRequestImpl(requestId)

    override suspend fun challenge(
        requestId: String,
        approvingDeviceId: String,
    ) = api.postDeviceApprovalChallengeImpl(requestId, approvingDeviceId)

    override suspend fun decide(
        requestId: String,
        body: DeviceApprovalDecisionBody,
    ) = api.postDeviceApprovalDecisionImpl(requestId, body)

    override suspend fun emailLink(requestId: String) = api.emailDeviceApprovalLinkImpl(requestId)

    override suspend fun myDevices() = api.getMyDevicesImpl()

    override suspend fun removeDevice(deviceId: String) = api.removeChatDeviceImpl(deviceId)
}

/** This device's public id, or null when its identity can't be loaded. */
internal fun thisChatDeviceId(): String? = runCatching { MessageCryptoV2.loadOrCreateDeviceIdentity().info.deviceId }.getOrNull()

/** Unwraps a challenge with this device's identity key (the epoch-key unwrap). */
internal fun unwrapWithThisDevice(
    metadata: MessageCryptoV2.EpochKeyWrapMetadata,
    envelope: String,
): ByteArray = MessageCryptoV2.unwrapEpochKey(metadata, MessageCryptoV2.loadOrCreateDeviceIdentity(), envelope)

/**
 * Approves or denies [requestId] from [myDeviceId]: fetch a challenge, unwrap its nonce with
 * [unwrap] (proving this device holds its key) and send it back with the decision.
 */
internal suspend fun decideDeviceApproval(
    api: DeviceApi,
    requestId: String,
    approve: Boolean,
    myDeviceId: String,
    unwrap: (MessageCryptoV2.EpochKeyWrapMetadata, String) -> ByteArray = ::unwrapWithThisDevice,
): Result<DeviceHistoryRequestDto?> {
    val challenge = api.challenge(requestId, myDeviceId).getOrElse { return Result.failure(it) }
    val proof =
        runCatching {
            encodeDeviceApprovalProof(unwrap(deviceApprovalWrapMetadata(requestId, myDeviceId, challenge), challenge.envelope))
        }.getOrElse { return Result.failure(it) }
    return api.decide(
        requestId,
        DeviceApprovalDecisionBody(
            decision = if (approve) "approve" else "deny",
            approvingDeviceId = myDeviceId,
            challengeId = challenge.challengeId,
            proof = proof,
        ),
    )
}

data class DeviceApprovalsState(
    /** The account's newer devices this one can approve. */
    val incoming: List<DeviceHistoryRequestDto> = emptyList(),
    /** This device's own request to read past messages (the Clicks banner while it waits). */
    val own: DeviceHistoryRequestDto? = null,
    val approvers: List<DeviceApproverDto> = emptyList(),
    /** "Email me a link" was tapped here (the server's `email_sent` lags a refresh). */
    val emailedHere: Boolean = false,
)

/**
 * Device approvals for the signed-in account (iOS `AppEnvironment.refreshDeviceApprovals`).
 * Refreshed after each device registration (launch, sign-in, foreground); a newly seen incoming
 * request opens "Approve new sign-in?" once per launch.
 */
object DeviceApprovals {
    private val _state = MutableStateFlow(DeviceApprovalsState())
    val state: StateFlow<DeviceApprovalsState> = _state.asStateFlow()

    internal var api: DeviceApi = ClickWebDeviceApi()
    private val presented = mutableSetOf<String>()

    suspend fun refresh() {
        val deviceId = thisChatDeviceId() ?: return
        api
            .historyRequests(deviceId)
            .onSuccess { dto ->
                _state.update {
                    it.copy(
                        incoming = dto.incoming.toList(),
                        own = dto.own?.copy(),
                        approvers = dto.approvers.toList(),
                        emailedHere = it.emailedHere && dto.own?.id == it.own?.id,
                    )
                }
                dto.incoming.firstOrNull { it.isPending && !it.expired && presented.add(it.id) }?.let {
                    AppRouter.open(AppRoute.DeviceApproval(it.id))
                }
            }.onFailure { e -> println("DeviceApprovals: refresh failed: ${e.redactedRestMessage()}") }
    }

    /** "Ask again" after a denial or expiry. */
    suspend fun askAgain(): Result<Unit> {
        val deviceId = thisChatDeviceId() ?: return Result.failure(IllegalStateException("No device identity"))
        return api.askForHistory(deviceId, reopen = true).map { response ->
            _state.update { it.copy(own = response.own ?: it.own, emailedHere = false) }
        }
    }

    /** "Email me a link instead" from the waiting device. */
    suspend fun emailLink(): Result<Unit> {
        val id = _state.value.own?.id ?: return Result.failure(IllegalStateException("No request"))
        return api.emailLink(id).map { _state.update { it.copy(emailedHere = true) } }
    }

    /** Label and time of [requestId] when it's in the incoming list. */
    fun incoming(requestId: String): DeviceHistoryRequestDto? = _state.value.incoming.firstOrNull { it.id == requestId }

    fun decided(requestId: String) {
        presented.add(requestId)
        _state.update { state -> state.copy(incoming = state.incoming.filterNot { it.id == requestId }) }
    }

    /** Sign-out: nothing carries over to the next account. */
    fun clear() {
        presented.clear()
        _state.value = DeviceApprovalsState()
    }
}
