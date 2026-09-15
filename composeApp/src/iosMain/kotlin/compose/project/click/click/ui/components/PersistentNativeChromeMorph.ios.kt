@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGAffineTransformIdentity
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView

/**
 * Visual transition policy for the single persistent iOS navigation chrome host.
 *
 * The important invariant is that route changes never replace the physical UIKit controls.
 * Their semantic role changes in-place (menu -> back -> close and the reverse), which is the
 * interaction model used by WhatsApp-style Liquid Glass navigation.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentChromeMorphProgress(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val distanceFromMid = kotlin.math.abs(p - 0.5f) / 0.5f
    val scale = 0.84f + (0.16f * distanceFromMid)
    val alpha = 0.78f + (0.22f * distanceFromMid)
    val transform = CGAffineTransformMakeScale(scale.toDouble(), scale.toDouble())

    CATransaction.begin()
    CATransaction.setDisableActions(true)
    backButton.transform = transform
    trailingCluster.transform = transform
    backButton.alpha = alpha.toDouble()
    trailingCluster.alpha = alpha.toDouble()
    titleColumn.alpha = (0.72f + (0.28f * distanceFromMid)).toDouble()
    CATransaction.commit()
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.resetPersistentChromeMorphVisuals(animated: Boolean) {
    val apply = {
        backButton.transform = CGAffineTransformIdentity
        trailingCluster.transform = CGAffineTransformIdentity
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
 * There is deliberately no cross-dissolve: the control compresses slightly and returns to its
 * resting size, matching the tactile expand/shrink language of system Liquid Glass controls.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.animatePersistentSemanticSettle(enabled: Boolean) {
    if (!enabled) {
        resetPersistentChromeMorphVisuals(animated = false)
        return
    }
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    val compressed = CGAffineTransformMakeScale(0.88, 0.88)
    backButton.transform = compressed
    trailingCluster.transform = compressed
    backButton.alpha = 0.86
    trailingCluster.alpha = 0.86
    CATransaction.commit()
    UIView.animateWithDuration(0.18) {
        backButton.transform = CGAffineTransformIdentity
        trailingCluster.transform = CGAffineTransformIdentity
        backButton.alpha = 1.0
        trailingCluster.alpha = 1.0
    }
}

/**
 * Root titles are laid out independently from asymmetric leading/trailing controls. The title
 * column always spans symmetric screen insets; compact text can therefore be truly centered even
 * when a menu exists on the left and search/actions exist on the right.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentRootTitleGeometry(isRoot: Boolean) {
    if (!isRoot) return
    titleLeadingToBack?.active = false
    titleLeadingToAvatar?.active = false
    avatarLeadingToBack?.active = false
    titleLeadingToBar?.active = true
    titleTrailingToCluster?.active = false
    titleTrailingToBar?.active = true
    bar.superview?.layoutIfNeeded()
    chromeRow.superview?.layoutIfNeeded()
}

/**
 * Root-screen menu anchor. This deliberately reuses the exact same leading UIButton later used as
 * Back and Close on pushed/media states. It is a real native UIMenu rather than a disguised search
 * button, so the visual anchor and its semantics stay consistent.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentRootMenu(action: NativeChromeAction?) {
    if (action == null) {
        backButton.hidden = true
        backButton.menu = null
        backButton.showsMenuAsPrimaryAction = false
        return
    }
    backButton.hidden = false
    backTarget.handler = action.onClick
    bindNativeMenu(backButton, action, actionIndex = -2)
    paintChromeButton(
        button = backButton,
        symbol = "ellipsis",
        accessibility = action.contentDescription,
        clustered = false,
    )
}
