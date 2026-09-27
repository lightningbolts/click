package compose.project.click.click.ui.components // pragma: allowlist secret

import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Lowercase Unicode name of the emoji's first code point ("grinning face"), or null. */
expect fun emojiUnicodeName(emoji: String): String?

/**
 * Emoji picker search and "Recently used" (iOS `EmojiPickerSheet` / `RecentEmoji`). The name index
 * is built once off the main thread from platform Unicode names.
 */
object EmojiSearch {
    const val RECENTS_MAX: Int = 24

    private var index: Map<String, String>? = null

    suspend fun buildIndex(emojis: List<String> = EmojiCatalog.all): Map<String, String> =
        index ?: withContext(Dispatchers.Default) {
            emojis.associateWith { emojiUnicodeName(it).orEmpty() }
        }.also { index = it }

    /** Emojis whose name contains every word of [query] (so "red heart" and "heart red" both match). */
    fun search(
        query: String,
        names: Map<String, String>,
    ): List<String> {
        val words =
            query
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        if (words.isEmpty()) return names.keys.toList()
        return names.filter { (_, name) -> words.all { name.contains(it) } }.keys.toList()
    }

    /** Most recent first, deduplicated, capped at [RECENTS_MAX]. */
    fun pushRecent(
        recents: List<String>,
        emoji: String,
    ): List<String> = (listOf(emoji) + recents.filterNot { it == emoji }).take(RECENTS_MAX)

    private val serializer = ListSerializer(String.serializer())

    suspend fun loadRecents(storage: TokenStorage): List<String> =
        storage.getRecentEmoji()?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    suspend fun recordRecent(
        storage: TokenStorage,
        emoji: String,
    ) {
        runCatching { storage.saveRecentEmoji(Json.encodeToString(serializer, pushRecent(loadRecents(storage), emoji))) }
    }
}
