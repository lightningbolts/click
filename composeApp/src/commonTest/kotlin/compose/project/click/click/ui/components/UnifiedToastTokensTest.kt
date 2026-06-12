package compose.project.click.click.ui.components

import kotlin.test.Test
import kotlin.test.assertTrue

class UnifiedToastTokensTest {

    @Test
    fun unifiedToastAnimations_useSharedCompactTimings() {
        assertTrue(UnifiedToastTokens.EnterMillis in 200..300)
        assertTrue(UnifiedToastTokens.ExitMillis in 150..220)
        assertTrue(UnifiedToastTokens.DefaultDurationMs >= 2_000L)
    }
}
