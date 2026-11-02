package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.ActiveHubEntry // pragma: allowlist secret
import compose.project.click.click.data.api.GlobalSearchEvent
import compose.project.click.click.data.api.GlobalSearchPayload
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Server half of global search. Mirrors iOS `Click/Features/Search/GlobalSearchView.swift`:
 * on-device rows paint first on every keystroke; `/api/search` is the debounced fallback for what
 * the device doesn't hold, and its rows are added after the local ones (local wins on duplicates).
 */

/** State of the `/api/search` request for the current query. */
sealed interface ServerSearchState {
    /** No request: the query is empty or shorter than two characters. */
    data object Idle : ServerSearchState

    data object Loading : ServerSearchState

    data object Loaded : ServerSearchState

    data object Offline : ServerSearchState

    data class Failed(
        val message: String,
    ) : ServerSearchState
}

/**
 * Local rows first, then the server's people, events and hubs that aren't already shown.
 * Server message hits are merged by the message scan (it de-duplicates them against stored and
 * cached messages), so they're not added here.
 */
internal fun mergeServerResults(
    local: List<SearchResult>,
    server: GlobalSearchPayload?,
    activeHubs: List<ActiveHubEntry> = emptyList(),
): List<SearchResult> {
    if (server == null) return local
    val localBeaconIds = local.mapNotNullTo(HashSet()) { (it as? SearchResult.BeaconMatch)?.beacon?.id }
    val localPeerIds =
        local.mapNotNullTo(HashSet()) { row ->
            when (row) {
                is SearchResult.ActiveConnection -> row.details.otherUser.id
                is SearchResult.ArchivedConnection -> row.details.otherUser.id
                else -> null
            }
        }
    val hubsById = activeHubs.associateBy { it.hubId }
    val out = ArrayList<SearchResult>(local.size + server.people.size + server.events.size + server.hubs.size)
    out.addAll(local)
    server.people
        .distinctBy { it.userId }
        .filterNot { it.userId in localPeerIds }
        .mapTo(out) { SearchResult.SharedContextPerson(it) }
    server.events
        .distinctBy { it.beaconId }
        .filterNot { it.beaconId in localBeaconIds }
        .mapTo(out) { SearchResult.RemoteEvent(it) }
    server.hubs.distinctBy { it.hubId }.mapTo(out) { hub ->
        val active = hubsById[hub.hubId]
        SearchResult.JoinedHub(
            hub = hub,
            realtimeChannel = active?.realtimeChannel?.takeIf { it.isNotBlank() } ?: "hub:${hub.hubId}",
            creatorId = active?.creatorId,
        )
    }
    return out
}

/** The event's start in epoch milliseconds, or null when it has none (or it doesn't parse). */
internal fun GlobalSearchEvent.startEpochMs(): Long? =
    startAt
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?.toEpochMilliseconds()

/** "Event · Tue Nov 3, 7:00 PM · Gas Works Park" (iOS `.remoteEvent` row). */
internal fun remoteEventSubtitle(
    event: GlobalSearchEvent,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val start =
        event.startEpochMs()?.let { ms ->
            val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone)
            val day =
                dt.dayOfWeek.name
                    .take(3)
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
            val month =
                dt.month.name
                    .take(3)
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
            val hour12 = (dt.hour % 12).let { if (it == 0) 12 else it }
            val minute = dt.minute.toString().padStart(2, '0')
            val amPm = if (dt.hour < 12) "AM" else "PM"
            "$day $month ${dt.dayOfMonth}, $hour12:$minute $amPm"
        }
    return listOfNotNull("Event", start, event.locationName?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")
}

/** "Hub · Coffee" (iOS `.joinedHub` row). */
internal fun joinedHubSubtitle(category: String?): String =
    listOfNotNull("Hub", category?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")

/** The status line under the results while the server is busy or failed (iOS `serverStatus`). */
internal fun serverStatusCopy(
    state: ServerSearchState,
    hasResults: Boolean,
): String? =
    when (state) {
        ServerSearchState.Loading -> "Searching Click…"
        ServerSearchState.Offline ->
            if (hasResults) "You're offline. Showing results on this device." else "Couldn't search. You're offline. Tap to retry."
        is ServerSearchState.Failed ->
            if (hasResults) "Showing results on this device. Tap to retry the rest." else "Couldn't search. ${state.message} Tap to retry."
        ServerSearchState.Idle, ServerSearchState.Loaded -> null
    }
