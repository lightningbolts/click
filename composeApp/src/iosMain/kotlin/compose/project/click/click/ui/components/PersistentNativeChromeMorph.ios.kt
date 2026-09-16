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
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentLeft
import platform.UIKit.UIButton
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
    val titleX: Double,
    val titleY: Double,
    val titleWidth: Double,
    val titleHeight: Double,
    val subtitleX: Double,
    val subtitleY: Double,
    val subtitleWidth: Double,
    val subtitleHeight: Double,
    val glassAlpha: Double,
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

private data class NativeLeadingSemanticSnapshot(
    val handler: (() -> Unit)?,
    val symbol: String,
    val accessibility: String,
)

private data class NativeLeadingVisualSnapshot(
    val symbol: String,
    val accessibility: String,
)

private class NativeTrailingTransitionState(
    val sourceButtons: List<UIButton>,
    val destinationSearchHandler: () -> Unit,
) {
    val sourceExtraWidth =
        (sourceButtons.size - 1).coerceAtLeast(0) * NativeHeaderMetrics.ChromeButtonSizePt
    val spacer =
        UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            userInteractionEnabled = false
        }
    val spacerWidthConstraint =
        spacer.widthAnchor.constraintEqualToConstant(sourceExtraWidth).apply {
            active = true
        }
    var destinationApplied = false
}

private val transitionViewsByLayer = mutableMapOf<IosHostNavBarLayer, NativeTitleTransitionViews>()
private val lastRootSnapshotByLayer = mutableMapOf<IosHostNavBarLayer, NativeTitleSnapshot>()
private val lastRootSearchHandlerByLayer = mutableMapOf<IosHostNavBarLayer, () -> Unit>()
private val leadingSemanticSnapshotByLayer = mutableMapOf<IosHostNavBarLayer, NativeLeadingSemanticSnapshot>()
private val leadingDestinationAppliedByLayer = mutableMapOf<IosHostNavBarLayer, Boolean>()
private val trailingTransitionByLayer = mutableMapOf<IosHostNavBarLayer, NativeTrailingTransitionState>()
private val lastSettledLeadingVisualByLayer = mutableMapOf<IosHostNavBarLayer, NativeLeadingVisualSnapshot>()
private val suppressNextSemanticSettleByLayer = mutableSetOf<IosHostNavBarLayer>()
private val semanticAnimationGenerationByLayer = mutableMapOf<IosHostNavBarLayer, Int>()

