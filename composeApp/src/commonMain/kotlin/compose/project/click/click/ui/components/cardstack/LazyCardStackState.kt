package compose.project.click.click.ui.components.cardstack // pragma: allowlist secret

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Vendored from Hukumister LazyCardStack. Index + rewind (`animateToBack`) live here; drag
 * physics stay in [SwiperState].
 */
@Stable
class LazyCardStackState internal constructor(
    firstVisibleItemIndex: Int = 0,
    private val animationSpec: AnimationSpec<Offset>,
    horizontalThreshold: (Float) -> Float,
    verticalThreshold: (Float) -> Float,
    velocityThresholdPx: Float,
) {
    internal val swiperState =
        SwiperState(
            horizontalThreshold = horizontalThreshold,
            verticalThreshold = verticalThreshold,
            animationSpec = animationSpec,
            velocityThresholdPx = velocityThresholdPx,
        )

    val offset: Offset get() = swiperState.offset

    val rotation: Float get() = swiperState.rotation

    val scale: Float get() = swiperState.scale

    val isAnimationRunning: Boolean get() = swiperState.isAnimationRunning

    var itemsCount: Int by mutableIntStateOf(0)
        internal set

    var visibleItemIndex: Int by mutableIntStateOf(firstVisibleItemIndex)
        internal set

    suspend fun animateToBack(
        fromDirection: SwipeDirection,
        animation: AnimationSpec<Offset> = animationSpec,
    ) {
        val nextIndex = visibleItemIndex - 1
        if (nextIndex < 0) return
        visibleItemIndex = nextIndex
        val target = swiperState.offsetByDirection(fromDirection)
        swiperState.snapTo(target)
        swiperState.animateToCenter(animation)
    }

    suspend fun snapTo(index: Int) {
        val realIndex = index.coerceIn(0, (itemsCount - 1).coerceAtLeast(0))
        visibleItemIndex = realIndex
        swiperState.snapTo(Offset.Zero)
    }

    suspend fun animateToNext(
        direction: SwipeDirection,
        animation: AnimationSpec<Offset> = animationSpec,
    ) {
        val nextIndex = (visibleItemIndex + 1).coerceAtMost((itemsCount - 1).coerceAtLeast(0))
        swiperState.animateTo(direction, animation)
        visibleItemIndex = nextIndex
        swiperState.snapTo(Offset.Zero)
    }

    companion object {
        fun Saver(
            horizontalThreshold: (Float) -> Float,
            verticalThreshold: (Float) -> Float,
            animationSpec: AnimationSpec<Offset>,
            velocityThresholdPx: Float,
        ): Saver<LazyCardStackState, Int> =
            Saver(
                save = { it.visibleItemIndex },
                restore = { index ->
                    LazyCardStackState(
                        firstVisibleItemIndex = index,
                        animationSpec = animationSpec,
                        horizontalThreshold = horizontalThreshold,
                        verticalThreshold = verticalThreshold,
                        velocityThresholdPx = velocityThresholdPx,
                    )
                },
            )
    }
}

@Composable
fun rememberLazyCardStackState(
    firstVisibleItemIndex: Int = 0,
    horizontalThreshold: (Float) -> Float = { distance -> distance * 0.4f },
    verticalThreshold: (Float) -> Float = { distance -> distance * 0.3f },
    velocityThresholdPx: Float = 0f,
    animationSpec: AnimationSpec<Offset> = SpringSpec(),
): LazyCardStackState =
    rememberSaveable(
        saver =
            LazyCardStackState.Saver(
                horizontalThreshold = horizontalThreshold,
                verticalThreshold = verticalThreshold,
                animationSpec = animationSpec,
                velocityThresholdPx = velocityThresholdPx,
            ),
    ) {
        LazyCardStackState(
            firstVisibleItemIndex = firstVisibleItemIndex,
            animationSpec = animationSpec,
            horizontalThreshold = horizontalThreshold,
            verticalThreshold = verticalThreshold,
            velocityThresholdPx = velocityThresholdPx,
        )
    }
