package compose.project.click.click.data.repository

/**
 * Epoch create/rotate rules for send.
 *
 * Rotation follows discovered v2 devices (same as hub): a failed PostgREST participant
 * lookup must not skip a fingerprint change, or this device can be left unable to unwrap.
 */
internal object E2eeV2LifecyclePolicy {
    fun shouldCreateInitialEpoch(
        currentEpoch: Int?,
        allParticipantsHaveV2Devices: Boolean,
    ): Boolean = currentEpoch == null && allParticipantsHaveV2Devices

    fun shouldRotateEpoch(
        currentEpoch: Int?,
        membershipFingerprintMatches: Boolean,
    ): Boolean = currentEpoch != null && !membershipFingerprintMatches

    fun shouldRekeyMissingUnwrap(
        allowLifecycle: Boolean,
        hasCurrentEpochKey: Boolean,
    ): Boolean = allowLifecycle && !hasCurrentEpochKey
}
