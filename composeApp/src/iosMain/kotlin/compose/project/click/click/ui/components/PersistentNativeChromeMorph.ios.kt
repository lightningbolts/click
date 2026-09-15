@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentLeft
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIView

private data class NativeTitleSnapshot(
    val title: String,
    val subtitle: String,
    val titleFontSize: Double,
    val subtitleFontSize: Double,
    val titleColor: UIColor,
    val subtitleColor: UIColor,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val centered: Boolean,
)

private class NativeTitleTransitionViews {
    val source = UIView()
    val destination = UIView()
    val sourceTitle = UILabel()
    val sourceSubtitle = UILabel()
    val destinationTitle = UILabel()
    val destinationSubtitle = UILabel()
    var sourceSnapshot: NativeTitleSnapshot? = null
    var destinationSnapshot: NativeTitleSnapshot? = null
    var active = false
}

private val transitionViewsByLayer = mutableMapOf<IosHostNavBarLayer, NativeTitleTransitionViews>()
private val lastRootSnapshotByLayer = mutableMapOf<IosHostNavBarLayer, NativeTitleSnapshot>()

/**
 * Controls remain persistent, but route text does not semantically morph. UIKit navigation keeps
 * outgoing and incoming route titles as separate visual objects during an interactive pop. The
 * source title follows the foreground page while the cached destination title parallaxes in.
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
    CATransaction.commit()

    applyRouteTitleTransition(p)
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.resetPersistentChromeMorphVisuals(animated: Boolean) {
    clearRouteTitleTransition()
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

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.animatePersistentSemanticSettle(enabled: Boolean) {
    repairPersistentLeadingControlIfNeeded()
    if (!enabled) {
        // `renderTransition()` renders source/destination semantics every gesture frame and then
        // immediately calls applyPersistentChromeMorphProgress(). Resetting here used to destroy
        // and recreate the dual-title overlay on every frame (and killed it completely after the
        // 50% semantic handoff). Gesture completion/cancel owns the explicit reset instead.
        return
    }
    clearRouteTitleTransition()
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    val compressed = scaleTransform(0.88)
    backButton.transform = compressed
    trailingCluster.transform = compressed
    backButton.alpha = 0.92
    trailingCluster.alpha = 0.92
    titleColumn.alpha = 1.0
    CATransaction.commit()
    UIView.animateWithDuration(0.18) {
        val identity = identityTransform()
        backButton.transform = identity
        trailingCluster.transform = identity
        backButton.alpha = 1.0
        trailingCluster.alpha = 1.0
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.captureCurrentTitleSnapshot(): NativeTitleSnapshot {
    var x = 0.0
    var y = 0.0
    var width = 0.0
    var height = 0.0
    titleColumn.frame.useContents {
        x = origin.x
        y = origin.y
        width = size.width
        height = size.height
    }
    return NativeTitleSnapshot(
        title = titleLabel.text.orEmpty(),
        subtitle = if (subtitleLabel.hidden) "" else subtitleLabel.text.orEmpty(),
        titleFontSize = titleLabel.font.pointSize,
        subtitleFontSize = subtitleLabel.font.pointSize,
        titleColor = titleLabel.textColor ?: if (lastIsDark) UIColor.whiteColor else UIColor.blackColor,
        subtitleColor =
            subtitleLabel.textColor
                ?: UIColor.colorWithWhite(if (lastIsDark) 0.72 else 0.38, alpha = 1.0),
        x = x,
        y = y,
        width = width,
        height = height.coerceAtLeast(NativeHeaderMetrics.CompactBarHeightPt),
        centered = titleLabel.textAlignment == NSTextAlignmentCenter,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.rememberRootTitleSnapshot() {
    chromeRow.superview?.layoutIfNeeded()
    lastRootSnapshotByLayer[this] = captureCurrentTitleSnapshot()
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.applyRouteTitleTransition(progress: Float) {
    val destinationSnapshot = lastRootSnapshotByLayer[this]
    if (progress <= 0.001f || destinationSnapshot == null) {
        clearRouteTitleTransition()
        return
    }

    val views = transitionViewsByLayer.getOrPut(this) { NativeTitleTransitionViews() }
    if (!views.active) {
        val sourceSnapshot = captureCurrentTitleSnapshot()
        if (sourceSnapshot.title == destinationSnapshot.title &&
            sourceSnapshot.subtitle == destinationSnapshot.subtitle
        ) {
            titleColumn.alpha = 1.0
            return
        }
        views.sourceSnapshot = sourceSnapshot
        views.destinationSnapshot = destinationSnapshot
        configureTransitionContainer(views.source, views.sourceTitle, views.sourceSubtitle, sourceSnapshot)
        configureTransitionContainer(
            views.destination,
            views.destinationTitle,
            views.destinationSubtitle,
            destinationSnapshot,
        )
        chromeRow.addSubview(views.destination)
        chromeRow.addSubview(views.source)
        views.active = true
    }

    val source = views.sourceSnapshot ?: return
    val destination = views.destinationSnapshot ?: return
    val hostWidth = chromeRow.bounds.useContents { size.width }.coerceAtLeast(1.0)
    val p = progress.coerceIn(0f, 1f).toDouble()

    CATransaction.begin()
    CATransaction.setDisableActions(true)
    titleColumn.alpha = 0.0
    views.source.setFrame(
        CGRectMake(
            source.x + hostWidth * p,
            source.y,
            source.width,
            source.height,
        ),
    )
    views.destination.setFrame(
        CGRectMake(
            destination.x - hostWidth * 0.22 * (1.0 - p),
            destination.y,
            destination.width,
            destination.height,
        ),
    )
    views.source.alpha = (1.0 - 0.18 * p).coerceIn(0.0, 1.0)
    views.destination.alpha = (0.78 + 0.22 * p).coerceIn(0.0, 1.0)
    CATransaction.commit()
}

@OptIn(ExperimentalForeignApi::class)
private fun configureTransitionContainer(
    container: UIView,
    title: UILabel,
    subtitle: UILabel,
    snapshot: NativeTitleSnapshot,
) {
    container.backgroundColor = UIColor.clearColor
    container.userInteractionEnabled = false
    container.setFrame(CGRectMake(snapshot.x, snapshot.y, snapshot.width, snapshot.height))
    container.subviews.map { it as UIView }.forEach { it.removeFromSuperview() }

    title.text = snapshot.title
    title.font = UIFont.boldSystemFontOfSize(snapshot.titleFontSize)
    title.textColor = snapshot.titleColor
    title.textAlignment = if (snapshot.centered) NSTextAlignmentCenter else NSTextAlignmentLeft
    title.numberOfLines = 1
    title.userInteractionEnabled = false

    subtitle.text = snapshot.subtitle
    subtitle.font = UIFont.systemFontOfSize(snapshot.subtitleFontSize)
    subtitle.textColor = snapshot.subtitleColor
    subtitle.textAlignment = title.textAlignment
    subtitle.numberOfLines = 1
    subtitle.hidden = snapshot.subtitle.isEmpty()
    subtitle.userInteractionEnabled = false

    val titleHeight = if (snapshot.titleFontSize > 22.0) 42.0 else 22.0
    val subtitleHeight = if (snapshot.subtitle.isEmpty()) 0.0 else 17.0
    val totalHeight = titleHeight + subtitleHeight
    val top = ((snapshot.height - totalHeight) / 2.0).coerceAtLeast(0.0)
    title.setFrame(CGRectMake(0.0, top, snapshot.width, titleHeight))
    subtitle.setFrame(CGRectMake(0.0, top + titleHeight, snapshot.width, subtitleHeight))
    container.addSubview(title)
    if (!subtitle.hidden) container.addSubview(subtitle)
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.clearRouteTitleTransition() {
    val views = transitionViewsByLayer[this] ?: run {
        titleColumn.alpha = 1.0
        return
    }
    views.source.removeFromSuperview()
    views.destination.removeFromSuperview()
    views.sourceSnapshot = null
    views.destinationSnapshot = null
    views.active = false
    titleColumn.alpha = 1.0
}

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
 * Expanded root titles live below the action plane. Compact titles occupy a protected lane between
 * the leading overflow control and trailing search/actions so glass controls can never cover text.
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
    rememberRootTitleSnapshot()
}

/**
 * Root overflow menu. Search is deliberately not duplicated here; it remains the dedicated
 * magnifier. Commands come from the app shell and are real navigation actions.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentRootMenu(
    @Suppress("UNUSED_PARAMETER") onClick: (() -> Unit)?,
) {
    NativeRootMenuRegistry.onChanged = {
        if (backTarget.handler == null) {
            applyPersistentRootMenu(onClick)
        }
    }

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
    backTarget.handler = null
    bindNativeMenu(backButton, menuAction, actionIndex = -2)
    paintChromeButton(
        button = backButton,
        symbol = "ellipsis",
        accessibility = "Menu",
        clustered = false,
    )
}
