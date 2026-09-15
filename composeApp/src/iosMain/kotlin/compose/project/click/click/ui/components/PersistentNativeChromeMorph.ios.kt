@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.CoreGraphics.CGAffineTransform
import platform.QuartzCore.CATransaction
import platform.UIKit.NSLayoutConstraintAxisVertical
import platform.UIKit.UIView

/**
 * Visual transition policy for the single persistent iOS navigation chrome host.
 *
 * Route changes never replace the physical UIKit controls. Their semantic role changes in-place
 * (menu -> back -> close and the reverse), which is the interaction model required for stable
 * WhatsApp-style Liquid Glass navigation.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentChromeMorphProgress(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val distanceFromMid = kotlin.math.abs(p - 0.5f) / 0.5f
    val scale = 0.82 + (0.18 * distanceFromMid)
    val alpha = 0.90 + (0.10 * distanceFromMid)
    val transform = scaleTransform(scale)

    CATransaction.begin()
    CATransaction.setDisableActions(true)
    backButton.transform = transform
    trailingCluster.transform = transform
    backButton.alpha = alpha
    trailingCluster.alpha = alpha
    // Titles stay readable while controls morph. Route-title handoff must never make the header
    // disappear in the middle of an interactive gesture.
    titleColumn.alpha = 1.0
    CATransaction.commit()
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.resetPersistentChromeMorphVisuals(animated: Boolean) {
    val identity = identityTransform()
    val apply = {
        backButton.transform = identity
        trailingCluster.transform = identity
        backButton.alpha = 1.0
        trailingCluster.alpha = 1.0
        titleColumn.alpha = 1.0
    }
    if (animated) {
        UIView.animateWithDuration(0.16, animations = apply)
    } else {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        apply()
        CATransaction.commit()
    }
}

/**
 * Semantic swaps that are not directly gesture-driven still settle on the same physical controls.
 * There is deliberately no cross-dissolve or replacement view: the persistent control compresses
 * and returns to rest.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.animatePersistentSemanticSettle(enabled: Boolean) {
    repairPersistentLeadingControlIfNeeded()
    if (!enabled) {
        resetPersistentChromeMorphVisuals(animated = false)
        return
    }
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    val compressed = scaleTransform(0.88)
    backButton.transform = compressed
    trailingCluster.transform = compressed
    backButton.alpha = 0.92
    trailingCluster.alpha = 0.92
    CATransaction.commit()
    UIView.animateWithDuration(0.18) {
        val identity = identityTransform()
        backButton.transform = identity
        trailingCluster.transform = identity
        backButton.alpha = 1.0
        trailingCluster.alpha = 1.0
    }
}

/**
 * Root menu state can leave a UIMenu and ellipsis painted on the persistent leading control.
 * A pushed screen owns the same UIButton, so explicitly clear root-menu semantics as soon as a
 * back handler is bound. This prevents the observed hub header from showing an ellipsis that opens
 * Search where a Back chevron belongs.
 */
@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.repairPersistentLeadingControlIfNeeded() {
    if (backTarget.handler == null) return
    if (backButton.showsMenuAsPrimaryAction || backButton.menu != null) {
        backButton.menu = null
        backButton.showsMenuAsPrimaryAction = false
    }
    if (paintedAccessibility[backButton] == "Menu" || paintedSymbols[backButton] == "ellipsis") {
        paintChromeButton(
            button = backButton,
            symbol = "chevron.backward",
            accessibility = "Back",
            clustered = false,
        )
    }
}

/**
 * Kotlin/Native UIKit exposes UIView.transform as CValue<CGAffineTransform>. Construct the value
 * explicitly instead of relying on CoreGraphics convenience-return bridging.
 */
@OptIn(ExperimentalForeignApi::class)
private fun scaleTransform(scale: Double): CValue<CGAffineTransform> =
    cValue {
        a = scale
        b = 0.0
        c = 0.0
        d = scale
        tx = 0.0
        ty = 0.0
    }

@OptIn(ExperimentalForeignApi::class)
private fun identityTransform(): CValue<CGAffineTransform> = scaleTransform(1.0)

/**
 * Expanded root titles live below the action plane and may use the page width. Compact titles do
 * not: they occupy a protected center lane between the persistent leading and trailing controls.
 * This is the important UIKit invariant the previous symmetric full-width constraints violated.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentRootTitleGeometry(isRoot: Boolean) {
    if (!isRoot) return
    val compact = titleLabel.font.pointSize <= NativeHeaderMetrics.CompactTitlePointSize + 1.5

    titleLeadingToAvatar?.active = false
    avatarLeadingToBack?.active = false
    if (compact) {
        titleLeadingToBar?.active = false
        titleLeadingToBack?.active = true
        if (trailingCluster.hidden) {
            titleTrailingToCluster?.active = false
            titleTrailingToBar?.active = true
        } else {
            titleTrailingToBar?.active = false
            titleTrailingToCluster?.active = true
        }
        // Root subtitles belong to the expanded hierarchy. Keeping greeting + subtitle inline in a
        // 52pt compact bar is what produced the overlapping Home header captured on device.
        subtitleLabel.hidden = true
        titleColumn.axis = UILayoutConstraintAxisVertical
        titleColumn.spacing = 0.0
    } else {
        titleLeadingToBack?.active = false
        titleLeadingToBar?.active = true
        titleTrailingToCluster?.active = false
        titleTrailingToBar?.active = true
    }
    bar.superview?.layoutIfNeeded()
    chromeRow.superview?.layoutIfNeeded()
}

/**
 * Root-screen menu anchor. This reuses the exact same leading UIButton later used as Back and
 * Close on pushed/media states. The caller still supplies the menu action; Search must remain a
 * separate trailing control rather than being duplicated as the root menu's only command.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentRootMenu(onClick: (() -> Unit)?) {
    if (onClick == null) {
        backButton.hidden = true
        backButton.menu = null
        backButton.showsMenuAsPrimaryAction = false
        return
    }
    backButton.hidden = false
    backButton.menu = null
    backButton.showsMenuAsPrimaryAction = false
    backTarget.handler = onClick
    paintChromeButton(
        button = backButton,
        symbol = "ellipsis",
        accessibility = "Menu",
        clustered = false,
    )
}
