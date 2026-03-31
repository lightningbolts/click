package compose.project.click.click.ui.chat

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

private val urlRegex =
    Regex("""(https?://[^\s<>"{}|\\^`\[\]()\u0000-\u001F]+)""", RegexOption.IGNORE_CASE)

@Composable
fun ChatLinkifyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    linkColor: Color,
    style: TextStyle = LocalTextStyle.current,
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, color, linkColor) {
        buildAnnotatedString {
            var last = 0
            for (match in urlRegex.findAll(text)) {
                if (match.range.first > last) {
                    withStyle(SpanStyle(color = color)) {
                        append(text.substring(last, match.range.first))
                    }
                }
                val raw = match.value
                val trailing = raw.takeLastWhile { it in ".,;:!?)" }
                val url = if (trailing.isNotEmpty()) raw.dropLast(trailing.length) else raw
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                        linkInteractionListener = {
                            uriHandler.openUri(url)
                        },
                    ),
                ) {
                    append(url)
                }
                if (trailing.isNotEmpty()) {
                    withStyle(SpanStyle(color = color)) {
                        append(trailing)
                    }
                }
                last = match.range.last + 1
            }
            if (last < text.length) {
                withStyle(SpanStyle(color = color)) {
                    append(text.substring(last))
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style.merge(TextStyle(color = color)),
    )
}

@Composable
fun ChatLinkifyTextMaterial(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    ChatLinkifyText(
        text = text,
        modifier = modifier,
        color = color,
        linkColor = linkColor,
        style = MaterialTheme.typography.bodyMedium,
    )
}
