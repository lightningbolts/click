package compose.project.click.click.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.replySnippetForMetadata
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

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
) {
    val messageInput by viewModel.messageInput.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    var attachmentMenuExpanded by remember { mutableStateOf(false) }
    val composerFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var hadSubmitInFlight by remember { mutableStateOf(false) }

    LaunchedEffect(isSending) {
        if (isSending) {
            hadSubmitInFlight = true
            return@LaunchedEffect
        }
        if (!hadSubmitInFlight) return@LaunchedEffect
        hadSubmitInFlight = false
        delay(48)
        composerFocusRequester.requestFocus()
        keyboardController?.show()
    }

    val composerStyle = LocalPlatformStyle.current
    val replyBannerVisible = replyingTo != null && editingMessageId == null
    val auxButtonSize = if (composerStyle.isIOS) 44.dp else 52.dp
    val composerRowVPad = if (composerStyle.isIOS) 6.dp else 8.dp
    val composerRowHPad = 8.dp
    val attachIconSize = if (composerStyle.isIOS) 24.dp else 26.dp
    val sendIconSize = if (composerStyle.isIOS) 22.dp else 20.dp
    val fieldCorner = if (composerStyle.isIOS) 20.dp else 12.dp
    val replyShape = RoundedCornerShape(if (composerStyle.isIOS) 12.dp else 14.dp)
    val composerStripInteraction = remember { MutableInteractionSource() }
    val composerStripBg = Color.Transparent
    val composerInputTextStyle = MaterialTheme.typography.bodyMedium
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(composerStripBg)
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
            Crossfade(
                targetState = replyBannerVisible,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                modifier = Modifier.fillMaxWidth(),
                label = "replyComposerBanner",
            ) { showBanner ->
                if (!showBanner) {
                    Spacer(Modifier.height(0.dp).fillMaxWidth())
                } else {
                    val rt = replyingTo
                    if (rt == null) {
                        Spacer(Modifier.height(0.dp).fillMaxWidth())
                    } else {
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
                    }
                }
            }
            if (replyBannerVisible) {
                Spacer(modifier = Modifier.height(6.dp))
            }
            val composerGap = if (composerStyle.isIOS) 6.dp else 8.dp
            val fieldSideInset = auxButtonSize + composerGap
            val attachTint = PrimaryBlue.copy(alpha = if (isSending) 0.35f else 0.92f)
            val attachInteraction = remember { MutableInteractionSource() }
            val sendInteraction = remember { MutableInteractionSource() }
            val canSend = messageInput.trim().isNotEmpty() && !isSending
            val sendGradient = Brush.linearGradient(
                colors = if (canSend) {
                    listOf(PrimaryBlue, LightBlue)
                } else {
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant,
                    )
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = auxButtonSize),
            ) {
                val composerFieldInteraction = remember { MutableInteractionSource() }
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue.copy(alpha = if (composerStyle.isIOS) 0.50f else 0.65f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (composerStyle.isIOS) 0.08f else 0.12f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (composerStyle.isIOS) 0.30f else 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (composerStyle.isIOS) 0.18f else 0.25f),
                )
                val fieldShape = RoundedCornerShape(fieldCorner)
                val composerTextStyleCentered = composerInputTextStyle.merge(
                    TextStyle(
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
                val approxLineBodyDp = 24.dp
                val innerVerticalPad =
                    ((auxButtonSize - approxLineBodyDp) / 2).coerceIn(6.dp, 12.dp)
                val innerHorizontalPad = 12.dp
                val fieldDecorPadding = PaddingValues(
                    start = innerHorizontalPad,
                    end = innerHorizontalPad,
                    top = innerVerticalPad,
                    bottom = innerVerticalPad,
                )
                BasicTextField(
                    value = messageInput,
                    onValueChange = { viewModel.updateMessageInput(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = fieldSideInset, end = fieldSideInset)
                        .heightIn(min = auxButtonSize)
                        .align(Alignment.BottomCenter)
                        .focusRequester(composerFocusRequester),
                    enabled = true,
                    textStyle = composerTextStyleCentered.merge(
                        TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.None,
                    ),
                    singleLine = false,
                    minLines = 1,
                    maxLines = 10,
                    interactionSource = composerFieldInteraction,
                    cursorBrush = SolidColor(PrimaryBlue),
                    decorationBox = { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = messageInput,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = composerFieldInteraction,
                            placeholder = {
                                Text(
                                    when {
                                        editingMessageId != null -> "Edit message…"
                                        isGroupChat -> "Message the group…"
                                        else -> "Message ${chatDetails.otherUser.name}…"
                                    },
                                    style = composerTextStyleCentered,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            },
                            colors = fieldColors,
                            contentPadding = fieldDecorPadding,
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = true,
                                    isError = false,
                                    interactionSource = composerFieldInteraction,
                                    modifier = Modifier,
                                    colors = fieldColors,
                                    shape = fieldShape,
                                )
                            },
                        )
                    },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(auxButtonSize)
                        .zIndex(4f)
                        .focusProperties { canFocus = false },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = if (isSending) 0.06f else 0.12f))
                            .chatSpringPressScale(attachInteraction)
                            .clickable(
                                interactionSource = attachInteraction,
                                indication = null,
                                enabled = !isSending,
                                onClick = { attachmentMenuExpanded = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Attach",
                            tint = attachTint,
                            modifier = Modifier.size(attachIconSize),
                        )
                    }
                    DropdownMenu(
                        expanded = attachmentMenuExpanded,
                        onDismissRequest = { attachmentMenuExpanded = false },
                        shape = RoundedCornerShape(if (composerStyle.isIOS) 14.dp else 12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = if (composerStyle.isIOS) {
                            BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            )
                        } else {
                            null
                        },
                        tonalElevation = if (composerStyle.isIOS) 0.dp else 4.dp,
                        shadowElevation = if (composerStyle.isIOS) 0.dp else 8.dp,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Photo library",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = PrimaryBlue.copy(alpha = 0.9f),
                                )
                            },
                            onClick = {
                                attachmentMenuExpanded = false
                                mediaPickers.openPhotoLibrary()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Take photo",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = PrimaryBlue.copy(alpha = 0.9f),
                                )
                            },
                            onClick = {
                                attachmentMenuExpanded = false
                                mediaPickers.openCamera()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Voice message",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Mic,
                                    contentDescription = null,
                                    tint = PrimaryBlue.copy(alpha = 0.9f),
                                )
                            },
                            onClick = {
                                attachmentMenuExpanded = false
                                mediaPickers.openVoiceRecorder()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "File",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.AttachFile,
                                    contentDescription = null,
                                    tint = PrimaryBlue.copy(alpha = 0.9f),
                                )
                            },
                            onClick = {
                                attachmentMenuExpanded = false
                                mediaPickers.openFilePicker()
                            },
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(auxButtonSize)
                        .zIndex(4f)
                        .focusProperties { canFocus = false }
                        .chatSpringPressScale(sendInteraction)
                        .clip(if (composerStyle.isIOS) CircleShape else RoundedCornerShape(fieldCorner))
                        .background(sendGradient)
                        .clickable(
                            interactionSource = sendInteraction,
                            indication = null,
                            enabled = canSend,
                            onClick = { viewModel.sendMessage() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (editingMessageId != null) {
                            Icons.Filled.Check
                        } else {
                            Icons.AutoMirrored.Filled.Send
                        },
                        contentDescription = if (editingMessageId != null) "Confirm edit" else "Send",
                        tint = if (canSend) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(sendIconSize),
                    )
                }
            }
        }
    }
}
