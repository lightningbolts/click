package compose.project.click.click.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.replySnippetForMetadata
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.utils.toImageBitmap // pragma: allowlist secret
import compose.project.click.click.viewmodel.CHAT_STAGED_MEDIA_MAX // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret

/**
 * Message composer strip for the chat screen: reply banner, text
 * field with platform-aware styling, attachment menu (photo library,
 * take photo, voice message), and send/confirm-edit button.
 *
 * Isolates [ChatViewModel.messageInput] from the message list. IME insets are
 * applied by the chat screen chrome so this composable stays layout-stable.
 */
@Composable
internal fun ConnectionChatMessageComposer(
    viewModel: ChatViewModel,
    chatDetails: ChatWithDetails,
    isGroupChat: Boolean,
    editingMessageId: String?,
    replyingTo: MessageWithUser?,
    mediaPickers: ChatMediaPickerHandles,
    onOpenDisposableRoll: () -> Unit = {},
    tetherPingEnabled: Boolean = false,
    pingTetherLoading: Boolean = false,
    onPingTether: () -> Unit = {},
) {
    val messageInput by viewModel.messageInput.collectAsState()
    val messageSendError by viewModel.messageSendError.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val stagedChatImages by viewModel.stagedChatImages.collectAsState()
    var attachmentMenuExpanded by remember { mutableStateOf(false) }

    val composerStyle = LocalPlatformStyle.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val replyBannerVisible = replyingTo != null && editingMessageId == null
    // Keep last target so AnimatedVisibility can exit after clearReplyTarget() nulls [replyingTo].
    var replyBannerContent by remember { mutableStateOf(replyingTo) }
    if (replyingTo != null && editingMessageId == null) {
        replyBannerContent = replyingTo
    }
    val composerRowVPad = if (composerStyle.isIOS) 6.dp else 8.dp
    val composerRowHPad = ChatChromeHorizontalPadding
    val replyShape = RoundedCornerShape(if (composerStyle.isIOS) 12.dp else 14.dp)
    val composerStripInteraction = remember { MutableInteractionSource() }
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable(
                    indication = null,
                    interactionSource = composerStripInteraction,
                ) {},
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = composerRowHPad, vertical = composerRowVPad),
        ) {
            AnimatedVisibility(
                visible = replyBannerVisible,
                enter = expandVertically(
                    animationSpec = tween(340, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Bottom,
                ) + fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(
                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Bottom,
                ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                label = "replyComposerBanner",
            ) {
                val rt = replyBannerContent
                if (rt != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = replyShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = if (composerStyle.isIOS) 0.45f else 0.55f,
                            ),
                            border = if (composerStyle.isIOS) {
                                BorderStroke(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                )
                            } else {
                                null
                            },
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Replying to ${rt.user.name ?: "message"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        replySnippetForMetadata(rt.message.content, maxLen = 100),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.clearReplyTarget() },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Cancel reply",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
            if (stagedChatImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(stagedChatImages, key = { it.id }) { item ->
                            val thumb: ImageBitmap? = remember(item.id, item.bytes) {
                                runCatching { item.bytes.toImageBitmap() }.getOrNull()
                            }
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.52f))
                                        .clickable { viewModel.removeStagedMedia(item.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            PlatformHapticsPolicy.lightImpact()
                            viewModel.commitStagedMediaToUpload()
                        },
                        enabled = !isSending,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    ) {
                        Text("Send (${stagedChatImages.size})")
                    }
                }
                Text(
                    text = "Up to $CHAT_STAGED_MEDIA_MAX photos per batch",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            messageSendError?.let { err ->
                Text(
                    text = if ("saved on this device" in err) {
                        err
                    } else {
                        "$err · Review and tap send to retry"
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }
            val attachTint = PrimaryBlue.copy(alpha = 0.92f)
            ChatComposerStrip(
                value = messageInput,
                onValueChange = viewModel::updateMessageInput,
                placeholder = when {
                    editingMessageId != null -> "Edit message…"
                    isGroupChat -> "Message the group…"
                    else -> "Message ${chatDetails.otherUser.name}…"
                },
                enabled = true,
                externallySending = isSending,
                sendIcon = if (editingMessageId != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                sendContentDescription = if (editingMessageId != null) "Confirm edit" else "Send",
                onSend = viewModel::sendMessage,
                attachmentMenuExpanded = attachmentMenuExpanded,
                onAttachmentMenuExpandedChange = { attachmentMenuExpanded = it },
                attachBackground = PrimaryBlue.copy(alpha = if (isSending) 0.12f else 0.24f),
                attachTint = attachTint,
                attachmentMenuContent = {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            ChatAttachmentMenuRow(
                                label = "Click Drops",
                                icon = Icons.Filled.PhotoCamera,
                                onClick = {
                                    PlatformHapticsPolicy.heavyImpact()
                                    PlatformHapticsPolicy.successNotification()
                                    attachmentMenuExpanded = false
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onOpenDisposableRoll()
                                },
                            )
                            if (tetherPingEnabled) {
                                ChatAttachmentMenuRow(
                                    label = "Ping Tether",
                                    icon = Icons.Filled.Explore,
                                    enabled = !pingTetherLoading,
                                    onClick = {
                                        if (pingTetherLoading) return@ChatAttachmentMenuRow
                                        PlatformHapticsPolicy.successNotification()
                                        attachmentMenuExpanded = false
                                        onPingTether()
                                    },
                                )
                            }
                            ChatAttachmentMenuRow(
                                label = "Photo library",
                                icon = Icons.Outlined.Image,
                                onClick = {
                                    PlatformHapticsPolicy.heavyImpact()
                                    attachmentMenuExpanded = false
                                    mediaPickers.openPhotoLibrary()
                                },
                            )
                            ChatAttachmentMenuRow(
                                label = "Take photo",
                                icon = Icons.Outlined.PhotoCamera,
                                onClick = {
                                    PlatformHapticsPolicy.heavyImpact()
                                    attachmentMenuExpanded = false
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    mediaPickers.openCamera()
                                },
                            )
                            ChatAttachmentMenuRow(
                                label = "Voice message",
                                icon = Icons.Outlined.Mic,
                                onClick = {
                                    PlatformHapticsPolicy.heavyImpact()
                                    attachmentMenuExpanded = false
                                    mediaPickers.openVoiceRecorder()
                                },
                            )
                            ChatAttachmentMenuRow(
                                label = "File",
                                icon = Icons.Outlined.AttachFile,
                                onClick = {
                                    PlatformHapticsPolicy.heavyImpact()
                                    attachmentMenuExpanded = false
                                    mediaPickers.openFilePicker()
                                },
                            )
                    }
                },
            )
        }
    }
}
