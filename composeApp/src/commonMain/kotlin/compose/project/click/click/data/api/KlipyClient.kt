package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.crypto.PlatformCrypto // pragma: allowlist secret
import compose.project.click.click.data.models.ChatGif // pragma: allowlist secret
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * The KLIPY app key from the build (`KLIPY_APP_KEY` in `local.properties`, injected as manifest
 * meta-data), or null when unset — which hides GIF search, as on iOS (`AppConfig.klipyAppKey`).
 *
 * KLIPY requires search requests and media loads to come from the user's device, not a Click
 * server, so there is no server proxy for it (click-web `lib/chat/klipy.ts` calls it from the
 * browser with its public key too). The key is never hard-coded in source.
 */
expect fun klipyAppKey(): String?

/**
 * KLIPY GIF API (https://docs.klipy.com/gifs-api). Mirrors iOS `KlipyClient`
 * (click-ios `Click/Core/Chat/KlipyClient.swift`) and click-web `lib/chat/klipy.ts`.
 */
class KlipyClient(
    private val appKey: String? = klipyAppKey(),
    private val httpClient: HttpClient? = null,
) {
    data class Rendition(
        val url: String,
        val width: Int,
        val height: Int,
    )

    data class Item(
        val id: String,
        val slug: String,
        val title: String,
        /** Grid thumbnail. */
        val preview: Rendition,
        /** What gets sent: sharp at bubble size without the multi-MB `hd` GIF. */
        val send: Rendition,
    ) {
        fun toChatGif(): ChatGif = ChatGif(url = send.url, width = send.width, height = send.height)
    }

    data class Page(
        val items: List<Item>,
        val page: Int,
        val hasNext: Boolean,
    )

    class NotConfiguredException : IllegalStateException("GIF search isn't available.")

    class BadResponseException : IllegalStateException("Couldn't load GIFs. Try again.")

    val isConfigured: Boolean get() = !appKey.isNullOrBlank()

    /** Trending when [query] is blank, else search. Results keep KLIPY's order. */
    suspend fun gifs(
        query: String,
        page: Int,
        customerId: String,
        region: String? = null,
    ): Result<Page> {
        val key = appKey?.takeIf { it.isNotBlank() } ?: return Result.failure(NotConfiguredException())
        val client = httpClient ?: HttpClient()
        return try {
            val response = client.get(gifsUrl(key, query, page, customerId, region))
            if (response.status.value !in 200..299) {
                Result.failure(BadResponseException())
            } else {
                parsePage(response.bodyAsText(), page)?.let { Result.success(it) } ?: Result.failure(BadResponseException())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(BadResponseException())
        } finally {
            if (httpClient == null) client.close()
        }
    }

    /** Share analytics (improves ranking). Best effort; never blocks or fails a send. */
    suspend fun triggerShare(
        slug: String,
        customerId: String,
        query: String,
    ) {
        val key = appKey?.takeIf { it.isNotBlank() } ?: return
        if (slug.isBlank()) return
        val client = httpClient ?: HttpClient()
        try {
            client.post("$BASE/${key.encodeURLPathPart()}/gifs/share/${slug.encodeURLPathPart()}") {
                contentType(ContentType.Application.Json)
                setBody(shareBody(customerId, query).toString())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        } finally {
            if (httpClient == null) client.close()
        }
    }

    companion object {
        const val BASE = "https://api.klipy.com/api/v1"
        const val PER_PAGE = 24
        const val CONTENT_FILTER = "medium"

        private val json = Json { ignoreUnknownKeys = true }

        /** Stable, non-identifying per-user ID for KLIPY personalization (never the raw user ID). */
        fun customerId(userId: String): String =
            PlatformCrypto
                .sha256("click-klipy:$userId".encodeToByteArray())
                .take(16)
                .joinToString("") { b -> (b.toInt() and 0xff).toString(16).padStart(2, '0') }

        /** `…/{key}/gifs/trending` or `…/{key}/gifs/search?q=…` with iOS's query items. */
        fun gifsUrl(
            appKey: String,
            query: String,
            page: Int,
            customerId: String,
            region: String? = null,
        ): String {
            val q = query.trim()
            val endpoint = if (q.isEmpty()) "trending" else "search"
            val params =
                buildList {
                    add("page" to page.toString())
                    add("per_page" to PER_PAGE.toString())
                    add("customer_id" to customerId)
                    add("content_filter" to CONTENT_FILTER)
                    add("format_filter" to "gif,webp")
                    if (q.isNotEmpty()) add("q" to q)
                    region?.trim()?.takeIf { it.length == 2 }?.let { add("locale" to it.lowercase()) }
                }
            val qs = params.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            return "$BASE/${appKey.encodeURLPathPart()}/gifs/$endpoint?$qs"
        }

        fun shareBody(
            customerId: String,
            query: String,
        ): JsonObject =
            buildJsonObject {
                put("customer_id", customerId)
                put("q", query.trim())
            }

        /** `{ data: { data: [...], current_page, has_next } }`; null when the envelope is wrong. */
        fun parsePage(
            body: String,
            requestedPage: Int,
        ): Page? {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val data = root["data"] as? JsonObject ?: return null
            val items = (data["data"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseItem) }
            return Page(
                items = items,
                page = data.int("current_page") ?: requestedPage,
                hasNext = (data["has_next"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }

        fun parseItem(row: JsonObject): Item? {
            val slug = row.string("slug") ?: return null
            val file = row["file"] as? JsonObject ?: return null
            val preview = rendition(file, listOf("sm", "xs", "md")) ?: return null
            val send = rendition(file, listOf("md", "hd", "sm")) ?: return null
            val id = row.string("id") ?: slug
            return Item(id = id, slug = slug, title = row.string("title") ?: "GIF", preview = preview, send = send)
        }

        /** Animated WebP is far smaller than GIF; both animate. Only KLIPY media hosts are accepted. */
        private fun rendition(
            file: JsonObject,
            sizes: List<String>,
        ): Rendition? {
            for (size in sizes) {
                val formats = file[size] as? JsonObject ?: continue
                for (format in listOf("webp", "gif")) {
                    val r = formats[format] as? JsonObject ?: continue
                    val url = r.string("url")?.takeIf { ChatGif.isKlipyMediaUrl(it) } ?: continue
                    val width = r.int("width")?.takeIf { it > 0 } ?: continue
                    val height = r.int("height")?.takeIf { it > 0 } ?: continue
                    return Rendition(url.trim(), width, height)
                }
            }
            return null
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

        private fun JsonObject.int(key: String): Int? =
            (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
    }
}
