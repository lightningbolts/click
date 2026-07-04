package compose.project.click.click.ui.chat

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.ui.components.native.NativeNavButton
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
    var attachmentMenuExpanded by remember { mutableStateOf(false) }

    ChatAttachmentMenuAnchorHost(
        expanded = attachmentMenuExpanded,
        onExpandedChange = { attachmentMenuExpanded = it },
        anchorSize = anchorSize,
        anchorInteraction = anchorInteraction,
        anchorEnabled = anchorEnabled,
        modifier = modifier,
        anchor = {
            NativeNavButton(
                icon = icon,
                contentDescription = contentDescription,
                onClick = {
                    PlatformHapticsPolicy.heavyImpact()
                    attachmentMenuExpanded = !attachmentMenuExpanded
                },
                enabled = anchorEnabled,
                size = anchorSize,
                tint = tint,
                style = style,
            )
        },
        menuContent = {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                items.forEach { action ->
                    ChatAttachmentMenuRow(
                        label = action.label,
                        icon = action.icon,
                        enabled = action.enabled,
                        onClick = action.onClick,
                    )
                }
            }
        },
    )
}
