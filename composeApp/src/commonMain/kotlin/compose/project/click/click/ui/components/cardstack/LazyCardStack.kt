@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components.cardstack // pragma: allowlist secret

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import kotlin.math.hypot
import kotlinx.coroutines.launch

/**
 * Vendored LazyCardStack renderer adapted for Compose Multiplatform 1.11.
 *
 * Intentionally **not** a Click-from-scratch physics engine: drag, fling, rewind, and stacked
 * depth come from the LazyCardStack lineage. Pointer events are taken on
 * [PointerEventPass.Initial] so a parent LazyColumn cannot steal the first delta (the Home pile
 * "jolt once" bug). Tap is a separate no-slop path and never runs a competing animation.
 *
 * Do not fold this back into a custom `awaitTouchSlop` loop in [PhotoPileStack].
 */
@Composable
fun LazyCardStack(
    itemCount: Int,
    modifier: Modifier = Modifier,
    state: LazyCardStackState = rememberLazyCardStackState(),
    maxVisibleCards: Int = 3,
    directions: Set<SwipeDirection> =
        setOf(
            SwipeDirection.Left,
            SwipeDirection.Right,
            SwipeDirection.Up,
            SwipeDirection.Down,
        ),
    onSwipedItem: (index: Int, direction: SwipeDirection) -> Unit = { _, _ -> },
    /**
     * Return true to rewind (bring back the previous card) instead of dismissing.
     * Typical Home mapping: down (and left when history exists) → rewind.
     */
    shouldRewind: (direction: SwipeDirection) -> Boolean = { false },
    onTopCardTap: () -> Unit = {},
    item: @Composable BoxScope.(index: Int, layer: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val onSwipedItemState by rememberUpdatedState(onSwipedItem)
    val shouldRewindState by rememberUpdatedState(shouldRewind)
    val onTopCardTapState by rememberUpdatedState(onTopCardTap)
    var dragging by remember { mutableStateOf(false) }
    var stackSize by remember { mutableStateOf(IntSize.Zero) }

    SideEffect {
        state.itemsCount = itemCount
        state.swiperState.directions = directions
        state.swiperState.isEnabled = itemCount > 0
        if (stackSize.width > 0 && stackSize.height > 0) {
            state.swiperState.setMaxWidthAndHeight(
                height = stackSize.height,
                width = stackSize.width,
            )
        }
    }

    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = if (dragging) available else Offset.Zero

                override suspend fun onPreFling(available: Velocity): Velocity =
                    if (dragging) available else Velocity.Zero
            }
        }

    val visibleCount = minOf(maxVisibleCards, (itemCount - state.visibleItemIndex).coerceAtLeast(0))

    Box(
        modifier =
            modifier
                .onSizeChanged { stackSize = it }
                .nestedScroll(nestedScrollConnection)
                .pointerInput(state, itemCount, directions) {
                    awaitEachGesture {
                        val down =
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                        down.consume()
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        state.swiperState.startDragAmount = down.position
                        var dragged = false
                        val slop = viewConfiguration.touchSlop
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change =
                                event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                break
                            }
                            val delta = change.positionChange()
                            val travel =
                                hypot(
                                    change.position.x - down.position.x,
                                    change.position.y - down.position.y,
                                )
                            if (!dragged && travel >= slop) {
                                dragged = true
                                dragging = true
                            }
                            if (dragged && delta != Offset.Zero) {
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch {
                                    state.swiperState.swiperDraggableState.drag(
                                        MutatePriority.UserInput,
                                    ) {
                                        dragBy(delta)
                                    }
                                }
                            }
                        }
                        dragging = false
                        if (!dragged) {
                            onTopCardTapState()
                            return@awaitEachGesture
                        }
                        val velocity = velocityTracker.calculateVelocity()
                        scope.launch {
                            val velocityOffset = Offset(velocity.x, velocity.y)
                            val direction = state.swiperState.peekFlingTarget(velocityOffset)
                            if (direction != null && shouldRewindState(direction) && state.visibleItemIndex > 0) {
                                state.swiperState.animateToCenter()
                                state.animateToBack(direction)
                            } else {
                                val committed = state.swiperState.performFling(velocityOffset)
                                if (committed != null) {
                                    val currentIndex = state.visibleItemIndex
                                    val next =
                                        (currentIndex + 1).coerceAtMost((itemCount - 1).coerceAtLeast(0))
                                    if (next != currentIndex) {
                                        state.visibleItemIndex = next
                                        state.swiperState.snapTo(Offset.Zero)
                                        onSwipedItemState(currentIndex, committed)
                                    } else {
                                        state.swiperState.animateToCenter()
                                    }
                                }
                            }
                            state.swiperState.startDragAmount = Offset.Zero
                        }
                    }
                },
    ) {
        // Paint back-to-front so layer 0 (top card) is last and receives the drag transform.
        for (layer in (visibleCount - 1) downTo 0) {
            val index = state.visibleItemIndex + layer
            if (index >= itemCount) continue
            Box(
                modifier = Modifier.zIndex((visibleCount - layer).toFloat()),
            ) {
                item(index, layer)
            }
        }
    }

    LaunchedEffect(itemCount) {
        if (state.visibleItemIndex >= itemCount && itemCount > 0) {
            state.snapTo(itemCount - 1)
        }
    }
}
