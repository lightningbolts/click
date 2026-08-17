@file:Suppress(
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared iOS-native header geometry. Compose 1.dp == 1pt on iOS, so these values drive both
 * the host `UINavigationBar` height and the Compose clearance under it.
 *
 * Expanded chrome is standard large-title type (34pt, up to 2 lines) on the **same row** as
 * bar buttons. The first line of that title shares a horizontal plane with compact subpage
 * chrome (back button + 17pt title). Extra lines and subtitles grow downward. The title
 * column wraps before the action cluster; it does not use UIKit `prefersLargeTitles`.
 */
object NativeHeaderMetrics {
    const val CompactBarHeightPt = 52.0
    const val LargeTitlePointSize = 34.0
    const val CompactTitlePointSize = 17.0
    const val LargeTitleLineHeightPt = 41.0
    const val LargeTitleMaxLines = 2
    const val CompactTitleMaxLines = 1
    const val TitleGutterPt = 8.0
    const val LeadingInsetPt = 20.0
    const val TrailingInsetPt = 20.0
    const val BarButtonWidthPt = 44.0
    const val SubtitleLineHeightPt = 18.0
    const val SubtitleMaxLines = 2
    const val ExpandedVerticalPaddingPt = 8.0
    const val CompactTitleCollapseThreshold = 0.45f
    const val MinTitleWidthPt = 64.0
    const val ChromeButtonSizePt = 40.0
    const val ChromeIconPointSize = 17.0
    const val ClusterIconSpacingPt = 0.0
    const val ClusterContentInsetPt = 0.0
    const val CompactRowBottomPaddingPt = 8.0
    const val GlassFadeExtensionPt = 8.0
    const val CollapsedGlassAlphaMax = 0.92f
    const val StackedIdentitySpacingPt = 3.0
    const val StackedIdentitySubtitlePointSize = 12.0
    const val OverlayUncoverEpsilonPt = 0.5

    /** Vertical center of compact chrome (40pt buttons in the 52pt bar). Expanded titles pin here too. */
    const val CompactChromeCenterYPt = CompactBarHeightPt / 2.0

    val CompactBarHeight: Dp = CompactBarHeightPt.toFloat().dp

    val ExpandedBarHeightPt: Double =
        ExpandedVerticalPaddingPt + LargeTitleLineHeightPt * LargeTitleMaxLines +
            ExpandedVerticalPaddingPt

    val ExpandedBarHeight: Dp = ExpandedBarHeightPt.toFloat().dp

    fun titlePointSize(collapseFraction: Float): Double {
        val fraction = collapseFraction.coerceIn(0f, 1f).toDouble()
        return LargeTitlePointSize - (LargeTitlePointSize - CompactTitlePointSize) * fraction
    }

    fun titleMaxLines(collapseFraction: Float): Int =
        if (collapseFraction < CompactTitleCollapseThreshold) {
            LargeTitleMaxLines
        } else {
            CompactTitleMaxLines
        }

    fun isCompactTitle(collapseFraction: Float): Boolean = collapseFraction >= CompactTitleCollapseThreshold

    fun barHeightPt(
        collapseFraction: Float,
        hasSubtitle: Boolean = false,
        stackSubtitle: Boolean = false,
        growCompactSubtitle: Boolean = false,
    ): Double {
        val fraction = collapseFraction.coerceIn(0f, 1f).toDouble()
        val expanded =
            ExpandedBarHeightPt +
                if (hasSubtitle) SubtitleLineHeightPt * SubtitleMaxLines else 0.0
        // Identity stacks stay 52pt (centered on the avatar). Subpages with a
        // back button grow so the subtitle is not clipped under the glass.
        val compact =
            CompactBarHeightPt +
                if (growCompactSubtitle && hasSubtitle) SubtitleLineHeightPt else 0.0
        return expanded + (compact - expanded) * fraction
    }

    fun barHeightDp(
        collapseFraction: Float,
        hasSubtitle: Boolean = false,
        stackSubtitle: Boolean = false,
        growCompactSubtitle: Boolean = false,
    ): Dp = barHeightPt(collapseFraction, hasSubtitle, stackSubtitle, growCompactSubtitle).toFloat().dp

    fun subtitleHeightPt(
        hasSubtitle: Boolean,
        collapseFraction: Float,
    ): Double {
        if (!hasSubtitle) return 0.0
        return SubtitleLineHeightPt * SubtitleMaxLines *
            (1.0 - collapseFraction.coerceIn(0f, 1f).toDouble())
    }

    fun subtitleHeightDp(
        hasSubtitle: Boolean,
        collapseFraction: Float,
    ): Dp = subtitleHeightPt(hasSubtitle, collapseFraction).toFloat().dp

    fun headerClearanceDp(
        statusBarTop: Dp,
        collapseFraction: Float,
        hasSubtitle: Boolean,
        stackSubtitle: Boolean = false,
        growCompactSubtitle: Boolean = false,
    ): Dp =
        statusBarTop +
            barHeightDp(collapseFraction, hasSubtitle, stackSubtitle, growCompactSubtitle)

    fun collapseRangeDp(hasSubtitle: Boolean): Dp = headerClearanceDp(0.dp, 0f, hasSubtitle) - headerClearanceDp(0.dp, 1f, hasSubtitle)

    fun collapsedGlassAlpha(collapseFraction: Float): Float = collapseFraction.coerceIn(0f, 1f) * CollapsedGlassAlphaMax

    fun titleLeadingInsetPt(
        hasBack: Boolean,
        measuredBackMaxXPt: Double? = null,
    ): Double =
        if (hasBack) {
            (measuredBackMaxXPt ?: BarButtonWidthPt) + TitleGutterPt
        } else {
            LeadingInsetPt
        }

    fun titleTrailingInsetPt(
        trailingCount: Int,
        measuredTrailingWidthPt: Double? = null,
    ): Double {
        val width =
            measuredTrailingWidthPt
                ?: if (trailingCount <= 0) {
                    0.0
                } else {
                    val icons = trailingCount * ChromeButtonSizePt
                    val gaps = (trailingCount - 1).coerceAtLeast(0) * ClusterIconSpacingPt
                    ClusterContentInsetPt * 2.0 + icons + gaps
                }
        return if (width <= 0.0) TrailingInsetPt else width + TitleGutterPt
    }

    fun titleMaxWidthPt(
        barWidthPt: Double,
        leadingInsetPt: Double,
        trailingInsetPt: Double,
    ): Double = (barWidthPt - leadingInsetPt - trailingInsetPt).coerceAtLeast(MinTitleWidthPt)

    /**
     * Top inset for the title column so the first line shares [CompactChromeCenterYPt] with
     * compact subpage chrome (back button, avatar, action capsule).
     */
    fun titleColumnTopInsetPt(collapseFraction: Float): Double {
        val t = collapseFraction.coerceIn(0f, 1f).toDouble()
        val lineHeight =
            LargeTitleLineHeightPt + (CompactTitlePointSize - LargeTitleLineHeightPt) * t
        return CompactChromeCenterYPt - lineHeight / 2.0
    }

    /**
     * Leading strip of the underlay (tab) header that should paint during interactive-back.
     * Zero while the overlay is at rest so translucent chat glass cannot show the list title.
     */
    fun overlayUncoverLeadingWidthPt(offsetPt: Double): Double = if (offsetPt <= OverlayUncoverEpsilonPt) 0.0 else offsetPt

    /** Username + status column height for a compact chat identity stack. */
    fun stackedIdentityColumnHeightPt(): Double = CompactTitlePointSize + StackedIdentitySpacingPt + StackedIdentitySubtitlePointSize

    /**
     * Collapsed tab-root chrome inlines title + subtitle. Subpages (back) and chat identity
     * always stack those lines so the subtitle is not truncated beside the title.
     */
    fun isCompactTabRootChrome(
        collapseFraction: Float,
        hasBack: Boolean,
        hasIdentity: Boolean,
    ): Boolean = !hasBack && !hasIdentity && isCompactTitle(collapseFraction)

    /**
     * Compact two-line identity (avatar + name/status) or subpage title + subtitle, centered
     * on the 40pt chrome plane.
     */
    fun shouldStackCompactSubtitle(
        hasBack: Boolean,
        hasIdentity: Boolean,
        hasSubtitle: Boolean,
        collapseFraction: Float,
    ): Boolean = hasSubtitle && isCompactTitle(collapseFraction) && (hasIdentity || hasBack)

    /**
     * Scan QR / Tap to Connect compact chrome must grow so the instruction line sits
     * *in* the native bar, not under the glass plate. Chat identity does not grow.
     */
    fun shouldGrowCompactBarForStackedSubtitle(
        hasBack: Boolean,
        hasIdentity: Boolean,
        hasSubtitle: Boolean,
        collapseFraction: Float,
    ): Boolean =
        shouldStackCompactSubtitle(hasBack, hasIdentity, hasSubtitle, collapseFraction) &&
            hasBack &&
            !hasIdentity

    /**
     * Only the live tab-root header may be clipped into the overlay peek. A stale header from
     * a previous tab (e.g. Add Click still sitting in the shared layer while Map is showing)
     * must not be unhidden.
     */
    fun shouldClipTabChromeUnderOverlay(tabWantVisible: Boolean): Boolean = tabWantVisible

    /**
     * Swipe underlays and inactive AnimatedContent copies must not bind the shared tab
     * `UINavigationBar`. Map in particular has no tab header — showing the previous tab's
     * title over the map at gesture start is a bug.
     */
    fun shouldBindSharedTabChrome(chromeActive: Boolean): Boolean = chromeActive
}

/** Status bar + interpolating native bar + collapsing subtitle overlay. */
fun platformNativeHeaderClearance(
    statusBarTop: Dp,
    collapseFraction: Float = 1f,
    hasSubtitle: Boolean = false,
    stackSubtitle: Boolean = false,
): Dp =
    NativeHeaderMetrics.headerClearanceDp(
        statusBarTop = statusBarTop,
        collapseFraction = collapseFraction,
        hasSubtitle = hasSubtitle,
        stackSubtitle = stackSubtitle,
    )
