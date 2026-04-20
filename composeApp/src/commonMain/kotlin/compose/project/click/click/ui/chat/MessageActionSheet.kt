package compose.project.click.click.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.copyableText
import compose.project.click.click.data.models.isEncryptedMedia
import compose.project.click.click.data.models.mediaUrlOrNull
import compose.project.click.click.data.models.originalMimeTypeOrNull
import compose.project.click.click.ui.components.EmojiCatalog
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Bottom sheet that appears when a user long-presses a message.
 * Shows reply action, emoji reactions strip with full-catalog picker,
 * optional image download, copy, and (for sent messages) edit +
 * two-step delete confirmation.
 *
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionSheet(
    messageWithUser: MessageWithUser,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
) {
    val isSent = messageWithUser.isSent
    val message = messageWithUser.message

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showDeleteMessageConfirm by remember { mutableStateOf(false) }
    var showDeleteMessageFinalConfirm by remember { mutableStateOf(false) }
    var emojiPickMode by remember { mutableStateOf(false) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        LaunchedEffect(Unit) {
            PlatformHapticsPolicy.lightImpact()
        }
        val sheetBg = MaterialTheme.colorScheme.surfaceContainerHigh
        val onSurface = MaterialTheme.colorScheme.onSurface
        val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(sheetBg)
                .padding(bottom = 32.dp),
        ) {
            if (emojiPickMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { emojiPickMode = false }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = onSurface,
                        )
                    }
                    Text(
                        "Choose emoji",
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(44.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(EmojiCatalog.all, key = { it }) { em ->
                        Text(
                            text = em,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clickable {
                                    PlatformHapticsPolicy.lightImpact()
                                    viewModel.addReaction(message.id, em)
                                    dismiss()
                                }
                                .padding(8.dp),
                        )
                    }
                }
            } else {
                ListItem(
                    headlineContent = {
                        Text("Reply", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = PrimaryBlue,
                        )
                    },
                    modifier = Modifier.clickable {
                        if (message.messageType != "call_log") {
                            viewModel.startReplyTo(messageWithUser)
                            dismiss()
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "😡")
                    emojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .clickable {
                                    PlatformHapticsPolicy.lightImpact()
                                    viewModel.addReaction(message.id, emoji)
                                    dismiss()
                                }
                                .padding(8.dp),
                        )
                    }
                }

                TextButton(
                    onClick = { emojiPickMode = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("More emojis…", color = PrimaryBlue)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

                val imageUrl = message.mediaUrlOrNull()
                if (message.messageType.lowercase() == ChatMessageType.IMAGE && imageUrl != null) {
                    ListItem(
                        headlineContent = {
                            Text("Save to gallery", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = "Save to gallery",
                                tint = PrimaryBlue,
                            )
                        },
                        modifier = Modifier.clickable {
                            scope.launch {
                                if (message.isEncryptedMedia()) {
                                    val bytes = viewModel.fetchDecryptedChatMediaBytes(message)
                                    if (bytes != null) {
                                        saveChatImageToGallery(
                                            imageUrl = imageUrl,
                                            decryptedImageBytes = bytes,
                                            mimeTypeHint = message.originalMimeTypeOrNull(),
                                        ).onSuccess { dismiss() }
                                    }
                                } else {
                                    saveChatImageToGallery(imageUrl).onSuccess { dismiss() }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    if (message.isEncryptedMedia()) {
                        ListItem(
                            headlineContent = {
                                Text("Share image", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share image",
                                    tint = PrimaryBlue,
                                )
                            },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val bytes = viewModel.fetchDecryptedChatMediaBytes(message)
                                    if (bytes != null) {
                                        val ext = when {
                                            message.originalMimeTypeOrNull()?.contains("png", ignoreCase = true) == true -> "png"
                                            message.originalMimeTypeOrNull()?.contains("webp", ignoreCase = true) == true -> "webp"
                                            else -> "jpg"
                                        }
                                        shareDecryptedImage(bytes, "click_chat.$ext")
                                        dismiss()
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }

                ListItem(
                    headlineContent = {
                        Text(
                            if (message.messageType.lowercase() == ChatMessageType.IMAGE) {
                                "Copy caption & link"
                            } else {
                                "Copy"
                            },
                            color = onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = onVariant,
                        )
                    },
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(message.copyableText()))
                        dismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                if (isSent) {
                    ListItem(
                        headlineContent = {
                            Text("Edit", color = onSurface, style = MaterialTheme.typography.bodyLarge)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit message",
                                tint = PrimaryBlue,
                            )
                        },
                        modifier = Modifier.clickable {
                            viewModel.startEditMessage(message.id, message.content)
                            dismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    ListItem(
                        headlineContent = {
                            Text("Delete", color = Color(0xFFFF4444), style = MaterialTheme.typography.bodyLarge)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete message",
                                tint = Color(0xFFFF4444),
                            )
                        },
                        modifier = Modifier.clickable {
                            showDeleteMessageConfirm = true
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }

    if (showDeleteMessageConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteMessageConfirm = false },
            title = { Text("Delete Message?") },
            text = { Text("This message will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteMessageConfirm = false
                        showDeleteMessageFinalConfirm = true
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMessageConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDeleteMessageFinalConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteMessageFinalConfirm = false },
            title = { Text("Delete Message Permanently?") },
            text = { Text("This action is permanent and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessage(message.id)
                        showDeleteMessageFinalConfirm = false
                        dismiss()
                    },
                ) {
                    Text("Yes, Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMessageFinalConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
