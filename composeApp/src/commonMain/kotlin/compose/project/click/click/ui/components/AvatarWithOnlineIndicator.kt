package compose.project.click.click.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.UserProfile
import compose.project.click.click.data.models.toUserProfile

private val OnlineGreen = Color(0xFF22C55E)

/**
 * Wraps a circular avatar and draws a small online indicator at the bottom-end when [isOnline].
 */
@Composable
fun AvatarWithOnlineIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    indicatorSize: Dp = 10.dp,
    indicatorBorder: Dp = 1.5.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(indicatorSize)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(
                        width = indicatorBorder,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Stacked trio for verified group chats on the chat list.
 * When there are more than three members, shows the three most recently active (by [User.lastPolled] / [User.last_paired])
 * in a compact overlapping cluster (slight vertical fan) — never a single-face placeholder for large groups.
 */
@Composable
fun GroupAvatar(
    members: List<User>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 36.dp,
) {
    if (members.isEmpty()) return
    val topUsers: List<User> = members
        .sortedWith(
            compareByDescending<User> { maxOf(it.lastPolled ?: 0L, it.last_paired ?: 0L) }
                .thenBy { it.name?.lowercase().orEmpty() }
                .thenBy { it.id },
        )
        .take(3)
    val profiles: List<UserProfile> = topUsers.map { it.toUserProfile() }
    val overlap = (avatarSize.value * 0.36f).dp
    val stackWidth = avatarSize + overlap * (profiles.size - 1).coerceAtLeast(0)
    val borderColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = modifier
            .width(stackWidth)
            .height(avatarSize + 4.dp),
    ) {
        profiles.forEachIndexed { index, profile ->
            val verticalNudge = when (profiles.size) {
                1 -> 0.dp
                2 -> if (index == 0) 2.dp else 0.dp
                else -> when (index) {
                    0 -> 3.dp
                    1 -> 0.dp
                    else -> 3.dp
                }
            }
            Surface(
                modifier = Modifier
                    .offset(x = overlap * index, y = verticalNudge)
                    .size(avatarSize)
                    .zIndex(index.toFloat())
                    .align(Alignment.BottomStart),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(2.dp, borderColor),
            ) {
                if (!profile.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size((avatarSize.value * 0.55f).dp),
                        )
                    }
                }
            }
        }
    }
}
