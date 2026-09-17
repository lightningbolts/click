@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.viewinterop.UIKitView
import compose.project.click.click.platform.rememberReduceTransparencyEnabled
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UICornerConfiguration
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightMedium
import platform.UIKit.UIVisualEffectView
import platform.UIKit.setAccessibilityLabel
import platform.darwin.NSObject

/**
 * Sheet-local iOS control that deliberately uses the same interactive UIGlassEffect primitive as
 * the persistent Astra navigation chrome. A bare glassButtonConfiguration inside UIKitView leaves
 * its interop host visible as a dark square and does not get the interactive glass deformation
 * used by the app-level controls.
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
    // This lives in the detached native sheet ComposeUIViewController. MaterialTheme is explicitly
    // propagated into that host; app-level CompositionLocals are not, so derive contrast here.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
            HubSheetHeaderGlassView(target).apply {
                applyAppearance(
                    symbol = symbol,
                    contentDescription = contentDescription,
                    isDark = isDark,
                    usesNativeLiquidGlass = usesNativeLiquidGlass,
                )
            }
        },
        modifier = modifier,
        update = { view ->
            val control = view as? HubSheetHeaderGlassView ?: return@UIKitView
            control.applyAppearance(
                symbol = symbol,
                contentDescription = contentDescription,
                isDark = isDark,
                usesNativeLiquidGlass = usesNativeLiquidGlass,
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class HubSheetHeaderGlassView(
    target: HubSheetHeaderTapTarget,
) : UIVisualEffectView() {
    private val button =
        UIButton.buttonWithType(UIButtonTypeSystem).apply {
            translatesAutoresizingMaskIntoConstraints = false
            setOpaque(false)
            backgroundColor = UIColor.clearColor
            layer.backgroundColor = UIColor.clearColor.CGColor
            layer.shadowOpacity = 0f
            addTarget(
                target,
                action = NSSelectorFromString("didTap"),
                forControlEvents = UIControlEventTouchUpInside,
            )
        }

    init {
        setOpaque(false)
        clipsToBounds = false
        userInteractionEnabled = true
        backgroundColor = UIColor.clearColor
        layer.backgroundColor = UIColor.clearColor.CGColor
        layer.shadowOpacity = 0f
        layer.borderWidth = 0.0
        contentView.setOpaque(false)
        contentView.backgroundColor = UIColor.clearColor
        contentView.addSubview(button)
        NSLayoutConstraint.activateConstraints(
            listOf(
                button.topAnchor.constraintEqualToAnchor(contentView.topAnchor),
                button.leadingAnchor.constraintEqualToAnchor(contentView.leadingAnchor),
                button.trailingAnchor.constraintEqualToAnchor(contentView.trailingAnchor),
                button.bottomAnchor.constraintEqualToAnchor(contentView.bottomAnchor),
            ),
        )
    }

    fun applyAppearance(
        symbol: String,
        contentDescription: String,
        isDark: Boolean,
        usesNativeLiquidGlass: Boolean,
    ) {
        val tint = if (isDark) UIColor.whiteColor else UIColor.blackColor
        val symbolConfig =
            UIImageSymbolConfiguration.configurationWithPointSize(
                NativeHeaderMetrics.ChromeIconPointSize,
                weight = UIImageSymbolWeightMedium,
            )
        val image =
            UIImage.systemImageNamed(symbol, withConfiguration = symbolConfig)
                ?: UIImage.systemImageNamed(symbol)

        if (usesNativeLiquidGlass) {
            val glass = UIGlassEffect.effectWithStyle(UIGlassEffectStyle.UIGlassEffectStyleRegular)
            glass.setInteractive(true)
            effect = glass
            cornerConfiguration = UICornerConfiguration.capsuleConfiguration()
            clipsToBounds = false
            layer.cornerRadius = 0.0
        } else {
            val style =
                if (isDark) {
                    UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialDark
                } else {
                    UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialLight
                }
            effect = UIBlurEffect.effectWithStyle(style)
            cornerConfiguration = null
            clipsToBounds = true
            layer.cornerRadius = NativeHeaderMetrics.ChromeButtonSizePt / 2.0
        }
        backgroundColor = UIColor.clearColor
        layer.backgroundColor = UIColor.clearColor.CGColor
        layer.shadowOpacity = 0f
        layer.borderWidth = 0.0

        val config = UIButtonConfiguration.plainButtonConfiguration()
        config.image = image?.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate)
        config.baseForegroundColor = tint
        config.preferredSymbolConfigurationForImage = symbolConfig
        config.contentInsets = NSDirectionalEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
        button.configuration = config
        button.tintColor = tint
        button.backgroundColor = UIColor.clearColor
        button.layer.backgroundColor = UIColor.clearColor.CGColor
        button.layer.shadowOpacity = 0f
        button.setAccessibilityLabel(contentDescription)
    }
}

@OptIn(BetaInteropApi::class)
private class HubSheetHeaderTapTarget : NSObject() {
    var handler: (() -> Unit)? = null

    @ObjCAction
    fun didTap() {
        handler?.invoke()
    }
}
