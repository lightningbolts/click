package compose.project.click.click.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isGestureActive by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val settleScope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val edgeWidthPx = with(density) { edgeSwipeWidth.toPx() }
        val dragCommitThresholdPx = with(density) { 10.dp.toPx() }
        val showPreviousLayer = isGestureActive || isSettling || dragOffsetPx > 0.5f

        if (showPreviousLayer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = (dragOffsetPx / widthPx).coerceIn(0f, 1f)
                        translationX = -widthPx * 0.18f + (widthPx * 0.18f * progress)
                        alpha = 0.86f + (0.14f * progress)
                    }
            ) {
                previousContent()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dragOffsetPx
                    shadowElevation = if (dragOffsetPx > 0f) 14f else 0f
                    ambientShadowColor = Color.Black.copy(alpha = 0.18f)
                    spotShadowColor = Color.Black.copy(alpha = 0.22f)
                }
                .pointerInput(enabled, widthPx, edgeWidthPx) {
                    if (!enabled) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstPressedChange(PointerEventPass.Final)
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

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) break

                            tracker.addPosition(change.uptimeMillis, change.position)

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
                                        dragOffsetPx = 0f
                                        break
                                    }
                                }
                            }

                            if (hasCommittedToSwipe) {
                                dragOffsetPx = dx.coerceIn(0f, widthPx)
                            }

                            if (hasCommittedToSwipe && change.positionChanged()) {
                                change.consumePositionChangeCompat()
                            }
                        }

                        if (hasCommittedToSwipe && dragOffsetPx > 0f) {
                            val velocityX = tracker.calculateVelocity().x
                            val progress = dragOffsetPx / widthPx
                            val projected = progress + (velocityX / 3200f)
                            val shouldComplete = projected > 0.35f || velocityX > 650f
                            val target = if (shouldComplete) widthPx else 0f
                            isSettling = true

                            settleJob = settleScope.launch {
                                animate(
                                    initialValue = dragOffsetPx,
                                    targetValue = target,
                                    animationSpec = spring(
                                        dampingRatio = 0.86f,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    initialVelocity = velocityX
                                ) { value, _ ->
                                    dragOffsetPx = value
                                }

                                if (shouldComplete) {
                                    onBack()
                                    dragOffsetPx = 0f
                                }
                                isGestureActive = false
                                isSettling = false
                                settleJob = null
                            }
                        } else {
                            dragOffsetPx = 0f
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
