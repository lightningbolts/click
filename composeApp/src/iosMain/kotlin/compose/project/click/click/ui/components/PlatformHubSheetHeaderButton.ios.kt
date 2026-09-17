@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import compose.project.click.click.platform.rememberReduceTransparencyEnabled
import compose.project.click.click.ui.theme.LocalIsDarkMode
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
import platform.UIKit.setAccessibilityLabel
import platform.darwin.NSObject

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
    val isDark = LocalIsDarkMode.current
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val usesNativeLiquidGlass =
        remember(reduceTransparency) {
            !reduceTransparency &&
                NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }
    val symbol =
        when (action) {
            HubSheetHeaderAction.Back -> "chevron.left"
            HubSheetHeaderAction.More -> "ellipsis"
        }

    SideEffect {
        target.handler = onClickState
    }

    UIKitView(
        factory = {
            UIButton.buttonWithType(UIButtonTypeSystem).apply {
                addTarget(
                    target,
                    action = NSSelectorFromString("didTap"),
                    forControlEvents = UIControlEventTouchUpInside,
                )
                applyHubSheetHeaderButtonAppearance(
                    symbol = symbol,
                    contentDescription = contentDescription,
                    isDark = isDark,
                    usesNativeLiquidGlass = usesNativeLiquidGlass,
                )
            }
        },
        modifier = modifier,
        update = { button ->
            button.applyHubSheetHeaderButtonAppearance(
                symbol = symbol,
                contentDescription = contentDescription,
                isDark = isDark,
                usesNativeLiquidGlass = usesNativeLiquidGlass,
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun UIButton.applyHubSheetHeaderButtonAppearance(
    symbol: String,
    contentDescription: String,
    isDark: Boolean,
    usesNativeLiquidGlass: Boolean,
) {
    val tint = if (isDark) UIColor.whiteColor else UIColor.blackColor
    val config =
        if (usesNativeLiquidGlass) {
            UIButtonConfiguration.glassButtonConfiguration()
        } else {
            UIButtonConfiguration.plainButtonConfiguration()
        }
    config.cornerStyle = UIButtonConfigurationCornerStyleCapsule
    config.image = UIImage.systemImageNamed(symbol)
    config.baseForegroundColor = tint
    config.contentInsets = NSDirectionalEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
    configuration = config
    tintColor = tint
    backgroundColor =
        if (usesNativeLiquidGlass) {
            UIColor.clearColor
        } else if (isDark) {
            UIColor.whiteColor.colorWithAlphaComponent(0.08)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.06)
        }
    layer.cornerRadius = 22.0
    setAccessibilityLabel(contentDescription)
}

@OptIn(BetaInteropApi::class)
private class HubSheetHeaderTapTarget : NSObject() {
    var handler: (() -> Unit)? = null

    @ObjCAction
    fun didTap() {
        handler?.invoke()
    }
}
