@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.rememberReduceTransparencyEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLRequest
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.QuartzCore.CAGradientLayer
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSLineBreakByClipping
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentLeft
import platform.UIKit.UIAction
import platform.UIKit.UIBarMetricsDefault
import platform.UIKit.UIBarPosition
import platform.UIKit.UIBarPositionTopAttached
import platform.UIKit.UIBarPositioningProtocol
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UICornerConfiguration
import platform.UIKit.UIFont
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightMedium
import platform.UIKit.UIImageView
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIMenu
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationBarAppearance
import platform.UIKit.UINavigationBarDelegateProtocol
import platform.UIKit.UINavigationItem
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UIStackViewAlignmentLeading
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptionTransitionCrossDissolve
import platform.UIKit.UIViewContentMode
import platform.UIKit.UIViewController
import platform.UIKit.UIVisualEffectView
import platform.UIKit.setAccessibilityLabel
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import kotlin.math.abs

@OptIn(ExperimentalForeignApi::class, ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
actual fun NativeCollapsingScaffold(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    presenceOnline: Boolean?,
    navigationIcon: @Composable (() -> Unit)?,
    actions: @Composable (RowScope.() -> Unit)?,
    onOpenSearch: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    collapseSearchIntoBar: Boolean,
    showHeader: Boolean,
    belowHeaderSpacing: Dp,
    horizontalPadding: Dp,
    lazyListState: LazyListState,
    headerBelowContent: @Composable (() -> Unit)?,
    verticalArrangement: Arrangement.Vertical,
    content: LazyListScope.() -> Unit,
) {
    val bottomChrome = rememberBottomChromePadding()
    val statusBarTop = rememberStatusBarTopPadding()
    val hasSubtitle = !subtitle.isNullOrBlank() || presenceOnline == true
    val extraHeight = NativeHeaderMetrics.collapseRangeDp(hasSubtitle)
    val density = LocalDensity.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val extraPx = with(density) { extraHeight.toPx() }
    SideEffect {
        if (scrollBehavior.state.heightOffsetLimit != -extraPx) {
            scrollBehavior.state.heightOffsetLimit = -extraPx
        }
    }
    val collapseFraction =
        when {
            !showHeader -> 0f
            onNavigateBack != null -> 1f
            else -> scrollBehavior.state.collapsedFraction
        }
    val chromeActive = LocalNativeChromeActive.current
    rememberIosHostNavBar(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        collapseFraction = collapseFraction,
        visible = showHeader && chromeActive,
        overlay = onNavigateBack != null,
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
        collapseSearchIntoBar = collapseSearchIntoBar,
    )

    val headerClearance =
        if (showHeader) {
            NativeHeaderMetrics.headerClearanceDp(
                statusBarTop = statusBarTop,
                collapseFraction = collapseFraction,
                hasSubtitle = hasSubtitle,
                growCompactSubtitle =
                    NativeHeaderMetrics.shouldGrowCompactBarForStackedSubtitle(
                        hasBack = onNavigateBack != null,
                        hasIdentity = false,
                        hasSubtitle = hasSubtitle,
                        collapseFraction = collapseFraction,
                    ),
            )
        } else {
            statusBarTop + 16.dp
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(
                    if (showHeader) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    },
                ),
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = verticalArrangement,
            contentPadding =
                PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = headerClearance + belowHeaderSpacing,
                    bottom = bottomChrome,
                ),
            content = content,
        )
        if (showHeader) {
            if (!chromeActive) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.fillMaxWidth().height(headerClearance))
                    headerBelowContent?.invoke()
                }
            } else if (headerBelowContent != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = headerClearance),
                ) {
                    headerBelowContent.invoke()
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
actual fun NativeCollapsingScrollScaffold(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    presenceOnline: Boolean?,
    navigationIcon: @Composable (() -> Unit)?,
    actions: @Composable (RowScope.() -> Unit)?,
    onOpenSearch: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    horizontalPadding: Dp,
    content: @Composable (Modifier) -> Unit,
) {
    val scrollState = rememberScrollState()
    val bottomChrome = rememberBottomChromePadding()
    val statusBarTop = rememberStatusBarTopPadding()
    val hasSubtitle = !subtitle.isNullOrBlank() || presenceOnline == true
    val extraHeight = NativeHeaderMetrics.collapseRangeDp(hasSubtitle)
    val density = LocalDensity.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val extraPx = with(density) { extraHeight.toPx() }
    SideEffect {
        if (scrollBehavior.state.heightOffsetLimit != -extraPx) {
            scrollBehavior.state.heightOffsetLimit = -extraPx
        }
    }
    val collapseFraction = if (onNavigateBack != null) 1f else scrollBehavior.state.collapsedFraction
    val chromeActive = LocalNativeChromeActive.current
    rememberIosHostNavBar(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        collapseFraction = collapseFraction,
        visible = chromeActive,
        overlay = onNavigateBack != null,
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
        collapseSearchIntoBar = false,
    )

    val headerClearance =
        NativeHeaderMetrics.headerClearanceDp(
            statusBarTop = statusBarTop,
            collapseFraction = collapseFraction,
            hasSubtitle = hasSubtitle,
            growCompactSubtitle =
                NativeHeaderMetrics.shouldGrowCompactBarForStackedSubtitle(
                    hasBack = onNavigateBack != null,
                    hasIdentity = false,
                    hasSubtitle = hasSubtitle,
                    collapseFraction = collapseFraction,
                ),
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = headerClearance,
                        bottom = bottomChrome,
                    ),
        ) {
            content(Modifier.fillMaxWidth())
        }
        if (!chromeActive) {
            Spacer(Modifier.fillMaxWidth().height(headerClearance))
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun rememberIosHostNavBar(
    title: String,
    subtitle: String?,
    presenceOnline: Boolean?,
    collapseFraction: Float,
    visible: Boolean,
    overlay: Boolean,
    onOpenSearch: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    collapseSearchIntoBar: Boolean,
    identity: NativeChromeIdentity? = null,
) {
    val viewController = LocalUIViewController.current
    val isDarkMode = LocalIsDarkMode.current
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val usesNativeLiquidGlass =
        remember {
            NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }
    val owner = remember { Any() }
    val searchHandler by rememberUpdatedState(onOpenSearch)
    val backHandler by rememberUpdatedState(onNavigateBack)
    val trailingHandlers by rememberUpdatedState(nativeTrailingActions)
    val identityHandler by rememberUpdatedState(identity)
    val hasSubtitle = !subtitle.isNullOrBlank() || presenceOnline != null
    val layer = if (overlay) IosNavChrome.overlay else IosNavChrome.tab

    DisposableEffect(viewController, overlay) {
        layer.attach(viewController)
        onDispose { layer.release(owner, immediate = overlay) }
    }

    SideEffect {
        if (!visible) {
            // Inactive underlays (Map swipe-back composing Home) must not unhide stale
            // tab chrome. Only hide if this composition currently owns the layer.
            if (layer.owns(owner)) {
                layer.setWantVisible(false)
            }
            return@SideEffect
        }
        layer.applyAppearance(
            isDarkMode = isDarkMode,
            reduceTransparency = reduceTransparency,
            usesNativeLiquidGlass = usesNativeLiquidGlass,
        )
        layer.update(
            owner = owner,
            host = viewController,
            title = title,
            subtitle = subtitle,
            presenceOnline = presenceOnline,
            identity = identityHandler,
            visible = true,
            collapseFraction = collapseFraction,
            hasSubtitle = hasSubtitle,
            onOpenSearch = searchHandler,
            onNavigateBack = backHandler,
            trailingActions = trailingHandlers,
            collapseSearchIntoBar = collapseSearchIntoBar,
        )
    }
}

@Composable
actual fun HidePlatformNativeNavigationBar() {
    DisposableEffect(Unit) {
        IosNavChrome.acquireCover()
        onDispose { IosNavChrome.releaseCover() }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformNativeNavigationBarSwipeReveal(revealPx: MutableFloatState) {
    val owner = remember { Any() }
    val density = LocalDensity.current.density
    DisposableEffect(owner) {
        onDispose { IosNavChrome.overlay.setSlideOffset(owner, 0.0) }
    }
    LaunchedEffect(owner, density) {
        snapshotFlow { revealPx.floatValue }.collect { px ->
            IosNavChrome.overlay.setSlideOffset(owner, (px / density).toDouble())
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun BindPlatformNativeNavigationBar(
    title: String,
    subtitle: String?,
    presenceOnline: Boolean?,
    identity: NativeChromeIdentity?,
    onNavigateBack: (() -> Unit)?,
    onOpenSearch: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    collapseFraction: Float,
) {
    rememberIosHostNavBar(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        collapseFraction = collapseFraction,
        visible = LocalNativeChromeActive.current,
        overlay = true,
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
        collapseSearchIntoBar = false,
        identity = identity,
    )
}

@OptIn(ExperimentalForeignApi::class)
private class IosHostNavBarLayer {
    val bar: UINavigationBar =
        UINavigationBar().apply {
            translatesAutoresizingMaskIntoConstraints = false
            setTranslucent(true)
            prefersLargeTitles = false
            clipsToBounds = false
            insetsLayoutMarginsFromSafeArea = false
            backgroundColor = UIColor.clearColor
        }
    private val glassPlate =
        UIVisualEffectView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            userInteractionEnabled = false
            clipsToBounds = false
        }
    private val item = UINavigationItem()
    private val titleLabel =
        UILabel().apply {
            font = UIFont.boldSystemFontOfSize(NativeHeaderMetrics.LargeTitlePointSize)
            textAlignment = NSTextAlignmentLeft
            numberOfLines = NativeHeaderMetrics.LargeTitleMaxLines.toLong()
            adjustsFontSizeToFitWidth = false
            lineBreakMode = NSLineBreakByTruncatingTail
            userInteractionEnabled = false
        }
    private val subtitleLabel =
        UILabel().apply {
            font = UIFont.systemFontOfSize(13.0)
            textAlignment = NSTextAlignmentLeft
            numberOfLines = NativeHeaderMetrics.SubtitleMaxLines.toLong()
            lineBreakMode = NSLineBreakByTruncatingTail
            userInteractionEnabled = false
        }
    private val titleColumn =
        UIStackView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            axis = UILayoutConstraintAxisVertical
            alignment = UIStackViewAlignmentLeading
            spacing = 2.0
        }
    private val trailingStack =
        UIStackView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            axis = UILayoutConstraintAxisHorizontal
            alignment = UIStackViewAlignmentCenter
            spacing = NativeHeaderMetrics.ClusterIconSpacingPt
        }
    private val trailingCluster =
        UIVisualEffectView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            clipsToBounds = false
            backgroundColor = UIColor.clearColor
        }
    private val chromeRow =
        UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            backgroundColor = UIColor.clearColor
        }
    private val backButton = makeChromeButton()
    private val searchButton = makeChromeButton()
    private val avatarButton = makeChromeButton()
    private val actionButtons = List(4) { makeChromeButton() }
    private var heightConstraint: NSLayoutConstraint? = null
    private var titleLeadingToBar: NSLayoutConstraint? = null
    private var titleLeadingToBack: NSLayoutConstraint? = null
    private var titleLeadingToAvatar: NSLayoutConstraint? = null
    private var avatarLeadingToBack: NSLayoutConstraint? = null
    private var avatarLeadingToBar: NSLayoutConstraint? = null
    private var titleTrailingToCluster: NSLayoutConstraint? = null
    private var titleTrailingToBar: NSLayoutConstraint? = null
    private var titleColumnTopConstraint: NSLayoutConstraint? = null
    private var titleColumnCenterYConstraint: NSLayoutConstraint? = null
    private var leadingRevealWidthPt = -1.0
    private var barLeadingMask: CALayer? = null
    private var chromeLeadingMask: CALayer? = null
    private var glassClipHost: CALayer? = null
    private var attachedHost: UIViewController? = null
    private var ownerToken: Any? = null
    private var coverCount = 0
    private var wantVisible = false
    private var suppressed = false
    private val slideOffsets = mutableMapOf<Any, Double>()
    private var searchPinnedVisible = false
    private val searchTarget = IosBarButtonTarget()
    private val backTarget = IosBarButtonTarget()
    private val avatarTarget = IosBarButtonTarget()
    private val actionTargets = List(4) { IosBarButtonTarget() }
    private var lastVisualKey: String? = null
    private var lastButtonSignature: String? = null
    private var lastCollapseFraction = 0f
    private var lastHeightPt = -1.0
    private var lastTitle: String? = null
    private var lastBoundIdentityUserId: String? = null
    private var lastAvatarUrl: String? = null
    private var lastAvatarUserId: String? = null
    private var usesGlassButtons = false
    private var lastIsDark = true
    private var lastAppearanceKey: String? = null
    private var lastClusterChromeKey: String? = null
    private var rowInstalled = false
    private var glassFadeMask: CAGradientLayer? = null
    private val menuClicksByKey = mutableMapOf<String, () -> Unit>()
    private var positionDelegate: IosNavBarPositionDelegate? = null
    private val avatarPhoto =
        UIImageView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            clipsToBounds = true
            hidden = true
            userInteractionEnabled = false
            contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
        }
    private val avatarInitialsLabel =
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
    private val presenceDot =
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
        applyVisibility()
        if (changed && this === IosNavChrome.overlay) {
            val tabLive = IosNavChrome.tab.isWantVisible()
            if (visible && NativeHeaderMetrics.shouldClipTabChromeUnderOverlay(tabLive)) {
                IosNavChrome.tab.clipLeadingUnderlay(0.0)
            } else if (!visible) {
                IosNavChrome.tab.clipLeadingUnderlay(null)
            }
            IosHostMapFloatingChrome.clipLeadingUnderlay(if (visible) 0.0 else null)
            IosNavChrome.tab.setSuppressed(visible)
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
        applyVisibility()
    }

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
    ) {
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
                trailingActions.forEach { action ->
                    append('|')
                    append(action.sfSymbol)
                    append(':')
                    append(action.contentDescription)
                }
            }
        bindRow(onOpenSearch, onNavigateBack, trailingActions, collapseSearchIntoBar, fraction)
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
        val snapTitle = identityUserId != lastBoundIdentityUserId || lastTitle == null
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
        if (lastHeightPt >= 0.0 && jump > 20.0) {
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
            paintChromeButton(backButton, "chevron.backward", "Back", clustered = false)
        }
        applyTitleSlot(onNavigateBack != null, trailingActions.size + if (showSearch) 1 else 0)
    }

    private fun bindIdentity(
        hasBack: Boolean,
        identity: NativeChromeIdentity?,
        presenceOnline: Boolean?,
    ) {
        val showAvatar = identity != null
        avatarButton.hidden = !showAvatar
        presenceDot.hidden = !showAvatar || presenceOnline != true
        titleLeadingToAvatar?.active = showAvatar
        titleLeadingToBack?.active = hasBack && !showAvatar
        titleLeadingToBar?.active = !hasBack && !showAvatar
        avatarLeadingToBack?.active = hasBack && showAvatar
        avatarLeadingToBar?.active = !hasBack && showAvatar
        avatarTarget.handler = identity?.onClick
        if (identity == null) {
            lastAvatarUrl = null
            lastAvatarUserId = null
            avatarPhoto.image = null
            avatarPhoto.hidden = true
            avatarInitialsLabel.text = null
            avatarInitialsLabel.hidden = true
            return
        }
        val spec =
            avatarFaceSpec(
                displayName = identity.displayName,
                email = identity.email,
                avatarUrl = identity.avatarUrl,
                userId = identity.userId,
            )
        applyIdentityFace(identity.userId, spec)
        avatarButton.setAccessibilityLabel(identity.displayName ?: "Profile")
    }

    private fun applyIdentityFace(
        userId: String,
        spec: AvatarFaceSpec,
    ) {
        val cachedPhoto = spec.photoUrl?.let { cachedAvatarPhoto(it) }
        if (cachedPhoto != null) {
            lastAvatarUserId = userId
            lastAvatarUrl = spec.photoUrl
            showIdentityPhoto(cachedPhoto)
            return
        }
        val sameFace = userId == lastAvatarUserId && lastAvatarUrl == spec.photoUrl
        if (sameFace && (spec.photoUrl == null || avatarPhoto.image != null)) {
            if (spec.photoUrl == null) {
                showIdentityInitials(spec)
            }
            return
        }
        val previousUser = lastAvatarUserId
        lastAvatarUserId = userId
        if (previousUser != userId) {
            avatarPhoto.image = null
            avatarPhoto.hidden = true
        }
        showIdentityInitials(spec)
        loadIdentityPhoto(spec.photoUrl)
    }

    private fun showIdentityInitials(spec: AvatarFaceSpec) {
        val fill =
            UIColor.colorWithRed(
                spec.background.red.toDouble(),
                green = spec.background.green.toDouble(),
                blue = spec.background.blue.toDouble(),
                alpha = spec.background.alpha.toDouble(),
            )
        avatarButton.configuration = null
        avatarButton.clipsToBounds = true
        avatarButton.layer.cornerRadius = NativeHeaderMetrics.ChromeButtonSizePt / 2.0
        avatarButton.backgroundColor = fill
        avatarButton.setTitle("", forState = UIControlStateNormal)
        avatarButton.setImage(null, forState = UIControlStateNormal)
        avatarInitialsLabel.text = spec.initials
        avatarInitialsLabel.hidden = false
        if (spec.photoUrl == null || avatarPhoto.image == null) {
            avatarPhoto.hidden = true
        }
    }

    private fun showIdentityPhoto(image: UIImage) {
        avatarButton.configuration = null
        avatarButton.clipsToBounds = true
        avatarButton.layer.cornerRadius = NativeHeaderMetrics.ChromeButtonSizePt / 2.0
        avatarButton.setTitle("", forState = UIControlStateNormal)
        avatarPhoto.image = image
        avatarPhoto.hidden = false
        avatarInitialsLabel.hidden = true
    }

    private fun loadIdentityPhoto(url: String?) {
        val trimmed = url?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed == lastAvatarUrl && avatarPhoto.image != null) return
        lastAvatarUrl = trimmed
        if (trimmed == null) return
        val immediate = cachedAvatarPhoto(trimmed)
        if (immediate != null) {
            showIdentityPhoto(immediate)
            return
        }
        val nsUrl = NSURL.URLWithString(trimmed) ?: return
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val data = NSData.dataWithContentsOfURL(nsUrl)
            val image = data?.let { UIImage.imageWithData(it) }
            dispatch_async(dispatch_get_main_queue()) {
                if (lastAvatarUrl == trimmed && image != null) {
                    IosNavChrome.avatarPhotos[trimmed] = image
                    showIdentityPhoto(image)
                }
            }
        }
    }

    private fun rebuildTrailing(
        trailingActions: List<NativeChromeAction>,
        showSearch: Boolean,
    ) {
        trailingStack.arrangedSubviews.map { it as UIView }.forEach { view ->
            trailingStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        trailingActions.forEachIndexed { index, action ->
            val button = actionButtons[index]
            button.hidden = false
            bindNativeMenu(button, action, actionIndex = index)
            paintChromeButton(button, action.sfSymbol, action.contentDescription, clustered = true)
            trailingStack.addArrangedSubview(button)
        }
        actionButtons.drop(trailingActions.size).forEach { button ->
            button.hidden = true
            button.menu = null
            button.showsMenuAsPrimaryAction = false
        }
        if (showSearch) {
            searchButton.hidden = false
            bindNativeMenu(searchButton, null, actionIndex = -1)
            paintChromeButton(searchButton, "magnifyingglass", "Search", clustered = true)
            trailingStack.addArrangedSubview(searchButton)
        } else {
            searchButton.hidden = true
            searchButton.menu = null
            searchButton.showsMenuAsPrimaryAction = false
        }
        val hasTrailing = trailingActions.isNotEmpty() || showSearch
        trailingCluster.hidden = !hasTrailing
        titleTrailingToCluster?.active = hasTrailing
        titleTrailingToBar?.active = !hasTrailing
    }

    private fun bindNativeMenu(
        button: UIButton,
        action: NativeChromeAction?,
        actionIndex: Int,
    ) {
        val items = action?.menuItems.orEmpty()
        if (items.isEmpty()) {
            button.menu = null
            button.showsMenuAsPrimaryAction = false
            return
        }
        button.showsMenuAsPrimaryAction = true
        button.menu =
            UIMenu.menuWithTitle(
                title = "",
                children =
                    items.mapIndexed { itemIndex, item ->
                        val key = "$actionIndex:$itemIndex"
                        UIAction.actionWithTitle(
                            title = item.title,
                            image = item.sfSymbol?.let { UIImage.systemImageNamed(it) },
                            identifier = null,
                            handler = { menuClicksByKey[key]?.invoke() },
                        )
                    },
            )
    }

    private fun applyTitleSlot(
        hasBack: Boolean,
        trailingCount: Int,
    ) {
        val barWidth = bar.bounds.useContents { size.width }
        val leading = NativeHeaderMetrics.titleLeadingInsetPt(hasBack)
        val trailing = NativeHeaderMetrics.titleTrailingInsetPt(trailingCount)
        titleLabel.preferredMaxLayoutWidth =
            NativeHeaderMetrics.titleMaxWidthPt(barWidth, leading, trailing)
        subtitleLabel.preferredMaxLayoutWidth = titleLabel.preferredMaxLayoutWidth
    }

    private fun installRowIfNeeded() {
        if (rowInstalled) return
        titleColumn.addArrangedSubview(titleLabel)
        titleColumn.addArrangedSubview(subtitleLabel)
        chromeRow.addSubview(backButton)
        chromeRow.addSubview(avatarButton)
        avatarButton.addSubview(avatarInitialsLabel)
        avatarButton.addSubview(avatarPhoto)
        chromeRow.addSubview(presenceDot)
        chromeRow.addSubview(titleColumn)
        chromeRow.addSubview(trailingCluster)
        trailingCluster.contentView.addSubview(trailingStack)
        backButton.addTarget(
            backTarget,
            action = NSSelectorFromString("didTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
        avatarButton.addTarget(
            avatarTarget,
            action = NSSelectorFromString("didTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
        searchButton.addTarget(
            searchTarget,
            action = NSSelectorFromString("didTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
        actionButtons.forEachIndexed { index, button ->
            button.addTarget(
                actionTargets[index],
                action = NSSelectorFromString("didTap"),
                forControlEvents = UIControlEventTouchUpInside,
            )
        }
        val leadingBar =
            titleColumn.leadingAnchor.constraintEqualToAnchor(
                chromeRow.leadingAnchor,
                constant = NativeHeaderMetrics.LeadingInsetPt,
            )
        val leadingBack =
            titleColumn.leadingAnchor.constraintEqualToAnchor(
                backButton.trailingAnchor,
                constant = NativeHeaderMetrics.TitleGutterPt,
            )
        leadingBack.active = false
        titleLeadingToBar = leadingBar
        titleLeadingToBack = leadingBack
        val avatarSize = NativeHeaderMetrics.ChromeButtonSizePt
        val leadingAvatar =
            titleColumn.leadingAnchor.constraintEqualToAnchor(
                avatarButton.trailingAnchor,
                constant = NativeHeaderMetrics.TitleGutterPt,
            )
        leadingAvatar.active = false
        titleLeadingToAvatar = leadingAvatar
        val avatarFromBack =
            avatarButton.leadingAnchor.constraintEqualToAnchor(
                backButton.trailingAnchor,
                constant = NativeHeaderMetrics.TitleGutterPt,
            )
        avatarFromBack.active = false
        avatarLeadingToBack = avatarFromBack
        val avatarFromBar =
            avatarButton.leadingAnchor.constraintEqualToAnchor(
                chromeRow.leadingAnchor,
                constant = NativeHeaderMetrics.LeadingInsetPt,
            )
        avatarFromBar.active = false
        avatarLeadingToBar = avatarFromBar
        val trailingClusterPin =
            titleColumn.trailingAnchor.constraintEqualToAnchor(
                trailingCluster.leadingAnchor,
                constant = -NativeHeaderMetrics.TitleGutterPt,
            )
        val trailingBarPin =
            titleColumn.trailingAnchor.constraintEqualToAnchor(
                chromeRow.trailingAnchor,
                constant = -NativeHeaderMetrics.TrailingInsetPt,
            )
        trailingBarPin.active = false
        titleTrailingToCluster = trailingClusterPin
        titleTrailingToBar = trailingBarPin
        val inset = NativeHeaderMetrics.ClusterContentInsetPt
        val chromePlane = NativeHeaderMetrics.CompactChromeCenterYPt
        val titleTop =
            titleColumn.topAnchor.constraintEqualToAnchor(
                chromeRow.topAnchor,
                constant = NativeHeaderMetrics.titleColumnTopInsetPt(0f),
            )
        titleColumnTopConstraint = titleTop
        val titleCenter =
            titleColumn.centerYAnchor.constraintEqualToAnchor(
                chromeRow.topAnchor,
                constant = NativeHeaderMetrics.CompactChromeCenterYPt,
            )
        titleCenter.active = false
        titleColumnCenterYConstraint = titleCenter
        titleColumn.setContentHuggingPriority(1000f, forAxis = UILayoutConstraintAxisVertical)
        titleColumn.setContentCompressionResistancePriority(1000f, forAxis = UILayoutConstraintAxisVertical)
        NSLayoutConstraint.activateConstraints(
            listOf(
                chromeRow.topAnchor.constraintEqualToAnchor(bar.topAnchor),
                chromeRow.leadingAnchor.constraintEqualToAnchor(bar.leadingAnchor),
                chromeRow.trailingAnchor.constraintEqualToAnchor(bar.trailingAnchor),
                chromeRow.bottomAnchor.constraintEqualToAnchor(bar.bottomAnchor),
                backButton.leadingAnchor.constraintEqualToAnchor(
                    chromeRow.leadingAnchor,
                    constant = NativeHeaderMetrics.LeadingInsetPt - 8.0,
                ),
                backButton.centerYAnchor.constraintEqualToAnchor(chromeRow.topAnchor, constant = chromePlane),
                avatarButton.centerYAnchor.constraintEqualToAnchor(chromeRow.topAnchor, constant = chromePlane),
                avatarInitialsLabel.leadingAnchor.constraintEqualToAnchor(avatarButton.leadingAnchor, constant = 3.0),
                avatarInitialsLabel.trailingAnchor.constraintEqualToAnchor(avatarButton.trailingAnchor, constant = -3.0),
                avatarInitialsLabel.centerYAnchor.constraintEqualToAnchor(avatarButton.centerYAnchor),
                avatarPhoto.topAnchor.constraintEqualToAnchor(avatarButton.topAnchor),
                avatarPhoto.leadingAnchor.constraintEqualToAnchor(avatarButton.leadingAnchor),
                avatarPhoto.trailingAnchor.constraintEqualToAnchor(avatarButton.trailingAnchor),
                avatarPhoto.bottomAnchor.constraintEqualToAnchor(avatarButton.bottomAnchor),
                presenceDot.widthAnchor.constraintEqualToConstant(8.0),
                presenceDot.heightAnchor.constraintEqualToConstant(8.0),
                presenceDot.trailingAnchor.constraintEqualToAnchor(avatarButton.trailingAnchor, constant = 1.0),
                presenceDot.bottomAnchor.constraintEqualToAnchor(avatarButton.bottomAnchor, constant = 1.0),
                trailingCluster.trailingAnchor.constraintEqualToAnchor(
                    chromeRow.trailingAnchor,
                    constant = -NativeHeaderMetrics.TrailingInsetPt,
                ),
                trailingCluster.centerYAnchor.constraintEqualToAnchor(chromeRow.topAnchor, constant = chromePlane),
                trailingCluster.heightAnchor.constraintEqualToConstant(NativeHeaderMetrics.ChromeButtonSizePt),
                trailingStack.leadingAnchor.constraintEqualToAnchor(trailingCluster.contentView.leadingAnchor, constant = inset),
                trailingStack.trailingAnchor.constraintEqualToAnchor(trailingCluster.contentView.trailingAnchor, constant = -inset),
                trailingStack.topAnchor.constraintEqualToAnchor(trailingCluster.contentView.topAnchor, constant = inset),
                trailingStack.bottomAnchor.constraintEqualToAnchor(trailingCluster.contentView.bottomAnchor, constant = -inset),
                leadingBar,
                trailingClusterPin,
                titleTop,
                titleColumn.bottomAnchor.constraintLessThanOrEqualToAnchor(
                    chromeRow.bottomAnchor,
                    constant = -NativeHeaderMetrics.CompactRowBottomPaddingPt,
                ),
            ),
        )
        avatarButton.layer.cornerRadius = avatarSize / 2.0
        avatarPhoto.layer.cornerRadius = avatarSize / 2.0
        presenceDot.layer.cornerRadius = 4.0
        presenceDot.layer.borderWidth = 1.5
        presenceDot.layer.borderColor = UIColor.whiteColor.CGColor
        rowInstalled = true
    }

    private fun applyVisibility() {
        val show = wantVisible && coverCount == 0
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
        bar.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
        glassPlate.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
        chromeRow.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
        glassPlate.removeFromSuperview()
        chromeRow.removeFromSuperview()
        bar.removeFromSuperview()
        attachedHost = null
    }

    private fun chromeButtons(): List<UIButton> = listOf(backButton, searchButton) + actionButtons

    private fun paintChromeButton(
        button: UIButton,
        symbol: String,
        accessibility: String,
        clustered: Boolean,
    ) {
        val symbolConfig =
            UIImageSymbolConfiguration.configurationWithPointSize(
                NativeHeaderMetrics.ChromeIconPointSize,
                weight = UIImageSymbolWeightMedium,
            )
        val image =
            UIImage.systemImageNamed(symbol, withConfiguration = symbolConfig)
                ?: UIImage.systemImageNamed(symbol)
        val config =
            if (usesGlassButtons && clustered) {
                UIButtonConfiguration.plainButtonConfiguration().apply {
                    baseForegroundColor = if (lastIsDark) UIColor.whiteColor else UIColor.blackColor
                }
            } else if (usesGlassButtons) {
                UIButtonConfiguration.glassButtonConfiguration().apply {
                    cornerStyle = UIButtonConfigurationCornerStyleCapsule
                }
            } else {
                UIButtonConfiguration.plainButtonConfiguration()
            }
        config.image = image
        config.preferredSymbolConfigurationForImage = symbolConfig
        config.contentInsets = NSDirectionalEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
        button.configuration = config
        button.setImage(image, forState = UIControlStateNormal)
        button.tintColor = if (lastIsDark) UIColor.whiteColor else UIColor.blackColor
        button.setAccessibilityLabel(accessibility)
    }

    private fun applyClusterChrome() {
        val key =
            if (usesGlassButtons) {
                "glass-interactive-capsule"
            } else if (lastIsDark) {
                "blur-dark"
            } else {
                "blur-light"
            }
        if (key == lastClusterChromeKey) return
        lastClusterChromeKey = key
        trailingCluster.clipsToBounds = false
        trailingCluster.layer.masksToBounds = false
        trailingCluster.layer.shadowOpacity = 0f
        trailingCluster.layer.borderWidth = 0.0
        trailingCluster.backgroundColor = UIColor.clearColor
        if (usesGlassButtons) {
            val glass = UIGlassEffect.effectWithStyle(UIGlassEffectStyle.UIGlassEffectStyleRegular)
            glass.setInteractive(true)
            trailingCluster.effect = glass
            trailingCluster.cornerConfiguration = UICornerConfiguration.capsuleConfiguration()
        } else {
            trailingCluster.layer.cornerRadius = NativeHeaderMetrics.ChromeButtonSizePt / 2.0
            val style =
                if (lastIsDark) {
                    UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialDark
                } else {
                    UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialLight
                }
            trailingCluster.effect = UIBlurEffect.effectWithStyle(style)
        }
    }

    private fun applyLeadingRevealMask(widthPt: Double?) {
        if (widthPt == null) {
            leadingRevealWidthPt = -1.0
            bar.layer.mask = null
            chromeRow.layer.mask = null
            if (!glassPlate.hidden) {
                updateGlassFadeMask()
            }
            return
        }
        leadingRevealWidthPt = widthPt.coerceAtLeast(0.0)
        barLeadingMask = leadingRectMask(bar, barLeadingMask, leadingRevealWidthPt)
        chromeLeadingMask = leadingRectMask(chromeRow, chromeLeadingMask, leadingRevealWidthPt)
        if (!glassPlate.hidden) {
            updateGlassFadeMask()
        }
    }

    private fun leadingRectMask(
        view: UIView,
        existing: CALayer?,
        widthPt: Double,
    ): CALayer {
        val height = view.bounds.useContents { size.height }.coerceAtLeast(1.0)
        val mask =
            existing ?: CALayer().apply {
                backgroundColor = UIColor.blackColor.CGColor
            }
        mask.frame = CGRectMake(0.0, 0.0, widthPt, height)
        view.layer.mask = mask
        return mask
    }

    private fun updateGlassFadeMask() {
        val bounds = glassPlate.bounds
        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        if (width <= 1.0 || height <= 1.0) return
        val mask =
            glassFadeMask ?: CAGradientLayer().also { layer ->
                layer.startPoint = CGPointMake(0.5, 0.0)
                layer.endPoint = CGPointMake(0.5, 1.0)
                glassFadeMask = layer
            }
        val clipping = leadingRevealWidthPt >= 0.0
        val clipWidth =
            if (clipping) {
                leadingRevealWidthPt.coerceAtMost(width)
            } else {
                width
            }
        mask.frame = CGRectMake(0.0, 0.0, clipWidth, height)
        val opaqueUntil = ((height - NativeHeaderMetrics.GlassFadeExtensionPt) / height).coerceIn(0.55, 0.92)
        mask.colors =
            listOf(
                UIColor.blackColor.CGColor,
                UIColor.blackColor.CGColor,
                UIColor.clearColor.CGColor,
            )
        mask.locations = listOf(0.0, opaqueUntil, 1.0)
        if (clipping) {
            val host =
                glassClipHost ?: CALayer().also { layer ->
                    glassClipHost = layer
                }
            host.frame = CGRectMake(0.0, 0.0, width, height)
            if (mask.superlayer != host) {
                mask.removeFromSuperlayer()
                host.addSublayer(mask)
            }
            glassPlate.layer.mask = host
        } else {
            if (mask.superlayer != null && mask.superlayer !== glassPlate.layer) {
                mask.removeFromSuperlayer()
            }
            glassPlate.layer.mask = mask
        }
    }
}

private object IosNavChrome {
    val tab = IosHostNavBarLayer()
    val overlay = IosHostNavBarLayer()
    val avatarPhotos = mutableMapOf<String, UIImage>()

    fun acquireCover() {
        tab.acquireCover()
        overlay.acquireCover()
    }

    fun releaseCover() {
        tab.releaseCover()
        overlay.releaseCover()
    }

    fun restack() {
        tab.bringChromeToFront()
        overlay.bringChromeToFront()
    }
}

private fun makeChromeButton(): UIButton =
    UIButton.buttonWithType(UIButtonTypeSystem).apply {
        translatesAutoresizingMaskIntoConstraints = false
        widthAnchor.constraintEqualToConstant(NativeHeaderMetrics.ChromeButtonSizePt).active = true
        heightAnchor.constraintEqualToConstant(NativeHeaderMetrics.ChromeButtonSizePt).active = true
    }

@OptIn(ExperimentalForeignApi::class)
private fun cachedAvatarPhoto(url: String): UIImage? {
    IosNavChrome.avatarPhotos[url]?.let { return it }
    val nsUrl = NSURL.URLWithString(url) ?: return null
    val cached =
        NSURLCache.sharedURLCache.cachedResponseForRequest(
            NSURLRequest.requestWithURL(nsUrl),
        )
    val fromUrlCache = cached?.data?.let { UIImage.imageWithData(it) }
    val image = fromUrlCache ?: coilCachedAvatarPhoto(url)
    if (image != null) {
        IosNavChrome.avatarPhotos[url] = image
    }
    return image
}

@OptIn(ExperimentalForeignApi::class)
private fun coilCachedAvatarPhoto(url: String): UIImage? {
    val loader = coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE)
    val snapshot = loader.diskCache?.openSnapshot(url) ?: return null
    return try {
        val bytes = loader.diskCache?.fileSystem?.read(snapshot.data) { readByteArray() } ?: return null
        if (bytes.isEmpty()) return null
        kotlinx.cinterop.memScoped {
            val data =
                bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
            UIImage.imageWithData(data)
        }
    } finally {
        snapshot.close()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosNavBarPositionDelegate :
    NSObject(),
    UINavigationBarDelegateProtocol {
    override fun positionForBar(bar: UIBarPositioningProtocol): UIBarPosition = UIBarPositionTopAttached
}

@OptIn(BetaInteropApi::class)
private class IosBarButtonTarget : NSObject() {
    var handler: (() -> Unit)? = null

    @ObjCAction
    fun didTap() {
        handler?.invoke()
    }
}
