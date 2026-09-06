@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import click.composeapp.generated.resources.Res
import click.composeapp.generated.resources.click_logo
import compose.project.click.click.platform.rememberReduceMotionEnabled
import compose.project.click.click.ui.theme.MotionTokens
import org.jetbrains.compose.resources.painterResource

private const val LOGO_PULSE_DURATION_MS = 2_400
private const val LOGO_ALPHA_MIN = 0.42f
private const val LOGO_ALPHA_MAX = 1f

/**
 * Scale + opacity pulse for tri-factor handshake (Scanning / Connecting).
 * Returns `(scale, alpha)` — both stay at rest values when [active] is false.
 */
@Composable
fun rememberConnectionHandshakePulse(active: Boolean): Pair<Float, Float> {
    val pulse =
        rememberWaitingPulse(
            active = active,
            durationMillis = MotionTokens.Pulse.Gentle,
            scaleMax = 1.04f,
            alphaMin = 0.92f,
        )
    return pulse.scale to pulse.alpha
}

/** Centered Click logo with a gentle opacity pulse — shared loading indicator. */
@Composable
fun ClickLogoPulse(
    modifier: Modifier = Modifier,
    logoSize: Dp = 88.dp,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    if (reduceMotion) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(Res.drawable.click_logo),
                contentDescription = "Loading",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(logoSize),
            )
        }
        return
    }
    val halfCycle = LOGO_PULSE_DURATION_MS / 2
    val transition = rememberInfiniteTransition(label = "click_logo_loading")
    val logoAlpha by transition.animateFloat(
        initialValue = LOGO_ALPHA_MIN,
        targetValue = LOGO_ALPHA_MIN,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = LOGO_PULSE_DURATION_MS
                        LOGO_ALPHA_MIN at 0
                        LOGO_ALPHA_MAX at halfCycle using FastOutSlowInEasing
                        LOGO_ALPHA_MIN at LOGO_PULSE_DURATION_MS using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "click_logo_pulse_alpha",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(Res.drawable.click_logo),
            contentDescription = "Loading",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .size(logoSize)
                    .graphicsLayer { alpha = logoAlpha },
        )
    }
}
