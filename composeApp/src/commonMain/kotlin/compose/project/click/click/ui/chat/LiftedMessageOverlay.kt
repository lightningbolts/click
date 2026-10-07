@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.copyableText // pragma: allowlist secret
import compose.project.click.click.data.models.hubMediaPathOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.previewLabel // pragma: allowlist secret
import compose.project.click.click.data.models.replyQuoteText // pragma: allowlist secret
import compose.project.click.click.data.models.replyRef // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import kotlinx.coroutines.launch

/** Where each visible bubble sits on screen, recorded as it lays out; read when a long-press lifts it. */
internal object MessageBubbleBounds {
    private val bounds = HashMap<String, Rect>()

    fun put(
        messageId: String,
        rect: Rect,
    ) {
        // Only on-screen bubbles matter; drop old entries rather than grow with long chats.
        if (bounds.size > MAX_TRACKED && messageId !in bounds) bounds.clear()
        bounds[messageId] = rect
    }

    private const val MAX_TRACKED = 400

    fun get(messageId: String): Rect? = bounds[messageId]

    fun clear() = bounds.clear()
}

/** Quick reactions in the capsule, same set as the sheet. */
internal val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "😡")

/**
 * Where the lifted stack starts (px from the top): at the bubble when it fits between the capsule
 * above and the action panel below, otherwise pushed back on screen.
 */
internal fun liftedStackTop(
    bubbleTop: Float,
    capsuleHeight: Float,
    bubbleHeight: Float,
    panelHeight: Float,
    screenHeight: Float,
    margin: Float,
): Float {
    val desired = bubbleTop - capsuleHeight
    val maxTop = screenHeight - margin - (capsuleHeight + bubbleHeight + panelHeight)
    return desired.coerceAtMost(maxTop).coerceAtLeast(margin)
}

/**
 * iOS-style long-press (04 §8): the pressed bubble lifts in place over a scrim, with a reaction
 * capsule above it and the actions below. Actions and their rules match [MessageActionSheet].
 */
