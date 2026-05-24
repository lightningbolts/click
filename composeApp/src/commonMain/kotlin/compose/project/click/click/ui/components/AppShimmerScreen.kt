package compose.project.click.click.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import click.composeapp.generated.resources.Res
import click.composeapp.generated.resources.click_logo
import compose.project.click.click.ui.theme.BackgroundDark
import compose.project.click.click.ui.theme.BackgroundLight
import org.jetbrains.compose.resources.painterResource

private const val LogoPulseDurationMs = 2_400
private const val LogoAlphaMin = 0.42f
private const val LogoAlphaMax = 1f

@Composable
fun AppShimmerScreen(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = if (isDarkMode) BackgroundDark else BackgroundLight
    val halfCycle = LogoPulseDurationMs / 2

    val transition = rememberInfiniteTransition(label = "app_logo_loading")
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
        label = "app_logo_pulse_alpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.click_logo),
            contentDescription = "Click",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { alpha = logoAlpha },
        )
    }
}
