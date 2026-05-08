package compose.project.click.click.ui.chat // pragma: allowlist secret

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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.copyableText // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.originalMimeTypeOrNull // pragma: allowlist secret
import compose.project.click.click.ui.components.EmojiCatalog // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassModalBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.BentoGlassOptionRow // pragma: allowlist secret
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

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
    ) {
        LaunchedEffect(Unit) {
            PlatformHapticsPolicy.lightImpact()
        }
        val sheetBg = GlassSheetTokens.OledBlack
        val onSurface = GlassSheetTokens.OnOled
        val onVariant = GlassSheetTokens.OnOledMuted
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
                val optionRadius = GlassSheetTokens.BentoExteriorCorner
                BentoGlassOptionRow(
                    title = "Reply",
                    onClick = {
                        if (message.messageType != "call_log") {
                            viewModel.startReplyTo(messageWithUser)
                            dismiss()
                        }
                    },
                    cornerRadius = optionRadius,
                    showBorder = false,
                    leading = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = PrimaryBlue,
                        )
                    },
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

                HorizontalDivider(color = GlassSheetTokens.GlassBorder.copy(alpha = 0.35f))

                val imageUrl = message.mediaUrlOrNull()
                if (message.messageType.lowercase() == ChatMessageType.IMAGE && imageUrl != null) {
                    BentoGlassOptionRow(
                        title = "Save to gallery",
                        onClick = {
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
                        cornerRadius = optionRadius,
                        showBorder = false,
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = "Save to gallery",
                                tint = PrimaryBlue,
                            )
                        },
                    )
                    if (message.isEncryptedMedia()) {
                        BentoGlassOptionRow(
                            title = "Share image",
                            onClick = {
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
                            cornerRadius = optionRadius,
                            showBorder = false,
                            leading = {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share image",
                                    tint = PrimaryBlue,
                                )
                            },
                        )
                    }
                }

                BentoGlassOptionRow(
                    title = if (message.messageType.lowercase() == ChatMessageType.IMAGE) {
                        "Copy caption & link"
                    } else {
                        "Copy"
                    },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.copyableText()))
                        dismiss()
                    },
                    cornerRadius = optionRadius,
                    showBorder = false,
                    leading = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = onVariant,
                        )
                    },
                )

                if (isSent) {
                    BentoGlassOptionRow(
                        title = "Edit",
                        onClick = {
                            viewModel.startEditMessage(message.id, message.content)
                            dismiss()
                        },
                        cornerRadius = optionRadius,
                        showBorder = false,
                        leading = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit message",
                                tint = PrimaryBlue,
                            )
                        },
                    )

                    BentoGlassOptionRow(
                        title = "Delete",
                        onClick = { showDeleteMessageConfirm = true },
                        destructive = true,
                        cornerRadius = optionRadius,
                        showBorder = false,
                        leading = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete message",
                                tint = Color(0xFFFF4444),
                            )
                        },
                    )
                }
            }
        }
    }

    if (showDeleteMessageConfirm) {
        GlassAlertDialog(
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
        GlassAlertDialog(
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
