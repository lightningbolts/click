package compose.project.click.click.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM unit tests for Functional Clarity card/sheet tokens (no Compose UI harness required).
 */
class GlassCardTest {

    @Test
    fun glassCornerRadius_matchesDesignToken() {
        assertEquals(16f, GlassCornerRadius.value)
    }

    @Test
    fun glassSheetTokens_sheetAndBentoRadii_matchFunctionalClarity() {
        assertEquals(16f, GlassSheetTokens.SheetTopCorner.value)
        assertEquals(16f, GlassSheetTokens.BentoExteriorCorner.value)
        assertEquals(8f, GlassSheetTokens.BentoInteriorCorner.value)
        assertEquals(0.40f, GlassSheetTokens.ScrimBaseAlpha)
    }
}
