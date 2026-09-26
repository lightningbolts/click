package compose.project.click.click.util // pragma: allowlist secret

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A song identified from a streaming link: name, artist, 30 s iTunes preview and artwork. */
data class SoundtrackMatch(
    val trackName: String,
    val artistName: String?,
    val previewUrl: String?,
    val artworkUrl: String?,
)

/**
 * On-device soundtrack lookup (iOS `SoundtrackResolver`, 05 §C6), mirroring click-web
 * `beaconSoundtrackEnrichment`: oEmbed / iTunes lookup for search terms, then the iTunes Search
 * API. Used only when a beacon has no server `preview_url`; results are cached per link.
 */
object SoundtrackResolver {
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy { HttpClient { install(HttpTimeout) { requestTimeoutMillis = 8_000 } } }
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, SoundtrackMatch?>()

    suspend fun resolve(link: String): SoundtrackMatch? {
        val trimmed = link.trim()
        if (!isAllowedMusicShareUrl(trimmed)) return null
        cacheMutex.withLock { if (trimmed in cache) return cache[trimmed] }
        val match = runCatching { lookup(trimmed) }.getOrNull()
        cacheMutex.withLock { cache[trimmed] = match }
        return match
    }

    private suspend fun lookup(link: String): SoundtrackMatch? {
        val host = hostOf(link)
        val terms = mutableListOf<String>()
        var fallback: SoundtrackMatch? = null

        fun remember(
            title: String?,
            thumbnail: String?,
        ) {
            val clean = title?.let(::cleaned)?.takeIf { it.length >= 2 } ?: return
            if (fallback != null) return
            val art = thumbnail?.takeIf { it.startsWith("https://") } ?: linkThumbnail(link)
            fallback = SoundtrackMatch(clean, null, null, art)
        }

        fun push(term: String?) {
            val t = term?.replace(Regex("""\s+"""), " ")?.trim() ?: return
            listOf(t, cleaned(t)).filter { it.length >= 2 && it !in terms }.forEach { terms += it }
        }

        if (host.endsWith("apple.com")) {
            appleCatalogId(link)?.let { id ->
                val rows = fetch("https://itunes.apple.com/lookup?id=$id&entity=song")?.array("results")
                rows?.firstOrNull { it.string("trackName") != null }?.let { row -> matchFrom(row)?.let { return it } }
            }
        }
        if ("spotify" in host) {
            val oembed = fetch("https://open.spotify.com/oembed?url=${link.encodeURLParameter()}")
            val title = oembed?.string("title")
            push(title?.let(::spotifyTerm))
            remember(title?.let(::spotifySongTitle), oembed?.string("thumbnail_url"))
        }
        if ("youtu" in host) {
            val watch = youtubeVideoId(link)?.let { "https://www.youtube.com/watch?v=$it" } ?: link
            val oembed = fetch("https://www.youtube.com/oembed?format=json&url=${watch.encodeURLParameter()}")
            val title = oembed?.string("title")
            if (title != null) {
                val author = oembed.string("author_name")?.replace(Regex("""\s*-\s*topic\s*$""", RegexOption.IGNORE_CASE), "")
                if (author != null && author.length > 1 && !title.contains(author, ignoreCase = true)) push("$author $title")
                push(title.replace(" - ", " "))
                remember(title, oembed.string("thumbnail_url"))
            }
        }
        if (host.endsWith("apple.com")) {
            val oembed = fetch("https://embed.music.apple.com/oembed?url=${link.encodeURLParameter()}")
            push(oembed?.string("title"))
            remember(oembed?.string("title"), oembed?.string("thumbnail_url"))
        }
        for (term in terms.take(4)) {
            val rows =
                fetch("https://itunes.apple.com/search?term=${term.encodeURLParameter()}&entity=song&limit=10")?.array("results")
                    ?: continue
            val preferred = rows.firstOrNull { !it.string("previewUrl").isNullOrEmpty() } ?: rows.firstOrNull()
            preferred?.let(::matchFrom)?.let { return it }
        }
        return fallback
    }

    private suspend fun fetch(url: String): JsonObject? =
        runCatching {
            val response = client.get(url)
            if (response.status.value != 200) return null
            json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        }.getOrNull()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.array(key: String): List<JsonObject>? = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }

    private fun matchFrom(row: JsonObject): SoundtrackMatch? {
        val name = row.string("trackName")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val preview = row.string("previewUrl")
        return SoundtrackMatch(
            trackName = name,
            artistName = row.string("artistName"),
            previewUrl = preview?.takeIf(::isTrustedPreview),
            artworkUrl = artwork(row.string("artworkUrl100")),
        )
    }

    // region pure helpers (unit-tested)

    internal fun hostOf(url: String): String =
        url
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore(':')
            .lowercase()

    /** iTunes artwork comes as 100×100; the same path serves any size. */
    fun artwork(
        raw: String?,
        size: Int = 600,
    ): String? {
        val value = raw?.takeIf { it.isNotEmpty() } ?: return null
        return value.replace(Regex("""/\d+x\d+(bb)?\.(jpg|png|webp)$"""), "/${size}x${size}bb.jpg")
    }

    /** Only Apple's preview CDN is played (the URL may come from another user's beacon). */
    fun isTrustedPreview(raw: String?): Boolean {
        val value = raw?.trim() ?: return false
        if (!value.startsWith("https://")) return false
        val host = hostOf(value)
        return host.endsWith(".apple.com") || host.endsWith(".mzstatic.com")
    }

    /** YouTube's thumbnail derived from the link alone. */
    fun linkThumbnail(link: String?): String? = link?.let(::youtubeVideoId)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    internal fun appleCatalogId(link: String): String? {
        val query = link.substringAfter('?', "")
        query
            .split(
                '&',
            ).firstOrNull { it.startsWith("i=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.let {
                return it
            }
        return link
            .substringBefore('?')
            .split('/')
            .lastOrNull { it.startsWith("id") && it.length > 2 && it.drop(2).all(Char::isDigit) }
            ?.drop(2)
    }

    internal fun youtubeVideoId(link: String): String? {
        val host = hostOf(link)
        if ("youtu" !in host) return null
        val id =
            if (host == "youtu.be") {
                link
                    .substringAfter("://")
                    .substringAfter('/')
                    .substringBefore('?')
                    .substringBefore('/')
            } else {
                link
                    .substringAfter('?', "")
                    .split('&')
                    .firstOrNull { it.startsWith("v=") }
                    ?.substringAfter("=")
            }
        return id?.takeIf { it.length == 11 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
    }

    /** "Song - song and lyrics by Artist | Spotify" → "Song". */
    internal fun spotifySongTitle(title: String): String {
        val t = title.replace(Regex("""\s*\|\s*spotify\s*$""", RegexOption.IGNORE_CASE), "")
        return t.split(" - song").first()
    }

    /** "Song - song and lyrics by Artist | Spotify" → "Artist Song". */
    internal fun spotifyTerm(title: String): String {
        var t = title.replace(Regex("""\s*\|\s*spotify\s*$""", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("""\s*-\s*song and lyrics by\s+""", RegexOption.IGNORE_CASE), " by ")
        val i = t.indexOf(" by ", ignoreCase = true)
        return if (i >= 0) "${t.substring(i + 4)} ${t.substring(0, i)}" else t
    }

    /** Drops "(Official Video)", "[Remastered]" noise. */
    internal fun cleaned(term: String): String =
        term
            .replace(
                Regex(
                    """\s*[(\[](official|lyric|lyrics|audio|video|visualizer|remaster|live|feat|ft)[^)\]]*[)\]]""",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).trim()

    // endregion
}
