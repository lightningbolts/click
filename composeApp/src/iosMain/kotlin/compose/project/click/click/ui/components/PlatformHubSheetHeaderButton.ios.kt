@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import compose.project.click.click.platform.rememberReduceTransparencyEnabled
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightMedium
import platform.UIKit.setAccessibilityLabel
import platform.darwin.NSObject

/**
 * Event-Hub sheet controls are real host-sibling UIKit buttons, like the app's persistent native
 * navigation/map chrome. The Compose slot only measures their exact position. This keeps the button
 * out of UIKitView interop so iOS 26 owns the Liquid Glass interaction/deformation while preserving
 * the sheet header's Compose layout.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun PlatformHubSheetHeaderButton(
    action: HubSheetHeaderAction,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val host = LocalUIViewController.current
    val density = LocalDensity.current.density.toDouble()
    val target = remember { HubSheetHeaderTapTarget() }
    val onClickState by rememberUpdatedState(onClick)
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val usesNativeLiquidGlass =
        remember(reduceTransparency) {
            !reduceTransparency &&
                NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }
    val button = remember(host, action) { makeHubSheetHeaderButton() }
    val symbol =
        when (action) {
            HubSheetHeaderAction.Back -> "chevron.backward"
            HubSheetHeaderAction.More -> "ellipsis"
        }
    var xPt by remember { mutableDoubleStateOf(Double.NaN) }
    var yPt by remember { mutableDoubleStateOf(Double.NaN) }
    var widthPt by remember { mutableDoubleStateOf(0.0) }
    var heightPt by remember { mutableDoubleStateOf(0.0) }

    DisposableEffect(host, button, action) {
        val hostView = host.view
        button.removeFromSuperview()
        button.translatesAutoresizingMaskIntoConstraints = true
        hostView.addSubview(button)
        button.addTarget(
            target,
            action = NSSelectorFromString("didTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
        onDispose {
            button.removeTarget(
                target,
                action = NSSelectorFromString("didTap"),
                forControlEvents = UIControlEventTouchUpInside,
            )
            button.removeFromSuperview()
        }
    }

    SideEffect {
        target.handler = onClickState
        paintHubSheetHeaderButton(
            button = button,
            symbol = symbol,
            contentDescription = contentDescription,
            usesNativeLiquidGlass = usesNativeLiquidGlass,
        )
        if (xPt.isFinite() && yPt.isFinite() && widthPt > 0.0 && heightPt > 0.0) {
            button.frame = CGRectMake(xPt, yPt, widthPt, heightPt)
        }
        host.view.bringSubviewToFront(button)
    }

    Box(
        modifier =
            modifier.onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                xPt = position.x.toDouble() / density
                yPt = position.y.toDouble() / density
                widthPt = coordinates.size.width.toDouble() / density
                heightPt = coordinates.size.height.toDouble() / density
            },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun makeHubSheetHeaderButton(): UIButton =
    UIButton.buttonWithType(UIButtonTypeSystem).apply {
        translatesAutoresizingMaskIntoConstraints = true
        setOpaque(false)
        backgroundColor = UIColor.clearColor
        layer.backgroundColor = UIColor.clearColor.CGColor
        layer.shadowOpacity = 0f
        layer.borderWidth = 0.0
    }

@OptIn(ExperimentalForeignApi::class)
private fun paintHubSheetHeaderButton(
    button: UIButton,
    symbol: String,
    contentDescription: String,
    usesNativeLiquidGlass: Boolean,
) {
    val symbolConfig =
        UIImageSymbolConfiguration.configurationWithPointSize(
            NativeHeaderMetrics.ChromeIconPointSize,
            weight = UIImageSymbolWeightMedium,
        )
    val image =
        (UIImage.systemImageNamed(symbol, withConfiguration = symbolConfig)
            ?: UIImage.systemImageNamed(symbol))
            ?.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate)
    val config =
        if (usesNativeLiquidGlass) {
            UIButtonConfiguration.glassButtonConfiguration()
        } else {
            UIButtonConfiguration.plainButtonConfiguration()
        }
    config.image = image
    config.baseForegroundColor = UIColor.whiteColor
    config.preferredSymbolConfigurationForImage = symbolConfig
    config.contentInsets = NSDirectionalEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
    button.configuration = config
    button.tintColor = UIColor.whiteColor
    button.backgroundColor =
        if (usesNativeLiquidGlass) {
            UIColor.clearColor
        } else {
            UIColor.colorWithWhite(0.18, alpha = 0.92)
        }
    button.layer.backgroundColor = button.backgroundColor?.CGColor
    button.layer.cornerRadius = if (usesNativeLiquidGlass) 0.0 else 22.0
    button.layer.shadowOpacity = 0f
    button.layer.borderWidth = 0.0
    button.setAccessibilityLabel(contentDescription)
}

@OptIn(BetaInteropApi::class)
private class HubSheetHeaderTapTarget : NSObject() {
    var handler: (() -> Unit)? = null

    @ObjCAction
    fun didTap() {
        handler?.invoke()
    }
}
