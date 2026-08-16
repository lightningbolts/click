@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.rememberReduceTransparencyEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.Foundation.setValue
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationBarAppearance
import platform.UIKit.UINavigationItem
import platform.UIKit.UINavigationItemLargeTitleDisplayMode
import platform.UIKit.UIViewController
import platform.UIKit.setAccessibilityLabel
import platform.darwin.NSObject

private val IosCompactBarHeight = 44.dp
private val IosLargeTitleExtraHeight = 52.dp

@OptIn(ExperimentalForeignApi::class)
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
    val scrollOffsetPx by remember(lazyListState) {
        derivedStateOf {
            nativeChromeScrollOffsetPx(
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
            )
        }
    }
    val collapseFraction = (scrollOffsetPx / 52f).coerceIn(0f, 1f)
    val collapsed = collapseFraction > 0.55f
    val navHeight =
        rememberIosHostNavBar(
            title = title,
            subtitle = subtitle,
            presenceOnline = presenceOnline,
            collapsed = collapsed,
            visible = showHeader,
            statusBarTop = statusBarTop,
            onOpenSearch = onOpenSearch,
            onNavigateBack = onNavigateBack,
            nativeTrailingActions = nativeTrailingActions,
        )
    val topPad =
        if (showHeader) {
            navHeight + belowHeaderSpacing
        } else {
            statusBarTop + 16.dp
        }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = verticalArrangement,
            contentPadding =
                PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = topPad,
                    bottom = bottomChrome,
                ),
            content = content,
        )
        if (showHeader) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(navHeight))
                headerBelowContent?.invoke()
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
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
    val collapseFraction = (scrollState.value / 52f).coerceIn(0f, 1f)
    val collapsed = collapseFraction > 0.55f
    val navHeight =
        rememberIosHostNavBar(
            title = title,
            subtitle = subtitle,
            presenceOnline = presenceOnline,
            collapsed = collapsed,
            visible = true,
            statusBarTop = statusBarTop,
            onOpenSearch = onOpenSearch,
            onNavigateBack = onNavigateBack,
            nativeTrailingActions = nativeTrailingActions,
        )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = navHeight,
                        bottom = bottomChrome,
                    ),
        ) {
            content(Modifier.fillMaxWidth())
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
    collapsed: Boolean,
    visible: Boolean,
    statusBarTop: Dp,
    onOpenSearch: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
): Dp {
    val density = LocalDensity.current
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
    val fallbackHeight =
        statusBarTop + IosCompactBarHeight +
            if (collapsed) 0.dp else IosLargeTitleExtraHeight
    var measuredHeight by remember { mutableStateOf(fallbackHeight) }

    DisposableEffect(viewController) {
        IosHostNavigationBar.attach(viewController)
        onDispose { IosHostNavigationBar.release(owner) }
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
            collapsed = collapsed,
            visible = visible,
            statusBarPoints = statusBarTop.value.toDouble(),
            onOpenSearch = searchHandler,
            onNavigateBack = backHandler,
            trailingActions = trailingHandlers,
        )
    }

    LaunchedEffect(viewController, visible, collapsed, statusBarTop) {
        var stable = 0
        while (true) {
            val frameH =
                IosHostNavigationBar.bar.frame.useContents { size.height }
            if (frameH > 0.0) {
                val h = with(density) { frameH.toFloat().toDp() }
                if (measuredHeight != h) {
                    measuredHeight = h
                    stable = 0
                } else {
                    stable++
                }
            }
            if (stable > 6) break
            withFrameMillis { }
        }
    }

    return if (measuredHeight > 0.dp) measuredHeight else fallbackHeight
}

