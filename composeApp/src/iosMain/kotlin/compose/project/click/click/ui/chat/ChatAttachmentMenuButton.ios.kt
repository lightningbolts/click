@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.ui.components.native.NativeContextMenuIconButton
import compose.project.click.click.ui.components.native.NativeContextMenuItem
import compose.project.click.click.ui.components.native.NavButtonStyle

@Composable
internal actual fun ChatAttachmentMenuButton(
    items: List<ChatAttachmentMenuAction>,
    modifier: Modifier,
    anchorSize: Dp,
    anchorInteraction: MutableInteractionSource,
    anchorEnabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    style: NavButtonStyle,
) {
    val menuItems = remember(items) {
        items.map { action ->
            NativeContextMenuItem(
                label = action.label,
                onClick = {
                    PlatformHapticsPolicy.heavyImpact()
                    action.onClick()
                },
                enabled = action.enabled,
            )
        }
    }
    NativeContextMenuIconButton(
        icon = icon,
        contentDescription = contentDescription,
        items = menuItems,
        modifier = modifier.size(anchorSize),
        enabled = anchorEnabled,
        tint = tint,
        onIconClick = {
            PlatformHapticsPolicy.heavyImpact()
        },
    )
}
