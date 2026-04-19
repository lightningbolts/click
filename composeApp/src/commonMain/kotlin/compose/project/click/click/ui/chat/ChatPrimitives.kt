package compose.project.click.click.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.Message
import compose.project.click.click.ui.theme.LightBlue

/**
 * Leaf composables used inside the chat timeline. Extracted from
 * ConnectionsScreen.kt; no arguments, no state, no side effects —
 * safe to host anywhere in the module.
 */

/** Row with centered label between two thin dividers — e.g. "Today". */
@Composable
internal fun ConversationDaySeparator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )
    }
}

/** Three-dot typing indicator with staggered bounce, matching iMessage-style UX. */
@Composable
internal fun ChatTypingDots() {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val delays = listOf(0, 140, 280)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        delays.forEachIndexed { index, delayMs ->
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, delayMillis = delayMs, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_$index",
            )
            Box(
                modifier = Modifier
                    .offset(y = (-4f * offsetY).dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
            )
        }
    }
}

/**
 * Shimmering rectangular placeholder used for the chat-row subtitle
 * while real metadata is loading.
 */
@Composable
internal fun LoadingSubtitlePlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "connection_subtitle_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connection_subtitle_shimmer_alpha",
    )

    Box(
        modifier = modifier
            .height(12.dp)
            .width(120.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
    )
}

/**
 * Wraps a chat bubble's content in a short fade+slide+scale enter and
 * exit animation keyed off [bubbleStabilityKey] (must stay constant when an
 * optimistic row is reconciled with the server-assigned message id).
 */
@Composable
internal fun AnimatedVisibilityChatBubble(
    bubbleStabilityKey: String,
    isSent: Boolean,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_PARAMETER") val unusedIsSent = isSent
    var visible by remember(bubbleStabilityKey) { mutableStateOf(false) }
    LaunchedEffect(bubbleStabilityKey) {
        visible = true
    }
    val enterFade = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    val enterSlide = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)
    val exitSlide = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(enterFade) +
            slideInVertically(animationSpec = enterSlide, initialOffsetY = { it / 10 }) +
            scaleIn(enterFade, initialScale = 0.97f),
        exit = fadeOut(animationSpec = tween(140)) +
            slideOutVertically(animationSpec = exitSlide, targetOffsetY = { it / 12 }) +
            scaleOut(animationSpec = tween(200), targetScale = 0.96f),
    ) {
        content()
    }
}

/**
 * System-row rendering for an in-chat call log entry (missed,
 * declined, or completed). The label color switches to a red accent
 * for missed calls.
 */
@Composable
internal fun CallLogSystemRow(message: Message) {
    val (label, isMissed) = remember(message.id, message.metadata) { callLogLabel(message) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isMissed) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMissed) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatMessageTime(message.timeCreated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/**
 * iOS: Material [androidx.compose.material3.DropdownMenu] uses a
 * platform popup that can show white bands in dark mode; this surface
 * hosts Voice/Video call action rows with fully themed colors.
 */
@Composable
internal fun ChatCallOptionsIosSurface(
    onVoice: () -> Unit,
    onVideo: () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Surface(
        modifier = Modifier.widthIn(min = 200.dp),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(0.5.dp, outline),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onVoice,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = onSurface)
                Spacer(Modifier.width(12.dp))
                Text("Voice call", style = MaterialTheme.typography.bodyLarge, color = onSurface)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onVideo,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = onSurface)
                Spacer(Modifier.width(12.dp))
                Text("Video call", style = MaterialTheme.typography.bodyLarge, color = onSurface)
            }
        }
    }
}

/**
 * Reply affordance drawn **behind** the bubble; uncovered as the
 * bubble slides (no layout gutter).
 */
@Composable
internal fun ReplySwipeSideIcon(
    hintProgress: Float,
    hintAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val t = hintProgress.coerceIn(0f, 1f)
    val smooth = t * t * (3f - 2f * t)
    val scale = 0.82f + 0.18f * smooth
    val visibility = smooth * (0.28f + 0.72f * smooth).coerceIn(0f, 1f)
    val a = (visibility * hintAlpha).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = a
                scaleX = scale
                scaleY = scale
            }
            .size(40.dp)
            .clip(CircleShape)
            .background(LightBlue.copy(alpha = (0.18f + 0.22f * smooth) * a)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = "Reply",
            tint = LightBlue.copy(alpha = a),
            modifier = Modifier.size(22.dp),
        )
    }
}
