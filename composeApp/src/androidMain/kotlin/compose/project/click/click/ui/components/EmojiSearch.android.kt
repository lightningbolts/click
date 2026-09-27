package compose.project.click.click.ui.components // pragma: allowlist secret

actual fun emojiUnicodeName(emoji: String): String? {
    if (emoji.isEmpty()) return null
    val codePoint = emoji.codePointAt(0)
    return runCatching { Character.getName(codePoint) }.getOrNull()?.lowercase()
}
