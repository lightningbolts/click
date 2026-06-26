package compose.project.click.click.util

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
}

private class IOException(message: String) : Exception(message)
