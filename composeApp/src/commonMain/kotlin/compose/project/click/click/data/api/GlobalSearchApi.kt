package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.repository.ConversationSearchHit // pragma: allowlist secret
import compose.project.click.click.util.isOfflineNetworkFailure
import compose.project.click.click.util.redactedRestMessage
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/*
 * Server-backed global search: click-web `app/api/search/route.ts` (`GET /api/search?q=`), with the
 * older message-only `GET /api/chat/search` as the fallback when a server doesn't serve it yet (404).
 * Mirrors iOS `Click/Features/Search/GlobalSearchView.swift` (`searchServer`, `decodeRemote`).
 *
 * The route has no paging and no per-route rate limit: one request returns capped sections
 * (20 people, 20 events, 15 hubs, plus message hits). Queries shorter than two characters return
 * empty sections server-side, so the client never sends them.
 */

/** Shorter queries are never sent (the route answers them with empty sections). */
const val GLOBAL_SEARCH_MIN_QUERY = 2

/**
 * `GET /api/search` for [query], or null when the trimmed query is too short to send.
 * [mine] adds `mine=1` (connections, groups and listed Places); the web command palette uses it,
 * the apps don't (they match those on-device), so it defaults off like iOS.
 */
internal fun globalSearchUrl(
    origin: String,
    query: String,
    mine: Boolean = false,
): String? {
    val q = query.trim()
    if (q.length < GLOBAL_SEARCH_MIN_QUERY) return null
    val base = "${origin.trimEnd('/')}/api/search?q=${q.encodeURLParameter()}"
    return if (mine) "$base&mine=1" else base
}

/** `people`: someone you share a group or hub with but haven't Clicked with. */
data class GlobalSearchPerson(
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
    /** "In a group with you" / "In a hub with you". */
    val context: String? = null,
)

/** `events`: a public upcoming event, or one of your own (any visibility). */
data class GlobalSearchEvent(
    val beaconId: String,
    val title: String,
    val locationName: String? = null,
    /** ISO-8601 start, when the event has one. */
    val startAt: String? = null,
    val imageUrl: String? = null,
)

/** `hubs`: a hub you have joined. */
data class GlobalSearchHub(
    val hubId: String,
    val name: String,
    val category: String? = null,
)

/** `clicks` (only with `mine=1`): one of your connections. */
data class GlobalSearchClick(
    val connectionId: String,
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
)

/** `groups` (only with `mine=1`): a group you are in. */
data class GlobalSearchGroup(
    val groupId: String,
    val name: String,
)

/** `places` (only with `mine=1`): a listed, verified Click Place. */
data class GlobalSearchPlace(
    val placeId: String,
    val slug: String,
    val name: String,
    val category: String? = null,
    val city: String? = null,
)

/** One `/api/search` response. Rows missing their id are dropped, never the whole response. */
data class GlobalSearchPayload(
    val people: List<GlobalSearchPerson> = emptyList(),
    val events: List<GlobalSearchEvent> = emptyList(),
    val hubs: List<GlobalSearchHub> = emptyList(),
    /** Plaintext message hits (same contract as `/api/chat/search`). */
    val hits: List<ConversationSearchHit> = emptyList(),
    val clicks: List<GlobalSearchClick> = emptyList(),
    val groups: List<GlobalSearchGroup> = emptyList(),
    val places: List<GlobalSearchPlace> = emptyList(),
)

/** How one server search ended. A failure is shown as an error, never as "No results" (iOS). */
sealed interface GlobalSearchOutcome {
    data class Success(
        val payload: GlobalSearchPayload,
    ) : GlobalSearchOutcome

    /** The device couldn't reach the server. */
    data object Offline : GlobalSearchOutcome

    data class Failed(
        val message: String,
    ) : GlobalSearchOutcome
}

