package compose.project.click.click.ui.chat

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import compose.project.click.click.ui.components.native.NavButtonStyle

data class ChatAttachmentMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
internal expect fun ChatAttachmentMenuButton(
    items: List<ChatAttachmentMenuAction>,
    modifier: Modifier = Modifier,
    anchorSize: Dp,
    anchorInteraction: MutableInteractionSource,
    anchorEnabled: Boolean = true,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    style: NavButtonStyle = NavButtonStyle.Prominent,
)
