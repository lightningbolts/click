package compose.project.click.click.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iOS-style interactive back container:
 * - Uses [Modifier.graphicsLayer] for the slide transform by default (draw phase; avoids layout
 *   thrash). Use [useLayoutOffsetForSwipeReveal] on screens with UIKit camera/map previews that
 *   can render black when flattened through a graphics layer.
 * - Full-width drag is applied on the **same** [Box] that wraps [currentContent] so nested
 *   composables (e.g. message bubbles) receive pointer input first and can own horizontal gestures.
 * - When [useFullWidthHorizontalDrag] is false, a start-edge strip handles drag (drawn above
 *   content so it intentionally captures that band).
 */
@Composable
fun InteractiveSwipeBackContainer(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    edgeSwipeWidth: Dp = 24.dp,
    useFullWidthHorizontalDrag: Boolean = true,
    useLayoutOffsetForSwipeReveal: Boolean = false,
    onBack: () -> Unit,
    /**
     * When false, the layer behind the sliding [currentContent] does not paint a solid
     * [MaterialTheme] background. Use this when the real destination UI is already
     * composed underneath this container (e.g. a persistent list) so the swipe reveals
     * that surface instead of a duplicate subtree with fresh scroll state.
     */
    opaquePreviousBackground: Boolean = true,
    previousContent: @Composable () -> Unit,
    currentContent: @Composable () -> Unit
) {
    val dragOffset = remember { mutableFloatStateOf(0f) }
    var isGestureActive by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val settleScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragJitterThresholdPx = remember(density) { with(density) { 0.75.dp.toPx() } }
    val dragTranslationX by remember {
        derivedStateOf { dragOffset.floatValue }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val showPreviousLayer = isGestureActive || isSettling

        fun snapDragOffset(nextOffset: Float) {
            val clampedOffset = nextOffset.coerceIn(0f, widthPx)
            val movedEnough = abs(clampedOffset - dragOffset.floatValue) >= dragJitterThresholdPx
            if (movedEnough || clampedOffset == 0f || clampedOffset == widthPx) {
                dragOffset.floatValue = clampedOffset
            }
        }

        fun finishGesture(velocityX: Float) {
            val currentOffset = dragOffset.floatValue
            if (currentOffset <= 0f) {
                dragOffset.floatValue = 0f
                isGestureActive = false
                isSettling = false
                settleJob = null
                return
            }

            val progress = currentOffset / widthPx
            val projected = progress + (velocityX / 3200f)
            val shouldComplete = projected > 0.35f || velocityX > 650f
            val target = if (shouldComplete) widthPx else 0f
            isSettling = true

            settleJob = settleScope.launch {
                animate(
                    initialValue = currentOffset,
                    targetValue = target,
                    initialVelocity = velocityX,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { value, _ ->
                    dragOffset.floatValue = value
                }

                if (shouldComplete) {
                    onBack()
                    kotlinx.coroutines.delay(34)
                } else {
                    dragOffset.floatValue = 0f
                }
                isGestureActive = false
                isSettling = false
                settleJob = null
            }
        }

        val dragState = rememberDraggableState { delta ->
            if (!isGestureActive || isSettling) return@rememberDraggableState
            snapDragOffset(dragOffset.floatValue + delta)
        }

        val dragModifier = if (enabled) {
            Modifier.draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = !isSettling,
                startDragImmediately = isGestureActive,
                onDragStarted = {
                    settleJob?.cancel()
                    settleJob = null
                    isSettling = false
                    isGestureActive = true
                },
                onDragStopped = { velocity ->
                    finishGesture(velocity)
                }
            )
        } else {
            Modifier
        }

        val slideTransform = if (useLayoutOffsetForSwipeReveal) {
            Modifier.offset {
                IntOffset(dragTranslationX.roundToInt(), 0)
            }
        } else {
            Modifier.graphicsLayer {
                translationX = dragTranslationX
            }
        }

        val contentChrome = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (enabled && useFullWidthHorizontalDrag) dragModifier else Modifier
            )

        if (showPreviousLayer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (opaquePreviousBackground) {
                            Modifier.background(MaterialTheme.colorScheme.background)
                        } else {
                            Modifier
                        }
                    )
            ) {
                previousContent()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(slideTransform)
        ) {
            Box(modifier = contentChrome) {
                currentContent()
            }

            if (enabled && !useFullWidthHorizontalDrag) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(edgeSwipeWidth)
                        .then(dragModifier)
                )
            }
        }
    }
}
