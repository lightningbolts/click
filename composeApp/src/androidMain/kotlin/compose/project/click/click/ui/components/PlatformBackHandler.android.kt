package compose.project.click.click.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Android predictive-back owner. Gesture progress is available to callers that have a persistent
 * underlay; cancellation never mutates navigation state. Button back completes the flow directly.
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBackProgress: (Float) -> Unit,
    onBackCancelled: () -> Unit,
    onBack: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event -> onBackProgress(event.progress.coerceIn(0f, 1f)) }
            onBackProgress(1f)
            onBack()
        } catch (cancelled: CancellationException) {
            onBackProgress(0f)
            onBackCancelled()
            throw cancelled
        }
    }
}
