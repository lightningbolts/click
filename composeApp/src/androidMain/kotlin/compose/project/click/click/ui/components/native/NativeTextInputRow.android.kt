package compose.project.click.click.ui.components.native

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.chat.rememberChatComposerFieldColors
import compose.project.click.click.ui.theme.PrimaryBlue

@Composable
actual fun NativeTextInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String,
    enabled: Boolean,
    singleLine: Boolean,
    maxLines: Int,
    keyboardOptions: KeyboardOptions,
    focusRequester: FocusRequester?,
) {
    val fieldColors = rememberChatComposerFieldColors()
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val textStyle = MaterialTheme.typography.bodyMedium.merge(
        TextStyle(color = MaterialTheme.colorScheme.onSurface),
    )
    val fieldShape = RoundedCornerShape(12.dp)
    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(focusModifier)
            .heightIn(min = 44.dp),
        enabled = enabled,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        maxLines = maxLines,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(PrimaryBlue),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                colors = fieldColors,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        modifier = Modifier,
                        colors = fieldColors,
                        shape = fieldShape,
                    )
                },
            )
        },
    )
}