@Composable
internal fun LiftedMessageOverlay(
    messageWithUser: MessageWithUser,
    replyTarget: Message?,
    capabilities: MessageActionCapabilities,
    handlers: MessageActionHandlers,
    onMoreEmoji: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = messageWithUser.message
    val bounds = remember(message.id) { MessageBubbleBounds.get(message.id) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val lift by animateFloatAsState(if (appeared) 1f else 0f, tween(160), label = "lift")

    fun done(action: () -> Unit) {
        action()
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * lift))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            val screenHeightPx = with(density) { maxHeight.toPx() }
            val bubbleWidth: Dp = with(density) { (bounds?.width ?: 0f).toDp() }.coerceIn(120.dp, maxWidth - 32.dp)
            val bubbleHeightPx = (bounds?.height ?: with(density) { 64.dp.toPx() }).coerceAtMost(screenHeightPx * 0.4f)
            val capsulePx = with(density) { 64.dp.toPx() }
            val actionRows = liftedActionCount(message, capabilities)
            val panelPx = with(density) { (actionRows * 48 + 16).dp.toPx() }
            val top =
                liftedStackTop(
                    bubbleTop = bounds?.top ?: (screenHeightPx / 3f),
                    capsuleHeight = capsulePx,
                    bubbleHeight = bubbleHeightPx,
                    panelHeight = panelPx,
                    screenHeight = screenHeightPx,
                    margin = with(density) { 48.dp.toPx() },
                )
            val alignEnd = messageWithUser.isSent
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, top.toInt()) }
                        .padding(horizontal = 16.dp)
                        .alpha(lift),
                horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (capabilities.canReact) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            QUICK_REACTIONS.forEach { emoji ->
                                Text(
                                    emoji,
                                    fontSize = 26.sp,
                                    modifier =
                                        Modifier
                                            .clickable {
                                                PlatformHapticsPolicy.lightImpact()
                                                done { handlers.onReact(message.id, emoji) }
                                            }.padding(6.dp),
                                )
                            }
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                        .clickable(onClickLabel = "More emoji", onClick = onMoreEmoji),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "More emoji")
                            }
                        }
                    }
                }
                LiftedBubbleCopy(
                    message = message,
                    replyTarget = replyTarget,
                    isSent = alignEnd,
                    width = bubbleWidth,
                    modifier = Modifier.scale(0.96f + 0.04f * lift),
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.width(240.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        if (capabilities.canReply) {
                            LiftedAction(
                                "Reply",
                                Icons.AutoMirrored.Filled.Reply,
                            ) { done { handlers.onReply(messageWithUser) } }
                        }
                        if (capabilities.canForward) {
                            LiftedAction(
                                "Forward",
                                Icons.AutoMirrored.Filled.Forward,
                            ) { done { handlers.onForward(messageWithUser) } }
                        }
                        if (capabilities.canCopy) {
                            LiftedAction(
                                "Copy",
                                Icons.Default.ContentCopy,
                            ) { done { clipboard.setText(AnnotatedString(message.copyableText())) } }
                        }
                        if (capabilities.canEdit) LiftedAction("Edit", Icons.Default.Edit) { done { handlers.onEdit(messageWithUser) } }
                        if (isSavableImage(message) && capabilities.canSaveMedia) {
                            LiftedAction("Save", Icons.Outlined.Save) {
                                scope.launch { if (saveMessageImage(message, handlers)) onDismiss() }
                            }
                        }
                        if (isSavableImage(message) && capabilities.canShareMedia && message.isEncryptedMedia()) {
                            LiftedAction("Share", Icons.Outlined.Share) {
                                scope.launch { if (shareMessageImage(message, handlers)) onDismiss() }
                            }
                        }
                        if (capabilities.canRetry) {
                            LiftedAction(
                                "Retry sending",
                                Icons.Default.Refresh,
                            ) { done { handlers.onRetry(messageWithUser) } }
                        }
                        if (capabilities.canDiscard) {
                            LiftedAction(
                                "Discard",
                                Icons.Default.Delete,
                                destructive = true,
                            ) { done { handlers.onDiscard(messageWithUser) } }
                        }
                        if (capabilities.canDelete) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            LiftedAction("Delete", Icons.Default.Delete, destructive = true) { confirmDelete = true }
                        }
                    }
                }
            }
        }
        if (confirmDelete) {
            GlassAlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete Message?") },
                text = { Text("This message will be permanently deleted. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        done { handlers.onDelete(messageWithUser) }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

private fun isSavableImage(message: Message): Boolean =
    message.messageType.lowercase() == ChatMessageType.IMAGE && (message.mediaUrlOrNull() ?: message.hubMediaPathOrNull()) != null

/** Rows the panel will show, for placement before it lays out. */
internal fun liftedActionCount(
    message: Message,
    c: MessageActionCapabilities,
): Int =
    listOf(
        c.canReply,
        c.canForward,
        c.canCopy,
        c.canEdit,
        isSavableImage(message) && c.canSaveMedia,
        isSavableImage(message) && c.canShareMedia && message.isEncryptedMedia(),
        c.canRetry,
        c.canDiscard,
        c.canDelete,
    ).count { it }

@Composable
private fun LiftedAction(
    title: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) Color(0xFFFF4444) else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = tint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** A light copy of the pressed bubble: reply quote (kept visible, as on iOS) and the message text. */
@Composable
private fun LiftedBubbleCopy(
    message: Message,
    replyTarget: Message?,
    isSent: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val bg = if (isSent) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isSent) Color.White else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(min = 80.dp, max = width),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            message.replyRef()?.let { ref ->
                Text(
                    replyQuoteText(ref, replyTarget),
                    color = fg.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .background(fg.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            val body =
                when (message.messageType.lowercase()) {
                    ChatMessageType.TEXT, "" -> message.content
                    else -> message.previewLabel()
                }
            Text(body, color = fg, style = MaterialTheme.typography.bodyLarge, maxLines = 12, overflow = TextOverflow.Ellipsis)
        }
    }
}