private val searchJson = Json { ignoreUnknownKeys = true }

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun JsonObject.rows(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

/** Decodes a `/api/search` (or `/api/chat/search`) body; a malformed body is an empty result. */
internal fun parseGlobalSearchPayload(body: String): GlobalSearchPayload {
    val root = runCatching { searchJson.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return GlobalSearchPayload()
    return parseGlobalSearchPayload(root)
}

internal fun parseGlobalSearchPayload(root: JsonObject): GlobalSearchPayload =
    GlobalSearchPayload(
        people =
            root.rows("people").mapNotNull { row ->
                val id = row.str("userId") ?: return@mapNotNull null
                val name = row.str("name") ?: return@mapNotNull null
                GlobalSearchPerson(id, name, row.str("avatarUrl"), row.str("context"))
            },
        events =
            root.rows("events").mapNotNull { row ->
                val id = row.str("beaconId") ?: return@mapNotNull null
                GlobalSearchEvent(id, row.str("title") ?: "Event", row.str("locationName"), row.str("startAt"), row.str("imageUrl"))
            },
        hubs =
            root.rows("hubs").mapNotNull { row ->
                val id = row.str("hubId") ?: return@mapNotNull null
                val name = row.str("name") ?: return@mapNotNull null
                GlobalSearchHub(id, name, row.str("category"))
            },
        hits = root.rows("hits").mapNotNull(::parseMessageHit),
        clicks =
            root.rows("clicks").mapNotNull { row ->
                val connectionId = row.str("connectionId") ?: return@mapNotNull null
                val userId = row.str("userId") ?: return@mapNotNull null
                val name = row.str("name") ?: return@mapNotNull null
                GlobalSearchClick(connectionId, userId, name, row.str("avatarUrl"))
            },
        groups =
            root.rows("groups").mapNotNull { row ->
                val id = row.str("groupId") ?: return@mapNotNull null
                GlobalSearchGroup(id, row.str("name") ?: return@mapNotNull null)
            },
        places =
            root.rows("places").mapNotNull { row ->
                val id = row.str("placeId") ?: return@mapNotNull null
                val slug = row.str("slug") ?: return@mapNotNull null
                val name = row.str("name") ?: return@mapNotNull null
                GlobalSearchPlace(id, slug, name, row.str("category"), row.str("city"))
            },
    )

/** A `/api/chat/search` hit; `messageId` and `chatId` are required (iOS `MessageHit.decode`). */
private fun parseMessageHit(row: JsonObject): ConversationSearchHit? {
    val messageId = row.str("messageId") ?: return null
    val chatId = row.str("chatId") ?: return null
    val timestamp = (row["timestamp"] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() } ?: 0L
    return ConversationSearchHit(
        messageId = messageId,
        chatId = chatId,
        conversationId = row.str("conversationId") ?: chatId,
        connectionId = row.str("connectionId") ?: chatId,
        senderId = row.str("senderId").orEmpty(),
        timestamp = timestamp,
        snippet = row.str("snippet").orEmpty(),
        chatName = row.str("chatName").orEmpty(),
        isHub = (row["isHub"] as? JsonPrimitive)?.booleanOrNull ?: false,
        hubId = row.str("hubId"),
        hubRealtimeChannel = row.str("hubRealtimeChannel"),
    )
}

/** Turns a thrown request failure into an outcome (offline vs. a server/other error). */
internal fun globalSearchFailure(error: Throwable): GlobalSearchOutcome =
    if (error.isOfflineNetworkFailure()) {
        GlobalSearchOutcome.Offline
    } else {
        GlobalSearchOutcome.Failed(globalSearchErrorCopy((error as? ClickWebRequestException)?.statusCode))
    }

/** Short copy for the retry row ("Couldn't search. <copy> Tap to retry."). */
internal fun globalSearchErrorCopy(status: Int?): String =
    when (status) {
        401, 403 -> "Your session expired."
        429 -> "Too many searches. Wait a moment."
        in 500..599 -> "Click is having trouble right now."
        else -> "Something went wrong."
    }

/** The server search the global search sheet calls (fakeable in tests). */
fun interface GlobalSearchService {
    suspend fun search(query: String): GlobalSearchOutcome
}

class GlobalSearchApi(
    private val api: ApiClient = ApiClient(),
) : GlobalSearchService {
    override suspend fun search(query: String): GlobalSearchOutcome {
        val origin = ApiClient.clickWebAuthOrigin
        val url = globalSearchUrl(origin, query) ?: return GlobalSearchOutcome.Success(GlobalSearchPayload())
        return try {
            val response = api.clickWebClient.get(url)
            when (response.status.value) {
                in 200..299 -> GlobalSearchOutcome.Success(parseGlobalSearchPayload(response.bodyAsText()))
                // Older servers: message hits only.
                404 -> chatSearchFallback(origin, query)
                else -> globalSearchFailure(ClickWebRequestException(response.status.value, "HTTP ${response.status.value}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("GlobalSearchApi: search failed: ${e.redactedRestMessage()}")
            globalSearchFailure(e)
        }
    }

    private suspend fun chatSearchFallback(
        origin: String,
        query: String,
    ): GlobalSearchOutcome {
        val response = api.clickWebClient.get("$origin/api/chat/search?q=${query.trim().encodeURLParameter()}")
        val status = response.status.value
        if (status !in 200..299) return globalSearchFailure(ClickWebRequestException(status, "HTTP $status"))
        return GlobalSearchOutcome.Success(GlobalSearchPayload(hits = parseGlobalSearchPayload(response.bodyAsText()).hits))
    }
}
