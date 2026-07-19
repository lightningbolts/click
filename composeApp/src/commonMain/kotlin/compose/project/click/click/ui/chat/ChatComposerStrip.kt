package compose.project.click.click.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.theme.clickBorderColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun chatComposerCanSubmit(
    value: String,
    enabled: Boolean,
    externallySending: Boolean,
    submitGuarded: Boolean,
): Boolean = enabled && value.isNotBlank() && !externallySending && !submitGuarded

/**
 * Shared text/attach/send row for connection and hub chat.
 *
 * [submitGuarded] bridges the frame between a tap and the ViewModel's sending state update, so a
 * double tap cannot enqueue the same draft twice. The field pulse is visual only: [onSend] still
 * runs synchronously, preserving optimistic append and cache behavior.
 */
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
    attachBackground: Color = MaterialTheme.colorScheme.primaryContainer,
    attachTint: Color = PrimaryBlue,
) {
    val composerStyle = LocalPlatformStyle.current
    val auxButtonSize = if (composerStyle.isIOS) 44.dp else 52.dp
    val attachIconSize = if (composerStyle.isIOS) 24.dp else 26.dp
    val sendIconSize = if (composerStyle.isIOS) 22.dp else 20.dp
    val fieldCorner = if (composerStyle.isIOS) 20.dp else 12.dp
    val composerGap = if (composerStyle.isIOS) 6.dp else 8.dp
    val fieldSideInset = auxButtonSize + composerGap
    val sendShape = if (composerStyle.isIOS) CircleShape else RoundedCornerShape(fieldCorner)
    val fieldShape = RoundedCornerShape(fieldCorner)

    val attachInteraction = remember { MutableInteractionSource() }
    val sendInteraction = remember { MutableInteractionSource() }
    val fieldInteraction = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val fieldPulse = remember { Animatable(1f) }
    var submitGuarded by remember { mutableStateOf(false) }

    LaunchedEffect(externallySending, submitGuarded) {
        if (submitGuarded && !externallySending) {
            delay(180)
            submitGuarded = false
            focusRequester.requestFocus()
        }
    }

    val canSend = chatComposerCanSubmit(
        value = value,
        enabled = enabled,
        externallySending = externallySending,
        submitGuarded = submitGuarded,
    )
    val showSending = externallySending || submitGuarded
    val textStyle = MaterialTheme.typography.bodyMedium.merge(
        TextStyle(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
    val fieldColors = rememberChatComposerFieldColors()
    val innerVerticalPad = ((auxButtonSize - 24.dp) / 2).coerceIn(6.dp, 12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = auxButtonSize),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = fieldSideInset, end = fieldSideInset)
                .heightIn(min = auxButtonSize)
                .align(Alignment.BottomCenter)
                .focusRequester(focusRequester)
                .graphicsLayer {
                    alpha = fieldPulse.value
                    scaleX = 0.985f + (0.015f * fieldPulse.value)
                },
            enabled = enabled,
            textStyle = textStyle.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface)),
            keyboardOptions = KeyboardOptions(
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
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
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
            anchorEnabled = enabled && !showSending,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .zIndex(4f)
                .focusProperties { canFocus = false },
            anchor = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(attachBackground)
                        .border(2.dp, clickBorderColor(), CircleShape)
                        .chatSpringPressScale(attachInteraction),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Attach",
                        tint = attachTint.copy(alpha = if (enabled && !showSending) 1f else 0.35f),
                        modifier = Modifier.size(attachIconSize),
                    )
                }
            },
            menuContent = attachmentMenuContent,
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(auxButtonSize)
                .zIndex(4f)
                .focusProperties { canFocus = false }
                .chatSpringPressScale(sendInteraction)
                .clip(sendShape)
                .background(if (canSend || showSending) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, clickBorderColor(), sendShape)
                .clickable(
                    interactionSource = sendInteraction,
                    indication = null,
                    enabled = canSend,
                    onClick = {
                        submitGuarded = true
                        PlatformHapticsPolicy.lightImpact()
                        onSend()
                        scope.launch {
                            fieldPulse.snapTo(0.62f)
                            fieldPulse.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(170, easing = FastOutSlowInEasing),
                            )
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = showSending,
                animationSpec = tween(120),
                label = "chatSendState",
            ) { sending ->
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(sendIconSize),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Icon(
                        sendIcon,
                        contentDescription = sendContentDescription,
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
