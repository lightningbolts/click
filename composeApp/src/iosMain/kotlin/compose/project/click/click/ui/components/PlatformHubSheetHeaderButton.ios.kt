@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import compose.project.click.click.platform.rememberReduceTransparencyEnabled
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
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
 * Sheet-local Event Hub controls stay in their Compose layout slots, but the control itself is a
 * native UIButton. On iOS 26 this is the same `glassButtonConfiguration()` primitive used by the
 * persistent navigation chrome, so UIKit owns press/drag deformation instead of a Compose fake.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun PlatformHubSheetHeaderButton(
    action: HubSheetHeaderAction,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val target = remember { HubSheetHeaderTapTarget() }
    val onClickState by rememberUpdatedState(onClick)
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val usesNativeLiquidGlass =
        remember(reduceTransparency) {
            !reduceTransparency &&
                NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }
    val symbol =
        when (action) {
            HubSheetHeaderAction.Back -> "chevron.backward"
            HubSheetHeaderAction.More -> "ellipsis"
        }

    SideEffect {
        target.handler = onClickState
    }

    UIKitView(
        factory = {
            makeHubSheetHeaderButton(target).apply {
                paintHubSheetHeaderButton(
                    button = this,
                    symbol = symbol,
                    contentDescription = contentDescription,
                    usesNativeLiquidGlass = usesNativeLiquidGlass,
                )
            }
        },
        modifier = modifier,
        update = { view ->
            paintHubSheetHeaderButton(
                button = view as UIButton,
                symbol = symbol,
                contentDescription = contentDescription,
                usesNativeLiquidGlass = usesNativeLiquidGlass,
            )
        },
        properties =
            UIKitInteropProperties(
                isInteractive = true,
                isNativeAccessibilityEnabled = true,
            ),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun makeHubSheetHeaderButton(target: HubSheetHeaderTapTarget): UIButton =
    UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setOpaque(false)
        backgroundColor = UIColor.clearColor
        layer.backgroundColor = UIColor.clearColor.CGColor
        layer.shadowOpacity = 0f
        layer.borderWidth = 0.0
        addTarget(
            target,
            action = NSSelectorFromString("didTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
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
        (
            UIImage.systemImageNamed(symbol, withConfiguration = symbolConfig)
                ?: UIImage.systemImageNamed(symbol)
        )?.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate)
    val config =
        if (usesNativeLiquidGlass) {
            UIButtonConfiguration.glassButtonConfiguration().apply {
                cornerStyle = UIButtonConfigurationCornerStyleCapsule
            }
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
