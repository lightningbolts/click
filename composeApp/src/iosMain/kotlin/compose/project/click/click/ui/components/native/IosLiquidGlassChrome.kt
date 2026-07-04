package compose.project.click.click.ui.components.native

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSThread
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIDevice
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffect
import platform.UIKit.UIVisualEffectView
import platform.UIKit.UIScreen
import platform.UIKit.UIColor
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync

@OptIn(ExperimentalForeignApi::class)
internal val isLiquidGlass: Boolean
    get() = UIDevice.currentDevice.systemVersion.toDoubleOrNull()?.let { it >= 26.0 } ?: false

@OptIn(ExperimentalForeignApi::class)
internal fun runOnMainQueue(block: () -> Unit) {
    if (NSThread.isMainThread) {
        block()
    } else {
        dispatch_async(dispatch_get_main_queue()) {
            block()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun <T> runOnMainQueueSync(block: () -> T): T {
    if (NSThread.isMainThread) {
        return block()
    }
    var result: T? = null
    var error: Throwable? = null
    dispatch_sync(dispatch_get_main_queue()) {
        try {
            result = block()
        } catch (t: Throwable) {
            error = t
        }
    }
    error?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

@OptIn(ExperimentalForeignApi::class)
internal fun applyTransparentChromeHost(view: UIView) {
    view.backgroundColor = UIColor.clearColor
    view.opaque = false
    view.clipsToBounds = true
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun nativeChromeUIKitInteropProperties(
    isInteractive: Boolean,
    isNativeAccessibilityEnabled: Boolean = true,
): UIKitInteropProperties {
    // iOS 26+ glass buttons render their own material; embedding in the Compose tree avoids
    // window-level overlays that ignore z-index (stray header icons over fullscreen map) and
    // square overlay host bounds. Pre-26 blur chrome still samples behind Compose as an overlay.
    val placedAsOverlay = !isLiquidGlass
    return if (isInteractive) {
        UIKitInteropProperties(
            isNativeAccessibilityEnabled = isNativeAccessibilityEnabled,
            placedAsOverlay = placedAsOverlay,
        )
    } else {
        UIKitInteropProperties(
            interactionMode = null,
            isNativeAccessibilityEnabled = isNativeAccessibilityEnabled,
            placedAsOverlay = placedAsOverlay,
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun chromeBlurStyle(): UIBlurEffectStyle {
    return when (UIScreen.mainScreen.traitCollection.userInterfaceStyle) {
        UIUserInterfaceStyle.UIUserInterfaceStyleDark ->
            UIBlurEffectStyle.UIBlurEffectStyleSystemChromeMaterialDark
        else ->
            UIBlurEffectStyle.UIBlurEffectStyleSystemChromeMaterial
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun makeChromeVisualEffect(interactive: Boolean = false): UIVisualEffect {
    if (isLiquidGlass) {
        return runCatching {
            UIGlassEffect().apply {
                setInteractive(interactive)
            }
        }.getOrElse {
            UIBlurEffect.effectWithStyle(chromeBlurStyle())
        }
    }
    return UIBlurEffect.effectWithStyle(chromeBlurStyle())
}

@OptIn(ExperimentalForeignApi::class)
internal fun createLiquidGlassChromeView(
    cornerRadius: Double,
    interactive: Boolean = false,
): UIVisualEffectView =
    UIVisualEffectView(makeChromeVisualEffect(interactive)).apply {
        applyTransparentChromeHost(this)
        layer.cornerRadius = cornerRadius
        autoresizingMask = platform.UIKit.UIViewAutoresizingFlexibleWidth or
            platform.UIKit.UIViewAutoresizingFlexibleHeight
    }

@OptIn(ExperimentalForeignApi::class)
internal fun UIVisualEffectView.applyChromeCornerRadius(cornerRadius: Double) {
    applyTransparentChromeHost(this)
    layer.cornerRadius = cornerRadius
}

@OptIn(ExperimentalForeignApi::class)
internal fun UIVisualEffectView.fillContentViewWith(subview: UIView) {
    applyTransparentChromeHost(subview)
    subview.translatesAutoresizingMaskIntoConstraints = false
    contentView.addSubview(subview)
    platform.UIKit.NSLayoutConstraint.activateConstraints(
        listOf(
            subview.leadingAnchor.constraintEqualToAnchor(contentView.leadingAnchor),
            subview.trailingAnchor.constraintEqualToAnchor(contentView.trailingAnchor),
            subview.topAnchor.constraintEqualToAnchor(contentView.topAnchor),
            subview.bottomAnchor.constraintEqualToAnchor(contentView.bottomAnchor),
        ),
    )
}

@OptIn(ExperimentalForeignApi::class)
internal enum class GlassButtonVariant {
    Regular,
    Prominent,
}

@OptIn(ExperimentalForeignApi::class)
internal fun UIButton.applyGlassConfiguration(variant: GlassButtonVariant) {
    if (!isLiquidGlass) return
    val config = runCatching {
        when (variant) {
            GlassButtonVariant.Regular -> UIButtonConfiguration.glassButtonConfiguration()
            GlassButtonVariant.Prominent -> UIButtonConfiguration.prominentGlassButtonConfiguration()
        }
    }.getOrNull() ?: return
    config.baseBackgroundColor = UIColor.clearColor
    config.background.backgroundColor = UIColor.clearColor
    configuration = config
    backgroundColor = UIColor.clearColor
    opaque = false
    clipsToBounds = true
}

@OptIn(ExperimentalForeignApi::class)
internal fun Color?.toUiColor(): platform.UIKit.UIColor {
    val c = this ?: Color.White
    return UIColor.colorWithRed(
        red = c.red.toDouble(),
        green = c.green.toDouble(),
        blue = c.blue.toDouble(),
        alpha = c.alpha.toDouble(),
    )
}

@OptIn(ExperimentalForeignApi::class)
internal fun UIView.glassIconButton(): UIButton? = when (this) {
    is UIButton -> this
    is UIVisualEffectView -> contentView.subviews.firstOrNull() as? UIButton
    else -> null
}

@OptIn(ExperimentalForeignApi::class)
internal fun createGlassIconButtonHost(
    buttonSizeDp: Double,
    cornerRadius: Double,
    glassVariant: GlassButtonVariant,
    target: platform.darwin.NSObject,
): UIView {
    if (isLiquidGlass) {
        return UIButton.buttonWithType(platform.UIKit.UIButtonTypeSystem).apply {
            applyTransparentChromeHost(this)
            applyGlassConfiguration(glassVariant)
            layer.cornerRadius = cornerRadius
            clipsToBounds = true
            addTarget(
                target = target,
                action = NSSelectorFromString("onTap"),
                forControlEvents = platform.UIKit.UIControlEventTouchUpInside,
            )
        }
    }
    val chrome = createLiquidGlassChromeView(cornerRadius, interactive = true)
    val button = UIButton.buttonWithType(platform.UIKit.UIButtonTypeCustom).apply {
        applyTransparentChromeHost(this)
        addTarget(
            target = target,
            action = NSSelectorFromString("onTap"),
            forControlEvents = platform.UIKit.UIControlEventTouchUpInside,
        )
    }
    chrome.fillContentViewWith(button)
    chrome.setFrame(platform.CoreGraphics.CGRectMake(0.0, 0.0, buttonSizeDp, buttonSizeDp))
    return chrome
}

@OptIn(ExperimentalForeignApi::class)
internal fun updateGlassIconButtonHost(
    root: UIView,
    symbolName: String,
    tintColor: platform.UIKit.UIColor,
    enabled: Boolean,
    cornerRadius: Double,
    glassVariant: GlassButtonVariant,
) {
    val button = root.glassIconButton() ?: return
    val pointSize = (cornerRadius * 2.0 * 0.42).coerceAtLeast(14.0)
    val symbolConfig = platform.UIKit.UIImageSymbolConfiguration.configurationWithPointSize(
        pointSize,
        weight = platform.UIKit.UIImageSymbolWeightMedium,
    )
    val image = platform.UIKit.UIImage.systemImageNamed(symbolName, symbolConfig)
    button.setImage(image, forState = platform.UIKit.UIControlStateNormal)
    button.enabled = enabled
    button.alpha = if (enabled) 1.0 else 0.45
    button.tintColor = tintColor
    button.backgroundColor = UIColor.clearColor
    button.opaque = false
    button.clipsToBounds = true
    if (root is UIButton) {
        button.applyGlassConfiguration(glassVariant)
        button.layer.cornerRadius = cornerRadius
    } else if (root is UIVisualEffectView) {
        root.applyChromeCornerRadius(cornerRadius)
        applyTransparentChromeHost(root)
    }
}
