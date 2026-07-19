package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.clickBorderColor
import compose.project.click.click.ui.theme.clickCardSurface
import compose.project.click.click.ui.theme.clickSheetOnSurface
import compose.project.click.click.ui.theme.clickSheetOnSurfaceMuted

/** Functional Clarity sheet tokens — opaque surfaces, hard borders, flat scrim. */
object GlassSheetTokens {
    /** Sheet/dialog background — follows app dark/light surface. */
    @Composable
    @ReadOnlyComposable
    fun OledBlack(): Color = MaterialTheme.colorScheme.surface

    @Composable
    @ReadOnlyComposable
    fun GlassSurface(): Color = clickCardSurface()

    @Composable
    @ReadOnlyComposable
    fun GlassBorder(): Color = clickBorderColor()

    @Composable
    @ReadOnlyComposable
    fun GlassBorderPressed(): Color = clickBorderColor(usePrimary = true).copy(alpha = 0.85f)

    @Composable
    @ReadOnlyComposable
    fun OnOled(): Color = clickSheetOnSurface()

    @Composable
    @ReadOnlyComposable
    fun OnOledMuted(): Color = clickSheetOnSurfaceMuted()

    val SheetTopCorner = 16.dp
    val BentoExteriorCorner = 16.dp
    val BentoInteriorCorner = 8.dp
    /** Flat high-opacity dim (no blur). */
    val ScrimBaseAlpha = 0.40f
}
