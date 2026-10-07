@file:Suppress(
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared native header geometry. */
object NativeHeaderMetrics {
    const val CompactBarHeightPt = 52.0
    const val LargeTitleLineHeightPt = 41.0
    const val LargeTitleMaxLines = 1
    const val LeadingInsetPt = 20.0
    const val TrailingInsetPt = 20.0
    const val SubtitleLineHeightPt = 18.0
    const val SubtitleMaxLines = 2
    const val ExpandedVerticalPaddingPt = 8.0
    const val ChromeButtonSizePt = 40.0

    val ExpandedBarHeightPt: Double =
        CompactBarHeightPt + ExpandedVerticalPaddingPt + LargeTitleLineHeightPt * LargeTitleMaxLines +
            ExpandedVerticalPaddingPt

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

    fun headerClearanceDp(
        statusBarTop: Dp,
        collapseFraction: Float,
        hasSubtitle: Boolean,
        stackSubtitle: Boolean = false,
        growCompactSubtitle: Boolean = false,
    ): Dp =
        statusBarTop +
            barHeightDp(collapseFraction, hasSubtitle, stackSubtitle, growCompactSubtitle)
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
