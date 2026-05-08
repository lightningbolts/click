package compose.project.click.click.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM unit tests for [GlassCard] public constants (no Compose UI harness required).
 */
class GlassCardTest {

    @Test
    fun glassCornerRadius_matchesDesignToken() {
        assertEquals(28f, GlassCornerRadius.value)
    }

    @Test
    fun glassSheetTokens_sheetAndBentoRadii_matchPhase2026() {
        assertEquals(32f, GlassSheetTokens.SheetTopCorner.value)
        assertEquals(28f, GlassSheetTokens.BentoExteriorCorner.value)
        assertEquals(8f, GlassSheetTokens.BentoInteriorCorner.value)
        assertEquals(0.58f, GlassSheetTokens.ScrimBaseAlpha)
    }
}
