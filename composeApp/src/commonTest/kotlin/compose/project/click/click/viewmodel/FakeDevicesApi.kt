package compose.project.click.click.viewmodel

import compose.project.click.click.data.DeviceApi
import compose.project.click.click.data.api.AskForHistoryResponseDto
import compose.project.click.click.data.api.DeviceApprovalChallengeDto
import compose.project.click.click.data.api.DeviceApprovalDecisionBody
import compose.project.click.click.data.api.DeviceHistoryRequestDto
import compose.project.click.click.data.api.DeviceHistoryRequestsDto
import compose.project.click.click.data.api.MyDeviceDto
import kotlinx.coroutines.CompletableDeferred

internal const val APPROVAL_REQUEST_ID = "4f1c2a3b-5d6e-4f70-8a9b-0c1d2e3f4a5b"

/** Answers like the click-web device routes and records, in order, what the view models call. */
internal class FakeDevicesApi(
    var incoming: List<DeviceHistoryRequestDto> =
        listOf(DeviceHistoryRequestDto(APPROVAL_REQUEST_ID, "pending", deviceLabel = "Chrome on Mac")),
    var historyResult: (() -> Result<DeviceHistoryRequestsDto>)? = null,
    var devices: () -> Result<List<MyDeviceDto>> = { Result.success(emptyList()) },
    var removeResult: Result<Unit> = Result.success(Unit),
) : DeviceApi {
    val calls = mutableListOf<String>()
    val decided = mutableListOf<DeviceApprovalDecisionBody>()
    val removed = mutableListOf<String>()
    var devicesCalls = 0

    /** When set, `DELETE /api/chat/devices` waits for it. */
    var removeGate: CompletableDeferred<Unit>? = null

    override suspend fun historyRequests(deviceId: String): Result<DeviceHistoryRequestsDto> {
        calls += "history"
        return historyResult?.invoke() ?: Result.success(DeviceHistoryRequestsDto(incoming = incoming))
    }

    override suspend fun askForHistory(
        deviceId: String,
        reopen: Boolean,
    ) = Result.success(AskForHistoryResponseDto())

    override suspend fun historyRequest(requestId: String) = Result.success<DeviceHistoryRequestDto?>(null)

    override suspend fun challenge(
        requestId: String,
        approvingDeviceId: String,
    ): Result<DeviceApprovalChallengeDto> {
        calls += "challenge"
        return Result.success(
            DeviceApprovalChallengeDto(
                challengeId = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
                envelope = "e2e2.wrapped",
                chatId = "approval:$requestId",
                epoch = 1,
                senderDeviceId = "click-device-approval",
            ),
        )
    }

    override suspend fun decide(
        requestId: String,
        body: DeviceApprovalDecisionBody,
    ): Result<DeviceHistoryRequestDto?> {
        calls += "decide"
        decided += body
        return Result.success(DeviceHistoryRequestDto(requestId, if (body.decision == "approve") "approved" else "denied"))
    }

    override suspend fun emailLink(requestId: String) = Result.success(true)

    override suspend fun myDevices(): Result<List<MyDeviceDto>> {
        devicesCalls++
        return devices()
    }

    override suspend fun removeDevice(deviceId: String): Result<Unit> {
        removed += deviceId
        removeGate?.await()
        return removeResult
    }
}
