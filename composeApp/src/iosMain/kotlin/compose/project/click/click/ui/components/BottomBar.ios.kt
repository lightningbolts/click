@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.navigation.NavigationItem
import compose.project.click.click.platform.rememberReduceTransparencyEnabled
import compose.project.click.click.ui.theme.LocalIsDarkMode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.setValue
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIBezierPath
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarAppearance
import platform.UIKit.UITabBarDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.drawInRect
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import kotlin.math.max

private const val ME_TAB_AVATAR_SIZE_PT = 26.0

@OptIn(ExperimentalForeignApi::class)
private fun tabAvatarUIImage(image: UIImage): UIImage? {
    val width = image.size.useContents { width }.coerceAtLeast(1.0)
    val height = image.size.useContents { height }.coerceAtLeast(1.0)
    val scale = max(ME_TAB_AVATAR_SIZE_PT / width, ME_TAB_AVATAR_SIZE_PT / height)
    val drawnWidth = width * scale
    val drawnHeight = height * scale
    val originX = (ME_TAB_AVATAR_SIZE_PT - drawnWidth) / 2.0
    val originY = (ME_TAB_AVATAR_SIZE_PT - drawnHeight) / 2.0

    UIGraphicsBeginImageContextWithOptions(
        CGSizeMake(ME_TAB_AVATAR_SIZE_PT, ME_TAB_AVATAR_SIZE_PT),
        false,
        0.0,
    )
    return try {
        UIBezierPath
            .bezierPathWithOvalInRect(
                CGRectMake(0.0, 0.0, ME_TAB_AVATAR_SIZE_PT, ME_TAB_AVATAR_SIZE_PT),
            ).addClip()
        image.drawInRect(CGRectMake(originX, originY, drawnWidth, drawnHeight))
        UIGraphicsGetImageFromCurrentImageContext()
    } finally {
        UIGraphicsEndImageContext()
    }
}

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
    val currentUser by AppDataManager.currentUser.collectAsState()
    val meAvatarUrl = currentUser?.image?.trim()?.takeIf { it.isNotEmpty() }
    var meAvatarImage by remember { mutableStateOf<UIImage?>(null) }
    val usesNativeLiquidGlass =
        remember {
            NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }

    val tabBar =
        remember {
            UITabBar().apply {
                translatesAutoresizingMaskIntoConstraints = false
                setTranslucent(true)
            }
        }

    LaunchedEffect(meAvatarUrl) {
        meAvatarImage = null
        val url = meAvatarUrl ?: return@LaunchedEffect
        IosNavChrome.avatarPhotos[url]?.let {
            meAvatarImage = it
            return@LaunchedEffect
        }
        val nsUrl = NSURL.URLWithString(url) ?: return@LaunchedEffect
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val data: NSData? = NSData.dataWithContentsOfURL(nsUrl)
            val image = data?.let { UIImage.imageWithData(it) }
            dispatch_async(dispatch_get_main_queue()) {
                if (image != null && meAvatarUrl == url) {
                    IosNavChrome.avatarPhotos[url] = image
                    meAvatarImage = image
                }
            }
        }
    }

    // Appearance is theme-only. Re-applying UITabBarAppearance on every chat-close recomposition
    // remounts Liquid Glass and looks like the whole nav bar restarted.
    LaunchedEffect(isDarkMode, reduceTransparency, usesNativeLiquidGlass, viewController) {
        val clear = UIColor.clearColor
        val selectedColor =
            if (isDarkMode) {
                UIColor.colorWithRed(0xD2 / 255.0, green = 0xBB / 255.0, blue = 0xFF / 255.0, alpha = 1.0)
            } else {
                UIColor.colorWithRed(0x63 / 255.0, green = 0x0E / 255.0, blue = 0xD4 / 255.0, alpha = 1.0)
            }
        val unselectedColor =
            if (isDarkMode) {
                UIColor.colorWithRed(0xF0 / 255.0, green = 0xF1 / 255.0, blue = 0xF1 / 255.0, alpha = 1.0)
            } else {
                UIColor.colorWithRed(0x4A / 255.0, green = 0x44 / 255.0, blue = 0x55 / 255.0, alpha = 1.0)
            }
        val clickTint =
            if (isDarkMode) {
                UIColor.colorWithRed(0x63 / 255.0, green = 0x0E / 255.0, blue = 0xD4 / 255.0, alpha = 0.22)
            } else {
                UIColor.colorWithRed(0x63 / 255.0, green = 0x0E / 255.0, blue = 0xD4 / 255.0, alpha = 0.14)
            }
        val accessibleMaterial =
            if (isDarkMode) {
                UIColor.colorWithRed(0x10 / 255.0, green = 0x12 / 255.0, blue = 0x12 / 255.0, alpha = 0.96)
            } else {
                UIColor.colorWithRed(0xF9 / 255.0, green = 0xF9 / 255.0, blue = 0xF9 / 255.0, alpha = 0.96)
            }

        viewController.view.backgroundColor =
            if (isDarkMode) {
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
            val materialStyle =
                if (isDarkMode) {
                    UIBlurEffectStyle.UIBlurEffectStyleSystemMaterialDark
                } else {
                    UIBlurEffectStyle.UIBlurEffectStyleSystemMaterialLight
                }
            val appearance =
                UITabBarAppearance().apply {
                    configureWithTransparentBackground()
                    backgroundColor = if (reduceTransparency) accessibleMaterial else clickTint
                    backgroundEffect = if (reduceTransparency) null else UIBlurEffect.effectWithStyle(materialStyle)
                    shadowColor = clear
                }
            tabBar.standardAppearance = appearance
            tabBar.scrollEdgeAppearance = appearance
        }
    }

    val delegate =
        remember {
            object : NSObject(), UITabBarDelegateProtocol {
                override fun tabBar(
                    tabBar: UITabBar,
                    didSelectItem: UITabBarItem,
                ) {
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
    val itemSignature =
        remember(items) {
            items.joinToString("|") { "${it.route}:${it.title}:${it.sfSymbol}" }
        }
    LaunchedEffect(itemSignature, isDarkMode) {
        val selectedColor =
            if (isDarkMode) {
                UIColor.colorWithRed(0xD2 / 255.0, green = 0xBB / 255.0, blue = 0xFF / 255.0, alpha = 1.0)
            } else {
                UIColor.colorWithRed(0x63 / 255.0, green = 0x0E / 255.0, blue = 0xD4 / 255.0, alpha = 1.0)
            }
        val uiItems =
            items.mapIndexed { index, navItem ->
                val symbol = UIImage.systemImageNamed(navItem.sfSymbol)
                val image =
                    if (navItem.route == NavigationItem.AddClick.route) {
                        symbol?.imageWithTintColor(
                            selectedColor,
                            renderingMode = UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal,
                        ) ?: symbol
                    } else {
                        symbol
                    }
                UITabBarItem(
                    title = navItem.title,
                    image = image,
                    tag = index.toLong(),
                ).apply {
                    setValue(navItem.maestroTestTag, forKey = "accessibilityIdentifier")
                }
            }
        tabBar.setItems(uiItems)
        val selectedIdx = items.indexOfFirst { it.route == currentRouteState }.coerceAtLeast(0)
        uiItems.getOrNull(selectedIdx)?.let { tabBar.selectedItem = it }
    }

    // Profile-photo changes retarget only the existing Me item. Do not call setItems here: keeping
    // the UITabBar and UITabBarItem objects warm prevents Liquid Glass from rematerializing.
    LaunchedEffect(meAvatarImage, meAvatarUrl, itemSignature) {
        val meIndex = items.indexOfFirst { it.route == NavigationItem.Settings.route }
        if (meIndex < 0) return@LaunchedEffect
        val nativeItems = tabBar.items ?: return@LaunchedEffect
        if (meIndex >= nativeItems.size.toInt()) return@LaunchedEffect
        val meItem = nativeItems[meIndex] as? UITabBarItem ?: return@LaunchedEffect
        val photo =
            meAvatarImage
                ?.let(::tabAvatarUIImage)
                ?.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal)
        if (photo != null) {
            meItem.image = photo
            meItem.selectedImage = photo
        } else {
            val fallback = UIImage.systemImageNamed(NavigationItem.Settings.sfSymbol)
            meItem.image = fallback
            meItem.selectedImage = fallback
        }
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
        tabBar.hidden = false
        tabBar.alpha = 1.0
        tabBar.layer.mask = null
        tabBar.userInteractionEnabled = visible
        if (visible) {
            viewController.view.bringSubviewToFront(tabBar)
        } else {
            viewController.view.sendSubviewToBack(tabBar)
        }
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
                    val clearanceFromTop =
                        with(density) {
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
        modifier =
            Modifier
                .onPlaced {
                    val p = it.positionInRoot()
                    positionInRoot = with(density) { DpOffset(p.x.toDp(), p.y.toDp()) }
                }.graphicsLayer {
                    translationX = (topLeft.x - positionInRoot.x).toPx()
                    translationY = (topLeft.y - positionInRoot.y).toPx()
                    alpha = if (visible) 1f else 0f
                }.width(tabBarWidth)
                .height(tabBarHeight),
    )
}
