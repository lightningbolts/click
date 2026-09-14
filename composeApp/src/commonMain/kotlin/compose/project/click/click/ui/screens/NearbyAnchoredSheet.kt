@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.clickCardSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class NearbySheetAnchor { Collapsed, Half, Expanded }

internal fun nearbySheetSettleAnchor(
    offset: Float,
    velocity: Float,
    expanded: Float,
    half: Float,
    collapsed: Float,
    velocityThreshold: Float,
): NearbySheetAnchor {
    val anchors = listOf(NearbySheetAnchor.Expanded to expanded, NearbySheetAnchor.Half to half, NearbySheetAnchor.Collapsed to collapsed)
    return when {
        velocity < -velocityThreshold -> NearbySheetAnchor.Expanded
        velocity > velocityThreshold -> NearbySheetAnchor.Collapsed
        else -> anchors.minBy { abs(it.second - offset) }.first
    }
}

/** A single mounted surface owns both the lip and the list; map gestures outside it stay free. */
@Composable
internal fun NearbyAnchoredSheet(
    expanded: Boolean,
    count: Int,
    bottomPadding: Dp,
    onExpandedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val top = with(density) { topInset.toPx() }
        val collapsed = (fullHeightPx - with(density) { (bottomPadding + 66.dp).toPx() }).coerceAtLeast(top)
        val half = (fullHeightPx * 0.46f).coerceIn(top, collapsed)
        var offset by remember(top, collapsed) { mutableFloatStateOf(if (expanded) half else collapsed) }
        var dragging by remember { mutableStateOf(false) }
        var dragDistance by remember { mutableFloatStateOf(0f) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val onExpanded by rememberUpdatedState(onExpandedChanged)
        val threshold = with(density) { 700.dp.toPx() }

        fun move(delta: Float): Float {
            val previous = offset
            offset = (offset + delta).coerceIn(top, collapsed)
            return offset - previous
        }

        fun settle(
            velocity: Float,
            tapped: Boolean = false,
        ) {
            dragging = false
            val anchor =
                if (tapped && abs(offset - collapsed) < 1f) {
                    NearbySheetAnchor.Half
                } else {
                    nearbySheetSettleAnchor(offset, velocity, top, half, collapsed, threshold)
                }
            val target =
                when (anchor) {
                    NearbySheetAnchor.Collapsed -> collapsed
                    NearbySheetAnchor.Half -> half
                    NearbySheetAnchor.Expanded -> top
                }
            settleJob?.cancel()
            settleJob =
                scope.launch {
                    animate(offset, target, animationSpec = tween(220)) { value, _ -> offset = value }
                    onExpanded(anchor != NearbySheetAnchor.Collapsed)
                }
        }
        LaunchedEffect(expanded, top, collapsed) {
            val target =
                when {
                    !expanded -> collapsed
                    offset == collapsed -> half
                    else -> null
                }
            if (!dragging && target != null && offset != target) {
                settleJob?.cancel()
                settleJob =
                    scope.launch {
                        animate(offset, target, animationSpec = tween(220)) { value, _ -> offset = value }
                    }
            }
        }
        DisposableEffect(top, collapsed) {
            onDispose { settleJob?.cancel() }
        }
        val nested =
            remember(top, half, collapsed) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput || available.y >= 0f || offset <= top) return Offset.Zero
                        settleJob?.cancel()
                        dragging = true
                        return Offset(0f, move(available.y))
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                        settleJob?.cancel()
                        dragging = true
                        return Offset(0f, move(available.y))
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (dragging && offset > top && offset < collapsed) {
                            settle(available.y)
                            return available
                        }
                        dragging = false
                        return Velocity.Zero
                    }

                    override suspend fun onPostFling(
                        consumed: Velocity,
                        available: Velocity,
                    ): Velocity {
                        if (dragging) settle(available.y)
                        return Velocity.Zero
                    }
                }
            }
        Column(
            Modifier
                .offset { IntOffset(0, offset.roundToInt()) }
                .fillMaxWidth()
                .height(with(density) { (fullHeightPx - offset).toDp() } - bottomPadding)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(clickCardSurface())
                .nestedScroll(nested),
        ) {
            EventsReopenChip(
                count = count,
                onClick = { settle(0f, tapped = true) },
                modifier =
                    Modifier.height(66.dp).draggable(
                        state =
                            rememberDraggableState { delta ->
                                dragDistance += abs(delta)
                                move(delta)
                            },
                        orientation = Orientation.Vertical,
                        startDragImmediately = true,
                        onDragStarted = {
                            settleJob?.cancel()
                            dragging = true
                            dragDistance = 0f
                        },
                        onDragStopped = { velocity -> settle(velocity, tapped = dragDistance < with(density) { 5.dp.toPx() }) },
                    ),
            )
            content(Modifier.weight(1f))
        }
    }
}
