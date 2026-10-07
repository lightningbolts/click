package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeHeaderMetricsTest {
    @Test
    fun barHeight_interpolatesFromActionRowPlusLargeTitleToCompact() {
        assertEquals(109.0, NativeHeaderMetrics.barHeightPt(0f), 0.01)
        assertEquals(52.0, NativeHeaderMetrics.barHeightPt(1f), 0.01)
        assertEquals(80.5, NativeHeaderMetrics.barHeightPt(0.5f), 0.01)
    }

    @Test
    fun barHeight_includesSubtitleInsideExpandedBar() {
        assertEquals(145.0, NativeHeaderMetrics.barHeightPt(0f, hasSubtitle = true), 0.01)
        assertEquals(52.0, NativeHeaderMetrics.barHeightPt(1f, hasSubtitle = true), 0.01)
    }

    @Test
    fun barHeight_stackedSubtitleStaysCompact() {
        assertEquals(
            52.0,
            NativeHeaderMetrics.barHeightPt(1f, hasSubtitle = true, stackSubtitle = true),
            0.01,
        )
        assertEquals(
            52.0,
            NativeHeaderMetrics.barHeightPt(1f, hasSubtitle = true, stackSubtitle = false),
            0.01,
        )
    }

    @Test
    fun barHeight_growCompactSubtitle() {
        assertEquals(
            70.0,
            NativeHeaderMetrics.barHeightPt(
                collapseFraction = 1f,
                hasSubtitle = true,
                growCompactSubtitle = true,
            ),
            0.01,
        )
        assertEquals(
            52.0,
            NativeHeaderMetrics.barHeightPt(
                collapseFraction = 1f,
                hasSubtitle = true,
                stackSubtitle = true,
                growCompactSubtitle = false,
            ),
            0.01,
        )
    }

    @Test
    fun headerClearance_hubCompactGrowsForSubtitle() {
        assertEquals(
            117.dp,
            platformNativeHeaderClearance(
                statusBarTop = 47.dp,
                collapseFraction = 1f,
                hasSubtitle = true,
                growCompactSubtitle = true,
            ),
        )
    }
}