@OptIn(ExperimentalForeignApi::class)
private object IosHostNavigationBar {
    val bar: UINavigationBar =
        UINavigationBar().apply {
            translatesAutoresizingMaskIntoConstraints = false
            setTranslucent(true)
            prefersLargeTitles = true
            backgroundColor = UIColor.clearColor
            clipsToBounds = true
        }
    private val item = UINavigationItem()
    private var heightConstraint: NSLayoutConstraint? = null
    private var attachedHost: UIViewController? = null
    private var ownerToken: Any? = null
    private var appearanceConfigured = false
    private val searchTarget = IosBarButtonTarget()
    private val backTarget = IosBarButtonTarget()
    private val trailingTargets = mutableListOf<IosBarButtonTarget>()

    fun attach(host: UIViewController) {
        if (attachedHost === host && bar.superview == host.view) return
        detachFromSuperview()
        attachedHost = host
        val hostView = host.view
        hostView.addSubview(bar)
        val height = bar.heightAnchor.constraintEqualToConstant(96.0)
        heightConstraint = height
        NSLayoutConstraint.activateConstraints(
            listOf(
                bar.topAnchor.constraintEqualToAnchor(hostView.topAnchor),
                bar.leadingAnchor.constraintEqualToAnchor(hostView.leadingAnchor),
                bar.trailingAnchor.constraintEqualToAnchor(hostView.trailingAnchor),
                height,
            ),
        )
        bar.setItems(listOf(item), animated = false)
        hostView.bringSubviewToFront(bar)
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
        bar.backgroundColor = UIColor.clearColor
        if (usesNativeLiquidGlass && !reduceTransparency) {
            // System Liquid Glass — matching UITabBar. Do not set UINavigationBarAppearance.
            appearanceConfigured = true
            return
        }
        val clear = UIColor.clearColor
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
        appearanceConfigured = true
    }

    fun update(
        owner: Any,
        host: UIViewController,
        title: String,
        subtitle: String?,
        presenceOnline: Boolean?,
        collapsed: Boolean,
        visible: Boolean,
        statusBarPoints: Double,
        onOpenSearch: (() -> Unit)?,
        onNavigateBack: (() -> Unit)?,
        trailingActions: List<NativeChromeAction>,
    ) {
        if (attachedHost !== host) {
            attach(host)
        }
        ownerToken = owner
        item.title = title
        val subtitleText =
            buildString {
                if (!subtitle.isNullOrBlank()) append(subtitle)
                if (presenceOnline == true) {
                    if (isNotEmpty()) append(" · ")
                    append("Online")
                }
            }.ifBlank { null }
        runCatching { item.setValue(subtitleText, forKey = "subtitle") }
        item.largeTitleDisplayMode =
            if (collapsed) {
                UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeNever
            } else {
                UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways
            }
        bar.prefersLargeTitles = true
        val extra = if (collapsed) 0.0 else 52.0
        heightConstraint?.constant = (statusBarPoints + 44.0 + extra).coerceAtLeast(44.0)
        bindButtons(onOpenSearch, onNavigateBack, trailingActions)
        setVisible(visible)
        if (visible) {
            host.view.bringSubviewToFront(bar)
        }
    }

    private fun bindButtons(
        onOpenSearch: (() -> Unit)?,
        onNavigateBack: (() -> Unit)?,
        trailingActions: List<NativeChromeAction>,
    ) {
        backTarget.handler = onNavigateBack
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
        if (onOpenSearch != null) {
            searchTarget.handler = onOpenSearch
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
    }

    private fun setVisible(visible: Boolean) {
        bar.hidden = !visible
        bar.userInteractionEnabled = visible
        val hostView = attachedHost?.view ?: return
        if (visible) {
            hostView.bringSubviewToFront(bar)
        } else {
            hostView.sendSubviewToBack(bar)
        }
    }

    private fun detachFromSuperview() {
        bar.removeFromSuperview()
        heightConstraint = null
        attachedHost = null
    }
}

@OptIn(BetaInteropApi::class)
private class IosBarButtonTarget : NSObject() {
    var handler: (() -> Unit)? = null

    @ObjCAction
    fun didTap() {
        handler?.invoke()
    }
}
