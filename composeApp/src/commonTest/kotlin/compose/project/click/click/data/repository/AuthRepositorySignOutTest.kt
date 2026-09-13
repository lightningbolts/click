package compose.project.click.click.data.repository // pragma: allowlist secret

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthRepositorySignOutTest {
    @Test
    fun signOutWithCleanup_clearsSdkAndStorageAfterRemoteSuccess() =
        runTest {
            val calls = mutableListOf<String>()

            val result =
                signOutWithCleanup(
                    remoteSignOut = { calls += "remote" },
                    clearSdkSession = { calls += "sdk" },
                    clearTokenStorage = { calls += "storage" },
                )

            assertTrue(result.isSuccess)
            assertEquals(listOf("remote", "sdk", "storage"), calls)
        }

    @Test
    fun signOutWithCleanup_preservesRemoteFailureAndStillClearsBothStores() =
        runTest {
            val remoteFailure = IllegalStateException("offline")
            val calls = mutableListOf<String>()

            val result =
                signOutWithCleanup(
                    remoteSignOut = { throw remoteFailure },
                    clearSdkSession = { calls += "sdk" },
                    clearTokenStorage = { calls += "storage" },
                )

            assertEquals(remoteFailure, result.exceptionOrNull())
            assertEquals(listOf("sdk", "storage"), calls)
        }

    @Test
    fun signOutWithCleanup_cleansBothStoresBeforeRethrowingCancellation() =
        runTest {
            val cancellation = CancellationException("caller cancelled")
            val calls = mutableListOf<String>()

            assertFailsWith<CancellationException> {
                signOutWithCleanup(
                    remoteSignOut = { throw cancellation },
                    clearSdkSession = { calls += "sdk" },
                    clearTokenStorage = { calls += "storage" },
                )
            }

            assertEquals(listOf("sdk", "storage"), calls)
        }

    @Test
    fun signOutWithCleanup_attemptsStorageWhenSdkCleanupFails() =
        runTest {
            val sdkFailure = IllegalStateException("sdk cleanup failed")
            val calls = mutableListOf<String>()

            val result =
                signOutWithCleanup(
                    remoteSignOut = { calls += "remote" },
                    clearSdkSession = {
                        calls += "sdk"
                        throw sdkFailure
                    },
                    clearTokenStorage = { calls += "storage" },
                )

            assertEquals(sdkFailure, result.exceptionOrNull())
            assertEquals(listOf("remote", "sdk", "storage"), calls)
        }
}
