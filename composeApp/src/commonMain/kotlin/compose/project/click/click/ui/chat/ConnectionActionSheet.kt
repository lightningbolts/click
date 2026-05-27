package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.ui.components.BentoGlassOptionRow // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickActionBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

/**
 * Menu actions emitted before [onDismiss] so the parent can show confirm dialogs
 * after the sheet has animated away.
 */
internal sealed class ConnectionMenuAction {
    data object Nudge : ConnectionMenuAction()
    data object Archive : ConnectionMenuAction()
    data object Unarchive : ConnectionMenuAction()
    data object AddToCore : ConnectionMenuAction()
    data object RemoveFromCore : ConnectionMenuAction()
    data object RequestRemove : ConnectionMenuAction()
    data object RequestReport : ConnectionMenuAction()
    data object RequestBlock : ConnectionMenuAction()
    data object RequestLeaveGroup : ConnectionMenuAction()
    data object RequestDeleteGroup : ConnectionMenuAction()
}

/**
 * Bottom sheet for connection-level actions. Confirm dialogs are owned by the parent
 * ([ConnectionSheetDialogs]) so the sheet scrim never blocks them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionActionSheet(
    chatDetails: ChatWithDetails?,
    currentUserId: String?,
    isArchived: Boolean = false,
    isServerLifecycleArchived: Boolean = false,
    isCore: Boolean = false,
    onDismiss: () -> Unit,
    onMenuAction: (ConnectionMenuAction) -> Unit,
) {
    val isGroup = chatDetails?.groupClique != null
    val uid = currentUserId.orEmpty()
    val isGroupCreator = isGroup && uid.isNotBlank() && chatDetails?.groupClique?.createdByUserId == uid

    fun pick(action: ConnectionMenuAction) {
        onMenuAction(action)
        onDismiss()
    }

    ClickActionBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(GlassSheetTokens.OledBlack)
                .padding(bottom = 32.dp),
        ) {
            chatDetails?.let { details ->
                val title = if (isGroup) {
                    details.groupClique?.name?.trim()?.ifBlank { null } ?: "Verified click"
                } else {
                    details.otherUser.name ?: "Connection"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassSheetTokens.OnOled,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .align(Alignment.CenterHorizontally),
                )
                HorizontalDivider(color = GlassSheetTokens.GlassBorder.copy(alpha = 0.5f))
            }

            if (!isGroup) {
                BentoGlassOptionRow(
                    showBorder = false,
                    title = "Nudge",
                    subtitle = "Send a quick ping",
                    cornerRadius = GlassSheetTokens.BentoExteriorCorner,
                    onClick = { pick(ConnectionMenuAction.Nudge) },
                    leading = {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = GlassSheetTokens.OnOledMuted,
                        )
                    },
                )

                if (isCore) {
                    BentoGlassOptionRow(
                        title = "Remove from Core",
                        subtitle = "Unpin from your core connections",
                        onClick = { pick(ConnectionMenuAction.RemoveFromCore) },
                        leading = {
                            Icon(
                                Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = GlassSheetTokens.OnOledMuted,
                            )
                        },
                    )
                } else {
                    BentoGlassOptionRow(
                        title = "Add to Core",
                        subtitle = "Pin to the top and unlock core-only features",
                        onClick = { pick(ConnectionMenuAction.AddToCore) },
                        leading = {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = PrimaryBlue,
                            )
                        },
                    )
                }

                if (isArchived) {
                    BentoGlassOptionRow(
                        title = "Unarchive",
                        subtitle = if (isServerLifecycleArchived) {
                            "Remove from your Archived tab (server-archived connections stay read-only)"
                        } else {
                            "Move this connection back to Active"
                        },
                        onClick = { pick(ConnectionMenuAction.Unarchive) },
                        leading = {
                            Icon(
                                Icons.Default.Unarchive,
                                contentDescription = null,
                                tint = GlassSheetTokens.OnOledMuted,
                            )
                        },
                    )
                } else if (!isServerLifecycleArchived) {
                    BentoGlassOptionRow(
                        title = "Archive",
                        subtitle = "Hide this connection (recoverable)",
                        onClick = { pick(ConnectionMenuAction.Archive) },
                        leading = {
                            Icon(
                                Icons.Default.Archive,
                                contentDescription = null,
                                tint = GlassSheetTokens.OnOledMuted,
                            )
                        },
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = GlassSheetTokens.GlassBorder.copy(alpha = 0.35f),
                )

                BentoGlassOptionRow(
                    showBorder = false,
                    title = "Remove Connection",
                    subtitle = "Permanently remove this chat",
                    onClick = { pick(ConnectionMenuAction.RequestRemove) },
                    destructive = true,
                    leading = {
                        Icon(
                            Icons.Default.PersonRemove,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                        )
                    },
                )

                BentoGlassOptionRow(
                    showBorder = false,
                    title = "Report",
                    subtitle = "Flag for review",
                    onClick = { pick(ConnectionMenuAction.RequestReport) },
                    titleColor = Color(0xFFFF8C00),
                    leading = {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color(0xFFFF8C00),
                        )
                    },
                )

                BentoGlassOptionRow(
                    showBorder = false,
                    title = "Block",
                    subtitle = "They can no longer reach you",
                    onClick = { pick(ConnectionMenuAction.RequestBlock) },
                    destructive = true,
                    leading = {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                        )
                    },
                )
            } else {
                BentoGlassOptionRow(
                    showBorder = false,
                    title = "Leave Group",
                    subtitle = "Lose access to this verified click",
                    onClick = { pick(ConnectionMenuAction.RequestLeaveGroup) },
                    leading = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = GlassSheetTokens.OnOledMuted,
                        )
                    },
                )
                if (isGroupCreator) {
                    BentoGlassOptionRow(
                        showBorder = false,
                        title = "Delete Group",
                        subtitle = "Remove for everyone",
                        onClick = { pick(ConnectionMenuAction.RequestDeleteGroup) },
                        destructive = true,
                        leading = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                            )
                        },
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
            )
        }
    }
}
