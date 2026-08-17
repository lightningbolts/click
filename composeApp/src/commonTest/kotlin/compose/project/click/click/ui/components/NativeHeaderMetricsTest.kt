package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeHeaderMetricsTest {
    @Test
    fun expandedTitle_isLargeTitlePointSize() {
        assertEquals(34.0, NativeHeaderMetrics.titlePointSize(0f), 0.01)
        assertEquals(2, NativeHeaderMetrics.titleMaxLines(0f))
    }

    @Test
    fun compactTitle_isSeventeenPoint() {
        assertEquals(17.0, NativeHeaderMetrics.titlePointSize(1f), 0.01)
        assertEquals(1, NativeHeaderMetrics.titleMaxLines(1f))
    }

    @Test
    fun barHeight_interpolatesFromTwoLineLargeTitleToCompact() {
        assertEquals(98.0, NativeHeaderMetrics.barHeightPt(0f), 0.01)
        assertEquals(52.0, NativeHeaderMetrics.barHeightPt(1f), 0.01)
        assertEquals(75.0, NativeHeaderMetrics.barHeightPt(0.5f), 0.01)
    }

    @Test
    fun barHeight_includesSubtitleInsideExpandedBar() {
        assertEquals(134.0, NativeHeaderMetrics.barHeightPt(0f, hasSubtitle = true), 0.01)
        assertEquals(52.0, NativeHeaderMetrics.barHeightPt(1f, hasSubtitle = true), 0.01)
    }

    @Test
    fun barHeight_stackedIdentityStaysCompactAndCentersOnAvatar() {
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
        assertTrue(NativeHeaderMetrics.stackedIdentityColumnHeightPt() <= NativeHeaderMetrics.ChromeButtonSizePt)
        assertEquals(26.0, NativeHeaderMetrics.CompactChromeCenterYPt, 0.01)
    }

    @Test
    fun overlayUncover_clipsToDragOffsetNotCommitMidpoint() {
        assertEquals(0.0, NativeHeaderMetrics.overlayUncoverLeadingWidthPt(0.0), 0.01)
        assertEquals(0.0, NativeHeaderMetrics.overlayUncoverLeadingWidthPt(0.5), 0.01)
        assertEquals(24.0, NativeHeaderMetrics.overlayUncoverLeadingWidthPt(24.0), 0.01)
        assertEquals(200.0, NativeHeaderMetrics.overlayUncoverLeadingWidthPt(200.0), 0.01)
    }

    @Test
    fun compactTabRoot_inlinesOnlyWithoutBackOrIdentity() {
        assertTrue(NativeHeaderMetrics.isCompactTabRootChrome(1f, hasBack = false, hasIdentity = false))
        assertTrue(!NativeHeaderMetrics.isCompactTabRootChrome(1f, hasBack = true, hasIdentity = false))
        assertTrue(!NativeHeaderMetrics.isCompactTabRootChrome(1f, hasBack = false, hasIdentity = true))
        assertTrue(!NativeHeaderMetrics.isCompactTabRootChrome(0f, hasBack = false, hasIdentity = false))
    }

    @Test
    fun stackCompactSubtitle_forChatIdentityAndSubpages() {
        assertTrue(
            NativeHeaderMetrics.shouldStackCompactSubtitle(
                hasBack = true,
                hasIdentity = false,
                hasSubtitle = true,
                collapseFraction = 1f,
            ),
        )
        assertTrue(
            NativeHeaderMetrics.shouldStackCompactSubtitle(
                hasBack = false,
                hasIdentity = true,
                hasSubtitle = true,
                collapseFraction = 1f,
            ),
        )
        assertTrue(
            !NativeHeaderMetrics.shouldStackCompactSubtitle(
                hasBack = false,
                hasIdentity = false,
                hasSubtitle = true,
                collapseFraction = 1f,
            ),
        )
    }

    @Test
    fun growCompactBar_onlyForSubpageSubtitleNotIdentity() {
        assertTrue(
            NativeHeaderMetrics.shouldGrowCompactBarForStackedSubtitle(
                hasBack = true,
                hasIdentity = false,
                hasSubtitle = true,
                collapseFraction = 1f,
            ),
        )
        assertTrue(
            !NativeHeaderMetrics.shouldGrowCompactBarForStackedSubtitle(
                hasBack = true,
                hasIdentity = true,
                hasSubtitle = true,
                collapseFraction = 1f,
            ),
        )
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
    fun tabChromeClip_onlyWhenTabHeaderIsLive() {
        assertTrue(NativeHeaderMetrics.shouldClipTabChromeUnderOverlay(tabWantVisible = true))
        assertTrue(!NativeHeaderMetrics.shouldClipTabChromeUnderOverlay(tabWantVisible = false))
        assertTrue(NativeHeaderMetrics.shouldBindSharedTabChrome(chromeActive = true))
        assertTrue(!NativeHeaderMetrics.shouldBindSharedTabChrome(chromeActive = false))
        assertTrue(NativeHeaderMetrics.shouldKeepDestinationChromeBoundUnderOverlay())
        assertTrue(!NativeHeaderMetrics.shouldHideMapFloatingChromeForNearbyCover(nearbyCovering = true))
        assertTrue(!NativeHeaderMetrics.shouldHideMapFloatingChromeForNearbyCover(nearbyCovering = false))
    }

    @Test
    fun hostLeadingClip_revealsTrailingControlsAsOverlaySlides() {
        assertEquals(0.0, NativeHeaderMetrics.hostLeadingClipWidthPt(0.0, viewMinXPt = 16.0), 0.01)
        assertEquals(24.0, NativeHeaderMetrics.hostLeadingClipWidthPt(40.0, viewMinXPt = 16.0), 0.01)
        assertEquals(0.0, NativeHeaderMetrics.hostLeadingClipWidthPt(200.0, viewMinXPt = 320.0), 0.01)
        assertEquals(80.0, NativeHeaderMetrics.hostLeadingClipWidthPt(400.0, viewMinXPt = 320.0), 0.01)
        assertEquals(200.0, NativeHeaderMetrics.hostLeadingClipWidthPt(200.0, viewMinXPt = 0.0), 0.01)
    }

    @Test
    fun titleMaxWidth_wrapsBeforeTrailingActions() {
        val leading = NativeHeaderMetrics.titleLeadingInsetPt(hasBack = false)
        val trailing = NativeHeaderMetrics.titleTrailingInsetPt(trailingCount = 2)
        val width = NativeHeaderMetrics.titleMaxWidthPt(393.0, leading, trailing)
        // 393 - 20 leading - (80 cluster + 8 title gutter)
        assertEquals(285.0, width, 0.01)
        assertTrue(trailing > NativeHeaderMetrics.BarButtonWidthPt)
    }

    @Test
    fun titleMaxWidth_withBackAndMeasuredCluster() {
        val leading = NativeHeaderMetrics.titleLeadingInsetPt(hasBack = true, measuredBackMaxXPt = 52.0)
        val trailing = NativeHeaderMetrics.titleTrailingInsetPt(trailingCount = 3, measuredTrailingWidthPt = 120.0)
        val width = NativeHeaderMetrics.titleMaxWidthPt(390.0, leading, trailing)
        assertEquals(202.0, width, 0.01)
    }

    @Test
    fun headerClearance_compactMatchesStatusPlusBar() {
        assertEquals(
            99.dp,
            platformNativeHeaderClearance(statusBarTop = 47.dp, collapseFraction = 1f, hasSubtitle = false),
        )
    }

    @Test
    fun titleColumnTop_pinsFirstLineToCompactChromePlane() {
        assertEquals(5.5, NativeHeaderMetrics.titleColumnTopInsetPt(0f), 0.01)
        assertEquals(17.5, NativeHeaderMetrics.titleColumnTopInsetPt(1f), 0.01)
    }

    @Test
    fun collapsedGlass_isOpaqueOnlyWhenFullyCollapsed() {
        assertEquals(0f, NativeHeaderMetrics.collapsedGlassAlpha(0f))
        assertEquals(0.92f, NativeHeaderMetrics.collapsedGlassAlpha(1f), 0.01f)
        assertEquals(0.368f, NativeHeaderMetrics.collapsedGlassAlpha(0.4f), 0.01f)
    }
}
