@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import compose.project.click.click.ui.chat.ChatHeaderIconButton

@Composable
actual fun PlatformHubSheetHeaderButton(
    action: HubSheetHeaderAction,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    ChatHeaderIconButton(
        icon =
            when (action) {
                HubSheetHeaderAction.Back -> Icons.AutoMirrored.Filled.ArrowBack
                HubSheetHeaderAction.More -> Icons.Filled.MoreVert
            },
        contentDescription = contentDescription,
        onClick = onClick,
        showBorder = action == HubSheetHeaderAction.Back,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action == HubSheetHeaderAction.More) 0.7f else 1f),
        modifier = modifier,
    )
}
