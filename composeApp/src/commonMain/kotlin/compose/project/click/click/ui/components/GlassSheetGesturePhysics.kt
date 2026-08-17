@file:Suppress("ktlint:standard:property-naming")

package compose.project.click.click.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.sheet.AdaptiveSheetState
import kotlinx.datetime.Clock
import kotlin.math.abs

/** iOS-style flick commit threshold (px/s). */
internal const val GlassGestureFlickVelocityPxPerSec = 800f

/** Fraction of travel past which a drag commits without a flick. */
internal const val GlassGestureCommitFraction = 0.5f

/**
 * Interactive-back settle. Slightly under-damped so cancel and commit both land with a
 * small iOS-like jiggle. `DampingRatioNoBouncy` plus a 0..width clamp killed commit settle.
 */
internal const val InteractiveBackCancelDampingRatio = 0.82f
internal const val InteractiveBackCommitDampingRatio = 0.78f
internal const val InteractiveBackCommitOvershootRatio = 0.045f
internal const val InteractiveBackCancelOvershootRatio = 0.028f

internal fun shouldCommitVerticalDismiss(
    offsetPx: Float,
    travelPx: Float,
    velocityPxPerSec: Float,
): Boolean {
    val travel = travelPx.coerceAtLeast(1f)
    return offsetPx > travel * GlassGestureCommitFraction ||
        velocityPxPerSec > GlassGestureFlickVelocityPxPerSec
}

/**
 * Interactive-back commit: past the horizontal midpoint, or a fast rightward flick.
 * Completing routes must animate remaining travel with the release velocity — never
 * snap the offset to the trailing edge in a single frame.
 */
internal fun shouldCommitInteractiveBack(
    offsetPx: Float,
    widthPx: Float,
    velocityXPxPerSec: Float,
): Boolean {
    val width = widthPx.coerceAtLeast(1f)
    return offsetPx > width * GlassGestureCommitFraction ||
        velocityXPxPerSec > GlassGestureFlickVelocityPxPerSec
}

/**
 * Finger tracking stays 1:1 inside 0..width. After lift, the spring may overshoot slightly
 * past rest (cancel) or past the trailing edge (commit) so the landing jiggle is visible.
 */
internal fun clampInteractiveBackSettleOffset(
    value: Float,
    widthPx: Float,
    committing: Boolean,
): Float {
    val width = widthPx.coerceAtLeast(1f)
    return if (committing) {
        value.coerceIn(0f, width * (1f + InteractiveBackCommitOvershootRatio))
    } else {
        value.coerceIn(-width * InteractiveBackCancelOvershootRatio, width)
    }
}

/**
 * Whether a downward nested-scroll leftover may drive sheet surface-drag dismiss.
 * Mid-list scrolls must finish before a new at-top gesture can dismiss.
 */
internal fun shouldAllowSheetSurfaceDismiss(
    atTop: Boolean,
    contentScrolledThisGesture: Boolean,
    surfaceDragActive: Boolean,
    blockSurfaceDrag: Boolean,
): Boolean {
    if (!surfaceDragActive || blockSurfaceDrag) return false
    if (contentScrolledThisGesture) return false
    return atTop
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberGlassModalBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    travel: Dp = 420.dp,
): SheetState {
    val density = LocalDensity.current
    val travelPx = with(density) { travel.toPx() }.coerceAtLeast(1f)
    val offsetPx = remember { mutableFloatStateOf(0f) }
    val velocityPxPerSec = remember { mutableFloatStateOf(0f) }
    val lastOffsetPx = remember { mutableFloatStateOf(0f) }
    val lastSampleMs = remember { mutableLongStateOf(0L) }

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = skipPartiallyExpanded,
            confirmValueChange = { target ->
                when (target) {
                    SheetValue.Hidden ->
                        shouldCommitVerticalDismiss(
                            offsetPx = offsetPx.floatValue,
                            travelPx = travelPx,
                            velocityPxPerSec = velocityPxPerSec.floatValue,
                        )
                    else -> true
                }
            },
        )

    LaunchedEffect(sheetState, travelPx) {
        snapshotFlow {
            runCatching { sheetState.requireOffset() }.getOrDefault(0f)
        }.collect { offset ->
            val now = Clock.System.now().toEpochMilliseconds()
            val previousMs = lastSampleMs.longValue
            if (previousMs > 0L) {
                val dtMs = (now - previousMs).coerceAtLeast(1L)
                velocityPxPerSec.floatValue =
                    (offset - lastOffsetPx.floatValue) / dtMs.toFloat() * 1_000f
            }
            lastOffsetPx.floatValue = offset
            lastSampleMs.longValue = now
            offsetPx.floatValue = abs(offset)
        }
    }

    return sheetState
}

/**
 * Calf adaptive sheet with a lower flick velocity threshold than the library default so
 * fast downward flings commit dismiss without reaching the 50% travel mark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberGlassAdaptiveSheetState(skipPartiallyExpanded: Boolean = false): AdaptiveSheetState {
    val density = LocalDensity.current
    val positionalThresholdToPx = { with(density) { 56.dp.toPx() } }
    val velocityThresholdToPx = { GlassGestureFlickVelocityPxPerSec }

    return rememberSaveable(
        skipPartiallyExpanded,
        saver =
            AdaptiveSheetState.Saver(
                skipPartiallyExpanded = skipPartiallyExpanded,
                positionalThreshold = positionalThresholdToPx,
                velocityThreshold = velocityThresholdToPx,
                confirmValueChange = { true },
                skipHiddenState = false,
            ),
    ) {
        AdaptiveSheetState(
            skipPartiallyExpanded = skipPartiallyExpanded,
            confirmValueChange = { target ->
                when (target) {
                    SheetValue.Hidden -> true
                    else -> true
                }
            },
            initialValue = SheetValue.Hidden,
            skipHiddenState = false,
            positionalThreshold = positionalThresholdToPx,
            velocityThreshold = velocityThresholdToPx,
        )
    }
}
