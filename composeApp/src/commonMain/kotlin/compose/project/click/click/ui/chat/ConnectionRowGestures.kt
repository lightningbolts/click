package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret

/**
 * Tap opens chat; hold opens the unified action sheet.
 * Uses [detectTapGestures] so vertical list scroll cancels the long-press (avoids accidental sheets).
 */
internal fun Modifier.connectionRowPressGestures(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = pointerInput(onClick, onLongPress) {
    detectTapGestures(
        onTap = { onClick() },
        onLongPress = {
            PlatformHapticsPolicy.heavyImpact()
            onLongPress()
        },
    )
}
