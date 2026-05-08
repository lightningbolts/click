package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Material [ColorScheme] tuned for true OLED sheet backgrounds so nested surfaces
 * (tabs, cards, text fields) stay on-black instead of default gray [surfaceContainerHigh].
 */
@Composable
fun OledSheetTheme(content: @Composable () -> Unit) {
    val bg = GlassSheetTokens.OledBlack
    val fg = GlassSheetTokens.OnOled
    val muted = GlassSheetTokens.OnOledMuted
    val elevated = GlassSheetTokens.GlassSurface
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = bg,
            surfaceContainerLow = bg,
            surfaceContainer = bg,
            surfaceContainerHigh = bg,
            surfaceContainerHighest = elevated,
            onSurface = fg,
            onSurfaceVariant = muted,
            outline = fg.copy(alpha = 0.34f),
            outlineVariant = fg.copy(alpha = 0.26f),
        ),
    ) {
        content()
    }
}
