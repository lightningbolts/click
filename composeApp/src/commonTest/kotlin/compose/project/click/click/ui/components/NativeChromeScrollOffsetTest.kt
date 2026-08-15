package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeChromeScrollOffsetTest {
    @Test
    fun firstRowOffsetIsPassedThrough() {
        assertEquals(0, nativeChromeScrollOffsetPx(0, 0))
        assertEquals(24, nativeChromeScrollOffsetPx(0, 24))
    }

    @Test
    fun laterRowsStayFullyCollapsedWithoutHidingChrome() {
        val offset = nativeChromeScrollOffsetPx(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0)
        assertTrue(offset >= 10_000)
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
