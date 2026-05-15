package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.UserProfile
import compose.project.click.click.data.models.toUserProfile

private val OnlineGreen = Color(0xFF22C55E)

/** Saturated backgrounds that keep white initials readable. */
private val PlaceholderAvatarColors = listOf(
    Color(0xFF4F46E5),
    Color(0xFF7C3AED),
    Color(0xFF0D9488),
    Color(0xFF2563EB),
    Color(0xFFBE185D),
    Color(0xFFB45309),
    Color(0xFF0F766E),
    Color(0xFF4338CA),
    Color(0xFF15803D),
    Color(0xFF92400E),
)

/**
 * Two-letter style initials for list avatars ("Alex Smith" → "AS", single token → up to two chars).
 */
fun initialsForAvatar(displayName: String?, email: String? = null): String {
    val primary = displayName?.trim()?.takeIf { it.isNotEmpty() }
    val source = primary
        ?: email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
        ?: return "?"
    val parts = source.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> {
            val a = parts.first().firstOrNull()?.uppercaseChar() ?: return "?"
            val b = parts.last().firstOrNull()?.uppercaseChar() ?: return "$a"
            "$a$b"
        }
        else -> {
            val p = parts.first()
            when {
                p.length >= 2 -> p.take(2).uppercase()
                else -> p.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            }
        }
    }
}

fun stableAvatarPlaceholderColor(seed: String): Color {
    if (seed.isBlank()) return PlaceholderAvatarColors.first()
    val h = seed.fold(0) { acc, ch -> 31 * acc + ch.code }
    return PlaceholderAvatarColors[(h and Int.MAX_VALUE) % PlaceholderAvatarColors.size]
}

/**
 * Circular face: loads [avatarUrl] on a background thread via Coil; on null/error or before load,
 * shows [initialsForAvatar] on a stable color from [userId].
 */
@Composable
fun ConnectionListUserAvatarFace(
    displayName: String?,
    email: String?,
    avatarUrl: String?,
    userId: String,
    modifier: Modifier = Modifier,
    useCompactTypography: Boolean = false,
) {
    val trimmedUrl = avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
    val initials = remember(displayName, email) { initialsForAvatar(displayName, email) }
    val bg = remember(userId) { stableAvatarPlaceholderColor(userId) }
    var imageReady by remember(trimmedUrl, userId) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                style = if (useCompactTypography) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            )
        }
        if (trimmedUrl != null) {
            AsyncImage(
                model = trimmedUrl,
                contentDescription = displayName?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it profile photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .alpha(if (imageReady) 1f else 0f),
                onSuccess = { imageReady = true },
                onError = { imageReady = false },
            )
        }
    }
}

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
 * Stacked cluster for verified group chats on the chat list. Overflow is rendered as a
 * bounded "+N" circle so text never overlaps neighboring faces.
 */
@Composable
fun GroupAvatar(
    members: List<User>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 36.dp,
) {
    if (members.isEmpty()) return
    val sortedUsers: List<User> = members
        .sortedWith(
            compareByDescending<User> { maxOf(it.lastPolled ?: 0L, it.last_paired ?: 0L) }
                .thenBy { it.name?.lowercase().orEmpty() }
                .thenBy { it.id },
        )
    val extraCount = (sortedUsers.size - 2).coerceAtLeast(0)
    val visibleUsers = if (extraCount > 0) sortedUsers.take(2) else sortedUsers.take(3)
    val stackCount = visibleUsers.size + if (extraCount > 0) 1 else 0
    val overlap = (avatarSize.value * 0.36f).dp
    val stackWidth = avatarSize + overlap * (stackCount - 1).coerceAtLeast(0)
    val ringColor = MaterialTheme.colorScheme.background
    val overflowDiameter = 40.dp
    Box(
        modifier = modifier
            .width(stackWidth)
            .height(avatarSize + 4.dp),
    ) {
        visibleUsers.forEachIndexed { index, member ->
            val profile: UserProfile = member.toUserProfile()
            val verticalNudge = when (stackCount) {
                1 -> 0.dp
                2 -> if (index == 0) 2.dp else 0.dp
                else -> when (index) {
                    0 -> 3.dp
                    1 -> 0.dp
                    else -> 3.dp
                }
            }
            Box(
                modifier = Modifier
                    .offset(x = overlap * index, y = verticalNudge)
                    .size(avatarSize)
                    .zIndex(index.toFloat())
                    .align(Alignment.BottomStart),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, ringColor, CircleShape),
                ) {
                    ConnectionListUserAvatarFace(
                        displayName = profile.displayName,
                        email = member.email,
                        avatarUrl = profile.avatarUrl,
                        userId = profile.id,
                        modifier = Modifier.fillMaxSize(),
                        useCompactTypography = avatarSize <= 38.dp,
                    )
                }
            }
        }
        if (extraCount > 0) {
            val index = visibleUsers.size
            Box(
                modifier = Modifier
                    .offset(x = overlap * index, y = 3.dp)
                    .size(overflowDiameter)
                    .zIndex(100f + index.toFloat())
                    .align(Alignment.BottomStart),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(2.dp, ringColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+$extraCount",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
