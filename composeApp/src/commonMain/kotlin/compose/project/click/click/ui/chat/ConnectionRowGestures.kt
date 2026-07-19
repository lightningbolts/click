package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

/**
 * Tap opens chat; hold opens the unified action sheet.
 *
 * Uses [combinedClickable] (not [androidx.compose.foundation.gestures.detectTapGestures]) so the
 * parent LazyColumn keeps ownership of drag + fling.
 *
 * Indication stays null (glass aesthetic); press impact is [connectionRowPressHighlight].
 */
internal fun Modifier.connectionRowPressGestures(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = combinedClickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
    onLongClick = {
        PlatformHapticsPolicy.heavyImpact()
        onLongPress()
    },
)

/** Slow fill-in so light taps still read as a wash, not a flash. */
private val PressHighlightIn = tween<Float>(durationMillis = 420, easing = LinearOutSlowInEasing)
private val PressHighlightOut = tween<Float>(durationMillis = 380, easing = FastOutSlowInEasing)
private const val PressHighlightAlpha = 0.14f

/**
 * Full-row wash highlight — pair with [connectionRowPressGestures].
 *
 * On release we fade from the current wash level (no snap-to-peak). That keeps light taps
 * from looking like a sudden fill-in.
 */
@Composable
internal fun Modifier.connectionRowPressHighlight(
    interactionSource: MutableInteractionSource,
): Modifier {
    val wash = remember { Animatable(0f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    wash.stop()
                    wash.animateTo(1f, animationSpec = PressHighlightIn)
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel,
                -> {
                    wash.stop()
                    // Soft hold only if we already reached a visible wash — never snap up to 1f.
                    if (wash.value >= 0.35f) {
                        kotlinx.coroutines.delay(48L)
                    }
                    wash.animateTo(0f, animationSpec = PressHighlightOut)
                }
            }
        }
    }
    return this.drawWithContent {
        drawContent()
        val t = wash.value
        if (t > 0.001f) {
            drawRect(color = PrimaryBlue.copy(alpha = PressHighlightAlpha * t.coerceIn(0f, 1f)))
        }
    }
}