/**
 * Controls remain persistent, but route text does not semantically morph. UIKit navigation keeps
 * outgoing and incoming route titles as separate visual objects during an interactive pop. The
 * source title follows the foreground page while the cached destination title parallaxes in.
 *
 * The leading button is the same physical UIButton for both routes. Its symbol therefore changes
 * while the control is at the compressed midpoint of the morph, not after the control has already
 * expanded into the destination. Reversing/cancelling the gesture restores the source semantic at
 * the same midpoint, keeping icon and material motion one continuous interaction.
 *
 * Root-pop trailing actions use the same contract. The existing Liquid Glass capsule never fades or
 * gets replaced: at the compressed midpoint its contents switch to Search while a temporary spacer
 * preserves the source width, then that spacer collapses through the second half of the gesture.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyPersistentChromeMorphProgress(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    applyRouteTitleTransition(p)
    applyTrailingRootSearchTransition(p)

    val distanceFromMid = kotlin.math.abs(p - 0.5f) / 0.5f
    val scale = 0.88 + (0.12 * distanceFromMid)
    val alpha = 0.94 + (0.06 * distanceFromMid)
    val transform = scaleTransform(scale)

    CATransaction.begin()
    CATransaction.setDisableActions(true)
    backButton.transform = transform
    trailingCluster.transform = transform
    backButton.alpha = alpha
    trailingCluster.alpha = alpha
    CATransaction.commit()

    applyLeadingRouteSemanticTransition(p)
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.resetPersistentChromeMorphVisuals(animated: Boolean) {
    val destinationWasApplied = leadingDestinationAppliedByLayer[this] == true
    val trailingState = trailingTransitionByLayer.remove(this)
    if (destinationWasApplied) {
        // A completed pop has already crossed the semantic midpoint. Do not repaint the source
        // chevron/xmark during source disposal and then immediately paint the destination again.
        leadingSemanticSnapshotByLayer.remove(this)
        leadingDestinationAppliedByLayer.remove(this)
        trailingState?.let(::finishTrailingDestinationTransition)
        suppressNextSemanticSettleByLayer += this
    } else {
        restoreLeadingRouteSemanticIfNeeded()
        if (trailingState?.destinationApplied == true) {
            restoreSourceTrailingTransition(trailingState)
        }
    }
    clearRouteTitleTransition()
    val identity = identityTransform()
    val apply = {
        backButton.transform = identity
        trailingCluster.transform = identity
        avatarButton.transform = identity
        backButton.alpha = 1.0
        trailingCluster.alpha = 1.0
        avatarButton.alpha = 1.0
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
    titleLabel.layer.removeAllAnimations()
    subtitleLabel.layer.removeAllAnimations()

    if (suppressNextSemanticSettleByLayer.remove(this)) {
        // The interactive/programmatic pop already committed the destination semantic. Source
        // disposal must not run the generic "repair" path, which would repaint the chevron for one
        // frame before root rendering paints Menu again.
        lastSettledLeadingVisualByLayer[this] =
            NativeLeadingVisualSnapshot(
                symbol = paintedSymbols[backButton] ?: "none",
                accessibility = paintedAccessibility[backButton] ?: "",
            )
        clearRouteTitleTransition()
        backButton.userInteractionEnabled = true
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        backButton.transform = identityTransform()
        trailingCluster.transform = identityTransform()
        backButton.alpha = 1.0
        trailingCluster.alpha = 1.0
        CATransaction.commit()
        return
    }

    repairPersistentLeadingControlIfNeeded()
    val destinationVisual =
        NativeLeadingVisualSnapshot(
            symbol = paintedSymbols[backButton] ?: "none",
            accessibility = paintedAccessibility[backButton] ?: "",
        )
    val sourceVisual = lastSettledLeadingVisualByLayer[this]
    lastSettledLeadingVisualByLayer[this] = destinationVisual

    if (!enabled) return

    clearRouteTitleTransition()
    val generation = (semanticAnimationGenerationByLayer[this] ?: 0) + 1
    semanticAnimationGenerationByLayer[this] = generation
    backButton.layer.removeAllAnimations()
    trailingCluster.layer.removeAllAnimations()

    if (sourceVisual == null || sourceVisual == destinationVisual || backButton.hidden) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        val compressed = scaleTransform(0.9)
        backButton.transform = compressed
        trailingCluster.transform = compressed
        backButton.alpha = 0.94
        trailingCluster.alpha = 0.94
        titleColumn.alpha = 1.0
        CATransaction.commit()
        UIView.animateWithDuration(0.16) {
            val identity = identityTransform()
            backButton.transform = identity
            trailingCluster.transform = identity
            backButton.alpha = 1.0
            trailingCluster.alpha = 1.0
        }
        return
    }

    // For immediate semantic transitions such as media xmark -> chat chevron, render the previous
    // symbol while the same physical glass control compresses, swap at the compressed midpoint,
    // then expand. The destination handler/menu is already authoritative; only the visual is held
    // until the midpoint, so route ownership cannot oscillate.
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    paintChromeButton(
        button = backButton,
        symbol = sourceVisual.symbol,
        accessibility = sourceVisual.accessibility,
        clustered = false,
    )
    backButton.transform = identityTransform()
    trailingCluster.transform = identityTransform()
    backButton.alpha = 1.0
    trailingCluster.alpha = 1.0
    backButton.userInteractionEnabled = false
    CATransaction.commit()

    UIView.animateWithDuration(
        duration = 0.08,
        animations = {
            val compressed = scaleTransform(0.88)
            backButton.transform = compressed
            trailingCluster.transform = compressed
            backButton.alpha = 0.94
            trailingCluster.alpha = 0.94
        },
        completion = { _ ->
            if (semanticAnimationGenerationByLayer[this] != generation) {
                backButton.userInteractionEnabled = true
                return@animateWithDuration
            }
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            paintChromeButton(
                button = backButton,
                symbol = destinationVisual.symbol,
                accessibility = destinationVisual.accessibility,
                clustered = false,
            )
            CATransaction.commit()
            UIView.animateWithDuration(
                duration = 0.1,
                animations = {
                    val identity = identityTransform()
                    backButton.transform = identity
                    trailingCluster.transform = identity
                    backButton.alpha = 1.0
                    trailingCluster.alpha = 1.0
                },
                completion = { _ ->
                    if (semanticAnimationGenerationByLayer[this] == generation) {
                        backButton.userInteractionEnabled = true
                    }
                },
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.applyLeadingRouteSemanticTransition(progress: Float) {
    // The existing route-title transition is only created when the cached root route is actually
    // the pop destination, so use that as the semantic handoff contract as well. This avoids
    // guessing that every pushed-to-pushed transition should become the root overflow menu.
    val routeTransition = transitionViewsByLayer[this]
    if (routeTransition?.active != true || routeTransition.destinationSnapshot == null) return

    val source =
        leadingSemanticSnapshotByLayer.getOrPut(this) {
            NativeLeadingSemanticSnapshot(
                handler = backTarget.handler,
                symbol = paintedSymbols[backButton] ?: "chevron.backward",
                accessibility = paintedAccessibility[backButton] ?: "Back",
            )
        }
    val destinationApplied = leadingDestinationAppliedByLayer[this] == true
    val shouldShowDestination = progress >= 0.5f
    if (shouldShowDestination == destinationApplied) return

    CATransaction.begin()
    CATransaction.setDisableActions(true)
    if (shouldShowDestination) {
        applyPersistentRootMenu(onClick = null)
    } else {
        backButton.menu = null
        backButton.showsMenuAsPrimaryAction = false
        backTarget.handler = source.handler
        backButton.hidden = false
        paintChromeButton(
            button = backButton,
            symbol = source.symbol,
            accessibility = source.accessibility,
            clustered = false,
        )
    }
    CATransaction.commit()
    leadingDestinationAppliedByLayer[this] = shouldShowDestination
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.applyTrailingRootSearchTransition(progress: Float) {
    val routeTransition = transitionViewsByLayer[this]
    if (routeTransition?.active != true || routeTransition.destinationSnapshot == null) return
    val destinationSearch = lastRootSearchHandlerByLayer[this] ?: return

    val state =
        trailingTransitionByLayer.getOrPut(this) {
            val sourceButtons = trailingStack.arrangedSubviews.mapNotNull { it as? UIButton }
            if (sourceButtons.isEmpty() || sourceButtons.any { it === searchButton }) {
                return
            }
            NativeTrailingTransitionState(
                sourceButtons = sourceButtons,
                destinationSearchHandler = destinationSearch,
            )
        }

    val shouldShowDestination = progress >= 0.5f
    if (shouldShowDestination != state.destinationApplied) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        if (shouldShowDestination) {
            showDestinationSearchTransition(state)
        } else {
            restoreSourceTrailingTransition(state)
        }
        CATransaction.commit()
    }

    if (state.destinationApplied && state.sourceExtraWidth > 0.0) {
        val secondHalfProgress = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f).toDouble()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        state.spacerWidthConstraint.constant = state.sourceExtraWidth * (1.0 - secondHalfProgress)
        trailingCluster.superview?.layoutIfNeeded()
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.showDestinationSearchTransition(state: NativeTrailingTransitionState) {
    state.sourceButtons.forEach { button ->
        trailingStack.removeArrangedSubview(button)
        button.removeFromSuperview()
    }
    if (searchButton.superview != null) {
        trailingStack.removeArrangedSubview(searchButton)
        searchButton.removeFromSuperview()
    }
    searchTarget.handler = state.destinationSearchHandler
    searchButton.hidden = false
    searchButton.menu = null
    searchButton.showsMenuAsPrimaryAction = false
    paintChromeButton(searchButton, "magnifyingglass", "Search", clustered = true)
    state.spacerWidthConstraint.constant = state.sourceExtraWidth
    if (state.sourceExtraWidth > 0.0) {
        trailingStack.addArrangedSubview(state.spacer)
    }
    trailingStack.addArrangedSubview(searchButton)
    trailingCluster.hidden = false
    trailingCluster.superview?.layoutIfNeeded()
    state.destinationApplied = true
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.restoreSourceTrailingTransition(state: NativeTrailingTransitionState) {
    if (state.spacer.superview != null) {
        trailingStack.removeArrangedSubview(state.spacer)
        state.spacer.removeFromSuperview()
    }
    if (searchButton.superview != null) {
        trailingStack.removeArrangedSubview(searchButton)
        searchButton.removeFromSuperview()
    }
    searchButton.hidden = true
    searchTarget.handler = null
    state.sourceButtons.forEach { button ->
        button.hidden = false
        trailingStack.addArrangedSubview(button)
    }
    trailingCluster.hidden = state.sourceButtons.isEmpty()
    trailingCluster.superview?.layoutIfNeeded()
    state.destinationApplied = false
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.finishTrailingDestinationTransition(state: NativeTrailingTransitionState) {
    if (!state.destinationApplied) return
    state.spacerWidthConstraint.constant = 0.0
    if (state.spacer.superview != null) {
        trailingStack.removeArrangedSubview(state.spacer)
        state.spacer.removeFromSuperview()
    }
    trailingCluster.superview?.layoutIfNeeded()
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.restoreLeadingRouteSemanticIfNeeded() {
    val source = leadingSemanticSnapshotByLayer.remove(this) ?: return
    val destinationApplied = leadingDestinationAppliedByLayer.remove(this) == true
    if (!destinationApplied) return

    CATransaction.begin()
    CATransaction.setDisableActions(true)
    backButton.menu = null
    backButton.showsMenuAsPrimaryAction = false
    backTarget.handler = source.handler
    backButton.hidden = false
    paintChromeButton(
        button = backButton,
        symbol = source.symbol,
        accessibility = source.accessibility,
        clustered = false,
    )
    CATransaction.commit()
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.captureCurrentTitleSnapshot(): NativeTitleSnapshot {
    chromeRow.superview?.layoutIfNeeded()
    var x = 0.0
    var y = 0.0
    var width = 0.0
    var height = 0.0
    var titleX = 0.0
    var titleY = 0.0
    var titleWidth = 0.0
    var titleHeight = 0.0
    var subtitleX = 0.0
    var subtitleY = 0.0
    var subtitleWidth = 0.0
    var subtitleHeight = 0.0
    titleColumn.frame.useContents {
        x = origin.x
        y = origin.y
        width = size.width
        height = size.height
    }
    titleLabel.frame.useContents {
        titleX = origin.x
        titleY = origin.y
        titleWidth = size.width
        titleHeight = size.height
    }
    subtitleLabel.frame.useContents {
        subtitleX = origin.x
        subtitleY = origin.y
        subtitleWidth = size.width
        subtitleHeight = size.height
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
        titleX = titleX,
        titleY = titleY,
        titleWidth = titleWidth.coerceAtLeast(width),
        titleHeight = titleHeight.coerceAtLeast(1.0),
        subtitleX = subtitleX,
        subtitleY = subtitleY,
        subtitleWidth = subtitleWidth.coerceAtLeast(width),
        subtitleHeight = subtitleHeight.coerceAtLeast(1.0),
        glassAlpha = glassPlate.alpha,
        centered = titleLabel.textAlignment == NSTextAlignmentCenter,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.rememberRootTitleSnapshot() {
    lastRootSnapshotByLayer[this] = captureCurrentTitleSnapshot()
    val rootSearch =
        searchTarget.handler?.takeIf {
            !searchButton.hidden && trailingStack.arrangedSubviews.any { view -> view === searchButton }
        }
    if (rootSearch != null) {
        lastRootSearchHandlerByLayer[this] = rootSearch
    } else {
        lastRootSearchHandlerByLayer.remove(this)
    }
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
        chromeRow.insertSubview(views.destination, belowSubview = backButton)
        chromeRow.insertSubview(views.source, belowSubview = backButton)
        chromeRow.bringSubviewToFront(backButton)
        chromeRow.bringSubviewToFront(avatarButton)
        chromeRow.bringSubviewToFront(trailingCluster)
        views.active = true
    }

    val source = views.sourceSnapshot ?: return
    val destination = views.destinationSnapshot ?: return
    val hostWidth = chromeRow.bounds.useContents { size.width }.coerceAtLeast(1.0)
    val p = progress.coerceIn(0f, 1f).toDouble()
    val transitionGlassAlpha =
        source.glassAlpha + (destination.glassAlpha - source.glassAlpha) * p

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
    clipDestinationToRevealedRegion(views.destination, hostWidth * p)
    views.source.alpha = (1.0 - 0.14 * p).coerceIn(0.0, 1.0)
    views.destination.alpha = (0.82 + 0.18 * p).coerceIn(0.0, 1.0)
    avatarButton.transform = translationTransform(hostWidth * p)
    avatarButton.alpha = (1.0 - 0.2 * p).coerceIn(0.0, 1.0)
    glassPlate.alpha = transitionGlassAlpha
    glassPlate.hidden = transitionGlassAlpha < 0.02
    CATransaction.commit()
}

@OptIn(ExperimentalForeignApi::class)
private fun clipDestinationToRevealedRegion(
    container: UIView,
    revealWidth: Double,
) {
    var frameX = 0.0
    var width = 0.0
    var height = 0.0
    container.frame.useContents {
        frameX = origin.x
        width = size.width
        height = size.height
    }
    val localStart = (-frameX).coerceIn(0.0, width)
    val localEnd = (revealWidth - frameX).coerceIn(0.0, width)
    val visibleWidth = (localEnd - localStart).coerceAtLeast(0.0)
    val mask =
        container.layer.mask ?: CALayer().apply {
            backgroundColor = UIColor.blackColor.CGColor
        }
    mask.frame = CGRectMake(localStart, 0.0, visibleWidth, height.coerceAtLeast(1.0))
    container.layer.mask = mask
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
    container.layer.mask = null
    container.setFrame(CGRectMake(snapshot.x, snapshot.y, snapshot.width, snapshot.height))
    container.subviews.map { it as UIView }.forEach { it.removeFromSuperview() }

    title.text = snapshot.title
    title.font = UIFont.boldSystemFontOfSize(snapshot.titleFontSize)
    title.textColor = snapshot.titleColor
    title.textAlignment = if (snapshot.centered) NSTextAlignmentCenter else NSTextAlignmentLeft
    title.numberOfLines = 1
    title.userInteractionEnabled = false
    title.setFrame(
        CGRectMake(
            snapshot.titleX,
            snapshot.titleY,
            snapshot.titleWidth,
            snapshot.titleHeight,
        ),
    )

    subtitle.text = snapshot.subtitle
    subtitle.font = UIFont.systemFontOfSize(snapshot.subtitleFontSize)
    subtitle.textColor = snapshot.subtitleColor
    subtitle.textAlignment = title.textAlignment
    subtitle.numberOfLines = 1
    subtitle.hidden = snapshot.subtitle.isEmpty()
    subtitle.userInteractionEnabled = false
    subtitle.setFrame(
        CGRectMake(
            snapshot.subtitleX,
            snapshot.subtitleY,
            snapshot.subtitleWidth,
            snapshot.subtitleHeight,
        ),
    )

    container.addSubview(title)
    if (!subtitle.hidden) container.addSubview(subtitle)
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.clearRouteTitleTransition() {
    val views =
        transitionViewsByLayer[this] ?: run {
            titleColumn.alpha = 1.0
            avatarButton.transform = identityTransform()
            avatarButton.alpha = 1.0
            return
        }
    views.sourceSnapshot?.let { source ->
        glassPlate.alpha = source.glassAlpha
        glassPlate.hidden = source.glassAlpha < 0.02
    }
    views.source.layer.mask = null
    views.destination.layer.mask = null
    views.source.removeFromSuperview()
    views.destination.removeFromSuperview()
    views.sourceSnapshot = null
    views.destinationSnapshot = null
    views.active = false
    titleColumn.alpha = 1.0
    avatarButton.transform = identityTransform()
    avatarButton.alpha = 1.0
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
private fun translationTransform(x: Double): CValue<CGAffineTransform> =
    cValue {
        a = 1.0
        b = 0.0
        c = 0.0
        d = 1.0
        tx = x
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
