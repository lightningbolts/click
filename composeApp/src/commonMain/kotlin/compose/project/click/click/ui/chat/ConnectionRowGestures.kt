package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret

/**
 * Tap opens chat; hold opens the unified action sheet.
 *
 * Uses [combinedClickable] (not [androidx.compose.foundation.gestures.detectTapGestures]) so the
 * parent LazyColumn keeps ownership of drag + fling. PointerInput tap detectors on every row
 * routinely cancel coast velocity when the finger lifts.
 */
internal fun Modifier.connectionRowPressGestures(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = combinedClickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
    onLongClick = {
        PlatformHapticsPolicy.heavyImpact()
        onLongPress()
    },
)
