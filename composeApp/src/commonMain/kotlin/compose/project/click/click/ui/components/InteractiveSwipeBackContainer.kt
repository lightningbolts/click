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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Optional integration for **right-to-left** horizontal drag while the interactive-back offset is 0.
 * Used by chat to reveal timestamp gutters without a second competing horizontal gesture layer.
 *
 * [onGestureStart] is invoked when any horizontal drag begins (same moment as interactive back).
 * [onLeftDragDelta] receives the **positive** pixel magnitude of leftward finger movement per step.
 * [onLeftDragEnd] runs when the finger lifts (alongside normal back-velocity settling).
 */
data class InteractiveSwipeBackRightToLeftPeek(
    val onGestureStart: () -> Unit = {},
    val onLeftDragDelta: (deltaPxLeftward: Float) -> Unit,
    /**
     * Horizontal fling velocity when the finger lifts (px/s); same convention as
     * [androidx.compose.foundation.gestures.draggable] `onDragStopped`.
     */
    val onLeftDragEnd: (velocityXPxPerSec: Float) -> Unit = { _ -> },
    /**
     * Rightward drag while the back offset is still ~0 (e.g. user begins interactive back after
     * timestamp peek); collapse peek so it does not stay visible under the sliding route.
     */
    val onRightDragFromRest: (deltaPxRightward: Float) -> Unit = {},
)

/**
 * Fraction of screen width the previous route sits left of its rest position while progress is 0.
 * Exposed so external “persistent underlay” UIs can mirror layer-1 parallax in [graphicsLayer].
 */
internal const val InteractiveSwipeBackParallaxPeekRatio = 0.3f

private const val ParallaxBackgroundPeek = InteractiveSwipeBackParallaxPeekRatio

