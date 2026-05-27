package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import kotlinx.coroutines.withTimeoutOrNull

/** Between system default (~400–500ms) and an overly quick trigger. */
private const val ConnectionRowLongPressTimeoutMs = 380L

/**
 * Tap opens chat; hold opens the unified action sheet. Haptic strength unchanged.
 */
internal fun Modifier.connectionRowPressGestures(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = pointerInput(onClick, onLongPress) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val releasedEarly = withTimeoutOrNull(ConnectionRowLongPressTimeoutMs) {
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
        }
        if (releasedEarly == null) {
            PlatformHapticsPolicy.heavyImpact()
            onLongPress()
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
        } else if (!releasedEarly.isConsumed) {
            onClick()
        }
    }
}
