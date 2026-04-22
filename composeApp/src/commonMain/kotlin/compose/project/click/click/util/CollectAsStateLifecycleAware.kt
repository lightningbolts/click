package compose.project.click.click.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-aware state collection for KMP.
 *
 * On Android this pauses upstream collection when the host Lifecycle drops below STARTED,
 * avoiding work that repaints hidden UI or keeps Supabase realtime subscriptions churning
 * mapped state when the app is backgrounded. On iOS Compose the Lifecycle owner is supplied
 * by Compose Multiplatform and the JetBrains `lifecycle-runtime-compose` artifact, which
 * collapses to `collectAsState` semantics when no real Lifecycle is active.
 *
 * Prefer this helper over `collectAsState()` for ViewModel state exposed to the screen tree,
 * especially inside tabs/navigation destinations that can be off-screen while still composed.
 *
 * This is the `expect` surface so we can vary behavior per target if we ever need to
 * (e.g. force `collectAsState` on iOS to sidestep platform lifecycle quirks).
 */
@Composable
expect fun <T> StateFlow<T>.collectAsStateLifecycleAware(): State<T>
