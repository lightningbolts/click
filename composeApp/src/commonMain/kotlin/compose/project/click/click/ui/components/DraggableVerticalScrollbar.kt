@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val FastScrollbarTouchWidth = 28.dp
private val FastScrollbarVisualWidth = 3.dp
private val FastScrollbarVisualWidthDragging = 5.dp
private val FastScrollbarMinThumbHeight = 44.dp

/**
 * Draggable fast-scroll thumb for [LazyListState].
 *
 * The visual thumb stays narrow, while the touch target is deliberately wider so it can be
 * grabbed reliably with a finger. Dragging maps directly to list position instead of sending
 * synthetic scroll deltas through parent gesture handlers.
 */
@Composable
fun DraggableLazyListScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
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
    val estimatedVisibleFraction =
        (viewportSizePx.toFloat() / visibleExtentPx.toFloat() * visibleItems.size / totalItems)
            .coerceIn(0f, 1f)

    val first = visibleItems.first()
    val firstItemProgress =
        if (first.size > 0) {
            ((layoutInfo.viewportStartOffset - first.offset).toFloat() / first.size.toFloat())
                .coerceIn(0f, 1f)
        } else {
            0f
        }
    val logicalPosition =
        ((first.index + firstItemProgress) / (totalItems - 1).toFloat())
            .coerceIn(0f, 1f)
    val displayPosition =
        if (reverseLayout) 1f - logicalPosition else logicalPosition

    DraggableScrollbarTrack(
        positionFraction = displayPosition,
        visibleFraction = estimatedVisibleFraction,
        modifier = modifier,
        onSeek = { fraction ->
            val logicalFraction = if (reverseLayout) 1f - fraction else fraction
            val targetIndex =
                (logicalFraction * (totalItems - 1))
                    .roundToInt()
                    .coerceIn(0, totalItems - 1)
            state.scrollToItem(targetIndex)
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
) {
    val maxValue = state.maxValue
    if (maxValue <= 0) return

    val viewportFraction =
        if (maxValue == 0) 1f else (1f / (maxValue + 1f)).coerceAtLeast(0.08f)
    val positionFraction = state.value.toFloat() / maxValue.toFloat()

    DraggableScrollbarTrack(
        positionFraction = positionFraction,
        visibleFraction = viewportFraction,
        modifier = modifier,
        onSeek = { fraction ->
            state.scrollTo((fraction * maxValue).roundToInt().coerceIn(0, maxValue))
        },
    )
}

@Composable
private fun DraggableScrollbarTrack(
    positionFraction: Float,
    visibleFraction: Float,
    modifier: Modifier,
    onSeek: suspend (Float) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val seekHandler by rememberUpdatedState(onSeek)
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    var dragPositionFraction by remember { mutableFloatStateOf(positionFraction) }

    val minThumbHeightPx = with(density) { FastScrollbarMinThumbHeight.toPx() }
    val visualWidthPx =
        with(density) {
            (if (dragging) FastScrollbarVisualWidthDragging else FastScrollbarVisualWidth).toPx()
        }
    val thumbColor: Color =
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dragging) 0.72f else 0.42f)

    val effectivePosition = if (dragging) dragPositionFraction else positionFraction

    Canvas(
        modifier =
            modifier
                .fillMaxHeight()
                .width(FastScrollbarTouchWidth)
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(trackHeightPx) {
                    fun seekTo(pointerY: Float) {
                        val height = trackHeightPx.toFloat().coerceAtLeast(1f)
                        val fraction = (pointerY / height).coerceIn(0f, 1f)
                        dragPositionFraction = fraction
                        dragJob?.cancel()
                        dragJob =
                            scope.launch {
                                seekHandler(fraction)
                            }
                    }

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            seekTo(offset.y)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            seekTo(change.position.y)
                        },
                        onDragEnd = {
                            dragging = false
                            dragJob = null
                        },
                        onDragCancel = {
                            dragging = false
                            dragJob?.cancel()
                            dragJob = null
                        },
                    )
                },
    ) {
        if (trackHeightPx <= 0) return@Canvas

        val trackHeight = size.height
        val thumbHeight =
            (trackHeight * visibleFraction.coerceIn(0f, 1f))
                .coerceAtLeast(minThumbHeightPx)
                .coerceAtMost(trackHeight)
        val travel = (trackHeight - thumbHeight).coerceAtLeast(0f)
        val thumbTop = travel * effectivePosition.coerceIn(0f, 1f)
        val thumbLeft = size.width - visualWidthPx

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(visualWidthPx, thumbHeight),
            cornerRadius = CornerRadius(visualWidthPx / 2f, visualWidthPx / 2f),
        )
    }
}
