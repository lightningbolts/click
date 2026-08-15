@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.zIndex
import compose.project.click.click.platform.rememberReduceTransparencyEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UINavigationBarAppearance
import platform.UIKit.UINavigationController
import platform.UIKit.UIScrollView
import platform.UIKit.UIViewController
import platform.UIKit.UIVisualEffectView

private val IosCompactBarHeight = 44.dp
private val IosLargeTitleExtraHeight = 52.dp

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeCollapsingScaffold(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    presenceOnline: Boolean?,
    navigationIcon: @Composable (() -> Unit)?,
    actions: @Composable (RowScope.() -> Unit)?,
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
    val expandedTop = statusBarTop + IosCompactBarHeight + IosLargeTitleExtraHeight
    val collapsedTop = statusBarTop + IosCompactBarHeight
    val scrollOffsetPx by remember(lazyListState) {
        derivedStateOf {
            nativeChromeScrollOffsetPx(
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
            )
        }
    }
    val collapseFraction =
        (scrollOffsetPx / 52f).coerceIn(0f, 1f)
    val topPad =
        if (showHeader) {
            lerp(expandedTop, collapsedTop, collapseFraction) +
                belowHeaderSpacing
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
            IosNativeNavigationChrome(
                title = title,
                subtitle = subtitle,
                scrollOffsetPx = scrollOffsetPx,
                modifier = Modifier.fillMaxSize().zIndex(2f),
            )
            IosStatusBarSystemBlur(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(3f),
            )
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(4f)
                        .fillMaxWidth()
                        .statusBarsPadding(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IosCompactBarHeight)
                            .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    navigationIcon?.invoke()
                    Spacer(Modifier.weight(1f))
                    actions?.invoke(this)
                }
                headerBelowContent?.invoke()
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeCollapsingScrollScaffold(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    presenceOnline: Boolean?,
    navigationIcon: @Composable (() -> Unit)?,
    actions: @Composable (RowScope.() -> Unit)?,
    horizontalPadding: Dp,
    content: @Composable (Modifier) -> Unit,
) {
    val scrollState = rememberScrollState()
    val bottomChrome = rememberBottomChromePadding()
    val statusBarTop = rememberStatusBarTopPadding()
    val expandedTop = statusBarTop + IosCompactBarHeight + IosLargeTitleExtraHeight
    val collapsedTop = statusBarTop + IosCompactBarHeight
    val collapseFraction = (scrollState.value / 52f).coerceIn(0f, 1f)
    val topPad = lerp(expandedTop, collapsedTop, collapseFraction)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = topPad,
                        bottom = bottomChrome,
                    ),
        ) {
            content(Modifier.fillMaxWidth())
        }
        IosNativeNavigationChrome(
            title = title,
            subtitle = subtitle,
            scrollOffsetPx = scrollState.value,
            modifier = Modifier.fillMaxSize().zIndex(2f),
        )
        IosStatusBarSystemBlur(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(3f),
        )
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(4f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(IosCompactBarHeight)
                    .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon?.invoke()
            Spacer(Modifier.weight(1f))
            actions?.invoke(this)
        }
    }
}

/**
 * Real UINavigationBar with large titles. A companion UIScrollView mirrors Compose scroll so
 * UIKit performs large→inline collapse and applies system material (including iOS 26 Liquid Glass).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosNativeNavigationChrome(
    title: String,
    subtitle: String?,
    scrollOffsetPx: Int,
    modifier: Modifier = Modifier,
) {
    val displayTitle =
        if (!subtitle.isNullOrBlank()) {
            title
        } else {
            title
        }
    val chrome = remember { IosLargeTitleChrome(displayTitle) }
    UIKitViewController(
        factory = { chrome.navController },
        modifier = modifier,
        update = {
            chrome.setTitle(displayTitle)
            chrome.applyScroll(scrollOffsetPx.toDouble())
        },
        properties =
            UIKitInteropProperties(
                isInteractive = false,
                isNativeAccessibilityEnabled = false,
            ),
    )
}

@OptIn(ExperimentalForeignApi::class)
private class IosLargeTitleChrome(
    initialTitle: String,
) {
    private val dummyScroll =
        UIScrollView().apply {
            showsVerticalScrollIndicator = false
            showsHorizontalScrollIndicator = false
            userInteractionEnabled = false
            bounces = true
            backgroundColor = UIColor.clearColor
            scrollsToTop = false
            translatesAutoresizingMaskIntoConstraints = false
        }
    private val rootController =
        object : UIViewController(nibName = null, bundle = null) {
            override fun viewDidLoad() {
                super.viewDidLoad()
                view.backgroundColor = UIColor.clearColor
                if (dummyScroll.superview == null) {
                    view.addSubview(dummyScroll)
                    NSLayoutConstraint.activateConstraints(
                        listOf(
                            dummyScroll.topAnchor.constraintEqualToAnchor(view.topAnchor),
                            dummyScroll.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                            dummyScroll.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                            dummyScroll.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
                        ),
                    )
                }
            }
        }.apply {
            title = initialTitle
        }
    val navController =
        UINavigationController(rootViewController = rootController).apply {
            navigationBar.prefersLargeTitles = true
            view.backgroundColor = UIColor.clearColor
            val standard =
                UINavigationBarAppearance().apply {
                    configureWithDefaultBackground()
                }
            val scrollEdge =
                UINavigationBarAppearance().apply {
                    configureWithTransparentBackground()
                }
            navigationBar.standardAppearance = standard
            navigationBar.scrollEdgeAppearance = scrollEdge
            navigationBar.compactAppearance = standard
            navigationBar.setTranslucent(true)
        }

    fun setTitle(title: String) {
        rootController.title = title
        navController.navigationBar.prefersLargeTitles = true
    }

    fun applyScroll(offsetY: Double) {
        val bounds = dummyScroll.bounds
        val width = bounds.useContents { size.width }.coerceAtLeast(1.0)
        val height = bounds.useContents { size.height }.coerceAtLeast(1.0)
        dummyScroll.setContentSize(CGSizeMake(width, height + 4_000.0))
        dummyScroll.setContentOffset(CGPointMake(0.0, offsetY.coerceAtLeast(0.0)), animated = false)
    }
}

/**
 * System material over the clock / Dynamic Island band so content scrolling under an expanded
 * large title stays legible. Never an opaque Compose Box on iOS 26.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosStatusBarSystemBlur(modifier: Modifier = Modifier) {
    val isDarkMode = LocalIsDarkMode.current
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (statusBarTop <= 0.dp) return
    val blurStyle =
        if (isDarkMode) {
            UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialDark
        } else {
            UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialLight
        }
    if (reduceTransparency) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(statusBarTop)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        )
        return
    }
    UIKitView(
        factory = {
            UIVisualEffectView(effect = UIBlurEffect.effectWithStyle(blurStyle)).apply {
                backgroundColor = UIColor.clearColor
            }
        },
        modifier =
            modifier
                .fillMaxWidth()
                .height(statusBarTop),
        update = { view ->
            val effectView = view as? UIVisualEffectView ?: return@UIKitView
            effectView.effect = UIBlurEffect.effectWithStyle(blurStyle)
            effectView.backgroundColor = UIColor.clearColor
        },
        properties =
            UIKitInteropProperties(
                isInteractive = false,
                isNativeAccessibilityEnabled = false,
            ),
    )
}
