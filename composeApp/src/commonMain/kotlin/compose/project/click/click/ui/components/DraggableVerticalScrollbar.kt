@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private val FastScrollbarTouchWidth = 28.dp
private val FastScrollbarVisualWidth = 3.dp
private val FastScrollbarVisualWidthDragging = 5.dp
private val FastScrollbarMinThumbHeight = 44.dp
private val FastScrollbarOverscrollCompressionDistance = 72.dp
private const val FAST_SCROLLBAR_MIN_EDGE_COMPRESSION = 0.34f
private const val FAST_SCROLLBAR_PASSIVE_EDGE_COMPRESSION = 0.58f

/**
 * Draggable fast-scroll thumb for [LazyListState].
 *
 * Position comes from Compose's [androidx.compose.foundation.ScrollIndicatorState] rather than
 * estimating an absolute offset from item indices. That state exists specifically for scrollbar
 * drawing and stays continuous across variable-height lazy rows.
 */
@Composable
fun DraggableLazyListScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    val indicator = state.scrollIndicatorState ?: return
    val contentSize = indicator.contentSize
    val viewportSize = indicator.viewportSize
    val scrollOffset = indicator.scrollOffset
    if (
        contentSize == Int.MAX_VALUE ||
        viewportSize == Int.MAX_VALUE ||
        scrollOffset == Int.MAX_VALUE ||
        viewportSize <= 0 ||
        contentSize <= viewportSize
    ) {
        return
    }

    val totalItems = state.layoutInfo.totalItemsCount
    if (totalItems <= 1) return

    // ScrollIndicatorState's lazy-list offset and content size are estimates that evolve together.
    // Keep those live for position/drag math, but stabilize only the visual thumb length while the
    // list is moving so estimator corrections cannot make the thumb "breathe".
    val effectiveContentSize = contentSize.coerceAtLeast(viewportSize + 1)
    val scrollRangePx = (effectiveContentSize - viewportSize).toFloat().coerceAtLeast(1f)
    val rawLogicalPosition = (scrollOffset.toFloat() / scrollRangePx).coerceIn(0f, 1f)
    val logicalPosition =
        when {
            !state.canScrollBackward -> 0f
            !state.canScrollForward -> 1f
            else -> rawLogicalPosition
        }
    val displayPosition = if (reverseLayout) 1f - logicalPosition else logicalPosition

    val visibleFraction =
        viewportSize.toFloat() /
            effectiveContentSize.toFloat()

    DraggableScrollbarTrack(
        positionFraction = displayPosition,
        visibleFraction = visibleFraction,
        modifier = modifier,
        topInset = topInset,
        bottomInset = bottomInset,
        scrollInProgress = state.isScrollInProgress,
        onDragFraction = { physicalDeltaFraction ->
            val direction = if (reverseLayout) -1f else 1f
            val requestedContentDelta = physicalDeltaFraction * scrollRangePx * direction
            val consumedContentDelta = state.dispatchRawDelta(requestedContentDelta)
            if (requestedContentDelta == 0f) {
                0f
            } else {
                (consumedContentDelta / scrollRangePx) * direction
            }
        },
    )
}

/**
 * Draggable fast-scroll thumb for ordinary [ScrollState] content.
 */
@Composable
fun DraggableScrollStateScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    val indicator = state.scrollIndicatorState ?: return
    val contentSize = indicator.contentSize
    val viewportSize = indicator.viewportSize
    val scrollOffset = indicator.scrollOffset
    if (
        contentSize == Int.MAX_VALUE ||
        viewportSize == Int.MAX_VALUE ||
        scrollOffset == Int.MAX_VALUE ||
        viewportSize <= 0 ||
        contentSize <= viewportSize
    ) {
        return
    }

    val scrollRangePx = (contentSize - viewportSize).toFloat().coerceAtLeast(1f)
    val positionFraction = (scrollOffset.toFloat() / scrollRangePx).coerceIn(0f, 1f)
    val visibleFraction = viewportSize.toFloat() / contentSize.toFloat()

    DraggableScrollbarTrack(
        positionFraction = positionFraction,
        visibleFraction = visibleFraction,
        modifier = modifier,
        topInset = topInset,
        bottomInset = bottomInset,
        scrollInProgress = state.isScrollInProgress,
        onDragFraction = { deltaFraction ->
            val requested = deltaFraction * scrollRangePx
            state.dispatchRawDelta(requested) / scrollRangePx
        },
    )
}

