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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.platform.rememberReduceTransparencyEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentLeft
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIBarPosition
import platform.UIKit.UIBarPositionTopAttached
import platform.UIKit.UIBarPositioningProtocol
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationBarAppearance
import platform.UIKit.UINavigationBarDelegateProtocol
import platform.UIKit.UINavigationItem
import platform.UIKit.UIViewController
import platform.UIKit.UIVisualEffectView
import platform.UIKit.setAccessibilityLabel
import platform.darwin.NSObject

private val IosCompactBarHeight = 44.dp
private val IosSubtitleLineHeight = 18.dp
private const val IosSubtitleMaxLines = 2
private const val IosTitleExpandedPt = 20.0
private const val IosTitleCollapsedPt = 17.0
private const val IosBarButtonReservePt = 48.0

private fun iosTitlePointSize(collapseFraction: Float): Double {
    val fraction = collapseFraction.coerceIn(0f, 1f).toDouble()
    return IosTitleExpandedPt - (IosTitleExpandedPt - IosTitleCollapsedPt) * fraction
}

private fun iosNavBarExtraHeight(hasSubtitle: Boolean): Dp = if (hasSubtitle) IosSubtitleLineHeight * IosSubtitleMaxLines else 0.dp

private fun iosSubtitleOverlayHeight(
    hasSubtitle: Boolean,
    collapseFraction: Float,
): Dp {
    if (!hasSubtitle) return 0.dp
    return IosSubtitleLineHeight * IosSubtitleMaxLines * (1f - collapseFraction.coerceIn(0f, 1f))
}

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
    val extraHeight = iosNavBarExtraHeight(hasSubtitle)
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
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
        collapseSearchIntoBar = collapseSearchIntoBar,
    )

    val subtitleHeight =
        if (showHeader) {
            iosSubtitleOverlayHeight(hasSubtitle, collapseFraction)
        } else {
            0.dp
        }
    val headerClearance =
        if (showHeader) {
            statusBarTop + IosCompactBarHeight + subtitleHeight
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
                    Spacer(Modifier.fillMaxWidth().height(statusBarTop))
                    Box(Modifier.fillMaxWidth().height(IosCompactBarHeight)) {
                        IosNativeChromeFallbackTitle(
                            title = title,
                            collapseFraction = collapseFraction,
                        )
                    }
                    if (subtitleHeight > 0.5.dp) {
                        Box(Modifier.fillMaxWidth().height(subtitleHeight)) {
                            IosNativeChromeFallbackSubtitle(subtitle = subtitle, presenceOnline = presenceOnline)
                        }
                    }
                    headerBelowContent?.invoke()
                }
            } else if (subtitleHeight > 0.5.dp || headerBelowContent != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = statusBarTop + IosCompactBarHeight),
                ) {
                    if (subtitleHeight > 0.5.dp) {
                        Box(Modifier.fillMaxWidth().height(subtitleHeight)) {
                            IosNativeChromeFallbackSubtitle(subtitle = subtitle, presenceOnline = presenceOnline)
                        }
                    }
                    headerBelowContent?.invoke()
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
    val extraHeight = iosNavBarExtraHeight(hasSubtitle)
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
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
        collapseSearchIntoBar = false,
    )

    val subtitleHeight = iosSubtitleOverlayHeight(hasSubtitle, collapseFraction)
    val headerClearance = statusBarTop + IosCompactBarHeight + subtitleHeight

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
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.fillMaxWidth().height(statusBarTop))
                Box(Modifier.fillMaxWidth().height(IosCompactBarHeight)) {
                    IosNativeChromeFallbackTitle(
                        title = title,
                        collapseFraction = collapseFraction,
                    )
                }
                if (subtitleHeight > 0.5.dp) {
                    Box(Modifier.fillMaxWidth().height(subtitleHeight)) {
                        IosNativeChromeFallbackSubtitle(subtitle = subtitle, presenceOnline = presenceOnline)
                    }
                }
            }
        } else if (subtitleHeight > 0.5.dp) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarTop + IosCompactBarHeight),
            ) {
                Box(Modifier.fillMaxWidth().height(subtitleHeight)) {
                    IosNativeChromeFallbackSubtitle(subtitle = subtitle, presenceOnline = presenceOnline)
                }
            }
        }
    }
}

