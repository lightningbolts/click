package compose.project.click.click.data.repository

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
}
