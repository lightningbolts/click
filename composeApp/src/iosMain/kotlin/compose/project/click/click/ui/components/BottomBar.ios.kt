package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIDevice
import platform.UIKit.UIImage
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformBottomBar(
    items: List<NavigationItem>,
    currentRoute: String,
    onItemSelected: (NavigationItem) -> Unit
) {
    val density = LocalDensity.current
    val viewController = LocalUIViewController.current
    val onItemSelectedState by rememberUpdatedState(onItemSelected)
    val currentItems by rememberUpdatedState(items)

    val isLiquidGlass = remember {
        UIDevice.currentDevice.systemVersion.toDoubleOrNull()?.let { it >= 26.0 } ?: false
    }

    val tabBar = remember { UITabBar().apply { translatesAutoresizingMaskIntoConstraints = false } }

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
        NSLayoutConstraint.activateConstraints(
            listOf(
                tabBar.leadingAnchor.constraintEqualToAnchor(viewController.view.leadingAnchor),
                tabBar.trailingAnchor.constraintEqualToAnchor(viewController.view.trailingAnchor),
                tabBar.bottomAnchor.constraintEqualToAnchor(
                    if (isLiquidGlass) viewController.view.bottomAnchor
                    else viewController.view.safeAreaLayoutGuide.bottomAnchor
                ),
            )
        )
        onDispose { tabBar.removeFromSuperview() }
    }

    val safeAreaBottom = remember {
        if (isLiquidGlass) 0.dp
        else viewController.view.safeAreaInsets.useContents { bottom.dp }
    }

    var topLeft by remember { mutableStateOf(DpOffset.Zero) }
    var positionInRoot by remember { mutableStateOf(DpOffset.Zero) }
    var tabBarWidth by remember { mutableStateOf(0.dp) }
    var tabBarHeight by remember { mutableStateOf(safeAreaBottom + 49.dp) }

    LaunchedEffect(Unit) {
        var stable = 0
        while (true) {
            tabBar.frame.useContents {
                topLeft = DpOffset(origin.x.dp, origin.y.dp)
                tabBarWidth = size.width.dp
                val bottom = if (isLiquidGlass) 0.dp
                    else viewController.view.safeAreaInsets.useContents { bottom.dp }
                val h = size.height.dp + bottom
                if (tabBarHeight != h) { tabBarHeight = h; stable = 0 } else stable++
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
            }
            .width(tabBarWidth)
            .height(tabBarHeight)
    )
}
