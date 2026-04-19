package compose.project.click.click.ui.chat

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal object ChatTimestampStripDefaults {
    /** Width reserved for the time column when fully revealed. */
    val GutterDp = 52.dp
}

/**
 * While the finger moves **left** (right-to-left on screen), reveals timestamps; progress snaps
 * back to 0 when the gesture ends. **Rightward** drag is not consumed so the parent
 * [InteractiveSwipeBackContainer] can still handle edge-style back swipes.
 */
internal fun Modifier.chatTimestampPeekOnSwipeLeft(
    maxRevealPx: Float,
    onPeekProgress: (Float) -> Unit,
): Modifier =
    this.pointerInput(maxRevealPx) {
        var accumulatedLeft = 0f
        detectDragGestures(
            onDragStart = {
                accumulatedLeft = 0f
                onPeekProgress(0f)
            },
            onDrag = { change, dragAmount ->
                if (dragAmount.x < 0f) {
                    accumulatedLeft -= dragAmount.x
                    onPeekProgress((accumulatedLeft / maxRevealPx).coerceIn(0f, 1f))
                    change.consume()
                }
            },
            onDragEnd = {
                accumulatedLeft = 0f
                onPeekProgress(0f)
            },
            onDragCancel = {
                accumulatedLeft = 0f
                onPeekProgress(0f)
            },
        )
    }

@Composable
internal fun rememberTimestampPeekRevealPx(): Float {
    val density = LocalDensity.current
    return remember(density) { with(density) { 120.dp.toPx() } }
}

@Composable
internal fun ChatMessageRowWithTimestampGutter(
    isCallLog: Boolean,
    isSent: Boolean,
    timeCreated: Long,
    stripProgress: Float,
    modifier: Modifier = Modifier,
    bubble: @Composable () -> Unit,
) {
    if (isCallLog) {
        bubble()
        return
    }
    val gutterW = ChatTimestampStripDefaults.GutterDp * stripProgress
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            bubble()
        }
        if (stripProgress > 0.01f) {
            Box(
                modifier = Modifier.width(gutterW),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = formatMessageTime(timeCreated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
