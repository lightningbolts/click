package compose.project.click.click.ui.chat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal object ChatTimestampStripDefaults {
    /**
     * Full width reserved for the time column at peek=1f (wider than typical "11:59 PM" in
     * labelSmall so AM/PM is not clipped).
     */
    val GutterDp = 80.dp
}

/** Same gains as swipe-to-reply in [ChatMessageBubble] (do not change reply; mirror here). */
private const val TimestampPeekTrackGain = 0.56f
private const val TimestampPeekOverflowRubberGain = 0.12f

private val TimestampPeekSettleEasing = CubicBezierEasing(0.17f, 0.88f, 0.24f, 1f)

@Composable
internal fun rememberTimestampPeekRevealPx(): Float {
    val density = LocalDensity.current
    // Full timestamp reveal should be reachable with a modest peek; this is intentionally
    // shorter than the gutter width so the time can fully appear before the bubble shifts too far.
    return remember(density) { with(density) { 56.dp.toPx() } }
}

/** Soft knee in px — matches reply swipe [ChatMessageBubble] `swipeSoftKneePx`. */
@Composable
internal fun rememberTimestampPeekSoftKneePx(): Float {
    val density = LocalDensity.current
    return remember(density) { with(density) { 5.dp.toPx() } }
}

/**
 * Finger-driven step for timestamp peek: same **raw → visual** mapping as swipe-to-reply
 * ([swipeVisualFromRawTravel]); updates are synchronous on the main thread.
 */
internal fun applyTimestampPeekDragStep(
    rawLeftPx: MutableFloatState,
    displayVisualPx: MutableFloatState,
    maxRevealPx: Float,
    softKneePx: Float,
    dLeftPx: Float,
) {
    val maxR = maxRevealPx.coerceAtLeast(1f)
    val soft = softKneePx.coerceAtLeast(1f)
    rawLeftPx.floatValue = (rawLeftPx.floatValue + dLeftPx).coerceIn(0f, 340f)
    displayVisualPx.floatValue =
        swipeVisualFromRawTravel(
            rawTravelPx = rawLeftPx.floatValue,
            isSent = false,
            maxVisualPx = maxR,
            softKneePx = soft,
            trackGain = TimestampPeekTrackGain,
            overflowRubberGain = TimestampPeekOverflowRubberGain,
        )
}

/** Same idea as picking up mid-settle in [ChatMessageBubble] `onDragStarted`. */
internal fun restoreTimestampPeekRawFromDisplay(
    rawLeftPx: MutableFloatState,
    displayVisualPx: MutableFloatState,
    maxRevealPx: Float,
    softKneePx: Float,
) {
    val v = displayVisualPx.floatValue
    if (v == 0f) return
    val maxR = maxRevealPx.coerceAtLeast(1f)
    val soft = softKneePx.coerceAtLeast(1f)
    rawLeftPx.floatValue =
        swipeRawTravelFromVisual(
            visualPx = v,
            isSent = false,
            maxVisualPx = maxR,
            softKneePx = soft,
            trackGain = TimestampPeekTrackGain,
            overflowRubberGain = TimestampPeekOverflowRubberGain,
        ).coerceIn(0f, 340f)
}

/** Same settle curve/duration as swipe-to-reply release in [ChatMessageBubble]. */
internal fun CoroutineScope.launchTimestampPeekReplyStyleSettle(
    rawLeftPx: MutableFloatState,
    displayVisualPx: MutableFloatState,
    settleJobHolder: MutableState<Job?>,
) {
    settleJobHolder.value?.cancel()
    rawLeftPx.floatValue = 0f
    settleJobHolder.value =
        launch {
            try {
                animate(
                    initialValue = displayVisualPx.floatValue,
                    targetValue = 0f,
                    animationSpec =
                        tween(
                            durationMillis = 480,
                            easing = TimestampPeekSettleEasing,
                        ),
                ) { v, _ ->
                    displayVisualPx.floatValue = v
                }
            } finally {
                settleJobHolder.value = null
            }
        }
}

/**
 * Local full-surface R→L peek: same drag math as swipe-to-reply; on release uses the same
 * [tween] settle as the bubble (not velocity-spring).
 */
internal fun Modifier.chatTimestampPeekOnSwipeLeft(
    maxRevealPx: Float,
    softKneePx: Float,
    rawLeftPx: MutableFloatState,
    displayVisualPx: MutableFloatState,
    scope: CoroutineScope,
    settleJobHolder: MutableState<Job?>,
): Modifier =
    this.pointerInput(maxRevealPx, softKneePx, rawLeftPx, displayVisualPx, scope, settleJobHolder) {
        detectDragGestures(
            onDragStart = {
                settleJobHolder.value?.cancel()
                settleJobHolder.value = null
                restoreTimestampPeekRawFromDisplay(rawLeftPx, displayVisualPx, maxRevealPx, softKneePx)
            },
            onDrag = { change, dragAmount ->
                if (dragAmount.x < 0f) {
                    applyTimestampPeekDragStep(
                        rawLeftPx = rawLeftPx,
                        displayVisualPx = displayVisualPx,
                        maxRevealPx = maxRevealPx,
                        softKneePx = softKneePx,
                        dLeftPx = -dragAmount.x,
                    )
                    change.consume()
                }
            },
            onDragEnd = {
                scope.launchTimestampPeekReplyStyleSettle(
                    rawLeftPx = rawLeftPx,
                    displayVisualPx = displayVisualPx,
                    settleJobHolder = settleJobHolder,
                )
            },
            onDragCancel = {
                scope.launchTimestampPeekReplyStyleSettle(
                    rawLeftPx = rawLeftPx,
                    displayVisualPx = displayVisualPx,
                    settleJobHolder = settleJobHolder,
                )
            },
        )
    }

@Composable
internal fun ChatMessageRowWithTimestampGutter(
    isCallLog: Boolean,
    isSent: Boolean,
    timeCreated: Long,
    stripVisualPx: MutableFloatState,
    maxRevealPx: Float,
    modifier: Modifier = Modifier,
    bubble: @Composable () -> Unit,
) {
    if (isCallLog) {
        bubble()
        return
    }
    val density = LocalDensity.current
    val gutterWidthDp = ChatTimestampStripDefaults.GutterDp
    val gutterWidthPx = remember(density) { with(density) { gutterWidthDp.toPx() } }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p =
                            (stripVisualPx.floatValue / maxRevealPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        translationX = if (isSent) -(gutterWidthPx * p) else 0f
                    },
            contentAlignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            bubble()
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(gutterWidthDp)
                    .clipToBounds()
                    .graphicsLayer {
                        val p =
                            (stripVisualPx.floatValue / maxRevealPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        alpha = p
                        translationX = size.width * (1f - p)
                    },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = formatMessageTime(timeCreated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 8.dp),
            )
        }
    }
}
