@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret

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
    showBorder: Boolean = true,
    containerColor: Color? = null,
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
    val fillColor = containerColor ?: scheme.surface

    Box(
        modifier =
            modifier
                .clip(shape)
                .then(
                    if (fillColor.alpha > 0f) {
                        Modifier.background(fillColor)
                    } else {
                        Modifier
                    },
                ).then(
                    if (showBorder) {
                        Modifier.border(borderWidth, clickBorderColor(), shape)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Box(Modifier.padding(horizontal = contentPaddingHorizontal, vertical = contentPaddingVertical)) {
            content()
        }
    }
}
