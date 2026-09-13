package compose.project.click.click.data.repository // pragma: allowlist secret

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Runs remote sign-out, then always attempts both local auth cleanup operations. */
internal suspend fun signOutWithCleanup(
    remoteSignOut: suspend () -> Unit,
    clearSdkSession: suspend () -> Unit,
    clearTokenStorage: suspend () -> Unit,
): Result<Unit> {
    var remoteFailure: Throwable? = null
    var cancellation: CancellationException? = null
    try {
        remoteSignOut()
    } catch (e: CancellationException) {
        cancellation = e
    } catch (e: Exception) {
        remoteFailure = e
    }

    var sdkCleanupFailure: Throwable? = null
    var storageCleanupFailure: Throwable? = null
    withContext(NonCancellable) {
        try {
            clearSdkSession()
        } catch (e: CancellationException) {
            cancellation = cancellation ?: e
        } catch (e: Exception) {
            sdkCleanupFailure = e
        }
        try {
            clearTokenStorage()
        } catch (e: CancellationException) {
            cancellation = cancellation ?: e
        } catch (e: Exception) {
            storageCleanupFailure = e
        }
    }

    cancellation?.let { throw it }
    currentCoroutineContext().ensureActive()
    val failure = remoteFailure ?: sdkCleanupFailure ?: storageCleanupFailure
    return failure?.let { Result.failure(it) } ?: Result.success(Unit)
}
