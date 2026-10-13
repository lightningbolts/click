package compose.project.click.click.platform

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import kotlin.test.Test
import kotlin.test.assertEquals

/** Sheet 10 §A1: which authenticators each API level allows, and how prompt errors map. */
class OwnerAuthMappingTest {
    @Test
    fun strongBiometricsOrTheScreenLockFromApi30() {
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, ownerAuthenticators(30))
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, ownerAuthenticators(36))
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, ownerAuthenticators(29))
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, ownerAuthenticators(24))
    }

    @Test
    fun cancelsGoBackToAskingAndEverythingElseFailsClosed() {
        assertEquals(OwnerAuthResult.Cancelled, ownerAuthResultForError(BiometricPrompt.ERROR_USER_CANCELED))
        assertEquals(OwnerAuthResult.Cancelled, ownerAuthResultForError(BiometricPrompt.ERROR_CANCELED))
        assertEquals(OwnerAuthResult.Cancelled, ownerAuthResultForError(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertEquals(OwnerAuthResult.Unavailable, ownerAuthResultForError(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(OwnerAuthResult.Unavailable, ownerAuthResultForError(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL))
        assertEquals(OwnerAuthResult.Unavailable, ownerAuthResultForError(BiometricPrompt.ERROR_HW_UNAVAILABLE))
    }
}
