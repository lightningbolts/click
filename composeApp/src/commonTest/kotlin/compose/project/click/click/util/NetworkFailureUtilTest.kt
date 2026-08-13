package compose.project.click.click.util // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkFailureUtilTest {
    @Test
    fun isOfflineNetworkFailure_matchesIOExceptionClassName() {
        assertTrue(RuntimeException("socket closed", IOException("network")).isOfflineNetworkFailure())
    }

    @Test
    fun isOfflineNetworkFailure_matchesOfflineMessage() {
        assertTrue(IllegalStateException("You are offline").isOfflineNetworkFailure())
    }

    @Test
    fun isOfflineNetworkFailure_rejectsUnrelatedErrors() {
        assertFalse(IllegalArgumentException("invalid user id").isOfflineNetworkFailure())
    }

    @Test
    fun isHardAuthFailure_matchesInvalidRefresh() {
        assertTrue(Exception("Invalid Refresh Token").isHardAuthFailure())
        assertTrue(Exception("Refresh Token Not Found").isHardAuthFailure())
        // Soft access-token expiry is refreshable — not a hard failure.
        assertFalse(Exception("JWT expired").isHardAuthFailure())
    }

    @Test
    fun isHardAuthFailure_doesNotTreatAuthRestExceptionJwtExpiredAsHard() {
        assertFalse(AuthRestException("JWT expired").isHardAuthFailure())
        assertFalse(AuthApiException("access token expired").isHardAuthFailure())
        assertTrue(AuthRestException("Refresh Token Not Found").isHardAuthFailure())
    }

    @Test
    fun isHardAuthFailure_rejectsNetworkErrors() {
        assertFalse(IOException("network unreachable").isHardAuthFailure())
        assertFalse(IllegalStateException("You are offline").isHardAuthFailure())
    }
}

private class IOException(
    message: String,
) : Exception(message)

/** Mirrors GoTrue exception simple names without depending on the SDK in unit tests. */
private class AuthRestException(
    message: String,
) : Exception(message)

private class AuthApiException(
    message: String,
) : Exception(message)
