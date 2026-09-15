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
import platform.UIKit.NSTextAlignmentCenter
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
    // Never fade the title column out during a back gesture. The previous alpha dip amplified the
    // source/destination text handoff and made the header look like it disappeared mid-gesture.
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
 * The root overflow and pushed Back control are the same UIButton. A root UIMenu can otherwise
 * survive into a hub/chat bind, leaving an ellipsis that still opens the root menu. `bindRow`
 * always assigns a back handler for pushed screens while root menu presentation needs no target
 * handler, so that distinction is stable and does not depend on mount order.
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

/** Kotlin/Native UIKit exposes UIView.transform as CValue<CGAffineTransform>. */
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
 * Expanded root titles live below the action plane and may use the page width. Once the native
 * title enters compact centered mode, it occupies a protected lane between the leading overflow
 * control and the trailing search/action cluster. This prevents either glass control from covering
 * title text at any intermediate collapse fraction.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentRootTitleGeometry(isRoot: Boolean) {
    if (!isRoot) return
    val compact = titleLabel.textAlignment == NSTextAlignmentCenter

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
        // Expanded descriptive copy does not belong in a 52pt compact navigation row.
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
 * Root-screen overflow menu. Search is deliberately not duplicated here: it remains the dedicated
 * magnifier on the trailing side. Commands come from the app shell so they are real navigation
 * actions, not placeholder menu entries.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentRootMenu(onClick: (() -> Unit)?) {
    val items = NativeRootMenuRegistry.snapshot()
    menuClicksByKey.keys
        .filter { it.startsWith("-2:") }
        .forEach(menuClicksByKey::remove)

    if (items.isEmpty()) {
        backButton.hidden = true
        backButton.menu = null
        backButton.showsMenuAsPrimaryAction = false
        backTarget.handler = null
        return
    }

    items.forEachIndexed { index, item ->
        menuClicksByKey["-2:$index"] = item.onClick
    }
    val menuAction =
        NativeChromeAction(
            sfSymbol = "ellipsis",
            contentDescription = "Menu",
            onClick = {},
            menuItems = items,
        )
    backButton.hidden = false
    // UIMenu is the primary action; keeping the target empty lets pushed routes reliably detect
    // that they must retarget this physical button to Back.
    backTarget.handler = null
    bindNativeMenu(backButton, menuAction, actionIndex = -2)
    paintChromeButton(
        button = backButton,
        symbol = "ellipsis",
        accessibility = "Menu",
        clustered = false,
    )
}
