package compose.project.click.click.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.previewLabel
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.getPlatform
import compose.project.click.click.ui.components.AvatarWithOnlineIndicator
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace
import compose.project.click.click.ui.components.GroupAvatar
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.util.AvailabilityOverlapCache
import compose.project.click.click.util.hasActiveAvailabilityIntentOverlap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single row in the Clicks list: avatar (single or grouped), name,
 * last-activity time, preview or shimmer, unread badge, per-row
 * nudge and overflow menu buttons. For 1:1 connections it also
 * asynchronously checks whether the viewer and peer share an active
 * availability intent window and renders a yellow bolt accent.
 *
 * Extracted verbatim from ConnectionsScreen.kt; no behavior change.
 */
@Composable
fun ConnectionItem(
    chatDetails: ChatWithDetails,
    viewerUserId: String? = null,
    showOnlineIndicator: Boolean = false,
    onAvatarClick: () -> Unit = {},
    onGroupMembersPicker: (List<User>) -> Unit = {},
    onClick: () -> Unit,
    onNudge: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    val isGroup = chatDetails.groupClique != null
    val headline = if (isGroup) {
        chatDetails.groupClique?.name?.trim()?.ifBlank { null } ?: "Verified click"
    } else {
        chatDetails.otherUser.name ?: "Unknown"
    }
    val user = chatDetails.otherUser
    val connection = chatDetails.connection
    val lastMessage = chatDetails.lastMessage
    val unreadCount = chatDetails.unreadCount
    val activityTs = lastMessage?.timeCreated ?: connection.last_message_at
    val timeText = activityTs?.let { formatConnectionListTimestamp(it) } ?: "No messages"
    val showLoadingSubtitle =
        lastMessage == null && user.name == "Connection" && connection.last_message_at == null
    val previewNeedsRefresh = connection.last_message_at?.let { latestAt ->
        lastMessage == null || lastMessage.timeCreated < latestAt
    } ?: false

    val peerId = chatDetails.otherUser.id
    var hasIntentOverlap by remember(chatDetails.otherUser.id, viewerUserId, isGroup) {
        val v = viewerUserId
        val cached = if (!isGroup && !v.isNullOrBlank()) AvailabilityOverlapCache.get(v, peerId) else null
        mutableStateOf(cached == true)
    }
    val overlapRepo = remember { SupabaseRepository() }
    LaunchedEffect(chatDetails.otherUser.id, viewerUserId, isGroup) {
        if (isGroup || viewerUserId.isNullOrBlank()) {
            hasIntentOverlap = false
            return@LaunchedEffect
        }
        val v = viewerUserId
        val theirsPeer = chatDetails.otherUser.id
        val result = withContext(Dispatchers.Default) {
            val mine = overlapRepo.fetchPeerProfileAvailabilityBubbles(v, v)
            val theirs = overlapRepo.fetchPeerProfileAvailabilityBubbles(v, theirsPeer)
            hasActiveAvailabilityIntentOverlap(mine, theirs)
        }
        AvailabilityOverlapCache.put(v, theirsPeer, result)
        hasIntentOverlap = result
    }

    val rowTapModifier = if (isIOS) {
        Modifier.pointerInput(onClick, onLongPress) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = {
                    PlatformHapticsPolicy.heavyImpact()
                    onLongPress()
                },
            )
        }
    } else {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                PlatformHapticsPolicy.heavyImpact()
                onLongPress()
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(rowTapModifier)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isGroup) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 24.dp),
                        onClick = { onGroupMembersPicker(orderedGroupMembersForPicker(chatDetails)) },
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (chatDetails.groupMemberUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    GroupAvatar(
                        members = chatDetails.groupMemberUsers,
                        avatarSize = 40.dp,
                    )
                }
            }
        } else {
            AvatarWithOnlineIndicator(
                isOnline = showOnlineIndicator,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 24.dp),
                        onClick = onAvatarClick,
                    ),
            ) {
                ConnectionListUserAvatarFace(
                    displayName = user.name,
                    email = user.email,
                    avatarUrl = user.image,
                    userId = user.id,
                    modifier = Modifier.fillMaxSize(),
                    useCompactTypography = false,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        headline,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isGroup && hasIntentOverlap) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = "Shared availability",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showLoadingSubtitle) {
                    Box(modifier = Modifier.weight(1f)) {
                        LoadingSubtitlePlaceholder()
                    }
                } else {
                    val previewText = when {
                        previewNeedsRefresh -> "New message"
                        lastMessage != null -> lastMessage.previewLabel()
                        connection.last_message_at != null -> "New message"
                        else -> "Start a conversation"
                    }
                    Text(
                        previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue)),
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            unreadCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (!isGroup) {
            IconButton(
                onClick = onNudge,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = "Nudge",
                    modifier = Modifier.size(18.dp),
                    tint = PrimaryBlue.copy(alpha = 0.7f),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(36.dp))
        }

        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "More options",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
