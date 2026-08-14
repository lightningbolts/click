@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.ui.components.AvatarWithOnlineIndicator // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.CoreConnectionAvatarFrame // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret

/**
 * Horizontal “Remember Me” rail for Core-pinned 1:1 connections.
 * Hierarchy intent from design-assets/chat — not a card stack.
 */
@Composable
fun RememberMeStrip(
    chats: List<ChatWithDetails>,
    onChatSelected: (chatId: String) -> Unit,
    modifier: Modifier = Modifier,
    showClicksSectionLabel: Boolean = true,
    onlineUserIds: Set<String> = emptySet(),
) {
    if (chats.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Remember Me",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(chats, key = { it.connection.id }) { chat ->
                RememberMeChip(
                    chat = chat,
                    isOnline = chat.otherUser.id in onlineUserIds,
                    onClick = {
                        onChatSelected(chat.chat.id ?: chat.connection.id)
                    },
                )
            }
        }
        if (showClicksSectionLabel) {
            Text(
                text = "Clicks",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RememberMeChip(
    chat: ChatWithDetails,
    isOnline: Boolean,
    onClick: () -> Unit,
) {
    val user = chat.otherUser
    val displayName = user.name?.trim()?.takeIf { it.isNotEmpty() }
    val firstName =
        user.firstName?.trim()?.takeIf { it.isNotEmpty() }
            ?: displayName?.substringBefore(' ')?.ifBlank { null }
            ?: displayName
            ?: "Friend"
    val activityTs = chat.connection.last_message_at ?: chat.lastMessage?.timeCreated
    val badge = formatRememberMeBadge(activityTs)

    Column(
        modifier = Modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            // Circular hit target only — avoid the 80dp-wide column becoming a square tap region.
            AvatarWithOnlineIndicator(isOnline = isOnline) {
                CoreConnectionAvatarFrame(
                    isCore = true,
                    avatarSize = 56.dp,
                    onClick = onClick,
                ) {
                    ConnectionListUserAvatarFace(
                        displayName = displayName,
                        email = user.email,
                        avatarUrl = user.image,
                        userId = user.id,
                        modifier =
                            Modifier
                                .size(56.dp)
                                .border(clickBorderWidth(), clickBorderColor(), CircleShape),
                        useCompactTypography = true,
                    )
                }
            }
            if (badge != null) {
                Text(
                    text = badge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PrimaryBlue)
                            .border(2.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(999.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            text = firstName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .widthIn(max = 80.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}
