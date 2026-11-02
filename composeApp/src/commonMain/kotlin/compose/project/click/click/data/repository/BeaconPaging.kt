package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconRows // pragma: allowlist secret
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Keyset paging for `GET /api/beacons` (click-web `app/api/beacons/route.ts`).
 *
 * The route pages newest first with `limit` (default 200, max 500) and an opaque `cursor` taken
 * from the previous response's `next_cursor`. Pages are cut before visibility filtering, so a
 * page can hold fewer than `limit` beacons while more remain: only a null `next_cursor` ends the
 * area. Mirrors iOS `BeaconRepository.nearbyBeacons` / `moreBeacons` and
 * `MapFeatureModel.refresh(around:)` (first page shows at once, the rest fills in behind it).
 */
object BeaconPaging {
    /** iOS `BeaconRepository.discoveryPageSize`. */
    const val PAGE_SIZE: Int = 200

    /** Safety ceiling for one area (25 × 200 = 5,000 beacons), iOS `MapFeatureModel.maxDiscoveryPages`. */
    const val MAX_PAGES: Int = 25
}

/** One `GET /api/beacons` page. */
data class BeaconPage(
    val beacons: List<MapBeacon>,
    /** Null on the last page. An empty string is treated as null, like iOS. */
    val nextCursor: String?,
)

private val beaconPageJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

/**
 * Parses a `GET /api/beacons` body: `{ beacons, radius_meters, next_cursor }`. Legacy shapes
 * (bare array, `data` / `rows` / `items`, a single row) still parse, with no next page.
 */
fun parseBeaconPage(text: String): BeaconPage {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed == "null") return BeaconPage(emptyList(), null)
    val root = runCatching { beaconPageJson.parseToJsonElement(trimmed) }.getOrNull() ?: return BeaconPage(emptyList(), null)
    if (root !is JsonObject) {
        return BeaconPage(runCatching { parseMapBeaconRows(root) }.getOrDefault(emptyList()), null)
    }
    val next =
        (root["next_cursor"] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    val rows = root["beacons"] ?: root["data"] ?: root["rows"] ?: root["items"]
    val beacons =
        runCatching {
            when {
                rows != null -> parseMapBeaconRows(rows)
                root.keys.any { it.equals("id", true) || it == "kind" || it == "beacon_type" } -> parseMapBeaconRows(root)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    return BeaconPage(beacons, next)
}

/**
 * State of paging one area. Pure: [reduce] folds page results in, [nextCursorToFetch] says what
 * to request next (null = stop).
 */
data class BeaconPagingState(
    val beacons: List<MapBeacon> = emptyList(),
    val nextCursor: String? = null,
    val pagesLoaded: Int = 0,
    /** First page failed: the area has nothing to show. */
    val failed: Boolean = false,
    /** No more requests: last page reached, page ceiling hit, or a page failed. */
    val done: Boolean = false,
    val maxPages: Int = BeaconPaging.MAX_PAGES,
) {
    val firstPageLoaded: Boolean get() = pagesLoaded > 0

    /**
     * Cursor for the next request, or null when paging should stop. The first request has no
     * cursor, so callers check [pagesLoaded] == 0 for it (see [needsFirstPage]).
     */
    val nextCursorToFetch: String?
        get() = if (done || !firstPageLoaded || pagesLoaded >= maxPages) null else nextCursor

    val needsFirstPage: Boolean get() = !done && !firstPageLoaded

    fun reduce(event: BeaconPagingEvent): BeaconPagingState =
        when (event) {
            is BeaconPagingEvent.PageLoaded -> {
                // Later pages can repeat a row the first page already had (iOS keeps the first).
                val seen = beacons.mapTo(HashSet()) { it.id }
                val fresh = event.page.beacons.filter { seen.add(it.id) }
                val pages = pagesLoaded + 1
                copy(
                    beacons = beacons + fresh,
                    nextCursor = event.page.nextCursor,
                    pagesLoaded = pages,
                    failed = false,
                    done = event.page.nextCursor == null || pages >= maxPages,
                )
            }
            // A later page failing keeps what already loaded.
            BeaconPagingEvent.PageFailed -> copy(failed = !firstPageLoaded, done = true)
        }
}

sealed class BeaconPagingEvent {
    data class PageLoaded(
        val page: BeaconPage,
    ) : BeaconPagingEvent()

    data object PageFailed : BeaconPagingEvent()
}

/**
 * Pages one area: the first page, then every `next_cursor` until the last page or
 * [BeaconPagingState.maxPages]. [onPage] gets the accumulated beacons after each page so the map
 * and Nearby can show the first page at once. Fails only when the first page fails.
 */
suspend fun pageBeacons(
    maxPages: Int = BeaconPaging.MAX_PAGES,
    onPage: (suspend (List<MapBeacon>) -> Unit)? = null,
    fetchPage: suspend (cursor: String?) -> Result<BeaconPage>,
): Result<List<MapBeacon>> {
    var state = BeaconPagingState(maxPages = maxPages)
    var firstError: Throwable? = null
    while (true) {
        val cursor: String? =
            when {
                state.needsFirstPage -> null
                else -> state.nextCursorToFetch ?: break
            }
        val result = fetchPage(cursor)
        state =
            result.fold(
                onSuccess = { state.reduce(BeaconPagingEvent.PageLoaded(it)) },
                onFailure = {
                    if (!state.firstPageLoaded) firstError = it
                    state.reduce(BeaconPagingEvent.PageFailed)
                },
            )
        if (result.isSuccess) onPage?.invoke(state.beacons)
        if (state.done) break
    }
    return if (state.failed) Result.failure(firstError ?: IllegalStateException("Beacon page failed")) else Result.success(state.beacons)
}
