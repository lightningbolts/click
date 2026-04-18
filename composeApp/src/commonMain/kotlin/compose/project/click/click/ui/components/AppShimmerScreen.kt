package compose.project.click.click.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.AccentBlue
import compose.project.click.click.ui.theme.BackgroundDark
import compose.project.click.click.ui.theme.BackgroundLight
import compose.project.click.click.ui.theme.NeonPurple
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class AppShimmerVariant {
    AuthSignIn,
    AuthSignUp,
    AuthSignOut,
    OnboardingLoading,
    OnboardingWelcome,
    HomeReveal,
    Generic,
}

private data class AppShimmerCopy(
    val title: String,
    val subtitle: String,
)

@Composable
fun AppShimmerScreen(
    isDarkMode: Boolean,
    variant: AppShimmerVariant,
    modifier: Modifier = Modifier,
    titleOverride: String? = null,
    subtitleOverride: String? = null,
) {
    val defaultCopy = when (variant) {
        AppShimmerVariant.AuthSignIn -> AppShimmerCopy(
            title = "Signing you in",
            subtitle = "Restoring your session...",
        )

        AppShimmerVariant.AuthSignUp -> AppShimmerCopy(
            title = "Creating your account",
            subtitle = "Setting everything up...",
        )

        AppShimmerVariant.AuthSignOut -> AppShimmerCopy(
            title = "Signing you out",
            subtitle = "Closing your session safely...",
        )

        AppShimmerVariant.OnboardingLoading -> AppShimmerCopy(
            title = "Preparing your space",
            subtitle = "Syncing your profile and preferences...",
        )

        AppShimmerVariant.OnboardingWelcome -> AppShimmerCopy(
            title = "Welcome to Click",
            subtitle = "Getting your space ready...",
        )

        AppShimmerVariant.HomeReveal -> AppShimmerCopy(
            title = "Almost there",
            subtitle = "Launching your home...",
        )

        AppShimmerVariant.Generic -> AppShimmerCopy(
            title = "Loading",
            subtitle = "Please wait...",
        )
    }

    val title = titleOverride ?: defaultCopy.title
    val subtitle = subtitleOverride ?: defaultCopy.subtitle

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showContent = true
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = LinearOutSlowInEasing),
        label = "app_shimmer_content_alpha",
    )
    val contentScale by animateFloatAsState(
        targetValue = if (showContent) 1f else 1.02f,
        animationSpec = tween(durationMillis = 420, easing = LinearOutSlowInEasing),
        label = "app_shimmer_content_scale",
    )

    val shimmer = rememberInfiniteTransition(label = "app_shimmer")
    val phase by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "app_shimmer_phase",
    )

    val base = if (isDarkMode) BackgroundDark else BackgroundLight
    val accent = if (isDarkMode) PrimaryBlue.copy(alpha = 0.30f) else AccentBlue.copy(alpha = 0.22f)
    val glow = if (isDarkMode) NeonPurple.copy(alpha = 0.20f) else PrimaryBlue.copy(alpha = 0.14f)

    val progress = (phase / (2f * PI).toFloat()).coerceIn(0f, 1f)
    val wave = sin(phase)
    val drift = cos(phase * 1.7f)
    val start = Offset(
        x = -700f + (progress * 2400f) + (wave * 180f),
        y = -200f + (drift * 160f),
    )
    val end = Offset(
        x = -100f + (progress * 2400f) + (drift * 220f),
        y = 1700f + (wave * 220f),
    )
    val glowCenter = Offset(
        x = 420f + (sin(phase * 1.3f) * 260f),
        y = 560f + (cos(phase * 1.1f) * 220f),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .graphicsLayer {
                alpha = contentAlpha
                scaleX = contentScale
                scaleY = contentScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.62f),
                            glow.copy(alpha = 0.90f),
                            accent.copy(alpha = 0.54f),
                            Color.Transparent,
                        ),
                        start = start,
                        end = end,
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = 0.34f),
                            Color.Transparent,
                        ),
                        center = glowCenter,
                        radius = 720f,
                    ),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
