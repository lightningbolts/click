@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package compose.project.click.click.ui.components.native

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIColor

@Composable
actual fun NativeContextMenuBox(
    items: List<NativeContextMenuItem>,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val itemsState by rememberUpdatedState(items)
    val enabledState by rememberUpdatedState(enabled)

    Box(modifier = modifier) {
        content()
        UIKitView(
            modifier = Modifier.matchParentSize(),
            properties = nativeChromeUIKitInteropProperties(
                isInteractive = enabled,
                isNativeAccessibilityEnabled = false,
            ),
            factory = {
                runOnMainQueueSync {
                    UIButton.buttonWithType(platform.UIKit.UIButtonTypeCustom).apply {
                        setFrame(CGRectMake(0.0, 0.0, 1.0, 1.0))
                        applyTransparentChromeHost(this)
                    }
                }
            },
            update = { button ->
                runOnMainQueue {
                    button as UIButton
                    applyTransparentChromeHost(button)
                    button.userInteractionEnabled = enabledState
                    button.alpha = if (enabledState) 1.0 else 0.0
                    button.attachNativeMenu(itemsState)
                }
            },
        )
    }
}
