package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import compose.project.click.click.ui.theme.BorderHard
import compose.project.click.click.ui.theme.OnSurfaceLight
import compose.project.click.click.ui.theme.OnSurfaceVariant
import compose.project.click.click.ui.theme.SurfaceContainer
import compose.project.click.click.ui.theme.SurfaceLight

/**
 * Sheet-local Material theme for Functional Clarity — opaque surfaces + hard outline.
 */
@Composable
fun OledSheetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = SurfaceLight,
            surfaceContainerLow = SurfaceLight,
            surfaceContainer = SurfaceContainer,
            surfaceContainerHigh = SurfaceContainer,
            surfaceContainerHighest = SurfaceContainer,
            onSurface = OnSurfaceLight,
            onSurfaceVariant = OnSurfaceVariant,
            outline = BorderHard,
            outlineVariant = BorderHard.copy(alpha = 0.4f),
        ),
    ) {
        content()
    }
}
