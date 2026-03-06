package compose.project.click.click.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iOS-style interactive back container:
 * - drag starts only from the edge zone to avoid stealing in-screen gestures
 * - current screen follows the finger in real time via graphicsLayer translation
 * - previous screen uses a small parallax offset for depth
 * - release settles using spring + release velocity
 */
@Composable
fun InteractiveSwipeBackContainer(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    edgeSwipeWidth: Dp = 24.dp,
    onBack: () -> Unit,
    previousContent: @Composable () -> Unit,
    currentContent: @Composable () -> Unit
) {
    val dragOffset = remember { Animatable(0f) }
    // Conflated channel: trySend is non-suspending (safe inside @RestrictsSuspension scope);
    // only the latest unconsumed position is kept, so stale intermediate values are dropped.
    val snapChannel = remember { Channel<Float>(Channel.CONFLATED) }
    var isGestureActive by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val settleScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Drains the conflated channel outside the restricted coroutine scope.
    // Because the channel is conflated, only the most-recent finger position is ever
    // processed — no convoy of stale coroutines can form, eliminating the shimmer.
    LaunchedEffect(snapChannel) {
        for (target in snapChannel) {
            dragOffset.snapTo(target)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val edgeWidthPx = with(density) { edgeSwipeWidth.toPx() }
        val dragCommitThresholdPx = with(density) { 10.dp.toPx() }
        val dragJitterThresholdPx = with(density) { 0.75.dp.toPx() }
        val showPreviousLayer = isGestureActive || isSettling

        if (showPreviousLayer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                previousContent()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Pixel-align translation to prevent sub-pixel text/layer shimmer on iOS.
                    translationX = dragOffset.value.roundToInt().toFloat()
                    clip = true
                }
                .pointerInput(enabled, widthPx, edgeWidthPx) {
                    if (!enabled) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstPressedChange(PointerEventPass.Initial)
                        if (down.position.x > edgeWidthPx) {
                            return@awaitEachGesture
                        }

                        settleJob?.cancel()
                        settleJob = null
                        isSettling = false

                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)

                        var pointerId = down.id
                        val dragStartPosition = down.position
                        var hasCommittedToSwipe = false
                        var lastDispatchedOffset = 0f

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) break

                            val positionChanged = change.positionChanged()
                            if (positionChanged) {
                                tracker.addPosition(change.uptimeMillis, change.position)
                            }

                            val totalDelta: Offset = change.position - dragStartPosition
                            val dx = totalDelta.x
                            val dy = totalDelta.y

                            if (!hasCommittedToSwipe) {
                                val horizontalIntent = dx > dragCommitThresholdPx && dx > abs(dy)
                                val verticalIntent = abs(dy) > dragCommitThresholdPx && abs(dy) > abs(dx)

                                when {
                                    horizontalIntent -> {
                                        hasCommittedToSwipe = true
                                        isGestureActive = true
                                    }
                                    verticalIntent -> {
                                        snapChannel.trySend(0f)
                                        break
                                    }
                                }
                            }

                            if (hasCommittedToSwipe) {
                                val nextOffset = dx.coerceIn(0f, widthPx)
                                val movedEnough = abs(nextOffset - lastDispatchedOffset) >= dragJitterThresholdPx
                                if (!positionChanged && nextOffset == lastDispatchedOffset) {
                                    continue
                                }
                                if (!movedEnough && nextOffset != 0f && nextOffset != widthPx) {
                                    continue
                                }

                                lastDispatchedOffset = nextOffset
                                snapChannel.trySend(nextOffset)
                            }

                            if (hasCommittedToSwipe && positionChanged) {
                                change.consumePositionChangeCompat()
                            }
                        }

                        if (hasCommittedToSwipe && dragOffset.value > 0f) {
                            val velocityX = tracker.calculateVelocity().x
                            val progress = dragOffset.value / widthPx
                            val projected = progress + (velocityX / 3200f)
                            val shouldComplete = projected > 0.35f || velocityX > 650f
                            val target = if (shouldComplete) widthPx else 0f
                            isSettling = true
                            // Drain any residual channel value so the LaunchedEffect
                            // drain loop doesn't snapTo over the spring animation.
                            snapChannel.tryReceive()

                            settleJob = settleScope.launch {
                                dragOffset.animateTo(
                                    targetValue = target,
                                    animationSpec = spring(
                                        dampingRatio = 0.86f,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    initialVelocity = velocityX
                                )

                                if (shouldComplete) {
                                    onBack()
                                    dragOffset.snapTo(0f)
                                }
                                isGestureActive = false
                                isSettling = false
                                settleJob = null
                            }
                        } else {
                            snapChannel.trySend(0f)
                            isGestureActive = false
                            isSettling = false
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                currentContent()
            }
        }
    }
}

private fun PointerInputChange.consumePositionChangeCompat() {
    consume()
}

private suspend fun AwaitPointerEventScope.awaitFirstPressedChange(
    pass: PointerEventPass = PointerEventPass.Initial
): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent(pass)
        val pressed = event.changes.firstOrNull { it.pressed }
        if (pressed != null) return pressed
    }
}
