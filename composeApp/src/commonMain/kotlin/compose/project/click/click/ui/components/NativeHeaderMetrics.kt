@file:Suppress(
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared iOS-native header geometry. Compose 1.dp == 1pt on iOS. */
object NativeHeaderMetrics {
    const val CompactBarHeightPt = 52.0
    const val LargeTitlePointSize = 34.0
    const val CompactTitlePointSize = 17.0
    const val LargeTitleLineHeightPt = 41.0
    const val LargeTitleMaxLines = 1
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

    // Let the material feather into page content rather than ending like a card/slab.
    const val GlassFadeExtensionPt = 16.0

    // Native glass already contributes contrast. A lighter host alpha keeps page color/content
    // visible underneath, closer to iOS system navigation and WhatsApp's integrated treatment.
    const val CollapsedGlassAlphaMax = 0.64f
    const val StackedIdentitySpacingPt = 3.0
    const val StackedIdentitySubtitlePointSize = 12.0
    const val OverlayUncoverEpsilonPt = 0.5
    const val OverlayHideKeepClipFraction = 0.92
    const val OverlayCompletedSwipeFraction = 0.85

    const val CompactChromeCenterYPt = CompactBarHeightPt / 2.0

    val CompactBarHeight: Dp = CompactBarHeightPt.toFloat().dp

    val ExpandedBarHeightPt: Double =
        CompactBarHeightPt + ExpandedVerticalPaddingPt + LargeTitleLineHeightPt * LargeTitleMaxLines +
            ExpandedVerticalPaddingPt

    val ExpandedBarHeight: Dp = ExpandedBarHeightPt.toFloat().dp

    fun titlePointSize(collapseFraction: Float): Double {
        val fraction = collapseFraction.coerceIn(0f, 1f).toDouble()
        return LargeTitlePointSize - (LargeTitlePointSize - CompactTitlePointSize) * fraction
    }

    fun titleMaxLines(collapseFraction: Float): Int =
        if (collapseFraction < CompactTitleCollapseThreshold) LargeTitleMaxLines else CompactTitleMaxLines

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

    fun titleColumnTopInsetPt(collapseFraction: Float): Double {
        val t = collapseFraction.coerceIn(0f, 1f).toDouble()
        val expandedTop = CompactBarHeightPt + ExpandedVerticalPaddingPt
        val compactTop = CompactChromeCenterYPt - CompactTitlePointSize / 2.0
        return expandedTop + (compactTop - expandedTop) * t
    }

    fun overlayUncoverLeadingWidthPt(offsetPt: Double): Double = if (offsetPt <= OverlayUncoverEpsilonPt) 0.0 else offsetPt

    fun stackedIdentityColumnHeightPt(): Double = CompactTitlePointSize + StackedIdentitySpacingPt + StackedIdentitySubtitlePointSize

    fun isCompactTabRootChrome(
        collapseFraction: Float,
        hasBack: Boolean,
        hasIdentity: Boolean,
    ): Boolean = !hasBack && !hasIdentity && isCompactTitle(collapseFraction)

    fun shouldStackCompactSubtitle(
        hasBack: Boolean,
        hasIdentity: Boolean,
        hasSubtitle: Boolean,
        collapseFraction: Float,
    ): Boolean = hasSubtitle && isCompactTitle(collapseFraction) && (hasIdentity || hasBack)

    fun shouldGrowCompactBarForStackedSubtitle(
        hasBack: Boolean,
        hasIdentity: Boolean,
        hasSubtitle: Boolean,
        collapseFraction: Float,
    ): Boolean =
        shouldStackCompactSubtitle(hasBack, hasIdentity, hasSubtitle, collapseFraction) &&
            hasBack &&
            !hasIdentity

    fun shouldClipTabChromeUnderOverlay(tabWantVisible: Boolean): Boolean = tabWantVisible

    fun shouldBindSharedTabChrome(chromeActive: Boolean): Boolean = chromeActive

    fun shouldKeepDestinationChromeBoundUnderOverlay(): Boolean = true

    fun shouldHideMapFloatingChromeForNearbyCover(nearbyCovering: Boolean): Boolean = false

    fun shouldClearLeadingClipOnOverlayHide(
        uncoverLeadingPt: Double,
        hostWidthPt: Double,
    ): Boolean {
        if (uncoverLeadingPt < 0.0) return false
        if (hostWidthPt <= 0.0) return true
        return uncoverLeadingPt < hostWidthPt * OverlayHideKeepClipFraction
    }

    fun shouldApplyOverlaySlideTransform(
        overlayWantVisible: Boolean,
        newOffsetPt: Double,
        currentAppliedOffsetPt: Double,
        hostWidthPt: Double,
    ): Boolean {
        if (!overlayWantVisible) return false
        if (newOffsetPt > OverlayUncoverEpsilonPt) return true
        if (hostWidthPt > 0.0 &&
            currentAppliedOffsetPt >= hostWidthPt * OverlayCompletedSwipeFraction
        ) {
            return false
        }
        return true
    }

    fun shouldRematerializeChromeOnUnsuppress(): Boolean = false

    fun shouldReapplyTabBarHeightOnOverlayHide(): Boolean = OverlayExclusiveBindPolicy.shouldReapplyTabBarHeightOnOverlayHide()

    fun hostLeadingClipWidthPt(
        uncoverLeadingPt: Double,
        viewMinXPt: Double,
    ): Double = (uncoverLeadingPt - viewMinXPt).coerceAtLeast(0.0)
}

fun platformNativeHeaderClearance(
    statusBarTop: Dp,
    collapseFraction: Float = 1f,
    hasSubtitle: Boolean = false,
    stackSubtitle: Boolean = false,
    growCompactSubtitle: Boolean = false,
): Dp =
    NativeHeaderMetrics.headerClearanceDp(
        statusBarTop = statusBarTop,
        collapseFraction = collapseFraction,
        hasSubtitle = hasSubtitle,
        stackSubtitle = stackSubtitle,
        growCompactSubtitle = growCompactSubtitle,
    )
