package compose.project.click.click.ui.components

import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.Modifier

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
