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
import platform.UIKit.UIMenu
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
    val hostWidth: Double,
)

private data class NativeRootDestinationSnapshot(
    val title: NativeTitleSnapshot,
    val searchHandler: (() -> Unit)?,
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

private data class NativeButtonVisualSnapshot(
    val symbol: String,
    val accessibility: String,
)

private class NativeTrailingTransitionState(
    val sourceButtons: List<UIButton>,
    val destinationSearchHandler: () -> Unit,
    val carrierButton: UIButton,
    val carrierTarget: IosBarButtonTarget,
    val sourceCarrierHandler: (() -> Unit)?,
    val sourceCarrierMenu: UIMenu?,
    val sourceCarrierShowsMenuAsPrimaryAction: Boolean,
    val sourceCarrierSymbol: String,
    val sourceCarrierAccessibility: String,
    val sourceStackSpacing: Double,
) {
    private val extraButtonCount = (sourceButtons.size - 1).coerceAtLeast(0)
    val sourceExtraWidth =
        extraButtonCount * (NativeHeaderMetrics.ChromeButtonSizePt + sourceStackSpacing)
    val spacerInitialWidth =
        if (extraButtonCount > 0) {
            (sourceExtraWidth - sourceStackSpacing).coerceAtLeast(0.0)
        } else {
            0.0
        }
    val spacer =
        UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            userInteractionEnabled = false
        }
    val spacerWidthConstraint =
        spacer.widthAnchor.constraintEqualToConstant(spacerInitialWidth).apply {
            active = true
        }
    var destinationApplied = false
}

