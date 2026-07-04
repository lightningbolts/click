package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.ui.components.native.NativeContextMenuItem

/**
 * Builds header overflow menu items mirroring [ConnectionActionSheet] visibility rules.
 */
internal fun buildConnectionContextMenuItems(
    chatDetails: ChatWithDetails?,
    currentUserId: String?,
    isArchived: Boolean,
    isServerLifecycleArchived: Boolean,
    isCore: Boolean,
    onMenuAction: (ConnectionMenuAction) -> Unit,
): List<NativeContextMenuItem> {
    if (chatDetails == null) return emptyList()
    val isGroup = chatDetails.groupClique != null
    val uid = currentUserId.orEmpty()
    val isGroupCreator = isGroup && uid.isNotBlank() && chatDetails.groupClique?.createdByUserId == uid
    val hasConversationActivity = chatDetails.lastMessage != null ||
        chatDetails.connection.last_message_at != null
    val canMarkUnread = hasConversationActivity && (chatDetails.unreadCount ?: 0) == 0

    val items = mutableListOf<NativeContextMenuItem>()

    if (!isGroup) {
        items += NativeContextMenuItem(label = "Nudge", onClick = { onMenuAction(ConnectionMenuAction.Nudge) })

        if (isCore) {
            items += NativeContextMenuItem(
                label = "Remove from Core",
                onClick = { onMenuAction(ConnectionMenuAction.RemoveFromCore) },
            )
        } else {
            items += NativeContextMenuItem(
                label = "Add to Core",
                onClick = { onMenuAction(ConnectionMenuAction.AddToCore) },
            )
        }

        when {
            isArchived -> {
                items += NativeContextMenuItem(
                    label = "Unarchive",
                    onClick = { onMenuAction(ConnectionMenuAction.Unarchive) },
                )
            }
            !isServerLifecycleArchived -> {
                items += NativeContextMenuItem(
                    label = "Archive",
                    onClick = { onMenuAction(ConnectionMenuAction.Archive) },
                )
            }
        }

        if (canMarkUnread) {
            items += NativeContextMenuItem(
                label = "Mark as Unread",
                onClick = { onMenuAction(ConnectionMenuAction.MarkUnread) },
            )
        }

        items += NativeContextMenuItem(
            label = "Remove Connection",
            onClick = { onMenuAction(ConnectionMenuAction.RequestRemove) },
            destructive = true,
        )
        items += NativeContextMenuItem(
            label = "Report",
            onClick = { onMenuAction(ConnectionMenuAction.RequestReport) },
        )
        items += NativeContextMenuItem(
            label = "Block",
            onClick = { onMenuAction(ConnectionMenuAction.RequestBlock) },
            destructive = true,
        )
    } else {
        if (canMarkUnread) {
            items += NativeContextMenuItem(
                label = "Mark as Unread",
                onClick = { onMenuAction(ConnectionMenuAction.MarkUnread) },
            )
        }
        items += NativeContextMenuItem(
            label = "Leave Group",
            onClick = { onMenuAction(ConnectionMenuAction.RequestLeaveGroup) },
        )
        if (isGroupCreator) {
            items += NativeContextMenuItem(
                label = "Delete Group",
                onClick = { onMenuAction(ConnectionMenuAction.RequestDeleteGroup) },
                destructive = true,
            )
        }
    }

    return items
}

internal fun buildCallContextMenuItems(
    isGroupChat: Boolean,
    onVoice: () -> Unit,
    onVideo: () -> Unit,
): List<NativeContextMenuItem> = listOf(
    NativeContextMenuItem(
        label = if (isGroupChat) "Group voice call" else "Voice call",
        onClick = onVoice,
    ),
    NativeContextMenuItem(
        label = if (isGroupChat) "Group video call" else "Video call",
        onClick = onVideo,
    ),
)
