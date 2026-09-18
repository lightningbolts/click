package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private val markdownInlineRegex =
    Regex(
        """(`[^`\n]+`|\*\*[^*\n]+\*\*|~~[^~\n]+~~|\[[^\]\n]+\]\([^\s)]+\)|\*[^*\n]+\*)""",
    )

private fun safeMarkdownUrl(raw: String): String? {
    val url = raw.trim()
    return if (
        url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("mailto:", ignoreCase = true)
    ) {
        url
    } else {
        null
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated =
        remember(text, color, linkColor) {
            buildAnnotatedString {
                var cursor = 0
                for (match in markdownInlineRegex.findAll(text)) {
                    if (match.range.first > cursor) {
                        append(text.substring(cursor, match.range.first))
                    }

                    val token = match.value
                    when {
                        token.startsWith("**") -> {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(token.removePrefix("**").removeSuffix("**"))
                            }
                        }
                        token.startsWith("~~") -> {
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                                append(token.removePrefix("~~").removeSuffix("~~"))
                            }
                        }
                        token.startsWith("`") -> {
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                append(token.removePrefix("`").removeSuffix("`"))
                            }
                        }
                        token.startsWith("[") -> {
                            val linkMatch = Regex("""^\[([^\]]+)]\(([^\s)]+)\)$""").matchEntire(token)
                            val label = linkMatch?.groupValues?.getOrNull(1)
                            val url = linkMatch?.groupValues?.getOrNull(2)?.let(::safeMarkdownUrl)
                            if (label != null && url != null) {
                                withLink(
                                    LinkAnnotation.Url(
                                        url = url,
                                        styles =
                                            TextLinkStyles(
                                                style =
                                                    SpanStyle(
                                                        color = linkColor,
                                                        textDecoration = TextDecoration.Underline,
                                                    ),
                                            ),
                                        linkInteractionListener = { uriHandler.openUri(url) },
                                    ),
                                ) {
                                    append(label)
                                }
                            } else {
                                append(token)
                            }
                        }
                        token.startsWith("*") -> {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(token.removePrefix("*").removeSuffix("*"))
                            }
                        }
                        else -> append(token)
                    }

                    cursor = match.range.last + 1
                }
                if (cursor < text.length) {
                    append(text.substring(cursor))
                }
            }
        }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
    )
}

@Composable
fun EventMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val source = markdown.trim()
    if (source.isEmpty()) return

    val blocks = remember(source) { source.split(Regex("""\n{2,}""")) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            val lines = block.lines()

            when {
                lines.size == 1 && Regex("""^#{1,3}\s+.+$""").matches(lines.first()) -> {
                    val heading = lines.first()
                    val level = heading.takeWhile { it == '#' }.length
                    val body = heading.drop(level).trimStart()
                    MarkdownInlineText(
                        text = body,
                        style =
                            when (level) {
                                1 -> MaterialTheme.typography.headlineSmall
                                2 -> MaterialTheme.typography.titleLarge
                                else -> MaterialTheme.typography.titleMedium
                            },
                        color = color,
                    )
                }

                lines.all { Regex("""^\s*[-+*]\s+.+$""").matches(it) } -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lines.forEach { line ->
                            Row {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = color,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                MarkdownInlineText(
                                    text = line.replaceFirst(Regex("""^\s*[-+*]\s+"""), ""),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = color,
                                )
                            }
                        }
                    }
                }

                lines.all { Regex("""^\s*\d+\.\s+.+$""").matches(it) } -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lines.forEachIndexed { index, line ->
                            Row {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = color,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                MarkdownInlineText(
                                    text = line.replaceFirst(Regex("""^\s*\d+\.\s+"""), ""),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = color,
                                )
                            }
                        }
                    }
                }

                lines.all { Regex("""^\s*>\s?.+$""").matches(it) } -> {
                    MarkdownInlineText(
                        text = lines.joinToString("\n") { it.replaceFirst(Regex("""^\s*>\s?"""), "") },
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    MarkdownInlineText(
                        text = lines.joinToString("\n"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = color,
                    )
                }
            }
        }
    }
}
