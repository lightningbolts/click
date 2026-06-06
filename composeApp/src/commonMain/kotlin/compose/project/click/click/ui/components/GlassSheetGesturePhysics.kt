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

internal fun shouldCommitVerticalDismiss(
    offsetPx: Float,
    travelPx: Float,
    velocityPxPerSec: Float,
): Boolean {
    val travel = travelPx.coerceAtLeast(1f)
    return offsetPx > travel * GlassGestureCommitFraction ||
        velocityPxPerSec > GlassGestureFlickVelocityPxPerSec
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

    val sheetState = rememberModalBottomSheetState(
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
fun rememberGlassAdaptiveSheetState(
    skipPartiallyExpanded: Boolean = false,
): AdaptiveSheetState {
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
