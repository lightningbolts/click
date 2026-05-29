package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.previewLabel // pragma: allowlist secret
import compose.project.click.click.ui.components.AvatarWithOnlineIndicator
import compose.project.click.click.ui.components.CoreConnectionAvatarFrame // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.GroupAvatar // pragma: allowlist secret
import compose.project.click.click.ui.components.groupAvatarClusterWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.LightBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.util.AvailabilityOverlapCache // pragma: allowlist secret

/**
 * Single row in the Clicks list. Tap opens chat; hold opens the unified action sheet.
 */
@Composable
fun ConnectionItem(
    chatDetails: ChatWithDetails,
    viewerUserId: String? = null,
    overlapPrefetchGeneration: Int = 0,
    isCore: Boolean = false,
    showOnlineIndicator: Boolean = false,
    decryptedPreview: String? = null,
    hasCachedThreadPreview: Boolean = false,
    onAvatarClick: () -> Unit = {},
    onGroupMembersPicker: (List<User>) -> Unit = {},
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val isGroup = chatDetails.groupClique != null
    val headline = if (isGroup) {
        chatDetails.groupClique?.name?.trim()?.ifBlank { null } ?: "Verified click"
    } else {
        chatDetails.otherUser.name ?: "Unknown"
    }
    val user = chatDetails.otherUser
    val connection = chatDetails.connection
    val lastMessage = chatDetails.lastMessage
    val embeddedLastMessage = connection.chat.messages.lastOrNull()
    val effectiveLastMessage = lastMessage ?: embeddedLastMessage
    val unreadCount = chatDetails.unreadCount
    val activityTs = effectiveLastMessage?.timeCreated ?: connection.last_message_at
    val timeText = activityTs?.let { formatConnectionListTimestamp(it) } ?: "No messages"
    val previewNeedsRefresh = connection.last_message_at?.let { latestAt ->
        effectiveLastMessage == null || effectiveLastMessage.timeCreated < latestAt
    } ?: false
    val hasPreviewText = effectiveLastMessage != null || decryptedPreview != null || hasCachedThreadPreview
    // Shimmer only while the row is still resolving peer metadata (cold start), not when
    // we already have cached/decrypted preview text or server activity to show as "New message".
    val showLoadingSubtitle = !hasPreviewText &&
        user.name == "Connection" &&
        connection.last_message_at == null

    val peerId = chatDetails.otherUser.id
    val hasIntentOverlap = remember(peerId, viewerUserId, isGroup, overlapPrefetchGeneration) {
        val v = viewerUserId
        if (isGroup || v.isNullOrBlank()) {
            false
        } else {
            AvailabilityOverlapCache.get(v, peerId) == true
        }
    }

    val rowInteraction = remember { MutableInteractionSource() }
    val pressed by rowInteraction.collectIsPressedAsState()
    val cardBorderAlpha by animateFloatAsState(
        targetValue = if (pressed) {
            GlassSheetTokens.GlassBorderPressed.alpha
        } else {
            GlassSheetTokens.GlassBorder.alpha
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "connection_row_glass_border",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner))
            .border(
                width = 1.dp,
                color = GlassSheetTokens.GlassBorder.copy(alpha = cardBorderAlpha),
                shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner),
            )
            .background(GlassSheetTokens.GlassSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isGroup) {
            val groupListAvatarSize = 40.dp
            val groupClusterWidth = groupAvatarClusterWidth(
                chatDetails.groupMemberUsers.size,
                groupListAvatarSize,
            )
            Box(
                modifier = Modifier
                    .width(groupClusterWidth)
                    .height(44.dp)
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
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
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
                        avatarSize = groupListAvatarSize,
                    )
                }
            }
        } else {
            CoreConnectionAvatarFrame(
                isCore = isCore,
                avatarSize = 44.dp,
                onClick = onAvatarClick,
            ) {
                AvatarWithOnlineIndicator(
                    isOnline = showOnlineIndicator,
                    modifier = Modifier.fillMaxSize(),
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
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .connectionRowPressGestures(onClick = onClick, onLongPress = onLongPress),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    val rawPreview = when {
                        previewNeedsRefresh -> "New message"
                        effectiveLastMessage != null -> effectiveLastMessage.previewLabel()
                        connection.last_message_at != null -> "New message"
                        else -> "Start a conversation"
                    }
                    val previewText = if (rawPreview == "New message" && decryptedPreview != null) {
                        decryptedPreview
                    } else {
                        rawPreview
                    }
                    Crossfade(
                        targetState = previewText,
                        animationSpec = tween(durationMillis = 300),
                        modifier = Modifier.weight(1f),
                        label = "preview_fade",
                    ) { text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unreadCount > 0) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
            }
        }
    }
}
