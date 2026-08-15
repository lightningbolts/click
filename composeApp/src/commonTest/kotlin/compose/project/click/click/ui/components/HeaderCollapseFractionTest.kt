package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderCollapseFractionTest {
    @Test
    fun computeHeaderCollapseFraction_atThreshold_isFullyCollapsed() {
        assertEquals(
            1f,
            computeHeaderCollapseFraction(
                scrollOffsetPx = 20,
                firstVisibleItemIndex = 0,
                thresholdPx = 20,
            ),
        )
    }

    @Test
    fun computeHeaderCollapseFraction_atHalfThreshold_isHalfway() {
        assertEquals(
            0.5f,
            computeHeaderCollapseFraction(
                scrollOffsetPx = 10,
                firstVisibleItemIndex = 0,
                thresholdPx = 20,
            ),
        )
    }

    @Test
    fun computeHeaderCollapseFraction_pastFirstItem_isFullyCollapsedNotHidden() {
        // Collapse is compact chrome. Native scaffolds must never treat this as "hide the bar".
        assertEquals(
            1f,
            computeHeaderCollapseFraction(
                scrollOffsetPx = 0,
                firstVisibleItemIndex = 1,
                thresholdPx = 20,
            ),
        )
    }
}
