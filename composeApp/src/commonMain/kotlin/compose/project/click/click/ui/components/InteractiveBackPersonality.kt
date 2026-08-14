package compose.project.click.click.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged

/**
 * Underlay motion during interactive back — Instagram / WhatsApp style.
 *
 * Scale-during-drag was removed: it forced expensive layer invalidation every frame and
 * made the gesture feel laggy. Parallax [translationX] alone is applied by
 * [InteractiveSwipeBackContainer] / persistent underlay callers.
 */
fun Modifier.interactiveBackUnderlayScale(
    swipeOffsetPx: MutableFloatState? = null,
    progress: Float = 0f,
): Modifier = this

/**
 * @deprecated Header twist/jiggle was removed. Kept as a no-op so call sites compile.
 */
@Deprecated(
    message = "Use parallax on the underlay instead of header twist",
    replaceWith = ReplaceWith("this"),
)
fun Modifier.interactiveBackPersonality(
    swipeOffsetPx: MutableFloatState? = null,
    progress: Float = 0f,
): Modifier = this

/**
 * Shared state for screens that keep the *previous* destination mounted underneath an
 * [InteractiveSwipeBackContainer] instead of duplicating it into `previousContent`.
 *
 * Pass [dragOffsetPx] as the container's `externalDragOffsetPx`, forward
 * `onBehindLayersVisibleChanged` into [behindLayersVisible], and mark the persistent underlay with
 * [Modifier.interactiveSwipeBackUnderlay]. That is the whole contract; there is exactly one parallax
 * implementation in the app so Connections, Map events, the profile route, and Settings all move the
 * same way.
 *
 * [dismiss] exists so a tapped Back button drives the *same* offset animation as a swipe. Screens
 * that animate tap-back with a separate `slideOutHorizontally` exit end up with two different-looking
 * back transitions and no parallax on the tap path.
 */
@Stable
class InteractiveBackHostState {
    /** Foreground horizontal offset in px, 0 at rest and screen-width when fully dismissed. */
    val dragOffsetPx: MutableFloatState = mutableFloatStateOf(0f)

    /** True while the gesture (or a programmatic [dismiss]) should reveal what is behind. */
    var behindLayersVisible: Boolean by mutableStateOf(false)

    /** Last measured underlay width; captured in the layout phase by the underlay modifier. */
    internal var measuredWidthPx: Float = 1f

    /** Returns the layer to rest: no offset, nothing revealed behind. */
    fun reset() {
        dragOffsetPx.floatValue = 0f
        behindLayersVisible = false
    }

    /**
     * Animates the foreground off to the trailing edge, exactly as a completed swipe does. Callers
     * should remove the foreground route once this returns, then call [reset].
     */
    suspend fun dismiss(
        animationSpec: AnimationSpec<Float> =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
    ) {
        behindLayersVisible = true
        animate(
            initialValue = dragOffsetPx.floatValue,
            targetValue = measuredWidthPx.coerceAtLeast(1f),
            animationSpec = animationSpec,
        ) { value, _ -> dragOffsetPx.floatValue = value }
    }
}

@Composable
fun rememberInteractiveBackHostState(): InteractiveBackHostState = remember { InteractiveBackHostState() }

/**
 * The one canonical parallax transform for a persistently-mounted underlay: it sits
 * [InteractiveSwipeBackParallaxPeekRatio] of a screen width to the left at rest and slides to its
 * natural position as the foreground is pushed away.
 *
 * Reads happen inside [graphicsLayer], so following the finger never recomposes the underlay.
 */
fun Modifier.interactiveSwipeBackUnderlay(state: InteractiveBackHostState): Modifier =
    this
        .onSizeChanged { state.measuredWidthPx = it.width.toFloat().coerceAtLeast(1f) }
        .graphicsLayer {
            if (!state.behindLayersVisible) {
                translationX = 0f
                return@graphicsLayer
            }
            val width = size.width.coerceAtLeast(1f)
            val progress = (state.dragOffsetPx.floatValue.coerceIn(0f, width) / width).coerceIn(0f, 1f)
            translationX = -(width * InteractiveSwipeBackParallaxPeekRatio) * (1f - progress)
        }
