package compose.project.click.click.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS currently uses the standard [collectAsState]. Compose Multiplatform on iOS still evolves
 * its lifecycle integration; matching Android's lifecycle semantics here risks pausing uploads
 * during common iOS app-state transitions (e.g. scene-phase inactivity) where we still want
 * the latest state to be visible when the app becomes active again.
 *
 * When Compose Multiplatform's iOS lifecycle owner stabilizes, flip this actual to delegate to
 * `collectAsStateWithLifecycle()` to mirror Android.
 */
@Composable
actual fun <T> StateFlow<T>.collectAsStateLifecycleAware(): State<T> =
    collectAsState()
