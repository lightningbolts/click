package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.clickBorderColor

/**
 * Functional Clarity pill — opaque fill + hard border (no blur, noise, or gradients).
 * API name retained for call-site compatibility.
 */
@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 24,
    noiseDensity: Float = 0.04f,
    backgroundStrength: Float = 0f,
    contentPaddingHorizontal: androidx.compose.ui.unit.Dp = 14.dp,
    contentPaddingVertical: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    // noiseDensity / backgroundStrength kept for signature compatibility; unused.
    @Suppress("UNUSED_VARIABLE")
    val ignoredNoise = noiseDensity
    @Suppress("UNUSED_VARIABLE")
    val ignoredStrength = backgroundStrength

    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val borderWidth = LocalPlatformStyle.current.cardBorderWidth
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(shape)
            .background(scheme.surface)
            .border(borderWidth, clickBorderColor(), shape),
    ) {
        Box(Modifier.padding(horizontal = contentPaddingHorizontal, vertical = contentPaddingVertical)) {
            content()
        }
    }
}
