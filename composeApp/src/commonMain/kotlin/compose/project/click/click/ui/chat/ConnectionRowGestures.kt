@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

/**
 * Tap opens chat; hold opens the unified action sheet.
 *
 * Uses [combinedClickable] (not [androidx.compose.foundation.gestures.detectTapGestures]) so the
 * parent LazyColumn keeps ownership of drag + fling.
 *
 * Indication is a bounded ripple (never null) plus [connectionRowPressHighlight] wash.
 */
internal fun Modifier.connectionRowPressGestures(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
): Modifier =
    combinedClickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true, color = PrimaryBlue.copy(alpha = 0.16f)),
        onClick = onClick,
        onLongClick = {
            PlatformHapticsPolicy.heavyImpact()
            onLongPress()
        },
    )

/** Clear full-row wash on press — no left-edge accent (that sat under the avatar). */
private val PressHighlightIn = tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)
private val PressHighlightOut = tween<Float>(durationMillis = 380, easing = LinearOutSlowInEasing)

/** Stronger than the original 0.14, without a harsh left bar. */
private const val PressHighlightAlpha = 0.18f

/** Brief hold after a solid press so the wash survives into the chat transition. */
private const val PressHighlightHoldMs = 140L

/**
 * Full-row wash highlight — pair with [connectionRowPressGestures].
 *
 * On release we hold briefly when the wash was strong, then fade — so opening a chat
 * still shows a clear tapped-row flash under the push.
 */
@Composable
internal fun Modifier.connectionRowPressHighlight(interactionSource: MutableInteractionSource): Modifier {
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
                    if (wash.value >= 0.25f) {
                        kotlinx.coroutines.delay(PressHighlightHoldMs)
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
            drawRoundRect(
                color = PrimaryBlue.copy(alpha = PressHighlightAlpha * t.coerceIn(0f, 1f)),
                cornerRadius =
                    CornerRadius(
                        GlassSheetTokens.BentoExteriorCorner.toPx(),
                        GlassSheetTokens.BentoExteriorCorner.toPx(),
                    ),
            )
        }
    }
}