@Composable
private fun IosNativeChromeFallbackTitle(
    title: String,
    collapseFraction: Float,
) {
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val size = iosTitlePointSize(fraction).toFloat().sp
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentAlignment = if (fraction < 0.45f) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IosNativeChromeFallbackSubtitle(
    subtitle: String?,
    presenceOnline: Boolean?,
) {
    val text =
        buildString {
            if (!subtitle.isNullOrBlank()) append(subtitle)
            if (presenceOnline == true) {
                if (isNotEmpty()) append(" · ")
                append("Online")
            }
        }.ifBlank { return }
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
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
    onOpenSearch: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
    collapseSearchIntoBar: Boolean,
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
    val hasSubtitle = !subtitle.isNullOrBlank() || presenceOnline == true

    DisposableEffect(viewController, visible) {
        if (visible) {
            IosHostNavigationBar.attach(viewController)
        } else {
            IosHostNavigationBar.release(owner)
        }
        onDispose { IosHostNavigationBar.release(owner) }
    }

    if (!visible) {
        return
    }

    LaunchedEffect(isDarkMode, reduceTransparency, usesNativeLiquidGlass) {
        IosHostNavigationBar.applyAppearance(
            isDarkMode = isDarkMode,
            reduceTransparency = reduceTransparency,
            usesNativeLiquidGlass = usesNativeLiquidGlass,
        )
    }

    SideEffect {
        IosHostNavigationBar.update(
            owner = owner,
            host = viewController,
            title = title,
            subtitle = subtitle,
            presenceOnline = presenceOnline,
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
        IosHostNavigationBar.acquireCover()
        onDispose { IosHostNavigationBar.releaseCover() }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformNativeNavigationBarSwipeReveal(revealPx: MutableFloatState) {
    val owner = remember { Any() }
    val density = LocalDensity.current.density
    DisposableEffect(owner) {
        onDispose { IosHostNavigationBar.setSlideOffset(owner, 0.0) }
    }
    LaunchedEffect(owner, density) {
        snapshotFlow { revealPx.floatValue }.collect { px ->
            IosHostNavigationBar.setSlideOffset(owner, (px / density).toDouble())
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun BindPlatformNativeNavigationBar(
    title: String,
    subtitle: String?,
    presenceOnline: Boolean?,
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
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
        collapseSearchIntoBar = false,
    )
}

@OptIn(ExperimentalForeignApi::class)
private object IosHostNavigationBar {
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
            clipsToBounds = true
        }
    private val item = UINavigationItem()
    private val titleLabel =
        UILabel().apply {
            translatesAutoresizingMaskIntoConstraints = false
            font = UIFont.boldSystemFontOfSize(IosTitleExpandedPt)
            textAlignment = NSTextAlignmentLeft
            numberOfLines = 1
            adjustsFontSizeToFitWidth = true
            minimumScaleFactor = 0.72
            lineBreakMode = NSLineBreakByTruncatingTail
            userInteractionEnabled = false
        }
    private var heightConstraint: NSLayoutConstraint? = null
    private var titleLeadingConstraint: NSLayoutConstraint? = null
    private var titleTrailingConstraint: NSLayoutConstraint? = null
    private var titleCenterXConstraint: NSLayoutConstraint? = null
    private var attachedHost: UIViewController? = null
    private var ownerToken: Any? = null
    private var coverCount = 0
    private var wantVisible = false
    private var appliedShow: Boolean? = null
    private val slideOffsets = mutableMapOf<Any, Double>()
    private var searchPinnedVisible = false
    private val searchTarget = IosBarButtonTarget()
    private val backTarget = IosBarButtonTarget()
    private val trailingTargets = mutableListOf<IosBarButtonTarget>()
    private var lastButtonSignature: String? = null
    private var glassEnabled = false
    private var positionDelegate: IosNavBarPositionDelegate? = null

    fun acquireCover() {
        coverCount++
        applyVisibility()
    }

    fun releaseCover() {
        coverCount = (coverCount - 1).coerceAtLeast(0)
        applyVisibility()
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
        val transform = CGAffineTransformMakeTranslation(x, 0.0)
        bar.transform = transform
        glassPlate.transform = transform
    }

    fun attach(host: UIViewController) {
        if (attachedHost === host && bar.superview == host.view) return
        detachFromSuperview()
        attachedHost = host
        val hostView = host.view
        hostView.addSubview(glassPlate)
        hostView.addSubview(bar)
        hostView.insertSubview(glassPlate, belowSubview = bar)
        val delegate = positionDelegate ?: IosNavBarPositionDelegate().also { positionDelegate = it }
        bar.delegate = delegate
        val height = bar.heightAnchor.constraintEqualToConstant(44.0)
        heightConstraint = height
        NSLayoutConstraint.activateConstraints(
            listOf(
                bar.topAnchor.constraintEqualToAnchor(hostView.safeAreaLayoutGuide.topAnchor),
                bar.leadingAnchor.constraintEqualToAnchor(hostView.leadingAnchor),
                bar.trailingAnchor.constraintEqualToAnchor(hostView.trailingAnchor),
                height,
                glassPlate.topAnchor.constraintEqualToAnchor(hostView.topAnchor),
                glassPlate.leadingAnchor.constraintEqualToAnchor(hostView.leadingAnchor),
                glassPlate.trailingAnchor.constraintEqualToAnchor(hostView.trailingAnchor),
                glassPlate.bottomAnchor.constraintEqualToAnchor(bar.bottomAnchor),
            ),
        )
        if (titleLabel.superview != bar) {
            bar.addSubview(titleLabel)
            val leading = titleLabel.leadingAnchor.constraintEqualToAnchor(bar.leadingAnchor, constant = 16.0)
            val trailing =
                titleLabel.trailingAnchor.constraintLessThanOrEqualToAnchor(bar.trailingAnchor, constant = -16.0)
            val centerX = titleLabel.centerXAnchor.constraintEqualToAnchor(bar.centerXAnchor)
            centerX.active = false
            titleLeadingConstraint = leading
            titleTrailingConstraint = trailing
            titleCenterXConstraint = centerX
            NSLayoutConstraint.activateConstraints(
                listOf(
                    leading,
                    trailing,
                    titleLabel.centerYAnchor.constraintEqualToAnchor(bar.centerYAnchor),
                ),
            )
        }
        item.titleView = null
        bar.setItems(listOf(item), animated = false)
        bar.bringSubviewToFront(titleLabel)
        lastButtonSignature = null
        appliedShow = null
        hostView.layoutIfNeeded()
        applyVisibility()
    }

    fun release(owner: Any) {
        if (ownerToken === owner) {
            ownerToken = null
            setVisible(false)
        }
    }

    fun applyAppearance(
        isDarkMode: Boolean,
        reduceTransparency: Boolean,
        usesNativeLiquidGlass: Boolean,
    ) {
        bar.setTranslucent(true)
        val titleColor =
            if (isDarkMode) {
                UIColor.whiteColor
            } else {
                UIColor.blackColor
            }
        titleLabel.textColor = titleColor
        bar.tintColor = titleColor
        bar.backgroundColor = UIColor.clearColor
        if (usesNativeLiquidGlass && !reduceTransparency) {
            glassEnabled = true
            glassPlate.effect = UIGlassEffect.effectWithStyle(UIGlassEffectStyle.UIGlassEffectStyleRegular)
            applyVisibility()
            return
        }
        glassEnabled = false
        glassPlate.effect = null
        val clear = UIColor.clearColor
        bar.backgroundColor = clear
        val accessibleMaterial =
            if (isDarkMode) {
                UIColor.colorWithRed(0x10 / 255.0, green = 0x12 / 255.0, blue = 0x12 / 255.0, alpha = 0.96)
            } else {
                UIColor.colorWithRed(0xF9 / 255.0, green = 0xF9 / 255.0, blue = 0xF9 / 255.0, alpha = 0.96)
            }
        val materialStyle =
            if (isDarkMode) {
                UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialDark
            } else {
                UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialLight
            }
        val appearance =
            UINavigationBarAppearance().apply {
                configureWithTransparentBackground()
                backgroundColor = if (reduceTransparency) accessibleMaterial else clear
                backgroundEffect =
                    if (reduceTransparency) {
                        null
                    } else {
                        UIBlurEffect.effectWithStyle(materialStyle)
                    }
                shadowColor = clear
            }
        bar.standardAppearance = appearance
        bar.scrollEdgeAppearance = appearance
        bar.compactAppearance = appearance
    }

    fun update(
        owner: Any,
        host: UIViewController,
        title: String,
        subtitle: String?,
        presenceOnline: Boolean?,
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
        bar.prefersLargeTitles = false
        item.titleView = null
        item.title = null
        val fraction = collapseFraction.coerceIn(0f, 1f)
        heightConstraint?.constant = 44.0
        titleLabel.hidden = false
        titleLabel.text = title
        titleLabel.font = UIFont.boldSystemFontOfSize(iosTitlePointSize(fraction))
        bindButtons(onOpenSearch, onNavigateBack, trailingActions, collapseSearchIntoBar, fraction)
        setVisible(visible)
        @Suppress("UNUSED_VARIABLE")
        val ignoredSubtitle = subtitle

        @Suppress("UNUSED_VARIABLE")
        val ignoredPresence = presenceOnline

        @Suppress("UNUSED_VARIABLE")
        val ignoredHasSubtitle = hasSubtitle
    }

    private fun bindButtons(
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
                }
            }
        backTarget.handler = onNavigateBack
        searchTarget.handler = onOpenSearch
        if (signature == lastButtonSignature) {
            trailingActions.asReversed().forEachIndexed { index, action ->
                trailingTargets.getOrNull(index)?.handler = action.onClick
            }
            applyTitleInsets(
                hasBack = onNavigateBack != null,
                trailingCount = trailingActions.size + if (showSearch) 1 else 0,
                collapseFraction = collapseFraction,
            )
            return
        }
        lastButtonSignature = signature

        item.leftBarButtonItem =
            if (onNavigateBack != null) {
                UIBarButtonItem(
                    image = UIImage.systemImageNamed("chevron.backward"),
                    style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
                    target = backTarget,
                    action = NSSelectorFromString("didTap"),
                ).apply {
                    setAccessibilityLabel("Back")
                }
            } else {
                null
            }

        trailingTargets.clear()
        val trailing = mutableListOf<UIBarButtonItem>()
        if (showSearch) {
            trailing.add(
                UIBarButtonItem(
                    image = UIImage.systemImageNamed("magnifyingglass"),
                    style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
                    target = searchTarget,
                    action = NSSelectorFromString("didTap"),
                ).apply {
                    setAccessibilityLabel("Search")
                },
            )
        }
        trailingActions.asReversed().forEach { action ->
            val target = IosBarButtonTarget().also { it.handler = action.onClick }
            trailingTargets.add(target)
            trailing.add(
                UIBarButtonItem(
                    image = UIImage.systemImageNamed(action.sfSymbol),
                    style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
                    target = target,
                    action = NSSelectorFromString("didTap"),
                ).apply {
                    setAccessibilityLabel(action.contentDescription)
                },
            )
        }
        item.rightBarButtonItems = trailing.ifEmpty { null }
        bar.setItems(listOf(item), animated = false)
        bar.bringSubviewToFront(titleLabel)
        applyTitleInsets(
            hasBack = onNavigateBack != null,
            trailingCount = trailing.size,
            collapseFraction = collapseFraction,
        )
    }

    private fun applyTitleInsets(
        hasBack: Boolean,
        trailingCount: Int,
        collapseFraction: Float,
    ) {
        titleTrailingConstraint?.constant = -16.0 - IosBarButtonReservePt * trailingCount.toDouble()
        // Pushed screens keep a leading title so it cannot sit under the back/refresh capsules.
        val compactTabRoot = !hasBack && collapseFraction >= 0.45f
        if (compactTabRoot) {
            titleLeadingConstraint?.active = false
            titleCenterXConstraint?.active = true
            titleLabel.textAlignment = NSTextAlignmentCenter
        } else {
            titleCenterXConstraint?.active = false
            titleLeadingConstraint?.constant = if (hasBack) 52.0 else 16.0
            titleLeadingConstraint?.active = true
            titleLabel.textAlignment = NSTextAlignmentLeft
        }
    }

    private fun setVisible(visible: Boolean) {
        wantVisible = visible
        applyVisibility()
    }

    private fun applyVisibility() {
        val show = wantVisible && coverCount == 0
        bar.hidden = !show
        bar.userInteractionEnabled = show
        glassPlate.hidden = !show || !glassEnabled
        if (appliedShow == show) {
            if (show) {
                attachedHost?.view?.bringSubviewToFront(bar)
            }
            return
        }
        appliedShow = show
        val hostView = attachedHost?.view ?: return
        if (show) {
            hostView.insertSubview(glassPlate, belowSubview = bar)
            hostView.bringSubviewToFront(bar)
        } else {
            hostView.sendSubviewToBack(bar)
            hostView.sendSubviewToBack(glassPlate)
        }
    }

    private fun detachFromSuperview() {
        titleLabel.removeFromSuperview()
        bar.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
        glassPlate.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
        glassPlate.removeFromSuperview()
        bar.removeFromSuperview()
        heightConstraint = null
        titleLeadingConstraint = null
        titleTrailingConstraint = null
        titleCenterXConstraint = null
        attachedHost = null
        lastButtonSignature = null
        appliedShow = null
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
