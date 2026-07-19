package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.navigation.NavigationItem
import compose.project.click.click.platform.rememberReduceTransparencyEnabled
import compose.project.click.click.ui.theme.LocalIsDarkMode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarAppearance
import platform.UIKit.UITabBarDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformBottomBar(
    items: List<NavigationItem>,
    currentRoute: String,
    onItemSelected: (NavigationItem) -> Unit,
    visible: Boolean,
) {
    val density = LocalDensity.current
    val viewController = LocalUIViewController.current
    val onItemSelectedState by rememberUpdatedState(onItemSelected)
    val currentItems by rememberUpdatedState(items)
    val currentRouteState by rememberUpdatedState(currentRoute)
    val isDarkMode = LocalIsDarkMode.current
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val usesNativeLiquidGlass = remember {
        NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
    }

    val tabBar = remember {
        UITabBar().apply {
            translatesAutoresizingMaskIntoConstraints = false
            setTranslucent(true)
        }
    }

    // Appearance is theme-only. Re-applying UITabBarAppearance on every chat-close recomposition
    // remounts Liquid Glass and looks like the whole nav bar restarted.
    LaunchedEffect(isDarkMode, reduceTransparency, usesNativeLiquidGlass, viewController) {
        val clear = UIColor.clearColor
        val selectedColor = if (isDarkMode) {
            UIColor.colorWithRed(0xD2 / 255.0, green = 0xBB / 255.0, blue = 0xFF / 255.0, alpha = 1.0)
        } else {
            UIColor.colorWithRed(0x63 / 255.0, green = 0x0E / 255.0, blue = 0xD4 / 255.0, alpha = 1.0)
        }
        val unselectedColor = if (isDarkMode) {
            UIColor.colorWithRed(0xF0 / 255.0, green = 0xF1 / 255.0, blue = 0xF1 / 255.0, alpha = 1.0)
        } else {
            UIColor.colorWithRed(0x4A / 255.0, green = 0x44 / 255.0, blue = 0x55 / 255.0, alpha = 1.0)
        }
        val clickTint = if (isDarkMode) {
            UIColor.colorWithRed(0x63 / 255.0, green = 0x0E / 255.0, blue = 0xD4 / 255.0, alpha = 0.22)
        } else {
            UIColor.colorWithRed(0x63 / 255.0, green = 0x0E / 255.0, blue = 0xD4 / 255.0, alpha = 0.14)
        }
        val accessibleMaterial = if (isDarkMode) {
            UIColor.colorWithRed(0x10 / 255.0, green = 0x12 / 255.0, blue = 0x12 / 255.0, alpha = 0.96)
        } else {
            UIColor.colorWithRed(0xF9 / 255.0, green = 0xF9 / 255.0, blue = 0xF9 / 255.0, alpha = 0.96)
        }

        viewController.view.backgroundColor = if (isDarkMode) {
            UIColor.colorWithRed(0x10 / 255.0, green = 0x12 / 255.0, blue = 0x12 / 255.0, alpha = 1.0)
        } else {
            UIColor.colorWithRed(0xF9 / 255.0, green = 0xF9 / 255.0, blue = 0xF9 / 255.0, alpha = 1.0)
        }

        tabBar.tintColor = selectedColor
        tabBar.unselectedItemTintColor = unselectedColor
        tabBar.setTranslucent(true)

        if (!usesNativeLiquidGlass) {
            tabBar.barTintColor = clear
            tabBar.backgroundColor = clear
            tabBar.backgroundImage = UIImage()
            tabBar.shadowImage = UIImage()
            val materialStyle = if (isDarkMode) {
                UIBlurEffectStyle.UIBlurEffectStyleSystemMaterialDark
            } else {
                UIBlurEffectStyle.UIBlurEffectStyleSystemMaterialLight
            }
            val appearance = UITabBarAppearance().apply {
                configureWithTransparentBackground()
                backgroundColor = if (reduceTransparency) accessibleMaterial else clickTint
                backgroundEffect = if (reduceTransparency) null else UIBlurEffect.effectWithStyle(materialStyle)
                shadowColor = clear
            }
            tabBar.standardAppearance = appearance
            tabBar.scrollEdgeAppearance = appearance
        }
    }

    val delegate = remember {
        object : NSObject(), UITabBarDelegateProtocol {
            override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
                tabBar.selectedItem = didSelectItem
                val idx = didSelectItem.tag.toInt()
                currentItems.getOrNull(idx)?.let { item ->
                    if (item.route != currentRouteState) {
                        if (item.route == NavigationItem.AddClick.route) {
                            PlatformHapticsPolicy.heavyImpact()
                        } else {
                            PlatformHapticsPolicy.lightImpact()
                        }
                    }
                    onItemSelectedState(item)
                }
            }
        }
    }

    LaunchedEffect(tabBar) { tabBar.delegate = delegate }

    // Item identity only — route selection is a SideEffect below (no setItems flash).
    val itemSignature = remember(items) {
        items.joinToString("|") { "${it.route}:${it.title}:${it.sfSymbol}" }
    }
    LaunchedEffect(itemSignature, isDarkMode) {
        val selectedColor = if (isDarkMode) {
            UIColor.colorWithRed(0xD2 / 255.0, green = 0xBB / 255.0, blue = 0xFF / 255.0, alpha = 1.0)
        } else {
            UIColor.colorWithRed(0x63 / 255.0, green = 0x0E / 255.0, blue = 0xD4 / 255.0, alpha = 1.0)
        }
        val uiItems = items.mapIndexed { index, navItem ->
            val symbol = UIImage.systemImageNamed(navItem.sfSymbol)
            val image = if (navItem.route == NavigationItem.AddClick.route) {
                symbol?.imageWithTintColor(
                    selectedColor,
                    renderingMode = platform.UIKit.UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal,
                ) ?: symbol
            } else {
                symbol
            }
            UITabBarItem(
                title = navItem.title,
                image = image,
                tag = index.toLong(),
            )
        }
        tabBar.setItems(uiItems)
        val selectedIdx = items.indexOfFirst { it.route == currentRouteState }.coerceAtLeast(0)
        uiItems.getOrNull(selectedIdx)?.let { tabBar.selectedItem = it }
    }

    SideEffect {
        val selectedIdx = currentItems.indexOfFirst { it.route == currentRouteState }.coerceAtLeast(0)
        val nativeItems = tabBar.items
        if (nativeItems != null && selectedIdx < nativeItems.size.toInt()) {
            val item = nativeItems[selectedIdx] as? UITabBarItem
            if (item != null && tabBar.selectedItem !== item) {
                tabBar.selectedItem = item
            }
        }
        // Alpha only — never hidden=, never appearance reset on chat open/close.
        tabBar.hidden = false
        tabBar.alpha = if (visible) 1.0 else 0.0
        tabBar.userInteractionEnabled = visible
    }

    DisposableEffect(tabBar, viewController) {
        viewController.view.addSubview(tabBar)
        NSLayoutConstraint.activateConstraints(
            listOf(
                tabBar.leadingAnchor.constraintEqualToAnchor(viewController.view.leadingAnchor),
                tabBar.trailingAnchor.constraintEqualToAnchor(viewController.view.trailingAnchor),
                tabBar.bottomAnchor.constraintEqualToAnchor(viewController.view.bottomAnchor),
            ),
        )
        onDispose { tabBar.removeFromSuperview() }
    }

    // Measure once on attach — do not restart when `visible` flips (that recomposed the
    // connections list chrome padding and looked like a nav remount).
    var topLeft by remember { mutableStateOf(DpOffset.Zero) }
    var positionInRoot by remember { mutableStateOf(DpOffset.Zero) }
    var tabBarWidth by remember { mutableStateOf(0.dp) }
    var tabBarHeight by remember { mutableStateOf(AppScreenDefaults.IosTabBarContentHeight) }
    var lastClearance by remember { mutableStateOf(AppScreenDefaults.IosTabBarContentHeight) }

    LaunchedEffect(tabBar) {
        AppScreenChromeState.updateBottomChromeHeight(lastClearance)
        var stable = 0
        while (true) {
            val viewHeightPx = viewController.view.bounds.useContents { size.height }
            tabBar.frame.useContents {
                topLeft = DpOffset(origin.x.dp, origin.y.dp)
                tabBarWidth = size.width.dp
                val h = size.height.dp
                if (tabBarHeight != h) {
                    tabBarHeight = h
                    stable = 0
                } else {
                    stable++
                }
                val clearanceFromTopPx = viewHeightPx - origin.y
                if (clearanceFromTopPx > 0.0) {
                    val clearanceFromTop = with(density) {
                        clearanceFromTopPx.toFloat().toDp()
                    }
                    lastClearance = clearanceFromTop
                    AppScreenChromeState.updateBottomChromeHeight(clearanceFromTop)
                }
            }
            if (tabBarHeight.value > 0f && stable > 6) break
            withFrameMillis { }
        }
    }

    Box(
        modifier = Modifier
            .onPlaced {
                val p = it.positionInRoot()
                positionInRoot = with(density) { DpOffset(p.x.toDp(), p.y.toDp()) }
            }
            .graphicsLayer {
                translationX = (topLeft.x - positionInRoot.x).toPx()
                translationY = (topLeft.y - positionInRoot.y).toPx()
                alpha = if (visible) 1f else 0f
            }
            .width(tabBarWidth)
            .height(tabBarHeight)
    )
}
