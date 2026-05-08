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
}
