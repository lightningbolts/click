@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package compose.project.click.click.ui.components.native

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIButton
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.darwin.NSObject

@Composable
actual fun NativeContextMenuChip(
    label: String,
    items: List<NativeContextMenuItem>,
    modifier: Modifier,
    enabled: Boolean,
) {
    val labelState by rememberUpdatedState(label)
    val itemsState by rememberUpdatedState(items)
    val enabledState by rememberUpdatedState(enabled)

    val target = remember {
        object : NSObject() {
            @ObjCAction
            fun onTap() = Unit
        }
    }

    UIKitView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp, max = 48.dp),
        properties = nativeChromeUIKitInteropProperties(isInteractive = enabled),
        factory = {
            runOnMainQueueSync {
                UIButton.buttonWithType(platform.UIKit.UIButtonTypeSystem).apply {
                    applyTransparentChromeHost(this)
                    applyGlassConfiguration(GlassButtonVariant.Regular)
                    layer.cornerRadius = 20.0
                    clipsToBounds = true
                    titleLabel?.font = platform.UIKit.UIFont.systemFontOfSize(15.0, platform.UIKit.UIFontWeightSemibold)
                    setTitleColor(UIColor.blackColor, forState = platform.UIKit.UIControlStateNormal)
                    val chevronConfig = UIImageSymbolConfiguration.configurationWithPointSize(
                        11.0,
                        weight = platform.UIKit.UIImageSymbolWeightSemibold,
                    )
                    val chevron = UIImage.systemImageNamed("chevron.down", chevronConfig)
                    setImage(chevron, forState = platform.UIKit.UIControlStateNormal)
                    semanticContentAttribute =
                        platform.UIKit.UISemanticContentAttributeForceRightToLeft
                    addTarget(
                        target = target,
                        action = NSSelectorFromString("onTap"),
                        forControlEvents = UIControlEventTouchUpInside,
                    )
                }
            }
        },
        update = { button ->
            runOnMainQueue {
                button as UIButton
                applyTransparentChromeHost(button)
                button.applyGlassConfiguration(GlassButtonVariant.Regular)
                button.setTitle(labelState, forState = platform.UIKit.UIControlStateNormal)
                button.userInteractionEnabled = enabledState
                button.alpha = if (enabledState) 1.0 else 0.45
                button.attachNativeMenu(itemsState)
            }
        },
    )
}
