package compose.project.click.click.ui.components.cardstack // pragma: allowlist secret

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Vendored from Hukumister LazyCardStack [SwiperState], with velocity-aware fling so a flick
 * past threshold commits even when travel is short (Home pile spec).
 */
class SwiperState(
    private val animationSpec: AnimationSpec<Offset> = SpringSpec(),
    private val horizontalThreshold: (Float) -> Float,
    private val verticalThreshold: (Float) -> Float,
    private val velocityThresholdPx: Float = 0f,
) {
    private var maxHeight: Int by mutableIntStateOf(0)
    private var maxWidth: Int by mutableIntStateOf(0)

    var offset: Offset by mutableStateOf(Offset.Zero)
        private set

    var scale: Float by mutableFloatStateOf(1f)
        private set

    var rotation: Float by mutableFloatStateOf(0f)
        private set

    var isAnimationRunning: Boolean by mutableStateOf(false)
        private set

    var isEnabled: Boolean by mutableStateOf(true)

    internal var directions: Set<SwipeDirection> by mutableStateOf(emptySet())

    internal var startDragAmount by mutableStateOf(Offset.Zero)

    internal val swiperDraggableState =
        SwiperDraggableState { delta ->
            offset += delta
            val travel = sqrt(offset.x.pow(2) + offset.y.pow(2))
            val width = maxWidth.toFloat().coerceAtLeast(1f)
            scale =
                normalize(
                    min = 0f,
                    max = width / 3f,
                    value = travel,
                    startRange = 0.8f,
                )
            rotation = computeRotation(startDragAmount, offset)
        }

    suspend fun animateToCenter(animation: AnimationSpec<Offset> = animationSpec) {
        try {
            internalAnimateTo(Offset.Zero, animation)
        } catch (_: CancellationException) {
            internalSnapTo(Offset.Zero)
        }
    }

    suspend fun animateTo(
        target: SwipeDirection,
        animation: AnimationSpec<Offset> = animationSpec,
    ) {
        try {
            animateToDirection(target, animation)
        } catch (_: CancellationException) {
            snapToDirection(target)
        }
    }

    suspend fun snapTo(target: Offset) {
        swiperDraggableState.drag {
            dragBy(target - offset)
        }
    }

    internal fun setMaxWidthAndHeight(
        height: Int,
        width: Int,
    ) {
        maxHeight = height
        maxWidth = width
    }

    internal fun peekFlingTarget(velocity: Offset = Offset.Zero): SwipeDirection? =
        computeTarget(offset, velocity)
            ?.takeIf { it in directions }
            ?.takeIf { isEnabled }

    /**
     * Commits a direction when travel or velocity crosses threshold; otherwise springs back.
     */
    internal suspend fun performFling(velocity: Offset = Offset.Zero): SwipeDirection? {
        val target = peekFlingTarget(velocity)
        val realTarget = target
        if (realTarget != null) {
            try {
                animateToDirection(realTarget)
            } catch (_: CancellationException) {
                snapToDirection(realTarget)
            }
        } else {
            internalAnimateTo(Offset.Zero, animationSpec)
        }
        return realTarget
    }

    internal fun offsetByDirection(direction: SwipeDirection): Offset =
        when (direction) {
            SwipeDirection.Left -> {
                val distance = -maxWidth - horizontalThreshold(maxWidth.toFloat())
                Offset(distance, offset.y)
            }
            SwipeDirection.Right -> {
                val distance = maxWidth + horizontalThreshold(maxWidth.toFloat())
                Offset(distance, offset.y)
            }
            SwipeDirection.Up -> {
                val distance = -maxHeight - verticalThreshold(maxHeight.toFloat())
                Offset(offset.x, distance)
            }
            SwipeDirection.Down -> {
                val distance = maxHeight + verticalThreshold(maxHeight.toFloat())
                Offset(offset.x, distance)
            }
        }

    private suspend fun internalSnapTo(
        target: Offset,
        dragPriority: MutatePriority = MutatePriority.Default,
    ) {
        swiperDraggableState.drag(dragPriority) {
            dragBy(target - offset)
        }
    }

    private suspend fun animateToDirection(
        target: SwipeDirection,
        animation: AnimationSpec<Offset> = animationSpec,
    ) {
        internalAnimateTo(offsetByDirection(target), animation)
    }

    private suspend fun snapToDirection(target: SwipeDirection) {
        internalSnapTo(offsetByDirection(target), MutatePriority.PreventUserInput)
    }

    private fun computeRotation(
        startDragPosition: Offset,
        offset: Offset,
    ): Float {
        val width = maxWidth.toFloat().coerceAtLeast(1f)
        val targetRotation =
            normalize(
                min = 0f,
                max = width,
                value = abs(offset.x),
                startRange = 0f,
                endRange = 15f,
            )
        val sign =
            if (startDragPosition.y < maxHeight.toFloat() / 2f) {
                offset.x.sign
            } else {
                -offset.x.sign
            }
        return targetRotation * sign
    }

    private suspend fun internalAnimateTo(
        target: Offset,
        animationSpec: AnimationSpec<Offset>,
    ) {
        swiperDraggableState.drag {
            try {
                var prevValue = offset
                isAnimationRunning = true
                animate(
                    typeConverter = Offset.VectorConverter,
                    initialValue = offset,
                    targetValue = target,
                    animationSpec = animationSpec,
                ) { value, _ ->
                    dragBy(value - prevValue)
                    prevValue = value
                }
            } finally {
                isAnimationRunning = false
            }
        }
    }

    private fun computeTarget(
        offset: Offset,
        velocity: Offset,
    ): SwipeDirection? {
        val horizontalRelativeThreshold = abs(horizontalThreshold(maxWidth.toFloat().coerceAtLeast(1f)))
        val verticalRelativeThreshold = abs(verticalThreshold(maxHeight.toFloat().coerceAtLeast(1f)))
        val speed = hypot(velocity.x, velocity.y)
        val flicked = speed >= velocityThresholdPx && velocityThresholdPx > 0f
        val travelX = abs(offset.x) > horizontalRelativeThreshold
        val travelY = abs(offset.y) > verticalRelativeThreshold
        val alongX = abs(offset.x) >= abs(offset.y)
        return when {
            (travelX || (flicked && alongX && abs(velocity.x) >= abs(velocity.y))) && offset.x <= 0f ->
                SwipeDirection.Left
            (travelX || (flicked && alongX && abs(velocity.x) >= abs(velocity.y))) && offset.x >= 0f ->
                SwipeDirection.Right
            (travelY || (flicked && !alongX)) && offset.y <= 0f -> SwipeDirection.Up
            (travelY || (flicked && !alongX)) && offset.y >= 0f -> SwipeDirection.Down
            flicked && abs(velocity.x) >= abs(velocity.y) && velocity.x < 0f -> SwipeDirection.Left
            flicked && abs(velocity.x) >= abs(velocity.y) && velocity.x > 0f -> SwipeDirection.Right
            flicked && velocity.y < 0f -> SwipeDirection.Up
            flicked && velocity.y > 0f -> SwipeDirection.Down
            else -> null
        }
    }

    private fun normalize(
        min: Float,
        max: Float,
        value: Float,
        startRange: Float = 0f,
        endRange: Float = 1f,
    ): Float {
        val span = (max - min).coerceAtLeast(1f)
        val coercedValue = value.coerceIn(min, max)
        return (coercedValue - min) / span * (endRange - startRange) + startRange
    }
}
