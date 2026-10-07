@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.hapticSendMessage
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret

internal fun chatComposerCanSubmit(
    value: String,
    enabled: Boolean,
    submitGuarded: Boolean,
): Boolean = enabled && value.isNotBlank() && !submitGuarded

/**
 * Shared text/attach/send row for connection and hub chat.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatComposerStrip(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    externallySending: Boolean,
    sendIcon: ImageVector,
    sendContentDescription: String,
    onSend: () -> Unit,
    attachmentMenuExpanded: Boolean,
    onAttachmentMenuExpandedChange: (Boolean) -> Unit,
    attachmentMenuContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    attachBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    attachTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** Long-press on send offers "Send Later" (text only). Null where scheduling isn't supported. */
    onScheduleSend: (() -> Unit)? = null,
) {
    val auxButtonSize = 48.dp
    val attachIconSize = 21.dp
    val sendIconSize = 21.dp
    val fieldCorner = 12.dp
    val composerGap = 8.dp
    val fieldSideInset = auxButtonSize + composerGap
    val sendShape = RoundedCornerShape(fieldCorner)
    val fieldShape = RoundedCornerShape(fieldCorner)

    val attachInteraction = remember { MutableInteractionSource() }
    val sendInteraction = remember { MutableInteractionSource() }
    val fieldInteraction = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    var submitGuarded by remember { mutableStateOf(false) }
    var sendMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(value, submitGuarded) {
        if (submitGuarded && value.isBlank()) {
            submitGuarded = false
            focusRequester.requestFocus()
        }
    }

    val canSend =
        chatComposerCanSubmit(
            value = value,
            enabled = enabled,
            submitGuarded = submitGuarded,
        )
    val textStyle =
        MaterialTheme.typography.bodyMedium.merge(
            TextStyle(
                lineHeightStyle =
                    LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
            ),
        )
    val fieldColors = rememberChatComposerFieldColors()
    val innerVerticalPad = ((auxButtonSize - 24.dp) / 2).coerceIn(6.dp, 12.dp)
    val attachDimmed = !enabled || externallySending

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = auxButtonSize),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = fieldSideInset, end = fieldSideInset)
                    .heightIn(min = auxButtonSize)
                    .align(Alignment.BottomCenter)
                    .focusRequester(focusRequester),
            enabled = enabled,
            textStyle = textStyle.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface)),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.None,
                ),
            singleLine = false,
            minLines = 1,
            maxLines = 10,
            interactionSource = fieldInteraction,
            cursorBrush = SolidColor(PrimaryBlue),
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = false,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = fieldInteraction,
                    placeholder = {
                        androidx.compose.material3.Text(
                            placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    colors = fieldColors,
                    contentPadding =
                        PaddingValues(
                            horizontal = 13.dp,
                            vertical = innerVerticalPad,
                        ),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = false,
                            interactionSource = fieldInteraction,
                            modifier = Modifier,
                            colors = fieldColors,
                            shape = fieldShape,
                        )
                    },
                )
            },
        )

        ChatAttachmentMenuAnchorHost(
            expanded = attachmentMenuExpanded,
            onExpandedChange = onAttachmentMenuExpandedChange,
            anchorSize = auxButtonSize,
            anchorInteraction = attachInteraction,
            anchorEnabled = !attachDimmed,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .zIndex(4f)
                    .focusProperties { canFocus = false },
            anchor = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(attachBackground.copy(alpha = 1f))
                            .border(clickBorderWidth(), clickBorderColor(), CircleShape)
                            .chatSpringPressScale(attachInteraction),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Attach",
                        tint = attachTint.copy(alpha = if (attachDimmed) 0.35f else 1f),
                        modifier = Modifier.size(attachIconSize),
                    )
                }
            },
            menuContent = attachmentMenuContent,
        )

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(auxButtonSize)
                    .zIndex(4f)
                    .focusProperties { canFocus = false }
                    .chatSpringPressScale(sendInteraction)
                    .clip(sendShape)
                    .background(if (canSend) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f))
                    .border(clickBorderWidth(), clickBorderColor(), sendShape)
                    .combinedClickable(
                        interactionSource = sendInteraction,
                        indication = null,
                        enabled = canSend,
                        onClick = {
                            submitGuarded = true
                            hapticSendMessage()
                            onSend()
                        },
                        onLongClick =
                            onScheduleSend?.let {
                                {
                                    PlatformHapticsPolicy.lightImpact()
                                    sendMenuExpanded = true
                                }
                            },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (onScheduleSend != null) {
                DropdownMenu(expanded = sendMenuExpanded, onDismissRequest = { sendMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Send Later") },
                        leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                        onClick = {
                            sendMenuExpanded = false
                            onScheduleSend()
                        },
                    )
                }
            }
            Icon(
                sendIcon,
                contentDescription = sendContentDescription,
                tint =
                    if (canSend) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                modifier = Modifier.size(sendIconSize),
            )
        }
    }
}
