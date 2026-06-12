package compose.project.click.click.ui.components

import kotlin.test.Test
import kotlin.test.assertTrue

class UnifiedPopupTokensTest {

    @Test
    fun unifiedPopupAnimations_areFasterThanLegacyMediaOverlay() {
        assertTrue(UnifiedPopupTokens.FadeInMillis < 420)
        assertTrue(UnifiedPopupTokens.FadeOutMillis < 280)
        assertTrue(UnifiedPopupTokens.ContentClearDelayMillis < 320)
    }
}