private val transitionViewsByLayer = mutableMapOf<IosHostNavBarLayer, NativeTitleTransitionViews>()
private val lastRootDestinationByLayer = mutableMapOf<IosHostNavBarLayer, NativeRootDestinationSnapshot>()
private val leadingSemanticSnapshotByLayer = mutableMapOf<IosHostNavBarLayer, NativeLeadingSemanticSnapshot>()
private val leadingDestinationAppliedByLayer = mutableMapOf<IosHostNavBarLayer, Boolean>()
private val trailingTransitionByLayer = mutableMapOf<IosHostNavBarLayer, NativeTrailingTransitionState>()
private val lastSettledLeadingVisualByLayer = mutableMapOf<IosHostNavBarLayer, NativeButtonVisualSnapshot>()
private val lastSettledTrailingVisualByLayer = mutableMapOf<IosHostNavBarLayer, List<NativeButtonVisualSnapshot>>()
private val suppressNextSemanticSettleByLayer = mutableSetOf<IosHostNavBarLayer>()
private val semanticAnimationGenerationByLayer = mutableMapOf<IosHostNavBarLayer, Int>()
private val rootMenuShapeByLayer = mutableMapOf<IosHostNavBarLayer, String>()

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
 * gets replaced: the right-most source button remains the physical carrier throughout the gesture,
 * changes semantic at the midpoint, and the surplus leading width collapses through the second half.
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
    val transitionWasActive = transitionViewsByLayer[this]?.active == true

    if (destinationWasApplied) {
        // Finish the destination snapshot before source disposal. Keep that snapshot mounted until
        // root reconciliation has updated the real title underneath it; otherwise the old route title
        // becomes visible for one frame and the root title appears to "rerender" after Back.
        if (transitionWasActive) {
            applyRouteTitleTransition(1f)
        }
        leadingSemanticSnapshotByLayer.remove(this)
        leadingDestinationAppliedByLayer.remove(this)
        trailingState?.let(::finishTrailingDestinationTransition)
        suppressNextSemanticSettleByLayer += this
    } else {
        restoreLeadingRouteSemanticIfNeeded()
        if (trailingState?.destinationApplied == true) {
            restoreSourceTrailingTransition(trailingState)
        }
        clearRouteTitleTransition()
    }

    val keepDestinationTitle = destinationWasApplied && transitionViewsByLayer[this]?.active == true
    val identity = identityTransform()
    val apply = {
        backButton.transform = identity
        trailingCluster.transform = identity
        avatarButton.transform = identity
        backButton.alpha = 1.0
        trailingCluster.alpha = 1.0
        avatarButton.alpha = if (keepDestinationTitle) 0.0 else 1.0
        titleColumn.alpha = if (keepDestinationTitle) 0.0 else 1.0
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
    val suppressSettle = suppressNextSemanticSettleByLayer.remove(this)
    if (!suppressSettle) repairPersistentLeadingControlIfNeeded()

    fun visual(button: UIButton) =
        NativeButtonVisualSnapshot(
            symbol = paintedSymbols[button] ?: "none",
            accessibility = paintedAccessibility[button] ?: "",
        )
    val trailingButtons = trailingStack.arrangedSubviews.mapNotNull { it as? UIButton }
    val destinationLeading = visual(backButton)
    val destinationTrailing = trailingButtons.map(::visual)
    val sourceLeading = lastSettledLeadingVisualByLayer.put(this, destinationLeading)
    val sourceTrailing = lastSettledTrailingVisualByLayer.put(this, destinationTrailing).orEmpty()

    if (suppressSettle) {
        // The gesture already committed these exact controls. Reveal the reconciled title without
        // replaying the menu/search animation after the source screen is disposed.
        clearRouteTitleTransition(restoreDestination = true)
        backButton.userInteractionEnabled = true
        trailingCluster.userInteractionEnabled = true
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        backButton.transform = identityTransform()
        trailingCluster.transform = identityTransform()
        backButton.alpha = 1.0
        trailingCluster.alpha = 1.0
        CATransaction.commit()
        return
    }
    if (!enabled) return

    val changes = mutableListOf<Triple<UIButton, NativeButtonVisualSnapshot, NativeButtonVisualSnapshot>>()
    if (sourceLeading != null && sourceLeading != destinationLeading && !backButton.hidden) {
        changes += Triple(backButton, sourceLeading, destinationLeading)
    }
    // Match from the fixed trailing edge, including transitions between a capsule and one action.
    trailingButtons.asReversed().forEachIndexed { index, button ->
        val source = sourceTrailing.asReversed().getOrNull(index)
        val destination = visual(button)
        if (source != null && source != destination) changes += Triple(button, source, destination)
    }
    if (changes.isEmpty()) return

    fun paintChanges(destination: Boolean) {
        changes.forEach { (button, sourceVisual, destinationVisual) ->
            val snapshot = if (destination) destinationVisual else sourceVisual
            paintChromeButton(button, snapshot.symbol, snapshot.accessibility, clustered = button != backButton)
        }
    }
    clearRouteTitleTransition()
    val generation = (semanticAnimationGenerationByLayer[this] ?: 0) + 1
    semanticAnimationGenerationByLayer[this] = generation
    backButton.layer.removeAllAnimations()
    trailingCluster.layer.removeAllAnimations()
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    paintChanges(destination = false)
    backButton.transform = identityTransform()
    trailingCluster.transform = identityTransform()
    backButton.alpha = 1.0
    trailingCluster.alpha = 1.0
    backButton.userInteractionEnabled = false
    trailingCluster.userInteractionEnabled = false
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
            if (semanticAnimationGenerationByLayer[this] == generation) {
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                paintChanges(destination = true)
                CATransaction.commit()
                UIView.animateWithDuration(
                    duration = 0.1,
                    animations = {
                        backButton.transform = identityTransform()
                        trailingCluster.transform = identityTransform()
                        backButton.alpha = 1.0
                        trailingCluster.alpha = 1.0
                    },
                    completion = { _ ->
                        if (semanticAnimationGenerationByLayer[this] == generation) {
                            backButton.userInteractionEnabled = true
                            trailingCluster.userInteractionEnabled = true
                        }
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.applyLeadingRouteSemanticTransition(progress: Float) {
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
    val rootDestination = lastRootDestinationByLayer[this] ?: return
    val destinationSearch = rootDestination.searchHandler ?: return

    val state =
        trailingTransitionByLayer.getOrPut(this) {
            val sourceButtons = trailingStack.arrangedSubviews.mapNotNull { it as? UIButton }
            if (sourceButtons.isEmpty()) return
            val carrierButton = sourceButtons.firstOrNull { it == searchButton } ?: sourceButtons.last()
            val carrierTarget =
                if (carrierButton == searchButton) {
                    searchTarget
                } else {
                    val carrierIndex = actionButtons.indexOfFirst { it == carrierButton }
                    if (carrierIndex < 0) return
                    actionTargets[carrierIndex]
                }
            NativeTrailingTransitionState(
                sourceButtons = sourceButtons,
                destinationSearchHandler = destinationSearch,
                carrierButton = carrierButton,
                carrierTarget = carrierTarget,
                sourceCarrierHandler = carrierTarget.handler,
                sourceCarrierMenu = carrierButton.menu,
                sourceCarrierShowsMenuAsPrimaryAction = carrierButton.showsMenuAsPrimaryAction,
                sourceCarrierSymbol = paintedSymbols[carrierButton] ?: "ellipsis",
                sourceCarrierAccessibility = paintedAccessibility[carrierButton] ?: "Action",
                sourceStackSpacing = trailingStack.spacing,
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
        val remaining = 1.0 - secondHalfProgress
        val spacerWidth = state.spacerInitialWidth * remaining
        val spacing = state.sourceStackSpacing * remaining
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        state.spacerWidthConstraint.constant = spacerWidth
        trailingStack.spacing = spacing
        state.carrierButton.transform = translationTransform(spacerWidth + spacing)
        trailingCluster.superview?.layoutIfNeeded()
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.showDestinationSearchTransition(state: NativeTrailingTransitionState) {
    // Keep the exact source UIButton mounted as the semantic carrier. The temporary trailing spacer
    // occupies the removed leading action's width; as it contracts, the carrier transform contracts
    // by the same amount, keeping the button at one fixed screen coordinate like the leading control.
    state.sourceButtons
        .filterNot { it == state.carrierButton }
        .forEach { button ->
            trailingStack.removeArrangedSubview(button)
            button.removeFromSuperview()
        }
    state.carrierButton.hidden = false
    state.carrierButton.menu = null
    state.carrierButton.showsMenuAsPrimaryAction = false
    state.carrierTarget.handler = state.destinationSearchHandler
    paintChromeButton(state.carrierButton, "magnifyingglass", "Search", clustered = true)
    trailingStack.spacing = state.sourceStackSpacing
    state.spacerWidthConstraint.constant = state.spacerInitialWidth
    if (state.sourceExtraWidth > 0.0 && state.spacer.superview == null) {
        trailingStack.addArrangedSubview(state.spacer)
        state.carrierButton.transform = translationTransform(state.sourceExtraWidth)
    } else {
        state.carrierButton.transform = identityTransform()
    }
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
    if (state.carrierButton.superview != null) {
        trailingStack.removeArrangedSubview(state.carrierButton)
        state.carrierButton.removeFromSuperview()
    }
    state.carrierButton.transform = identityTransform()
    trailingStack.spacing = state.sourceStackSpacing
    state.carrierTarget.handler = state.sourceCarrierHandler
    state.carrierButton.menu = state.sourceCarrierMenu
    state.carrierButton.showsMenuAsPrimaryAction = state.sourceCarrierShowsMenuAsPrimaryAction
    paintChromeButton(
        state.carrierButton,
        state.sourceCarrierSymbol,
        state.sourceCarrierAccessibility,
        clustered = true,
    )
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
    trailingStack.spacing = 0.0
    state.carrierButton.transform = identityTransform()
    if (state.spacer.superview != null) {
        trailingStack.removeArrangedSubview(state.spacer)
        state.spacer.removeFromSuperview()
    }
    trailingStack.spacing = state.sourceStackSpacing
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
        titleWidth = titleWidth.coerceAtLeast(1.0),
        titleHeight = titleHeight.coerceAtLeast(1.0),
        subtitleX = subtitleX,
        subtitleY = subtitleY,
        subtitleWidth = subtitleWidth.coerceAtLeast(1.0),
        subtitleHeight = subtitleHeight.coerceAtLeast(1.0),
        glassAlpha = glassPlate.alpha,
        centered = titleLabel.textAlignment == NSTextAlignmentCenter,
        hostWidth = chromeRow.bounds.useContents { size.width },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.rememberRootTitleSnapshot() {
    val titleSnapshot = captureCurrentTitleSnapshot()
    val arrangedButtons = trailingStack.arrangedSubviews.mapNotNull { it as? UIButton }
    val nativeSearch =
        searchTarget.handler?.takeIf {
            arrangedButtons.any { button -> button == searchButton }
        }
    var carriedSearch: (() -> Unit)? = null
    if (nativeSearch == null) {
        actionButtons.forEachIndexed { index, button ->
            if (
                carriedSearch == null &&
                arrangedButtons.any { arranged -> arranged == button } &&
                paintedSymbols[button] == "magnifyingglass" &&
                paintedAccessibility[button] == "Search"
            ) {
                carriedSearch = actionTargets[index].handler
            }
        }
    }
    lastRootDestinationByLayer[this] =
        NativeRootDestinationSnapshot(
            title = titleSnapshot,
            searchHandler = nativeSearch ?: carriedSearch,
        )
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.applyRouteTitleTransition(progress: Float) {
    val hostWidth = chromeRow.bounds.useContents { size.width }.coerceAtLeast(1.0)
    val destinationSnapshot = lastRootDestinationByLayer[this]?.title?.fittedToHostWidth(hostWidth)
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
    val p = progress.coerceIn(0f, 1f).toDouble()
    val transitionGlassAlpha =
        source.glassAlpha + (destination.glassAlpha - source.glassAlpha) * p
    // The destination is the underlay revealed from the leading edge. Always start it to the left
    // of its settled position and let it parallax right as the foreground page is dismissed. This
    // is especially visible for centered compact root titles such as "Clicks".
    val destinationX =
        destination.x +
            NativeHeaderMetrics.rootTitleParallaxOffsetPt(
                hostWidthPt = hostWidth,
                progress = progress,
                centered = destination.centered,
            )

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
            destinationX,
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

/** A rotation or split-view resize may occur while the root is covered by another route. */
private fun NativeTitleSnapshot.fittedToHostWidth(currentWidth: Double): NativeTitleSnapshot {
    if (kotlin.math.abs(currentWidth - hostWidth) < 0.5) return this
    val laneWidth = (width + currentWidth - hostWidth).coerceAtLeast(1.0)
    val labelWidth = titleWidth.coerceIn(1.0, laneWidth)
    return copy(
        hostWidth = currentWidth,
        width = laneWidth,
        titleX = if (centered) (laneWidth - labelWidth) / 2.0 else titleX,
        titleWidth = labelWidth,
        subtitleWidth = laneWidth,
    )
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
    title.setFrame(CGRectMake(snapshot.titleX, snapshot.titleY, snapshot.titleWidth, snapshot.titleHeight))

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
private fun IosHostNavBarLayer.clearRouteTitleTransition(restoreDestination: Boolean = false) {
    val views =
        transitionViewsByLayer[this] ?: run {
            titleColumn.alpha = 1.0
            avatarButton.transform = identityTransform()
            avatarButton.alpha = 1.0
            return
        }
    val restoreSnapshot =
        if (restoreDestination) {
            views.destinationSnapshot
        } else {
            views.sourceSnapshot
        }
    restoreSnapshot?.let { snapshot ->
        glassPlate.alpha = snapshot.glassAlpha
        glassPlate.hidden = snapshot.glassAlpha < 0.02
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
        rootMenuShapeByLayer.remove(this)
        backButton.hidden = true
        backButton.menu = null
        backButton.showsMenuAsPrimaryAction = false
        backTarget.handler = null
        return
    }

    items.forEachIndexed { index, item ->
        menuClicksByKey["-2:$index"] = item.onClick
    }

    val menuShape =
        items.joinToString(separator = "|") { item ->
            "${item.title}:${item.sfSymbol.orEmpty()}"
        }
    val canReuseExistingMenu =
        rootMenuShapeByLayer[this] == menuShape &&
            backButton.showsMenuAsPrimaryAction &&
            backButton.menu != null &&
            paintedSymbols[backButton] == "ellipsis" &&
            paintedAccessibility[backButton] == "Menu"
    if (canReuseExistingMenu) {
        // UIActions dispatch through menuClicksByKey, which was refreshed above. Keeping the same
        // UIMenu/UIButton prevents the one-frame Liquid Glass rematerialization after every pop.
        backButton.hidden = false
        backTarget.handler = null
        return
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
    rootMenuShapeByLayer[this] = menuShape
}
