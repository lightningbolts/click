@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import compose.project.click.click.platform.rememberReduceTransparencyEnabled
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.NSLayoutConstraint
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
 * Event-Hub sheet controls must be real host-sibling UIKit buttons, just like the app's map and
 * navigation chrome. Keeping a glass button inside `UIKitView` leaves it inside Compose interop and
 * prevents iOS 26 from giving it the same Liquid Glass interaction/deformation as native chrome.
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

    DisposableEffect(host, button, action) {
        val hostView = host.view
        button.removeFromSuperview()
        button.translatesAutoresizingMaskIntoConstraints = false
        hostView.addSubview(button)
        val horizontalConstraint =
            when (action) {
                HubSheetHeaderAction.Back ->
                    button.leadingAnchor.constraintEqualToAnchor(hostView.leadingAnchor, constant = 16.0)
                HubSheetHeaderAction.More ->
                    button.trailingAnchor.constraintEqualToAnchor(hostView.trailingAnchor, constant = -16.0)
            }
        NSLayoutConstraint.activateConstraints(
            listOf(
                horizontalConstraint,
                button.topAnchor.constraintEqualToAnchor(hostView.topAnchor, constant = 6.0),
                button.widthAnchor.constraintEqualToConstant(44.0),
                button.heightAnchor.constraintEqualToConstant(44.0),
            ),
        )
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
        host.view.bringSubviewToFront(button)
    }

    // Reserve the exact Compose layout slot while the interactive native control lives as a
    // sibling of the Compose renderer. This avoids a fake/interposed glass surface entirely.
    Box(modifier = modifier)
}

@OptIn(ExperimentalForeignApi::class)
private fun makeHubSheetHeaderButton(): UIButton =
    UIButton.buttonWithType(UIButtonTypeSystem).apply {
        translatesAutoresizingMaskIntoConstraints = false
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
