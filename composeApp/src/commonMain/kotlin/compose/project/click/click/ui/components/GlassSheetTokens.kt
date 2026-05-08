package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Phase 2026 — shared OLED / floating-glass tokens for sheets and bento surfaces. */
object GlassSheetTokens {
    val OledBlack: Color = Color(0xFF000000)
    val GlassSurface: Color = Color.White.copy(alpha = 0.05f)
    val GlassBorder: Color = Color.White.copy(alpha = 0.12f)
    val GlassBorderPressed: Color = Color.White.copy(alpha = 0.22f)
    val OnOled: Color = Color.White.copy(alpha = 0.92f)
    val OnOledMuted: Color = Color.White.copy(alpha = 0.62f)
    val SheetTopCorner = 32.dp
    val BentoExteriorCorner = 28.dp
    val BentoInteriorCorner = 8.dp
    val ScrimBaseAlpha = 0.58f
}
