package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import compose.project.click.click.ui.theme.LocalIsDarkMode
import compose.project.click.click.ui.theme.OnSurfaceDark
import compose.project.click.click.ui.theme.OnSurfaceLight
import compose.project.click.click.ui.theme.OnSurfaceVariant
import compose.project.click.click.ui.theme.SurfaceContainer
import compose.project.click.click.ui.theme.SurfaceContainerDark
import compose.project.click.click.ui.theme.SurfaceDark
import compose.project.click.click.ui.theme.SurfaceLight
import compose.project.click.click.ui.theme.SurfaceVariantDark
import compose.project.click.click.ui.theme.clickBorderColor

/**
 * Sheet-local Material theme for Functional Clarity — opaque surfaces + hard outline.
 * Follows app dark/light mode (no forced light surfaces).
 */
@Composable
fun OledSheetTheme(content: @Composable () -> Unit) {
    val dark = LocalIsDarkMode.current
    val border = clickBorderColor()
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = if (dark) SurfaceDark else SurfaceLight,
            surfaceContainerLow = if (dark) SurfaceDark else SurfaceLight,
            surfaceContainer = if (dark) SurfaceContainerDark else SurfaceContainer,
            surfaceContainerHigh = if (dark) SurfaceContainerDark else SurfaceContainer,
            surfaceContainerHighest = if (dark) SurfaceVariantDark else SurfaceContainer,
            onSurface = if (dark) OnSurfaceDark else OnSurfaceLight,
            onSurfaceVariant = if (dark) Color(0xFFC8CBCB) else OnSurfaceVariant,
            outline = border,
            outlineVariant = border.copy(alpha = 0.4f),
        ),
    ) {
        content()
    }
}
