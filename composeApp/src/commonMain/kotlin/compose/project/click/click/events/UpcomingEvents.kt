package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebRequestException // pragma: allowlist secret
import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An event the viewer created or RSVP'd to (`GET /api/beacons/mine`, click-web `mineEvents.ts`). */
@Serializable
data class MineEventDto(
    @SerialName("beacon_id") val beaconId: String,
    val title: String? = null,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("event_start_at") val eventStartAt: String? = null,
    @SerialName("event_end_at") val eventEndAt: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("rsvp_count") val rsvpCount: Int = 0,
    @SerialName("expires_at") val expiresAt: String? = null,
    /** "creator" or "rsvp". */
    val role: String = "rsvp",
)

@Serializable
internal data class MineEventsEnvelope(
    val events: List<MineEventDto> = emptyList(),
)

internal suspend fun ApiClient.getMyEvents(): Result<List<MineEventDto>> =
    try {
        val response = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons/mine")
        if (response.status.value in 200..299) {
            Result.success(response.body<MineEventsEnvelope>().events)
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/** Why an event is in Upcoming (iOS Home "Hosting" / "Going" / "Saved" tags). */
enum class UpcomingTag(
    val label: String,
) {
    HOSTING("Hosting"),
    GOING("Going"),
    SAVED("Saved"),
}

data class UpcomingEvent(
    /** Rendered with the saved-event tile, so mine events are mapped onto the same shape. */
    val event: EventBookmarkItemDto,
    val tag: UpcomingTag,
)

private fun String?.epochMs(): Long? = this?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

internal fun MineEventDto.toBookmarkShape(): EventBookmarkItemDto =
    EventBookmarkItemDto(
        beaconId = beaconId,
        title = title,
        eventStartAt = eventStartAt,
        eventEndAt = eventEndAt,
        locationName = locationName,
        latitude = latitude,
        longitude = longitude,
        expiresAt = expiresAt,
    )

/**
 * Still upcoming: not ended yet (end, else start + 3 h, still ahead), or with no time set, not
 * expired. Events without any time or expiry are kept (the server decided they're live).
 */
internal fun isStillUpcoming(
    startIso: String?,
    endIso: String?,
    expiresIso: String?,
    nowEpochMs: Long,
): Boolean {
    val start = startIso.epochMs()
    val end = endIso.epochMs() ?: start?.plus(3 * 60 * 60 * 1000L)
    if (end != null) return end > nowEpochMs
    val expires = expiresIso.epochMs() ?: return true
    return expires > nowEpochMs
}

/**
 * Hosting, going and saved events still ahead, one row per event (Hosting > Going > Saved), soonest
 * first; events with no time yet go last (iOS `UpcomingEventsView`, #69).
 */
fun upcomingEvents(
    mine: List<MineEventDto>,
    saved: List<EventBookmarkItemDto>,
    nowEpochMs: Long,
): List<UpcomingEvent> {
    val byId = linkedMapOf<String, UpcomingEvent>()

    fun offer(candidate: UpcomingEvent) {
        val existing = byId[candidate.event.beaconId]
        if (existing == null || candidate.tag.ordinal < existing.tag.ordinal) byId[candidate.event.beaconId] = candidate
    }
    mine.forEach { m ->
        if (!isStillUpcoming(m.eventStartAt, m.eventEndAt, m.expiresAt, nowEpochMs)) return@forEach
        offer(UpcomingEvent(m.toBookmarkShape(), if (m.role == "creator") UpcomingTag.HOSTING else UpcomingTag.GOING))
    }
    saved.forEach { s ->
        if (!isStillUpcoming(s.eventStartAt, s.eventEndAt, s.expiresAt, nowEpochMs)) return@forEach
        offer(UpcomingEvent(s, UpcomingTag.SAVED))
    }
    return byId.values.sortedWith(compareBy({ it.event.eventStartAt.epochMs() ?: Long.MAX_VALUE }, { it.event.beaconId }))
}
