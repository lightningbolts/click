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
import org.jetbrains.compose.resources.painterResource

private const val LogoPulseDurationMs = 2_400
private const val LogoAlphaMin = 0.42f
private const val LogoAlphaMax = 1f

/** Centered Click logo with a gentle opacity pulse — shared loading indicator. */
@Composable
fun ClickLogoPulse(
    modifier: Modifier = Modifier,
    logoSize: Dp = 88.dp,
) {
    val halfCycle = LogoPulseDurationMs / 2
    val transition = rememberInfiniteTransition(label = "click_logo_loading")
    val logoAlpha by transition.animateFloat(
        initialValue = LogoAlphaMin,
        targetValue = LogoAlphaMin,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = LogoPulseDurationMs
                LogoAlphaMin at 0
                LogoAlphaMax at halfCycle using FastOutSlowInEasing
                LogoAlphaMin at LogoPulseDurationMs using FastOutSlowInEasing
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
            modifier = Modifier
                .size(logoSize)
                .graphicsLayer { alpha = logoAlpha },
        )
    }
}
