package compose.project.click.click.data.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class E2eeV2LifecyclePolicyTest {
    @Test
    fun existingEpoch_keepsSendingWhenParticipantLookupFails() {
        assertFalse(
            E2eeV2LifecyclePolicy.shouldCreateInitialEpoch(
                currentEpoch = 3,
                allParticipantsHaveV2Devices = false,
            ),
        )
        assertTrue(
            E2eeV2LifecyclePolicy.shouldRotateEpoch(
                currentEpoch = 3,
                membershipFingerprintMatches = false,
            ),
        )
    }

    @Test
    fun missingEpoch_waitsUntilEveryParticipantHasADevice() {
        assertFalse(
            E2eeV2LifecyclePolicy.shouldCreateInitialEpoch(
                currentEpoch = null,
                allParticipantsHaveV2Devices = false,
            ),
        )
        assertTrue(
            E2eeV2LifecyclePolicy.shouldCreateInitialEpoch(
                currentEpoch = null,
                allParticipantsHaveV2Devices = true,
            ),
        )
    }

    @Test
    fun existingEpoch_rotatesOnlyWhenMembershipIsFullyKnownAndChanged() {
        assertTrue(
            E2eeV2LifecyclePolicy.shouldRotateEpoch(
                currentEpoch = 1,
                membershipFingerprintMatches = false,
            ),
        )
        assertFalse(
            E2eeV2LifecyclePolicy.shouldRotateEpoch(
                currentEpoch = 1,
                membershipFingerprintMatches = true,
            ),
        )
    }

    @Test
    fun missingUnwrap_rekeysWhenLifecycleIsAllowed() {
        assertTrue(
            E2eeV2LifecyclePolicy.shouldRekeyMissingUnwrap(
                allowLifecycle = true,
                hasCurrentEpochKey = false,
            ),
        )
        assertFalse(
            E2eeV2LifecyclePolicy.shouldRekeyMissingUnwrap(
                allowLifecycle = true,
                hasCurrentEpochKey = true,
            ),
        )
        assertFalse(
            E2eeV2LifecyclePolicy.shouldRekeyMissingUnwrap(
                allowLifecycle = false,
                hasCurrentEpochKey = false,
            ),
        )
    }
}
