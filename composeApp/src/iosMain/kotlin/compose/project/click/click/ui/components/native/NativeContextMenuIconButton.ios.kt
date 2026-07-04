@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package compose.project.click.click.ui.components.native

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import compose.project.click.click.PlatformHapticsPolicy
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIButton
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffectView
import platform.darwin.NSObject

@Composable
actual fun NativeContextMenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    items: List<NativeContextMenuItem>,
    modifier: Modifier,
    enabled: Boolean,
    tint: Color?,
    size: Dp?,
    onIconClick: (() -> Unit)?,
) {
    val itemsState by rememberUpdatedState(items)
    val enabledState by rememberUpdatedState(enabled)
    val onIconClickState by rememberUpdatedState(onIconClick)
    val symbolName = remember(icon) { icon.toSfSymbolName().orEmpty() }
    val buttonSize = size ?: 48.dp
    val cornerRadius = buttonSize.value / 2.0
    val tintColor = remember(tint) { tint.toUiColor() }

    val target = remember {
        object : NSObject() {
            @ObjCAction
            fun onTap() {
                if (enabledState) {
                    PlatformHapticsPolicy.lightImpact()
                    onIconClickState?.invoke()
                }
            }
        }
    }

    UIKitView(
        modifier = modifier.size(buttonSize),
        properties = nativeChromeUIKitInteropProperties(isInteractive = enabled),
        factory = {
            runOnMainQueueSync {
                createGlassIconButtonHost(
                    buttonSizeDp = buttonSize.value.toDouble(),
                    cornerRadius = cornerRadius,
                    glassVariant = GlassButtonVariant.Regular,
                    target = target,
                )
            }
        },
        update = { root ->
            runOnMainQueue {
                updateGlassIconButtonHost(
                    root = root,
                    symbolName = symbolName,
                    tintColor = tintColor,
                    enabled = enabledState,
                    cornerRadius = cornerRadius,
                    glassVariant = GlassButtonVariant.Regular,
                )
                val button = root.glassIconButton() ?: return@runOnMainQueue
                button.attachNativeMenu(itemsState)
            }
        },
    )
}
