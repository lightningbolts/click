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
    // Compress into the semantic hand-off, then expand the same physical controls back out.
    // Keep alpha high enough that the glass control never appears to blink out/remount.
    val scale = 0.82 + (0.18 * distanceFromMid)
    val alpha = 0.90 + (0.10 * distanceFromMid)
    val transform = scaleTransform(scale)

    CATransaction.begin()
    CATransaction.setDisableActions(true)
    backButton.transform = transform
    trailingCluster.transform = transform
    backButton.alpha = alpha
    trailingCluster.alpha = alpha
    titleColumn.alpha = 0.88 + (0.12 * distanceFromMid)
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
 * Kotlin/Native UIKit exposes UIView.transform as CValue<CGAffineTransform>. Construct the value
 * explicitly instead of relying on CoreGraphics convenience-return bridging, which differs across
 * Kotlin/Native SDK bindings and caused the iOS PR gate to fail at compile time.
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
 * Root-screen menu anchor. This reuses the exact same leading UIButton later used as Back and
 * Close on pushed/media states.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentRootMenu(onClick: (() -> Unit)?) {
    if (onClick == null) {
        backButton.hidden = true
        backButton.menu = null
        backButton.showsMenuAsPrimaryAction = false
        return
    }
    val menuAction =
        NativeChromeAction(
            sfSymbol = "ellipsis",
            contentDescription = "Menu",
            onClick = {},
            menuItems =
                listOf(
                    NativeChromeMenuItem(
                        title = "Search",
                        sfSymbol = "magnifyingglass",
                        onClick = onClick,
                    ),
                ),
        )
    backButton.hidden = false
    backTarget.handler = {}
    bindNativeMenu(backButton, menuAction, actionIndex = -2)
    paintChromeButton(
        button = backButton,
        symbol = "ellipsis",
        accessibility = "Menu",
        clustered = false,
    )
}
