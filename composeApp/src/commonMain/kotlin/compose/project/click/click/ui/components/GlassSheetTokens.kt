package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.BorderHard
import compose.project.click.click.ui.theme.OnSurfaceLight
import compose.project.click.click.ui.theme.OnSurfaceVariant
import compose.project.click.click.ui.theme.SurfaceLight

/** Functional Clarity sheet tokens — opaque surfaces, hard borders, flat scrim. */
object GlassSheetTokens {
    val OledBlack: Color = Color(0xFF1A1C1C)
    val GlassSurface: Color = SurfaceLight
    val GlassBorder: Color = BorderHard
    val GlassBorderPressed: Color = BorderHard
    val OnOled: Color = OnSurfaceLight
    val OnOledMuted: Color = OnSurfaceVariant
    val SheetTopCorner = 16.dp
    val BentoExteriorCorner = 16.dp
    val BentoInteriorCorner = 8.dp
    /** Flat high-opacity dim (no blur). */
    val ScrimBaseAlpha = 0.40f
}
