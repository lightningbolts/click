@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.QuartzCore.CAGradientLayer
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSLineBreakByClipping
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentLeft
import platform.UIKit.UIBarMetricsDefault
import platform.UIKit.UIBarPosition
import platform.UIKit.UIBarPositionTopAttached
import platform.UIKit.UIBarPositioningProtocol
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationBarAppearance
import platform.UIKit.UINavigationBarDelegateProtocol
import platform.UIKit.UINavigationItem
import platform.UIKit.UIScreen
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UIStackViewAlignmentLeading
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptionTransitionCrossDissolve
import platform.UIKit.UIViewContentMode
import platform.UIKit.UIViewController
import platform.UIKit.UIVisualEffectView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.abs

@OptIn(ExperimentalForeignApi::class)
internal class IosHostNavBarLayer {
    val bar: UINavigationBar =
        UINavigationBar().apply {
            translatesAutoresizingMaskIntoConstraints = false
            setTranslucent(true)
            prefersLargeTitles = false
            clipsToBounds = false
            insetsLayoutMarginsFromSafeArea = false
            backgroundColor = UIColor.clearColor
        }
    internal val glassPlate =
        UIVisualEffectView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            userInteractionEnabled = false
            clipsToBounds = false
        }
    private val item = UINavigationItem()
    internal val titleLabel =
        UILabel().apply {
            font = UIFont.boldSystemFontOfSize(NativeHeaderMetrics.LargeTitlePointSize)
            textAlignment = NSTextAlignmentLeft
            numberOfLines = NativeHeaderMetrics.LargeTitleMaxLines.toLong()
            adjustsFontSizeToFitWidth = false
            lineBreakMode = NSLineBreakByTruncatingTail
            userInteractionEnabled = false
        }
    internal val subtitleLabel =
        UILabel().apply {
            font = UIFont.systemFontOfSize(13.0)
            textAlignment = NSTextAlignmentLeft
            numberOfLines = NativeHeaderMetrics.SubtitleMaxLines.toLong()
            lineBreakMode = NSLineBreakByTruncatingTail
            userInteractionEnabled = false
        }
    internal val titleColumn =
        UIStackView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            axis = UILayoutConstraintAxisVertical
            alignment = UIStackViewAlignmentLeading
            spacing = 2.0
        }
    internal val trailingStack =
        UIStackView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            axis = UILayoutConstraintAxisHorizontal
            alignment = UIStackViewAlignmentCenter
            spacing = NativeHeaderMetrics.ClusterIconSpacingPt
        }
    internal val trailingCluster =
        UIVisualEffectView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            clipsToBounds = false
            backgroundColor = UIColor.clearColor
        }
    internal val chromeRow =
        UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            backgroundColor = UIColor.clearColor
        }
    internal val backButton = makeChromeButton()
    internal val searchButton = makeChromeButton()
    internal val avatarButton = makeChromeButton()
    internal val actionButtons = List(4) { makeChromeButton() }
    private var heightConstraint: NSLayoutConstraint? = null
    internal var titleLeadingToBar: NSLayoutConstraint? = null
    internal var titleLeadingToBack: NSLayoutConstraint? = null
    internal var titleLeadingToAvatar: NSLayoutConstraint? = null
    internal var avatarLeadingToBack: NSLayoutConstraint? = null
    internal var avatarLeadingToBar: NSLayoutConstraint? = null
    internal var titleTrailingToCluster: NSLayoutConstraint? = null
    internal var titleTrailingToBar: NSLayoutConstraint? = null
    internal var titleColumnTopConstraint: NSLayoutConstraint? = null
    internal var titleColumnCenterYConstraint: NSLayoutConstraint? = null
    internal var leadingRevealWidthPt = -1.0
    private var appliedSlideOffsetPt = 0.0
    internal var barLeadingMask: CALayer? = null
    internal var chromeLeadingMask: CALayer? = null
    internal var glassClipHost: CALayer? = null
    private var attachedHost: UIViewController? = null
    private var ownerToken: Any? = null
    private var coverCount = 0
    private var wantVisible = false
    private var suppressed = false
    private val slideOffsets = mutableMapOf<Any, Double>()
    private var searchPinnedVisible = false
    internal val searchTarget = IosBarButtonTarget()
    internal val backTarget = IosBarButtonTarget()
    internal val avatarTarget = IosBarButtonTarget()
    internal val actionTargets = List(4) { IosBarButtonTarget() }
    private var lastVisualKey: String? = null
    private var lastButtonSignature: String? = null
    private var lastCollapseFraction = 0f
    private var lastHeightPt = -1.0
    private var lastTitle: String? = null
    private var lastBoundIdentityUserId: String? = null
    internal var lastAvatarUrl: String? = null
    internal var lastAvatarUserId: String? = null
    internal var usesGlassButtons = false
    internal var lastIsDark = true
    private var lastAppearanceKey: String? = null
    internal var lastClusterChromeKey: String? = null
    internal var rowInstalled = false
    internal var glassFadeMask: CAGradientLayer? = null
    internal val menuClicksByKey = mutableMapOf<String, () -> Unit>()
    private var positionDelegate: IosNavBarPositionDelegate? = null
    internal val avatarPhoto =
        UIImageView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            clipsToBounds = true
            hidden = true
            userInteractionEnabled = false
            contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
        }
    internal val avatarInitialsLabel =
        UILabel().apply {
            translatesAutoresizingMaskIntoConstraints = false
            textAlignment = NSTextAlignmentCenter
            numberOfLines = 1
            lineBreakMode = NSLineBreakByClipping
            adjustsFontSizeToFitWidth = true
            minimumScaleFactor = 0.65
            textColor = UIColor.whiteColor
            font = UIFont.boldSystemFontOfSize(13.0)
            userInteractionEnabled = false
        }
    internal val presenceDot =
        UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            hidden = true
            userInteractionEnabled = false
            backgroundColor = UIColor.colorWithRed(34.0 / 255.0, green = 197.0 / 255.0, blue = 94.0 / 255.0, alpha = 1.0)
        }

    fun acquireCover() {
        coverCount++
        applyVisibility()
    }

    fun releaseCover() {
        coverCount = (coverCount - 1).coerceAtLeast(0)
        applyVisibility()
    }

    fun setWantVisible(visible: Boolean) {
        val changed = wantVisible != visible
        wantVisible = visible
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        if (changed && this === IosNavChrome.overlay && visible) {
            // Next open must not inherit a completed-swipe translation.
            resetSlideTransformLocked()
        }
        applyVisibility()
        if (changed && this === IosNavChrome.overlay) {
            val tabLive = IosNavChrome.tab.isWantVisible()
            val hostW = hostWidthPt()
            if (visible && NativeHeaderMetrics.shouldClipTabChromeUnderOverlay(tabLive)) {
                IosNavChrome.tab.clipLeadingUnderlay(0.0)
            } else if (!visible) {
                if (NativeHeaderMetrics.shouldClearLeadingClipOnOverlayHide(
                        IosNavChrome.tab.leadingClipUncoverPt(),
                        hostW,
                    )
                ) {
                    IosNavChrome.tab.clipLeadingUnderlay(null)
                }
            }
            if (visible) {
                IosHostMapFloatingChrome.clipLeadingUnderlay(0.0)
            } else if (
                NativeHeaderMetrics.shouldClearLeadingClipOnOverlayHide(
                    IosHostMapFloatingChrome.leadingClipUncoverPt(),
                    hostW,
                )
            ) {
                IosHostMapFloatingChrome.clipLeadingUnderlay(null)
            }
            IosNavChrome.tab.setSuppressed(visible)
            if (!visible) {
                // Overlay is already hidden; identity is safe and unsticks the next present.
                resetSlideTransformLocked()
            }
        }
        CATransaction.commit()
        // Restack only when showing the overlay. Re-inserting the tab glass plate on
        // dismiss rematerializes Liquid Glass and flashes the destination header.
        if (changed && visible) {
            IosNavChrome.restack()
        }
    }

    fun setSuppressed(value: Boolean) {
        if (suppressed == value) return
        suppressed = value
        if (NativeHeaderMetrics.shouldRematerializeChromeOnUnsuppress()) {
            applyVisibility()
            return
        }
        val show = wantVisible && coverCount == 0
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        bar.userInteractionEnabled = show && !suppressed
        chromeRow.userInteractionEnabled = show && !suppressed
        CATransaction.commit()
    }

    fun leadingClipUncoverPt(): Double = leadingRevealWidthPt

    fun isWantVisible(): Boolean = wantVisible

    fun owns(owner: Any): Boolean = ownerToken === owner

    fun bringChromeToFront() {
        val hostView = attachedHost?.view ?: return
        if (bar.hidden) return
        hostView.insertSubview(glassPlate, belowSubview = bar)
        hostView.bringSubviewToFront(glassPlate)
        hostView.bringSubviewToFront(bar)
        hostView.bringSubviewToFront(chromeRow)
    }

    fun setSlideOffset(
        owner: Any,
        points: Double,
    ) {
        if (points <= 0.5) {
            slideOffsets.remove(owner)
        } else {
            slideOffsets[owner] = points
        }
        val x = slideOffsets.values.maxOrNull() ?: 0.0
        val hostW = hostWidthPt()
        if (!NativeHeaderMetrics.shouldApplyOverlaySlideTransform(
                overlayWantVisible = isWantVisible(),
                newOffsetPt = x,
                currentAppliedOffsetPt = appliedSlideOffsetPt,
                hostWidthPt = hostW,
            )
        ) {
            return
        }
        appliedSlideOffsetPt = x
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        val transform = CGAffineTransformMakeTranslation(x, 0.0)
        bar.transform = transform
        glassPlate.transform = transform
        chromeRow.transform = transform
        val tabLive = IosNavChrome.tab.isWantVisible()
        if (isWantVisible() && NativeHeaderMetrics.shouldClipTabChromeUnderOverlay(tabLive)) {
            IosNavChrome.tab.clipLeadingUnderlay(NativeHeaderMetrics.overlayUncoverLeadingWidthPt(x))
        }
        if (isWantVisible()) {
            IosHostMapFloatingChrome.clipLeadingUnderlay(
                NativeHeaderMetrics.overlayUncoverLeadingWidthPt(x),
            )
        }
        CATransaction.commit()
    }

    private fun resetSlideTransformLocked() {
        slideOffsets.clear()
        appliedSlideOffsetPt = 0.0
        val identity = CGAffineTransformMakeTranslation(0.0, 0.0)
        bar.transform = identity
        glassPlate.transform = identity
        chromeRow.transform = identity
    }

    private fun hostWidthPt(): Double {
        val fromSuperview = bar.superview?.bounds?.useContents { size.width } ?: 0.0
        if (fromSuperview > 1.0) return fromSuperview
        return UIScreen.mainScreen.bounds.useContents { size.width }
    }

    /**
     * Clip the tab header to [leadingWidthPt] points while an overlay covers it.
     * `null` removes the clip (overlay gone). `0.0` hides every pixel so chat glass
     * cannot show the list title, without toggling `hidden` (that flashed twice).
     */
    fun clipLeadingUnderlay(leadingWidthPt: Double?) {
        applyLeadingRevealMask(leadingWidthPt)
    }

    fun release(
        owner: Any,
        immediate: Boolean = false,
    ) {
        if (ownerToken !== owner) return
        ownerToken = null
        if (immediate) {
            setWantVisible(false)
            return
        }
        dispatch_async(dispatch_get_main_queue()) {
            if (ownerToken == null) {
                setWantVisible(false)
            }
        }
    }

    fun attach(host: UIViewController) {
        if (attachedHost === host && bar.superview == host.view && chromeRow.superview == host.view) {
            return
        }
        detachFromSuperview()
        attachedHost = host
        val hostView = host.view
        hostView.addSubview(glassPlate)
        hostView.addSubview(bar)
        hostView.addSubview(chromeRow)
        hostView.insertSubview(glassPlate, belowSubview = bar)
        val delegate = positionDelegate ?: IosNavBarPositionDelegate().also { positionDelegate = it }
        bar.delegate = delegate
        val height =
            heightConstraint
                ?: bar.heightAnchor.constraintEqualToConstant(NativeHeaderMetrics.ExpandedBarHeightPt).also {
                    heightConstraint = it
                }
        NSLayoutConstraint.activateConstraints(
            listOf(
                bar.topAnchor.constraintEqualToAnchor(hostView.safeAreaLayoutGuide.topAnchor),
                bar.leadingAnchor.constraintEqualToAnchor(hostView.leadingAnchor),
                bar.trailingAnchor.constraintEqualToAnchor(hostView.trailingAnchor),
                height,
                glassPlate.topAnchor.constraintEqualToAnchor(hostView.topAnchor),
                glassPlate.leadingAnchor.constraintEqualToAnchor(hostView.leadingAnchor),
                glassPlate.trailingAnchor.constraintEqualToAnchor(hostView.trailingAnchor),
                glassPlate.bottomAnchor.constraintEqualToAnchor(
                    bar.bottomAnchor,
                    constant = NativeHeaderMetrics.GlassFadeExtensionPt,
                ),
            ),
        )
        installRowIfNeeded()
        item.titleView = null
        item.title = null
        item.hidesBackButton = true
        item.leftBarButtonItem = null
        item.rightBarButtonItems = null
        bar.setItems(listOf(item), animated = false)
        hostView.layoutIfNeeded()
        applyVisibility()
    }

    fun applyAppearance(
        isDarkMode: Boolean,
        reduceTransparency: Boolean,
        usesNativeLiquidGlass: Boolean,
    ) {
        val appearanceKey = "$isDarkMode|$reduceTransparency|$usesNativeLiquidGlass"
        if (appearanceKey == lastAppearanceKey) return
        lastAppearanceKey = appearanceKey
        bar.setTranslucent(true)
        val titleColor =
            if (isDarkMode) {
                UIColor.whiteColor
            } else {
                UIColor.blackColor
            }
        titleLabel.textColor = titleColor
        subtitleLabel.textColor = titleColor.colorWithAlphaComponent(0.62)
        bar.tintColor = titleColor
        bar.backgroundColor = UIColor.clearColor
        lastIsDark = isDarkMode
        val nextGlass = usesNativeLiquidGlass && !reduceTransparency
        if (nextGlass != usesGlassButtons) {
            lastButtonSignature = null
        }
        usesGlassButtons = nextGlass
        chromeButtons().forEach { button ->
            button.tintColor = titleColor
        }
        applyClusterChrome()
        val clear = UIColor.clearColor
        val appearance =
            UINavigationBarAppearance().apply {
                configureWithTransparentBackground()
                backgroundColor = clear
                backgroundEffect = null
                shadowColor = clear
            }
        bar.standardAppearance = appearance
        bar.scrollEdgeAppearance = appearance
        bar.compactAppearance = appearance
        bar.setShadowImage(UIImage())
        bar.setBackgroundImage(UIImage(), forBarMetrics = UIBarMetricsDefault)
        glassPlate.layer.shadowOpacity = 0f
        glassPlate.layer.borderWidth = 0.0
        glassPlate.layer.shadowRadius = 0.0
        val accessibleMaterial =
            if (isDarkMode) {
                UIColor.colorWithRed(0x10 / 255.0, green = 0x12 / 255.0, blue = 0x12 / 255.0, alpha = 0.96)
            } else {
                UIColor.colorWithRed(0xF9 / 255.0, green = 0xF9 / 255.0, blue = 0xF9 / 255.0, alpha = 0.96)
            }
        if (usesNativeLiquidGlass && !reduceTransparency) {
            glassPlate.effect = UIGlassEffect.effectWithStyle(UIGlassEffectStyle.UIGlassEffectStyleRegular)
            glassPlate.backgroundColor =
                if (isDarkMode) {
                    UIColor.colorWithWhite(0.0, alpha = 0.50)
                } else {
                    UIColor.colorWithWhite(1.0, alpha = 0.32)
                }
            applyVisibility()
            return
        }
        if (reduceTransparency) {
            glassPlate.effect = null
            glassPlate.backgroundColor = accessibleMaterial
        } else {
            val materialStyle =
                if (isDarkMode) {
                    UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialDark
                } else {
                    UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialLight
                }
            glassPlate.effect = UIBlurEffect.effectWithStyle(materialStyle)
            glassPlate.backgroundColor = clear
        }
        applyVisibility()
    }

    fun update(
        owner: Any,
        host: UIViewController,
        title: String,
        subtitle: String?,
        presenceOnline: Boolean?,
        identity: NativeChromeIdentity?,
        visible: Boolean,
        collapseFraction: Float,
        hasSubtitle: Boolean,
        onOpenSearch: (() -> Unit)?,
        onNavigateBack: (() -> Unit)?,
        trailingActions: List<NativeChromeAction>,
        collapseSearchIntoBar: Boolean,
        leadingClose: Boolean = false,
    ) {
        if (this === IosNavChrome.overlay) {
            val exclusive = IosNavChrome.overlayExclusiveOwner
            if (exclusive != null && exclusive !== owner) {
                return
            }
        }
        if (attachedHost !== host) {
            attach(host)
        }
        ownerToken = owner
        val fraction = collapseFraction.coerceIn(0f, 1f)
        val stackIdentity = identity != null
        val subtitleText =
            subtitle?.trim()?.takeIf { it.isNotEmpty() }
                ?: when (presenceOnline) {
                    true -> "Online"
                    false -> "Offline"
                    null -> ""
                }
        val visualKey =
            buildString {
                append(title)
                append('|')
                append(subtitleText)
                append('|')
                append(presenceOnline)
                append('|')
                append(identity?.userId.orEmpty())
                append('|')
                append(fraction)
                append('|')
                append(hasSubtitle)
                append('|')
                append(visible)
                append('|')
                append(stackIdentity)
                append('|')
                append(onNavigateBack != null)
                append('|')
                append(leadingClose)
                trailingActions.forEach { action ->
                    append('|')
                    append(action.sfSymbol)
                    append(':')
                    append(action.contentDescription)
                }
            }
        bindRow(onOpenSearch, onNavigateBack, trailingActions, collapseSearchIntoBar, fraction, leadingClose)
        if (visualKey == lastVisualKey) {
            bindIdentity(
                hasBack = onNavigateBack != null,
                identity = identity,
                presenceOnline = presenceOnline,
            )
            setWantVisible(visible)
            return
        }
        lastVisualKey = visualKey
        bar.prefersLargeTitles = false
        item.titleView = null
        item.title = null
        item.leftBarButtonItem = null
        item.rightBarButtonItems = null
        lastCollapseFraction = fraction
        val hasBack = onNavigateBack != null
        val stackTwoLine =
            NativeHeaderMetrics.shouldStackCompactSubtitle(
                hasBack = hasBack,
                hasIdentity = stackIdentity,
                hasSubtitle = subtitleText.isNotEmpty(),
                collapseFraction = fraction,
            )
        val growCompactSubtitle =
            NativeHeaderMetrics.shouldGrowCompactBarForStackedSubtitle(
                hasBack = hasBack,
                hasIdentity = stackIdentity,
                hasSubtitle = subtitleText.isNotEmpty(),
                collapseFraction = fraction,
            )
        setBarHeight(
            NativeHeaderMetrics.barHeightPt(
                fraction,
                hasSubtitle,
                stackSubtitle = stackTwoLine,
                growCompactSubtitle = growCompactSubtitle,
            ),
        )
        titleColumnTopConstraint?.constant = NativeHeaderMetrics.titleColumnTopInsetPt(fraction)
        titleColumnTopConstraint?.active = !stackTwoLine
        titleColumnCenterYConstraint?.active = stackTwoLine
        chromeRow.clipsToBounds = false
        titleLabel.font = UIFont.boldSystemFontOfSize(NativeHeaderMetrics.titlePointSize(fraction))
        val identityUserId = identity?.userId
        val snapTitle =
            identityUserId != lastBoundIdentityUserId ||
                lastTitle == null ||
                !wantVisible ||
                bar.hidden
        lastBoundIdentityUserId = identityUserId
        if (!snapTitle && title != lastTitle && lastTitle != null) {
            UIView.transitionWithView(
                titleLabel,
                duration = 0.18,
                options = UIViewAnimationOptionTransitionCrossDissolve,
                animations = { titleLabel.text = title },
                completion = null,
            )
        } else {
            titleLabel.layer.removeAllAnimations()
            titleLabel.text = title
        }
        lastTitle = title
        val compactTabRoot =
            NativeHeaderMetrics.isCompactTabRootChrome(
                collapseFraction = fraction,
                hasBack = hasBack,
                hasIdentity = stackIdentity,
            )
        titleColumn.axis =
            if (compactTabRoot) UILayoutConstraintAxisHorizontal else UILayoutConstraintAxisVertical
        titleColumn.alignment =
            if (compactTabRoot) UIStackViewAlignmentCenter else UIStackViewAlignmentLeading
        titleColumn.spacing =
            when {
                stackTwoLine -> NativeHeaderMetrics.StackedIdentitySpacingPt
                compactTabRoot -> 6.0
                else -> 2.0
            }
        subtitleLabel.font =
            UIFont.systemFontOfSize(
                if (stackTwoLine) {
                    NativeHeaderMetrics.StackedIdentitySubtitlePointSize
                } else {
                    13.0
                },
            )
        titleLabel.numberOfLines =
            if (stackTwoLine || NativeHeaderMetrics.isCompactTitle(fraction)) {
                1
            } else {
                NativeHeaderMetrics.titleMaxLines(fraction).toLong()
            }
        subtitleLabel.numberOfLines =
            when {
                stackTwoLine && growCompactSubtitle -> NativeHeaderMetrics.SubtitleMaxLines.toLong()
                stackTwoLine -> 1
                else -> NativeHeaderMetrics.SubtitleMaxLines.toLong()
            }
        titleLabel.setContentCompressionResistancePriority(749f, forAxis = UILayoutConstraintAxisHorizontal)
        subtitleLabel.setContentCompressionResistancePriority(751f, forAxis = UILayoutConstraintAxisHorizontal)
        subtitleLabel.text = subtitleText
        subtitleLabel.hidden = subtitleText.isEmpty()
        subtitleLabel.textColor =
            if (presenceOnline == true && !subtitleText.equals("Typing…", ignoreCase = true)) {
                UIColor.colorWithRed(34.0 / 255.0, green = 197.0 / 255.0, blue = 94.0 / 255.0, alpha = 1.0)
            } else {
                titleLabel.textColor.colorWithAlphaComponent(0.62)
            }
        bindIdentity(
            hasBack = onNavigateBack != null,
            identity = identity,
            presenceOnline = presenceOnline,
        )
        setWantVisible(visible)
    }

    private fun setBarHeight(targetPt: Double) {
        val current = heightConstraint?.constant ?: targetPt
        val jump = abs(targetPt - current)
        if (jump < 0.5 && lastHeightPt >= 0.0) {
            return
        }
        heightConstraint?.constant = targetPt
        val animateJump = lastHeightPt >= 0.0 && jump > 20.0 && wantVisible && !bar.hidden
        if (animateJump) {
            UIView.animateWithDuration(0.22) {
                bar.superview?.layoutIfNeeded()
                chromeRow.superview?.layoutIfNeeded()
            }
        } else {
            bar.superview?.layoutIfNeeded()
        }
        lastHeightPt = targetPt
        updateGlassFadeMask()
    }

    private fun bindRow(
        onOpenSearch: (() -> Unit)?,
        onNavigateBack: (() -> Unit)?,
        trailingActions: List<NativeChromeAction>,
        collapseSearchIntoBar: Boolean,
        collapseFraction: Float,
        leadingClose: Boolean,
    ) {
        val showSearch =
            when {
                onOpenSearch == null -> false
                !collapseSearchIntoBar -> true
                else -> {
                    searchPinnedVisible =
                        if (searchPinnedVisible) {
                            collapseFraction > 0.28f
                        } else {
                            collapseFraction > 0.45f
                        }
                    searchPinnedVisible
                }
            }
        val signature =
            buildString {
                append(if (onNavigateBack != null) "B" else "-")
                append(if (leadingClose) "X" else "C")
                append(if (showSearch) "S" else "-")
                trailingActions.forEach { action ->
                    append('|')
                    append(action.sfSymbol)
                    append(':')
                    append(action.contentDescription)
                    action.menuItems.forEach { item ->
                        append('>')
                        append(item.title)
                    }
                }
            }
        backTarget.handler = onNavigateBack
        searchTarget.handler = onOpenSearch
        trailingActions.forEachIndexed { index, action ->
            actionTargets.getOrNull(index)?.handler = action.onClick
            action.menuItems.forEachIndexed { itemIndex, item ->
                menuClicksByKey["$index:$itemIndex"] = item.onClick
            }
        }
        val compactTabRoot =
            NativeHeaderMetrics.isCompactTabRootChrome(
                collapseFraction = collapseFraction,
                hasBack = onNavigateBack != null,
                hasIdentity = false,
            )
        titleLabel.textAlignment = if (compactTabRoot) NSTextAlignmentCenter else NSTextAlignmentLeft
        subtitleLabel.textAlignment = titleLabel.textAlignment
        if (signature != lastButtonSignature) {
            lastButtonSignature = signature
            backButton.hidden = onNavigateBack == null
            rebuildTrailing(trailingActions, showSearch)
            paintChromeButton(
                backButton,
                if (leadingClose) "xmark" else "chevron.backward",
                if (leadingClose) "Close" else "Back",
                clustered = false,
            )
        }
        applyTitleSlot(onNavigateBack != null, trailingActions.size + if (showSearch) 1 else 0)
    }

    private fun applyVisibility() {
        val exclusiveShow =
            this === IosNavChrome.overlay &&
                IosNavChrome.overlayExclusiveOwner != null &&
                ownerToken === IosNavChrome.overlayExclusiveOwner
        val show = wantVisible && (coverCount == 0 || exclusiveShow)
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        bar.hidden = !show
        bar.userInteractionEnabled = show && !suppressed
        chromeRow.hidden = !show
        chromeRow.userInteractionEnabled = show && !suppressed
        val glassAlpha = NativeHeaderMetrics.collapsedGlassAlpha(lastCollapseFraction)
        glassPlate.alpha = glassAlpha.toDouble()
        glassPlate.hidden = !show || glassAlpha < 0.02f
        CATransaction.commit()
        if (show) updateGlassFadeMask()
    }

    private fun detachFromSuperview() {
        resetSlideTransformLocked()
        glassPlate.removeFromSuperview()
        chromeRow.removeFromSuperview()
        bar.removeFromSuperview()
        attachedHost = null
    }

    private fun chromeButtons(): List<UIButton> = listOf(backButton, searchButton) + actionButtons
}

private fun makeChromeButton(): UIButton =
    UIButton.buttonWithType(UIButtonTypeSystem).apply {
        translatesAutoresizingMaskIntoConstraints = false
        widthAnchor.constraintEqualToConstant(NativeHeaderMetrics.ChromeButtonSizePt).active = true
        heightAnchor.constraintEqualToConstant(NativeHeaderMetrics.ChromeButtonSizePt).active = true
    }

@OptIn(ExperimentalForeignApi::class)
private class IosNavBarPositionDelegate :
    NSObject(),
    UINavigationBarDelegateProtocol {
    override fun positionForBar(bar: UIBarPositioningProtocol): UIBarPosition = UIBarPositionTopAttached
}

@OptIn(BetaInteropApi::class)
internal class IosBarButtonTarget : NSObject() {
    var handler: (() -> Unit)? = null

    @ObjCAction
    fun didTap() {
        handler?.invoke()
    }
}
