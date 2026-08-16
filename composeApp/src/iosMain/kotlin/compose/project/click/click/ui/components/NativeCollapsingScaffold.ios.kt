@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
private val IosSubtitleExtraHeight = 22.dp

private fun iosNavBarExtraHeight(hasSubtitle: Boolean): Dp = IosLargeTitleExtraHeight + if (hasSubtitle) IosSubtitleExtraHeight else 0.dp

private fun iosNavBarBodyHeight(
    hasSubtitle: Boolean,
    collapseFraction: Float,
): Dp = IosCompactBarHeight + iosNavBarExtraHeight(hasSubtitle) * (1f - collapseFraction.coerceIn(0f, 1f))

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
    val collapseFraction = if (showHeader) scrollBehavior.state.collapsedFraction else 0f
    val barHeight = iosNavBarBodyHeight(hasSubtitle, collapseFraction)
    rememberIosHostNavBar(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        collapseFraction = collapseFraction,
        visible = showHeader,
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
    )

    Column(
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
        if (showHeader) {
            Spacer(Modifier.fillMaxWidth().height(statusBarTop))
            Spacer(Modifier.fillMaxWidth().height(barHeight))
            headerBelowContent?.invoke()
        } else {
            Spacer(Modifier.fillMaxWidth().height(statusBarTop + 16.dp))
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = verticalArrangement,
            contentPadding =
                PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = belowHeaderSpacing,
                    bottom = bottomChrome,
                ),
            content = content,
        )
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
    val collapseFraction = scrollBehavior.state.collapsedFraction
    val barHeight = iosNavBarBodyHeight(hasSubtitle, collapseFraction)
    rememberIosHostNavBar(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        collapseFraction = collapseFraction,
        visible = true,
        onOpenSearch = onOpenSearch,
        onNavigateBack = onNavigateBack,
        nativeTrailingActions = nativeTrailingActions,
    )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        Spacer(Modifier.fillMaxWidth().height(statusBarTop))
        Spacer(Modifier.fillMaxWidth().height(barHeight))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
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
    collapseFraction: Float,
    visible: Boolean,
    onOpenSearch: (() -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    nativeTrailingActions: List<NativeChromeAction>,
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
            visible = visible,
            collapseFraction = collapseFraction,
            hasSubtitle = hasSubtitle,
            onOpenSearch = searchHandler,
            onNavigateBack = backHandler,
            trailingActions = trailingHandlers,
        )
    }
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
            insetsLayoutMarginsFromSafeArea = false
        }
    private val item = UINavigationItem()
    private var heightConstraint: NSLayoutConstraint? = null
    private var attachedHost: UIViewController? = null
    private var ownerToken: Any? = null
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
                bar.topAnchor.constraintEqualToAnchor(hostView.safeAreaLayoutGuide.topAnchor),
                bar.leadingAnchor.constraintEqualToAnchor(hostView.leadingAnchor),
                bar.trailingAnchor.constraintEqualToAnchor(hostView.trailingAnchor),
                height,
            ),
        )
        bar.setItems(listOf(item), animated = false)
        hostView.layoutIfNeeded()
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
        val collapsed = collapseFraction > 0.55f
        item.largeTitleDisplayMode =
            if (collapsed) {
                UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeNever
            } else {
                UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways
            }
        bar.prefersLargeTitles = true
        val extra = (52.0 + if (hasSubtitle) 22.0 else 0.0) * (1.0 - collapseFraction.toDouble().coerceIn(0.0, 1.0))
        heightConstraint?.constant = (44.0 + extra).coerceAtLeast(44.0)
        host.view.layoutIfNeeded()
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