@Composable
private fun DraggableScrollbarTrack(
    positionFraction: Float,
    visibleFraction: Float,
    modifier: Modifier,
    topInset: Dp,
    bottomInset: Dp,
    scrollInProgress: Boolean,
    onDragFraction: (Float) -> Float,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentPositionFraction by rememberUpdatedState(positionFraction)
    val dragFractionHandler by rememberUpdatedState(onDragFraction)
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var dragPositionFraction by remember { mutableFloatStateOf(positionFraction) }
    var edgePullPx by remember { mutableFloatStateOf(0f) }

    val minThumbHeightPx = with(density) { FastScrollbarMinThumbHeight.toPx() }
    val compressionDistancePx =
        with(density) { FastScrollbarOverscrollCompressionDistance.toPx() }.coerceAtLeast(1f)
    val visualWidthPx =
        with(density) {
            (if (dragging) FastScrollbarVisualWidthDragging else FastScrollbarVisualWidth).toPx()
        }
    val thumbColor =
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dragging) 0.72f else 0.42f)

    var settledVisibleFraction by
        remember {
            mutableFloatStateOf(visibleFraction.coerceIn(0f, 1f))
        }
    LaunchedEffect(visibleFraction, scrollInProgress, dragging) {
        if (!scrollInProgress && !dragging) {
            settledVisibleFraction = visibleFraction.coerceIn(0f, 1f)
        }
    }
    val animatedVisibleFraction by
        animateFloatAsState(
            targetValue = settledVisibleFraction,
            animationSpec = spring(dampingRatio = 1f, stiffness = 700f),
        )

    val baseThumbHeightPx =
        if (trackHeightPx <= 0) {
            0f
        } else {
            (trackHeightPx * animatedVisibleFraction)
                .coerceAtLeast(minThumbHeightPx)
                .coerceAtMost(trackHeightPx.toFloat())
        }
    val baseTravelPx = (trackHeightPx - baseThumbHeightPx).coerceAtLeast(1f)
    val animatedPosition by
        animateFloatAsState(
            targetValue = positionFraction.coerceIn(0f, 1f),
            animationSpec = spring(dampingRatio = 1f, stiffness = 1_400f),
        )
    val effectivePosition =
        if (dragging) {
            dragPositionFraction
        } else {
            animatedPosition
        }
    val atStart =
        if (dragging) {
            effectivePosition <= 0.0001f
        } else {
            positionFraction <= 0.0001f
        }
    val atEnd =
        if (dragging) {
            effectivePosition >= 0.9999f
        } else {
            positionFraction >= 0.9999f
        }

    val activeCompression =
        if (abs(edgePullPx) > 0.5f) {
            val pullFraction = (abs(edgePullPx) / compressionDistancePx).coerceIn(0f, 1f)
            1f - pullFraction * (1f - FAST_SCROLLBAR_MIN_EDGE_COMPRESSION)
        } else if (scrollInProgress && (atStart || atEnd)) {
            FAST_SCROLLBAR_PASSIVE_EDGE_COMPRESSION
        } else {
            1f
        }
    val compression by
        animateFloatAsState(
            targetValue = activeCompression,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
        )

    fun consumeThumbDelta(deltaPx: Float): Float {
        if (baseTravelPx <= 0f) return 0f
        val requestedFraction = deltaPx / baseTravelPx
        val consumedFraction = dragFractionHandler(requestedFraction)
        val consumedPx = consumedFraction * baseTravelPx

        dragPositionFraction =
            (dragPositionFraction + consumedFraction).coerceIn(0f, 1f)

        val unconsumedPx = deltaPx - consumedPx
        edgePullPx =
            if (abs(unconsumedPx) > 0.05f) {
                (edgePullPx + unconsumedPx)
                    .coerceIn(-compressionDistancePx, compressionDistancePx)
            } else {
                edgePullPx * 0.55f
            }
        return consumedPx
    }

    val draggableState =
        rememberDraggableState { deltaPx ->
            consumeThumbDelta(deltaPx)
        }

    Canvas(
        modifier =
            modifier
                .fillMaxHeight()
                .width(FastScrollbarTouchWidth)
                .padding(top = topInset, bottom = bottomInset)
                .onSizeChanged { trackHeightPx = it.height }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        flingJob?.cancel()
                        edgePullPx = 0f
                        dragPositionFraction = currentPositionFraction.coerceIn(0f, 1f)
                        dragging = true
                        PlatformHapticsPolicy.heavyImpact()
                    },
                    onDragStopped = { velocityPxPerSec ->
                        dragging = false
                        edgePullPx = 0f
                        if (abs(velocityPxPerSec) < 80f || baseTravelPx <= 0f) return@draggable

                        flingJob?.cancel()
                        flingJob =
                            scope.launch {
                                var previousValue = 0f
                                AnimationState(
                                    initialValue = 0f,
                                    initialVelocity = velocityPxPerSec,
                                ).animateDecay(
                                    animationSpec = exponentialDecay(frictionMultiplier = 1.75f),
                                ) {
                                    val delta = value - previousValue
                                    previousValue = value
                                    val consumed = consumeThumbDelta(delta)
                                    if (
                                        abs(delta) > 0.5f &&
                                        abs(consumed) < abs(delta) * 0.05f
                                    ) {
                                        cancelAnimation()
                                    }
                                }
                                edgePullPx = 0f
                            }
                    },
                ),
    ) {
        if (trackHeightPx <= 0 || baseThumbHeightPx <= 0f) return@Canvas

        val thumbHeight =
            (baseThumbHeightPx * compression)
                .coerceAtLeast(visualWidthPx * 2f)
                .coerceAtMost(size.height)
        val travel = (size.height - thumbHeight).coerceAtLeast(0f)
        val thumbTop =
            when {
                atStart -> 0f
                atEnd -> travel
                else -> travel * effectivePosition
            }
        val thumbLeft = size.width - visualWidthPx

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(visualWidthPx, thumbHeight),
            cornerRadius = CornerRadius(visualWidthPx / 2f, visualWidthPx / 2f),
        )
    }
}
