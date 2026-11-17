package compose.project.click.click.data.activity

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebRequestException // pragma: allowlist secret
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Activity inbox (click-web `app/api/activity`, `lib/activity/activityView.ts`; iOS `ActivityView`).
 * Every alert plus friend requests to answer. An item's `data` is its push payload, so a tap routes
 * exactly like the push (`PushRoutes`).
 */

@Serializable
data class ActivityActorDto(
    val id: String,
    val name: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class ActivityItemDto(
    val id: String,
    val type: String,
    val title: String = "",
    val body: String = "",
    val data: Map<String, String> = emptyMap(),
    @SerialName("created_at") val createdAt: String,
    val actor: ActivityActorDto? = null,
) {
    val createdAtMs: Long? get() = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrNull()

    /** A prior-connection request the viewer can answer inline (web `requestConnectionId`). */
    val requestConnectionId: String?
        get() = if (type == "prior_connection_request") data["connection_id"]?.takeIf { it.isNotBlank() } else null
}

@Serializable
data class ActivityPageDto(
    val items: List<ActivityItemDto> = emptyList(),
    @SerialName("seen_at") val seenAt: String? = null,
    @SerialName("next_before") val nextBefore: String? = null,
    @SerialName("pending_requests") val pendingRequests: List<String> = emptyList(),
)

enum class ActivityGroup(
    val title: String,
) {
    NEW("New"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("This week"),
    EARLIER("Earlier"),
}

private fun seenMs(seenAt: String?): Long =
    seenAt?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: Long.MIN_VALUE

/**
 * New (after the seen mark) / Today / Yesterday / This week / Earlier, newest first, days in the
 * viewer's zone; empty groups left out. Same rules as web `groupActivity`.
 */
fun groupActivity(
    items: List<ActivityItemDto>,
    seenAt: String?,
    nowEpochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<Pair<ActivityGroup, List<ActivityItemDto>>> {
    val seen = seenMs(seenAt)

    fun day(ms: Long) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone).date
    val today = day(nowEpochMs)
    val yesterday = day(nowEpochMs - 86_400_000L)
    val groups = linkedMapOf<ActivityGroup, MutableList<ActivityItemDto>>()
    items.sortedByDescending { it.createdAtMs ?: 0L }.forEach { item ->
        val ms = item.createdAtMs ?: return@forEach
        val key =
            when {
                ms > seen -> ActivityGroup.NEW
                day(ms) == today -> ActivityGroup.TODAY
                day(ms) == yesterday -> ActivityGroup.YESTERDAY
                nowEpochMs - ms < 7 * 86_400_000L -> ActivityGroup.WEEK
                else -> ActivityGroup.EARLIER
            }
        groups.getOrPut(key) { mutableListOf() }.add(item)
    }
    return ActivityGroup.entries.mapNotNull { g -> groups[g]?.let { g to it.toList() } }
}

/** Items newer than the seen mark (the Home bell's dot). */
fun unseenActivityCount(
    items: List<ActivityItemDto>,
    seenAt: String?,
): Int {
    val seen = seenMs(seenAt)
    return items.count { (it.createdAtMs ?: Long.MIN_VALUE) > seen }
}

/** One newest-first list from several pages: one row per id, the most recent copy wins. */
fun mergeActivityPages(
    current: List<ActivityItemDto>,
    incoming: List<ActivityItemDto>,
): List<ActivityItemDto> =
    (incoming + current)
        .distinctBy { it.id }
        .sortedByDescending { it.createdAtMs ?: 0L }

internal suspend fun ApiClient.getActivity(before: String? = null): Result<ActivityPageDto> =
    try {
        val response =
            clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/activity") {
                before?.let { parameter("before", it) }
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ActivityPageDto>())
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

internal suspend fun ApiClient.markActivitySeen(seenAt: String): Result<Unit> =
    try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/activity/seen") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("seen_at", seenAt) }.toString())
            }
        if (response.status.value in
            200..299
        ) {
            Result.success(Unit)
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

data class ActivityState(
    val items: List<ActivityItemDto> = emptyList(),
    val seenAt: String? = null,
    val nextBefore: String? = null,
    val pendingRequests: Set<String> = emptySet(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val loadedOnce: Boolean = false,
    /** Bumped by [ActivityStore.clear]; a response started under an older value is dropped. */
    internal val generation: Int = 0,
) {
    val unseenCount: Int get() = unseenActivityCount(items, seenAt)
}

/** The later of two seen marks (null when both are): the dot never moves back. */
internal fun laterSeenAt(
    local: String?,
    server: String?,
): String? =
    when {
        local == null -> server
        server == null -> local
        seenMs(server) > seenMs(local) -> server
        else -> local
    }

/** First page: replaces the list and the pending set (iOS `loadFirstPage`); keeps the later seen mark. */
internal fun ActivityState.withFirstPage(page: ActivityPageDto): ActivityState =
    copy(
        items = mergeActivityPages(emptyList(), page.items),
        // `markSeen` may have stored a newer mark while this GET was in flight (iOS `ActivityStore.swift`).
        seenAt = laterSeenAt(seenAt, page.seenAt),
        nextBefore = page.nextBefore,
        pendingRequests = page.pendingRequests.toSet(),
        loading = false,
        failed = false,
        loadedOnce = true,
    )

/** The viewer's activity, shared by the Home bell and the Activity sheet. Cleared at sign-out. */
object ActivityStore {
    private val _state = MutableStateFlow(ActivityState())
    val state: StateFlow<ActivityState> = _state.asStateFlow()
    private val mutex = Mutex()
    private val defaultApi by lazy { ApiClient() }

    suspend fun refresh(api: ApiClient = defaultApi) =
        mutex.withLock {
            val generation = _state.value.generation
            _state.update { if (it.generation != generation) it else it.copy(loading = true, failed = false) }
            api.getActivity().fold(
                onSuccess = { page ->
                    // Checked inside the CAS: a sign-out (clear) between the GET and here wins.
                    _state.update { if (it.generation != generation) it else it.withFirstPage(page) }
                },
                onFailure = {
                    _state.update { if (it.generation != generation) it else it.copy(loading = false, failed = !it.loadedOnce) }
                },
            )
        }

    suspend fun loadMore(api: ApiClient = defaultApi) =
        mutex.withLock {
            val start = _state.value
            val before = start.nextBefore ?: return@withLock
            val generation = start.generation
            _state.update { if (it.generation != generation) it else it.copy(loading = true) }
            api.getActivity(before).fold(
                onSuccess = { page ->
                    _state.update {
                        if (it.generation != generation) {
                            it
                        } else {
                            it.copy(
                                items = mergeActivityPages(it.items, page.items),
                                nextBefore = page.nextBefore,
                                pendingRequests = it.pendingRequests + page.pendingRequests,
                                loading = false,
                            )
                        }
                    }
                },
                onFailure = { _state.update { if (it.generation != generation) it else it.copy(loading = false) } },
            )
        }

    /** Clears the dot up to the newest item shown. */
    suspend fun markSeen(api: ApiClient = defaultApi) {
        val start = _state.value
        val newest = start.items.maxByOrNull { it.createdAtMs ?: 0L } ?: return
        if (unseenActivityCount(listOf(newest), start.seenAt) == 0) return
        var applied = false
        _state.update {
            applied = it.generation == start.generation
            if (applied) it.copy(seenAt = laterSeenAt(it.seenAt, newest.createdAt)) else it
        }
        // Signed out meanwhile: never post the previous account's mark.
        if (!applied) return
        api.markActivitySeen(newest.createdAt)
    }

    /** A request was answered: it no longer offers Accept / Ignore. */
    fun requestAnswered(connectionId: String) {
        _state.update { it.copy(pendingRequests = it.pendingRequests - connectionId) }
    }

    /** Sign-out. Bumps the generation so an in-flight refresh can't write the previous account's page. */
    fun clear() {
        _state.update { ActivityState(generation = it.generation + 1) }
    }
}
