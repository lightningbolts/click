@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickTextFieldTextStyle // pragma: allowlist secret

/** Shared radii / heights so search, form, and drop fields match. */
object ClickFieldTokens {
    val CornerRadius = 16.dp
    val CompactCornerRadius = 14.dp
    val SearchHeight = 56.dp
    val SingleLineMinHeight = 56.dp
    val MultilineMinHeight = 112.dp
    val Shape = RoundedCornerShape(CornerRadius)
}

/** Alias of [ClickFieldTokens.SingleLineMinHeight] for existing search/NFC call sites. */
val ClickTextFieldMinHeight: Dp = ClickFieldTokens.SingleLineMinHeight

private val ClickTextFieldContentPadding =
    PaddingValues(
        start = 16.dp,
        top = 14.dp,
        end = 16.dp,
        bottom = 14.dp,
    )

private val ClickMultilineContentPadding =
    PaddingValues(
        start = 16.dp,
        top = 12.dp,
        end = 16.dp,
        bottom = 12.dp,
    )

/**
 * App outlined text field with field-safe typography, 16dp corners, and quiet 1dp borders.
 * Single-line text is vertically centered; multiline text and the caret stay on the first line.
 *
 * Prefer [placeholderText] over the [placeholder] slot for plain hint strings: a wrapping
 * placeholder grows the decoration box past [minHeight], which pushes the vertically-centered
 * single-line caret to the middle of a double-height field. Long hints ellipsize instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = clickTextFieldTextStyle(),
    label: @Composable (() -> Unit)? = null,
    /** Plain hint text, rendered as one ellipsized line when [singleLine] is true. */
    placeholderText: String? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = if (singleLine) 1 else 3,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = ClickFieldTokens.Shape,
    colors: TextFieldColors = clickFieldColors(),
    minHeight: Dp = if (singleLine) ClickFieldTokens.SingleLineMinHeight else ClickFieldTokens.MultilineMinHeight,
) {
    val fieldInteraction = interactionSource ?: remember { MutableInteractionSource() }
    val textColor =
        textStyle.color.takeOrElse {
            MaterialTheme.colorScheme.onSurface
        }
    val mergedTextStyle =
        LocalTextStyle.current.merge(textStyle).merge(
            TextStyle(color = textColor),
        )
    val contentPadding =
        if (singleLine && label == null) {
            ClickTextFieldContentPadding
        } else if (!singleLine) {
            ClickMultilineContentPadding
        } else {
            ClickTextFieldContentPadding
        }
    // Height-clamp both paths: a wrapping hint is what makes a 56dp single-line field 112dp tall.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val oneLineHeight =
        remember(mergedTextStyle, density) {
            with(density) {
                textMeasurer
                    .measure(
                        text = AnnotatedString("Ag"),
                        style = mergedTextStyle,
                        maxLines = 1,
                    ).size.height
                    .toDp()
            }
        }
    val placeholderSlot: @Composable (() -> Unit)? =
        when {
            placeholderText != null -> {
                {
                    Text(
                        text = placeholderText,
                        style = mergedTextStyle,
                        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                    )
                }
            }
            placeholder != null && singleLine -> {
                {
                    Box(
                        modifier =
                            Modifier
                                .heightIn(max = oneLineHeight)
                                .clipToBounds(),
                    ) {
                        placeholder()
                    }
                }
            }
            else -> placeholder
        }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .heightIn(min = minHeight)
                .defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = minHeight,
                ),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = fieldInteraction,
        cursorBrush =
            SolidColor(
                if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                visualTransformation = visualTransformation,
                innerTextField = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                    ) {
                        innerTextField()
                    }
                },
                placeholder = placeholderSlot,
                label = label,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                singleLine = singleLine,
                enabled = enabled,
                isError = isError,
                interactionSource = fieldInteraction,
                colors = colors,
                contentPadding = contentPadding,
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = fieldInteraction,
                        colors = colors,
                        shape = shape,
                    )
                },
            )
        },
    )
}

@Composable
fun clickFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = clickBorderColor(),
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = clickCardSurface(),
        unfocusedContainerColor = clickCardSurface(),
        disabledContainerColor = clickCardSurface(),
    )

/**
 * Rounded search field used by global search, map nearby search, and connection pickers.
 */
@Composable
fun ClickSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = clickCardSurface(),
    height: Dp = ClickFieldTokens.SearchHeight,
    focusRequester: FocusRequester? = null,
) {
    val shape = ClickFieldTokens.Shape
    val borderWidth = clickBorderWidth()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(containerColor)
                .border(borderWidth, clickBorderColor(), shape)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = clickTextFieldTextStyle().copy(color = textColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 10.dp)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = clickTextFieldTextStyle(),
                            color = placeholderColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = { onSearch?.invoke() },
                    onDone = { onSearch?.invoke() },
                ),
        )
        if (trailingIcon != null) {
            trailingIcon()
        }
    }
}
