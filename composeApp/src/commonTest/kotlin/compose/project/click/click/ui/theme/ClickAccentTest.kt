package compose.project.click.click.ui.theme // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClickAccentTest {
    @Test
    fun slotRatioIsFivePurpleThreeBlue() {
        val colors = (0 until 8).map { ClickAccent.colorForSlot(it) }
        val purple = colors.count { it == ClickAccent.Purple }
        val blue = colors.count { it == ClickAccent.Blue }
        assertEquals(5, purple)
        assertEquals(3, blue)
    }

    @Test
    fun stableIdsStayInRatioBand() {
        val purple =
            (0 until 800).count {
                ClickAccent.colorForStableId("accent-$it") == ClickAccent.Purple
            }
        val ratio = purple.toDouble() / 800.0
        assertTrue(ratio in 0.58..0.68, "purple ratio was $ratio")
    }
}
