package compose.project.click.click.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Reports whether the active sheet body scroll is at the top.
 * Default true so non-scrollable action sheets dismiss on any downward swipe.
 */
val LocalSheetScrollAtTop = compositionLocalOf { { true } }

/** Dismiss callback for the hosting bottom sheet (platform sheet / adaptive sheet). */
val LocalSheetOnDismissRequest = compositionLocalOf { {} }

@Composable
fun ProvideSheetSwipeDismiss(
    onDismissRequest: () -> Unit,
    scrollAtTop: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSheetOnDismissRequest provides onDismissRequest,
        LocalSheetScrollAtTop provides scrollAtTop,
        content = content,
    )
}

fun ScrollState.isSheetScrollAtTop(): Boolean = value <= 0

fun LazyListState.isSheetScrollAtTop(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= 0

/**
 * Swipe-down dismiss when nested content is already at the top **and this gesture did not
 * scroll content**.
 *
 * Slow drags translate the sheet with the finger; on release the sheet either springs back or
 * commits dismiss (same thresholds as [shouldCommitVerticalDismiss] / glass sheet physics).
 * A continuous drag that scrolls the list to the top must not also dismiss — lift and swipe
 * again from the top.
 */
@Composable
fun Modifier.sheetSwipeDismissWhenAtTop(
    onDismissRequest: () -> Unit = LocalSheetOnDismissRequest.current,
    scrollAtTop: () -> Boolean = LocalSheetScrollAtTop.current,
): Modifier {
    val density = LocalDensity.current
    val minTravelPx = with(density) { 240.dp.toPx() }
    val dismissOvershootPx = with(density) { 64.dp.toPx() }

    val dragOffsetPx = remember { mutableFloatStateOf(0f) }
    val sheetHeightPx = remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }

    val onDismissUpdated by rememberUpdatedState(onDismissRequest)
    val scrollAtTopUpdated by rememberUpdatedState(scrollAtTop)

    val connection = remember(minTravelPx, dismissOvershootPx) {
        object : NestedScrollConnection {
            private var gestureActive = false
            /** True once any nested child consumed scroll during this finger gesture. */
            private var contentScrolledThisGesture = false

            private fun noteUserInput() {
                if (gestureActive) return
                gestureActive = true
                contentScrolledThisGesture = false
            }

            private fun endGestureTracking() {
                gestureActive = false
                contentScrolledThisGesture = false
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (settling) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                noteUserInput()
                if (consumed.y != 0f) {
                    contentScrolledThisGesture = true
                    if (dragOffsetPx.floatValue > 0f) {
                        dragOffsetPx.floatValue = 0f
                    }
                    return Offset.Zero
                }
                if (contentScrolledThisGesture) return Offset.Zero
                if (!scrollAtTopUpdated()) return Offset.Zero

                val current = dragOffsetPx.floatValue
                if (current <= 0f && available.y <= 0f) return Offset.Zero

                val next = (current + available.y).coerceAtLeast(0f)
                val delta = next - current
                if (delta == 0f) return Offset.Zero
                dragOffsetPx.floatValue = next
                return Offset(0f, delta)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (settling) {
                    endGestureTracking()
                    return Velocity.Zero
                }
                try {
                    val offset = dragOffsetPx.floatValue
                    if (contentScrolledThisGesture || offset <= 0f) {
                        if (offset > 0f) {
                            settleBackToRest()
                        }
                        return Velocity.Zero
                    }

                    val travel = sheetHeightPx.floatValue.coerceAtLeast(minTravelPx)
                    val commit = shouldCommitVerticalDismiss(
                        offsetPx = offset,
                        travelPx = travel,
                        velocityPxPerSec = available.y,
                    )
                    if (commit) {
                        settling = true
                        val anim = Animatable(offset)
                        anim.animateTo(
                            targetValue = travel + dismissOvershootPx,
                            animationSpec = tween(
                                durationMillis = 220,
                                easing = FastOutLinearInEasing,
                            ),
                        ) {
                            dragOffsetPx.floatValue = value
                        }
                        onDismissUpdated()
                        return available
                    }

                    settleBackToRest()
                    return Velocity.Zero
                } finally {
                    endGestureTracking()
                }
            }

            private suspend fun settleBackToRest() {
                val start = dragOffsetPx.floatValue
                if (start <= 0f) return
                settling = true
                try {
                    val anim = Animatable(start)
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) {
                        dragOffsetPx.floatValue = value
                    }
                } finally {
                    dragOffsetPx.floatValue = 0f
                    settling = false
                }
            }
        }
    }

    return this
        .onSizeChanged { size ->
            sheetHeightPx.floatValue = size.height.toFloat()
        }
        .graphicsLayer {
            translationY = dragOffsetPx.floatValue
        }
        .nestedScroll(connection)
}

@Composable
fun rememberSheetScrollAtTop(scrollState: ScrollState): () -> Boolean =
    remember(scrollState) { { scrollState.isSheetScrollAtTop() } }

@Composable
fun rememberSheetScrollAtTop(listState: LazyListState): () -> Boolean =
    remember(listState) { { listState.isSheetScrollAtTop() } }
