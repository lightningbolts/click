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
import platform.UIKit.UIButton
import platform.UIKit.UIMenu
import platform.UIKit.UIView
import platform.darwin.NSObject

@Composable
actual fun NativeNavButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    tint: Color?,
    style: NavButtonStyle,
    size: Dp?,
) {
    val onClickState by rememberUpdatedState(onClick)
    val enabledState by rememberUpdatedState(enabled)
    val symbolName = remember(icon) { icon.toSfSymbolName().orEmpty() }
    val buttonSize = size ?: when (style) {
        NavButtonStyle.Icon -> 48.dp
        NavButtonStyle.Prominent -> 56.dp
    }
    val cornerRadius = buttonSize.value / 2.0
    val glassVariant = when (style) {
        NavButtonStyle.Icon -> GlassButtonVariant.Regular
        NavButtonStyle.Prominent -> GlassButtonVariant.Prominent
    }
    val tintColor = remember(tint, style) {
        tint.toUiColor()
    }

    val target = remember {
        object : NSObject() {
            @ObjCAction
            fun onTap() {
                if (enabledState) {
                    PlatformHapticsPolicy.lightImpact()
                    onClickState()
                }
            }
        }
    }

    UIKitView(
        modifier = modifier.size(buttonSize),
        properties = nativeChromeUIKitInteropProperties(isInteractive = true),
        factory = {
            runOnMainQueueSync {
                createGlassIconButtonHost(
                    buttonSizeDp = buttonSize.value.toDouble(),
                    cornerRadius = cornerRadius,
                    glassVariant = glassVariant,
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
                    glassVariant = glassVariant,
                )
            }
        },
    )
}

internal fun UIButton.attachNativeMenu(items: List<NativeContextMenuItem>) {
    val actions = items.map { item ->
        platform.UIKit.UIAction.actionWithTitle(
            title = item.label,
            image = null,
            identifier = null,
            handler = { _ ->
                if (item.enabled) item.onClick()
            },
        ).apply {
            if (item.destructive) {
                attributes = platform.UIKit.UIMenuElementAttributesDestructive
            }
        }
    }
    menu = UIMenu.menuWithChildren(actions)
    showsMenuAsPrimaryAction = true
}
