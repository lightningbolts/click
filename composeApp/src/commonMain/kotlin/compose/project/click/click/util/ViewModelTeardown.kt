package compose.project.click.click.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Perform best-effort asynchronous cleanup from a synchronous lifecycle hook
 * (e.g. [androidx.lifecycle.ViewModel.onCleared]) where the owning
 * `viewModelScope` has already been cancelled.
 *
 * Design constraints (NASA P10 applicability):
 * - Bounded wall-time: every invocation times out after [timeoutMs].
 * - No unbounded recursion or retries.
 * - All thrown exceptions are swallowed intentionally — callers have no way to
 *   surface failures from a destructor path.
 *
 * Runs [block] on [Dispatchers.Default] inside [NonCancellable] so a newly
 * constructed coroutine can actually progress past the cancelled parent scope.
 * Callers MUST keep [block] short (single unsubscribe/detach); anything long
 * running belongs on an app-scoped coroutine instead.
 */
internal fun teardownBlocking(
    timeoutMs: Long = DEFAULT_TEARDOWN_TIMEOUT_MS,
    block: suspend () -> Unit,
) {
    runCatching {
        runBlocking(Dispatchers.Default + NonCancellable) {
            withTimeoutOrNull(timeoutMs) { block() }
        }
    }
}

private const val DEFAULT_TEARDOWN_TIMEOUT_MS: Long = 500L