/**
 * iOS-style interactive back container:
 * - Slide transform uses [Modifier.graphicsLayer] so [translationX] is read only during draw;
 *   the drag offset is never observed during composition (avoids full-tree recompositions per frame).
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
    onBack: () -> Unit,
    /**
     * When false, the layer behind the sliding [currentContent] does not paint a solid
     * [MaterialTheme] background. Use this when the real destination UI is already
     * composed underneath this container (e.g. a persistent list) so the swipe reveals
     * that surface instead of a duplicate subtree with fresh scroll state.
     */
    opaquePreviousBackground: Boolean = true,
    /**
     * When non-null, horizontal drag offset is stored in this ref (instead of an internal one).
     * Pair with [onBehindLayersVisibleChanged] and apply the same parallax [graphicsLayer] on any
     * content composed *outside* this container (e.g. a single persistent list) so it moves with
     * layer 1 while [opaquePreviousBackground] is false and [previousContent] is empty.
     */
    externalDragOffsetPx: MutableFloatState? = null,
    /** Invoked when layer 1/2 visibility (`isGestureActive || isSettling`) changes; also `false` on dispose. */
    onBehindLayersVisibleChanged: (Boolean) -> Unit = {},
    /**
     * When non-null and [useFullWidthHorizontalDrag] is true, **negative** horizontal drag deltas
     * (finger moving left) are routed here while the back-swipe offset is ~0; **positive** deltas
     * still drive interactive back everywhere this container is used.
     */
    rightToLeftPeek: InteractiveSwipeBackRightToLeftPeek? = null,
    /** Invoked when a horizontal back-swipe gesture begins (after jitter cancel / settle reset). */
    onHorizontalDragStarted: () -> Unit = {},
    /**
     * Called whenever the foreground horizontal offset changes (drag + settling). Chat surfaces
     * can mirror this (or observe `externalDragOffsetPx` with `snapshotFlow`) to dismiss the
     * software keyboard after a small horizontal threshold (~20px) so IME teardown tracks the
     * gesture. The foreground layer already uses [graphicsLayer.translationX] for the slide;
     * do not apply a second horizontal [Modifier.offset] for the same motion.
     */
    onInteractiveSwipeOffsetChanged: (offsetPx: Float, widthPx: Float) -> Unit = { _, _ -> },
    /** Invoked when the swipe offset returns to rest (canceled or container disposed). */
    onInteractiveSwipeFinished: () -> Unit = {},
    previousContent: @Composable () -> Unit,
    currentContent: @Composable () -> Unit
) {
    val internalOffsetPx = remember { mutableFloatStateOf(0f) }
    val offsetPx = externalDragOffsetPx ?: internalOffsetPx
    var isGestureActive by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val settleScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragJitterThresholdPx = remember(density) { with(density) { 0.75.dp.toPx() } }
    val onInteractiveSwipeOffsetChangedState = rememberUpdatedState(onInteractiveSwipeOffsetChanged)
    val onInteractiveSwipeFinishedState = rememberUpdatedState(onInteractiveSwipeFinished)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val showPreviousLayer = isGestureActive || isSettling

        SideEffect {
            onBehindLayersVisibleChanged(showPreviousLayer)
        }

        DisposableEffect(Unit) {
            onDispose {
                onBehindLayersVisibleChanged(false)
                onInteractiveSwipeFinishedState.value.invoke()
            }
        }

        fun notifySwipeOffset() {
            val o = offsetPx.floatValue
            onInteractiveSwipeOffsetChangedState.value.invoke(o, widthPx)
        }

        fun snapDragOffset(nextOffset: Float) {
            val clampedOffset = nextOffset.coerceIn(0f, widthPx)
            val movedEnough = abs(clampedOffset - offsetPx.floatValue) >= dragJitterThresholdPx
            if (movedEnough || clampedOffset == 0f || clampedOffset == widthPx) {
                offsetPx.floatValue = clampedOffset
                notifySwipeOffset()
            }
        }

        fun finishGesture(velocityX: Float) {
            val currentOffset = offsetPx.floatValue
            if (currentOffset <= 0f) {
                offsetPx.floatValue = 0f
                notifySwipeOffset()
                isGestureActive = false
                isSettling = false
                settleJob = null
                onInteractiveSwipeFinishedState.value.invoke()
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
                    offsetPx.floatValue = value
                    notifySwipeOffset()
                }

                if (shouldComplete) {
                    onBack()
                    // Do not zero [offsetPx] here: while this composable is still composed until the
                    // next frame, resetting would snap the foreground (translationX) back to 0 and
                    // flash the chat full-screen. External mirrors are cleared after removal via
                    // [LaunchedEffect] in the parent.
                    kotlinx.coroutines.delay(34)
                    onInteractiveSwipeFinishedState.value.invoke()
                } else {
                    offsetPx.floatValue = 0f
                    notifySwipeOffset()
                    onInteractiveSwipeFinishedState.value.invoke()
                }
                isGestureActive = false
                isSettling = false
                settleJob = null
            }
        }

    val rightToLeftPeekState = rememberUpdatedState(rightToLeftPeek)
    val onHorizontalDragStartedState = rememberUpdatedState(onHorizontalDragStarted)
    val dragState = rememberDraggableState { delta ->
            if (!isGestureActive || isSettling) return@rememberDraggableState
            val offset = offsetPx.floatValue
            when {
                delta > 0f -> {
                    if (offset <= 0f) {
                        rightToLeftPeekState.value?.onRightDragFromRest?.invoke(delta)
                    }
                    snapDragOffset(offset + delta)
                }
                delta < 0f && offset > 0f -> snapDragOffset(offset + delta)
                delta < 0f && offset <= 0f ->
                    rightToLeftPeekState.value?.onLeftDragDelta?.invoke(-delta)
                else -> Unit
            }
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
                    onHorizontalDragStartedState.value.invoke()
                    rightToLeftPeekState.value?.onGestureStart?.invoke()
                },
                onDragStopped = { velocity ->
                    rightToLeftPeekState.value?.onLeftDragEnd?.invoke(velocity)
                    finishGesture(velocity)
                }
            )
        } else {
            Modifier
        }

        val foregroundSlide = Modifier.graphicsLayer {
            translationX = offsetPx.floatValue
        }

        val contentChrome = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (enabled && useFullWidthHorizontalDrag) dragModifier else Modifier
            )

        // Strict 3-layer stack: (1) previous route + parallax, (2) scrim (opacity only), (3) current route + finger offset.
        if (showPreviousLayer) {
            // Layer 1 — background / previous route: parallax translation only on this Box.
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
                    .graphicsLayer {
                        val currentSwipeOffset = offsetPx.floatValue.coerceIn(0f, widthPx)
                        val progress = (currentSwipeOffset / widthPx).coerceIn(0f, 1f)
                        translationX = -(size.width * ParallaxBackgroundPeek) * (1f - progress)
                    },
            ) {
                previousContent()
            }
            // Layer 2 — scrim over background, under foreground: no translation, opacity only (draw-phase progress).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val w = size.width.coerceAtLeast(1f)
                        val currentSwipeOffset = offsetPx.floatValue.coerceIn(0f, w)
                        val progress = (currentSwipeOffset / w).coerceIn(0f, 1f)
                        drawRect(Color.Black.copy(alpha = 0.5f * (1f - progress)))
                    },
            )
        }

        // Layer 3 — foreground: follows the finger; drawn above layers 1–2 when they are present.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(foregroundSlide)
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
