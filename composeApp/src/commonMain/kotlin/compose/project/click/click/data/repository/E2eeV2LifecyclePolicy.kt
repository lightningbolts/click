package compose.project.click.click.data.repository

import compose.project.click.click.data.api.ClickWebChatDeviceDto
import compose.project.click.click.data.api.ClickWebRequestException
import kotlinx.coroutines.CancellationException

/**
 * Epoch create/rotate rules for send.
 *
 * Initial creation and rotation require a complete participant/device view. A failed
 * participant lookup must fail closed rather than rotating keys for a partial membership.
 */
internal object E2eeV2LifecyclePolicy {
    fun shouldCreateInitialEpoch(
        currentEpoch: Int?,
        allParticipantsHaveV2Devices: Boolean,
    ): Boolean = currentEpoch == null && allParticipantsHaveV2Devices

    fun shouldRotateEpoch(
        currentEpoch: Int?,
        allParticipantsHaveV2Devices: Boolean,
        membershipFingerprintMatches: Boolean,
    ): Boolean = currentEpoch != null && allParticipantsHaveV2Devices && !membershipFingerprintMatches

    fun shouldRekeyMissingUnwrap(
        allowLifecycle: Boolean,
        hasCurrentEpochKey: Boolean,
    ): Boolean = allowLifecycle && !hasCurrentEpochKey

    /**
     * One envelope per distinct logical device id. One phone signed into several member accounts
     * lists the same device (same key) once per account; the epoch RPC rejects duplicate
     * `recipient_device_id` (23505) and stores the single wrap for every matching row.
     */
    fun distinctEpochRecipients(devices: List<ClickWebChatDeviceDto>): List<ClickWebChatDeviceDto> = devices.distinctBy { it.deviceId }

    /** 409 from POST /api/chat/epochs or /api/hub/epochs: a peer device rotated first. */
    fun isEpochConflict(error: Throwable): Boolean = (error as? ClickWebRequestException)?.statusCode == 409

    /**
     * Membership-mismatch rotation (mirrors iOS `resolveV2Session`):
     * - 409: a peer rotated first, so the re-fetched state is authoritative.
     * - Any other failure on the send path is logged and the send continues on [current]; the
     *   server's write gate still rejects it if the epoch no longer covers every active device.
     * - A membership change ([strict]) must rotate, so it rethrows.
     */
    suspend fun <S> rotateOrContinue(
        strict: Boolean,
        current: S,
        rotate: suspend () -> S,
        refetch: suspend () -> S,
        onSkipped: (Throwable) -> Unit = {},
    ): S =
        try {
            rotate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when {
                isEpochConflict(e) -> refetch()
                strict -> throw e
                else -> {
                    onSkipped(e)
                    current
                }
            }
        }
}
