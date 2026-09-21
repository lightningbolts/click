@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret

private val FastScrollbarTouchWidth = 28.dp
private val FastScrollbarVisualWidth = 3.dp
private val FastScrollbarVisualWidthDragging = 5.dp
private val FastScrollbarMinThumbHeight = 44.dp
private const val FastScrollbarEdgeCompression = 0.72f
private const val FastScrollbarLayoutReadyFraction = 0.75f

/**
 * Draggable fast-scroll thumb for [LazyListState].
 *
 * The scrollbar intentionally behaves like a native mobile scroll indicator: stable thumb size,
 * direct continuous pixel scrolling while tracking, bounded indicator insets, and edge compression.
 */
@Composable
fun DraggableLazyListScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= 1 || visibleItems.isEmpty() || totalItems <= visibleItems.size) return

    val viewportSizePx =
        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
            .coerceAtLeast(1)
    val visibleExtentPx =
        visibleItems
            .sumOf { item -> item.size.coerceAtLeast(0) }
            .coerceAtLeast(1)
    val layoutReady =
        visibleItems.all { it.size > 0 } &&
            visibleExtentPx >= viewportSizePx * FastScrollbarLayoutReadyFraction
    if (!layoutReady) return

    // Freeze the initial reliable row-size estimate for this content/viewport geometry. Re-sampling
    // variable-height rows while scrolling is what made the thumb visibly grow and shrink.
    val averageItemSizePx =
        remember(totalItems, viewportSizePx) {
            visibleExtentPx.toFloat() / visibleItems.size.toFloat()
        }.coerceAtLeast(1f)
    val estimatedContentSizePx = averageItemSizePx * totalItems
    val estimatedScrollRangePx = (estimatedContentSizePx - viewportSizePx).coerceAtLeast(1f)
    val visibleFraction = (viewportSizePx / estimatedContentSizePx).coerceIn(0f, 1f)

    val estimatedLogicalOffsetPx =
        state.firstVisibleItemIndex * averageItemSizePx + state.firstVisibleItemScrollOffset
    val logicalPosition =
        when {
            !state.canScrollBackward -> 0f
            !state.canScrollForward -> 1f
            else -> (estimatedLogicalOffsetPx / estimatedScrollRangePx).coerceIn(0f, 1f)
        }
    val displayPosition = if (reverseLayout) 1f - logicalPosition else logicalPosition

    DraggableScrollbarTrack(
        positionFraction = displayPosition,
        visibleFraction = visibleFraction,
        modifier = modifier,
        topInset = topInset,
        bottomInset = bottomInset,
        onDragFraction = { physicalDeltaFraction ->
            val logicalDeltaFraction =
                if (reverseLayout) {
                    -physicalDeltaFraction
                } else {
                    physicalDeltaFraction
                }
            state.dispatchRawDelta(logicalDeltaFraction * estimatedScrollRangePx)
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
    val maxValue = state.maxValue
    if (maxValue <= 0) return

    val positionFraction = state.value.toFloat() / maxValue.toFloat()

    DraggableScrollbarTrack(
        positionFraction = positionFraction,
        visibleFraction = null,
        scrollRangePx = maxValue.toFloat(),
        modifier = modifier,
        topInset = topInset,
        bottomInset = bottomInset,
        onDragFraction = { deltaFraction ->
            state.dispatchRawDelta(deltaFraction * maxValue)
        },
    )
}

@Composable
private fun DraggableScrollbarTrack(
    positionFraction: Float,
    visibleFraction: Float?,
    modifier: Modifier,
    topInset: Dp,
    bottomInset: Dp,
    scrollRangePx: Float? = null,
    onDragFraction: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val currentPositionFraction by rememberUpdatedState(positionFraction)
    val dragFractionHandler by rememberUpdatedState(onDragFraction)
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragPositionFraction by remember { mutableFloatStateOf(positionFraction) }

    val minThumbHeightPx = with(density) { FastScrollbarMinThumbHeight.toPx() }
    val visualWidthPx =
        with(density) {
            (if (dragging) FastScrollbarVisualWidthDragging else FastScrollbarVisualWidth).toPx()
        }
    val thumbColor =
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dragging) 0.72f else 0.42f)

    val resolvedVisibleFraction =
        when {
            visibleFraction != null -> visibleFraction
            trackHeightPx <= 0 || scrollRangePx == null -> 1f
            else ->
                trackHeightPx.toFloat() /
                    (trackHeightPx.toFloat() + scrollRangePx).coerceAtLeast(1f)
        }.coerceIn(0f, 1f)

    val baseThumbHeightPx =
        if (trackHeightPx <= 0) {
            0f
        } else {
            (trackHeightPx * resolvedVisibleFraction)
                .coerceAtLeast(minThumbHeightPx)
                .coerceAtMost(trackHeightPx.toFloat())
        }
    val baseTravelPx = (trackHeightPx - baseThumbHeightPx).coerceAtLeast(1f)
    val effectivePosition = if (dragging) dragPositionFraction else positionFraction.coerceIn(0f, 1f)

    Canvas(
        modifier =
            modifier
                .fillMaxHeight()
                .width(FastScrollbarTouchWidth)
                .padding(top = topInset, bottom = bottomInset)
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(trackHeightPx, baseThumbHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragPositionFraction = currentPositionFraction.coerceIn(0f, 1f)
                            PlatformHapticsPolicy.heavyImpact()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaFraction = dragAmount / baseTravelPx
                            dragPositionFraction =
                                (dragPositionFraction + deltaFraction).coerceIn(0f, 1f)
                            dragFractionHandler(deltaFraction)
                        },
                        onDragEnd = {
                            dragging = false
                        },
                        onDragCancel = {
                            dragging = false
                        },
                    )
                },
    ) {
        if (trackHeightPx <= 0 || baseThumbHeightPx <= 0f) return@Canvas

        val atStart = effectivePosition <= 0.0001f
        val atEnd = effectivePosition >= 0.9999f
        val thumbHeight =
            if (atStart || atEnd) {
                (baseThumbHeightPx * FastScrollbarEdgeCompression)
                    .coerceAtLeast(visualWidthPx * 2f)
            } else {
                baseThumbHeightPx
            }
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
