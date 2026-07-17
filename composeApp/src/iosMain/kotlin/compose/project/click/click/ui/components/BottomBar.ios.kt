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
import compose.project.click.click.navigation.NavigationItem
import compose.project.click.click.ui.theme.LocalIsDarkMode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIImage
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
    val isDarkMode = LocalIsDarkMode.current

    val tabBar = remember {
        UITabBar().apply {
            translatesAutoresizingMaskIntoConstraints = false
            setTranslucent(true)
        }
    }

    // Fully clear chrome so Compose materials under the icons match materials above —
    // no fill, blur, or hairline that would create a different "material" band.
    SideEffect {
        val clear = UIColor.clearColor
        val selectedColor = if (isDarkMode) {
            // NeonPurple #D2BBFF — readable over dark page content without a bar fill
            UIColor.colorWithRed(0xD2 / 255.0, green = 0xBB / 255.0, blue = 0xFF / 255.0, alpha = 1.0)
        } else {
            UIColor.colorWithRed(0x63 / 255.0, green = 0x0E / 255.0, blue = 0xD4 / 255.0, alpha = 1.0)
        }
        val unselectedColor = if (isDarkMode) {
            UIColor.colorWithRed(0xF0 / 255.0, green = 0xF1 / 255.0, blue = 0xF1 / 255.0, alpha = 1.0)
        } else {
            UIColor.colorWithRed(0x4A / 255.0, green = 0x44 / 255.0, blue = 0x55 / 255.0, alpha = 1.0)
        }

        // Match page BackgroundDark so any uncovered gap is not pure black.
        viewController.view.backgroundColor = if (isDarkMode) {
            UIColor.colorWithRed(0x10 / 255.0, green = 0x12 / 255.0, blue = 0x12 / 255.0, alpha = 1.0)
        } else {
            UIColor.colorWithRed(0xF9 / 255.0, green = 0xF9 / 255.0, blue = 0xF9 / 255.0, alpha = 1.0)
        }

        tabBar.barTintColor = clear
        tabBar.backgroundColor = clear
        tabBar.tintColor = selectedColor
        tabBar.unselectedItemTintColor = unselectedColor
        tabBar.backgroundImage = UIImage()
        tabBar.shadowImage = UIImage()
        tabBar.setTranslucent(true)

        val appearance = UITabBarAppearance().apply {
            configureWithTransparentBackground()
            backgroundColor = clear
            backgroundEffect = null
            shadowColor = clear
        }
        tabBar.standardAppearance = appearance
        tabBar.scrollEdgeAppearance = appearance
    }

    val delegate = remember {
        object : NSObject(), UITabBarDelegateProtocol {
            override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
                tabBar.selectedItem = didSelectItem
                val idx = didSelectItem.tag.toInt()
                currentItems.getOrNull(idx)?.let { onItemSelectedState(it) }
            }
        }
    }

    LaunchedEffect(tabBar) { tabBar.delegate = delegate }

    LaunchedEffect(items, currentRoute) {
        val uiItems = items.mapIndexed { index, navItem ->
            UITabBarItem(
                title = navItem.title,
                image = UIImage.systemImageNamed(navItem.sfSymbol),
                tag = index.toLong()
            )
        }
        tabBar.setItems(uiItems)
        val selectedIdx = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
        uiItems.getOrNull(selectedIdx)?.let { tabBar.selectedItem = it }
    }

    DisposableEffect(tabBar, viewController) {
        viewController.view.addSubview(tabBar)
        // Pin to the absolute bottom so Compose paints continuously under icons + home indicator.
        NSLayoutConstraint.activateConstraints(
            listOf(
                tabBar.leadingAnchor.constraintEqualToAnchor(viewController.view.leadingAnchor),
                tabBar.trailingAnchor.constraintEqualToAnchor(viewController.view.trailingAnchor),
                tabBar.bottomAnchor.constraintEqualToAnchor(viewController.view.bottomAnchor),
            )
        )
        onDispose { tabBar.removeFromSuperview() }
    }

    SideEffect {
        tabBar.hidden = !visible
        tabBar.alpha = if (visible) 1.0 else 0.0
        tabBar.userInteractionEnabled = visible
    }

    LaunchedEffect(visible, tabBar) {
        if (!visible) {
            AppScreenChromeState.updateBottomChromeHeight(
                AppScreenDefaults.ExtraScrollBottomPadding,
            )
        }
    }

    var topLeft by remember { mutableStateOf(DpOffset.Zero) }
    var positionInRoot by remember { mutableStateOf(DpOffset.Zero) }
    var tabBarWidth by remember { mutableStateOf(0.dp) }
    var tabBarHeight by remember { mutableStateOf(AppScreenDefaults.IosTabBarContentHeight) }

    LaunchedEffect(Unit, visible) {
        if (!visible) return@LaunchedEffect
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
                    AppScreenChromeState.updateBottomChromeHeight(clearanceFromTop)
                }
            }
            if (tabBarHeight.value > 0f && stable > 6) break
            withFrameMillis { }
        }
    }

    if (!visible) return

    Box(
        modifier = Modifier
            .onPlaced {
                val p = it.positionInRoot()
                positionInRoot = with(density) { DpOffset(p.x.toDp(), p.y.toDp()) }
            }
            .graphicsLayer {
                translationX = (topLeft.x - positionInRoot.x).toPx()
                translationY = (topLeft.y - positionInRoot.y).toPx()
            }
            .width(tabBarWidth)
            .height(tabBarHeight)
    )
}
