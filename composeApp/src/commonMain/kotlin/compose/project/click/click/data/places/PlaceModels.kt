package compose.project.click.click.data.places

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull

/*
 * Click Places value types (click-web `lib/server/places/serialize.ts`, CLICK_PLACES_SPEC §5.1).
 * Mirrors iOS `Click/Core/Places/Place.swift`. Decoded leniently: unknown enum strings fall back,
 * and a row missing its id or coordinates is dropped instead of failing the whole list.
 */

internal val placesJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

/** §1.3 categories and labels (iOS `PlaceCategory`). Unknown raw values read as [OTHER]. */
enum class PlaceCategory(
    val wire: String,
    val label: String,
) {
    CAFE("cafe", "Café"),
    BAR("bar", "Bar"),
    NIGHTLIFE("nightlife", "Nightlife"),
    MUSIC_VENUE("music_venue", "Music venue"),
    RESTAURANT("restaurant", "Restaurant"),
    GYM("gym", "Gym"),
    COWORKING("coworking", "Coworking"),
    STUDY_SPACE("study_space", "Study space"),
    ENTERTAINMENT("entertainment", "Entertainment"),
    BOOKSTORE("bookstore", "Bookstore"),
    CAMPUS_SPACE("campus_space", "Campus space"),
    EVENT_SPACE("event_space", "Event space"),
    OFFICE("office", "Company office"),
    OTHER("other", "Place"),
    ;

    companion object {
        fun from(raw: String?): PlaceCategory = entries.firstOrNull { it.wire == raw } ?: OTHER
    }
}

/** 1 Chill … 4 Packed (iOS `EnergyLabel`). */
enum class EnergyLabel(
    val wire: String,
    val title: String,
    val value: Int,
) {
    CHILL("chill", "Chill", 1),
    STEADY("steady", "Steady", 2),
    LIVELY("lively", "Lively", 3),
    PACKED("packed", "Packed", 4),
    ;

    companion object {
        fun from(raw: String?): EnergyLabel? = entries.firstOrNull { it.wire == raw }

        fun fromValue(value: Int): EnergyLabel? = entries.firstOrNull { it.value == value }
    }
}

internal fun parseInstantMs(raw: String?): Long? = raw?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

@Serializable
data class PulseTalkableDto(
    val yes: Int = 0,
    val no: Int = 0,
)

@Serializable
data class PulseCategoryDto(
    val question: String? = null,
    val counts: List<Int> = emptyList(),
)

@Serializable
data class PulseSummaryDto(
    val state: String = "none",
    val label: String? = null,
    @SerialName("energy_score") val energyScore: Double? = null,
    @SerialName("report_count") val reportCount: Int = 0,
    @SerialName("newest_at") val newestAt: String? = null,
    val confidence: String? = null,
    val distribution: List<Int> = listOf(0, 0, 0, 0),
    val talkable: PulseTalkableDto? = null,
    val category: PulseCategoryDto? = null,
    @SerialName("window_minutes") val windowMinutes: Int = 90,
) {
    val isLive: Boolean get() = state == "live"
    val isStale: Boolean get() = state == "stale"
    val energy: EnergyLabel? get() = EnergyLabel.from(label)
    val newestAtMs: Long? get() = parseInstantMs(newestAt)

    /** Raw counts Chill … Packed; anything but four values reads as zeros (iOS). */
    val bars: List<Int> get() = if (distribution.size == 4) distribution else listOf(0, 0, 0, 0)
}

@Serializable
data class PlaceEventRefDto(
    @SerialName("beacon_id") val beaconId: String,
    val title: String = "Event",
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("image_url") val imageUrl: String? = null,
) {
    val startsAtMs: Long? get() = parseInstantMs(startsAt)
}

@Serializable
data class PlaceViewerFlagsDto(
    @SerialName("checked_in") val checkedIn: Boolean = false,
    @SerialName("has_history") val hasHistory: Boolean = false,
    @SerialName("connections_been_here_count") val connectionsBeenHereCount: Int = 0,
)

@Serializable
data class PlaceSummaryDto(
    val id: String,
    val slug: String? = null,
    val name: String = "Place",
    val category: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("radius_meters") val radiusMeters: Int = 75,
    @SerialName("distance_meters") val distanceMeters: Double? = null,
    @SerialName("address_line") val addressLine: String? = null,
    val city: String? = null,
    @SerialName("open_now") val openNow: Boolean? = null,
    val pulse: PulseSummaryDto = PulseSummaryDto(),
    @SerialName("here_now_count") val hereNowCount: Int = 0,
    @SerialName("events_today_count") val eventsTodayCount: Int = 0,
    @SerialName("next_event") val nextEvent: PlaceEventRefDto? = null,
    @SerialName("hub_id") val hubId: String? = null,
    val viewer: PlaceViewerFlagsDto? = null,
) {
    val placeCategory: PlaceCategory get() = PlaceCategory.from(category)
    val slugOrId: String get() = slug?.takeIf { it.isNotBlank() } ?: id
}

@Serializable
data class PulseQuestionOptionDto(
    val value: Int,
    val label: String,
)

@Serializable
data class PulseQuestionDto(
    /** "energy" | "talkable" | "category" | "would_return" */
    val key: String,
    @SerialName("category_question") val categoryQuestion: String? = null,
    val prompt: String,
    val required: Boolean = false,
    /** "present" | "leaving" */
    val phase: String = "present",
    val options: List<PulseQuestionOptionDto> = emptyList(),
)

@Serializable
data class PlacePersonDto(
    @SerialName("user_id") val userId: String,
    val name: String = "Someone",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("last_met_at") val lastMetAt: String? = null,
)

@Serializable
data class PlacePatternDto(
    val label: String? = null,
    @SerialName("report_count") val reportCount: Int = 0,
    val weeks: Int = 0,
) {
    val energy: EnergyLabel? get() = EnergyLabel.from(label)
}

@Serializable
data class ClicksBeenHereDto(
    val count: Int = 0,
    val names: List<String> = emptyList(),
)

@Serializable
data class YouMetHereDto(
    val total: Int = 0,
    val people: List<PlacePersonDto> = emptyList(),
)

@Serializable
data class PlaceOwnHistoryDto(
    @SerialName("check_in_count") val checkInCount: Int = 0,
    @SerialName("last_check_in_at") val lastCheckInAt: String? = null,
    @SerialName("encounter_count") val encounterCount: Int = 0,
)

/**
 * An active check-in. The detail's `check_in` uses `active`; the check-in routes use `checked_in`
 * (click-web `lib/server/places/checkIns.ts`). Either being true with an id means checked in.
 */
@Serializable
data class PlaceCheckInDto(
    val active: Boolean? = null,
    @SerialName("checked_in") val checkedIn: Boolean? = null,
    @SerialName("check_in_id") val checkInId: String? = null,
    @SerialName("checked_in_at") val checkedInAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val proof: String = "gps",
    @SerialName("share_with_connections") val shareWithConnections: Boolean = false,
    @SerialName("hub_id") val hubId: String? = null,
    @SerialName("here_now_count") val hereNowCount: Int? = null,
    @SerialName("ask_would_return") val askWouldReturn: Boolean = false,
) {
    val isActive: Boolean get() = (active == true || checkedIn == true) && !checkInId.isNullOrBlank()
    val expiresAtMs: Long? get() = parseInstantMs(expiresAt)
}

@Serializable
data class PulseLastDto(
    val id: String,
    val energy: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("editable_until") val editableUntil: String? = null,
)

@Serializable
data class PulseEligibilityDto(
    @SerialName("can_pulse") val canPulse: Boolean = false,
    /** "not_present" | "cooldown" | "manager" */
    val reason: String? = null,
    @SerialName("cooldown_until") val cooldownUntil: String? = null,
    val questions: List<PulseQuestionDto> = emptyList(),
    @SerialName("leaving_questions") val leavingQuestions: List<PulseQuestionDto> = emptyList(),
    @SerialName("my_last_pulse") val myLastPulse: PulseLastDto? = null,
) {
    val cooldownUntilMs: Long? get() = parseInstantMs(cooldownUntil)
}

@Serializable
data class PlaceHubRefDto(
    val id: String,
    val name: String? = null,
    val joined: Boolean = false,
)

@Serializable
data class PlaceDirectionsDto(
    @SerialName("apple_maps_url") val appleMapsUrl: String? = null,
    @SerialName("google_maps_url") val googleMapsUrl: String? = null,
)

/** `GET /api/places/{idOrSlug}` → `{ place }` (§5.3). */
data class PlaceDetail(
    val summary: PlaceSummaryDto,
    val description: String? = null,
    val websiteUrl: String? = null,
    val todayHoursLabel: String? = null,
    val directions: PlaceDirectionsDto = PlaceDirectionsDto(),
    val pattern: PlacePatternDto? = null,
    val upcomingEvents: List<PlaceEventRefDto> = emptyList(),
    val hereNowConnections: List<PlacePersonDto> = emptyList(),
    val clicksBeenHere: ClicksBeenHereDto? = null,
    val youMetHere: YouMetHereDto? = null,
    val ownHistory: PlaceOwnHistoryDto? = null,
    val checkIn: PlaceCheckInDto? = null,
    val pulseEligibility: PulseEligibilityDto? = null,
    val hub: PlaceHubRefDto? = null,
    val isManager: Boolean = false,
) {
    val id: String get() = summary.id

    /** Android opens Google Maps; Apple Maps links are a fallback that still open in a browser. */
    val directionsUrl: String? get() = directions.googleMapsUrl ?: directions.appleMapsUrl
}

@Serializable
private data class PlaceDetailExtrasDto(
    val description: String? = null,
    @SerialName("website_url") val websiteUrl: String? = null,
    @SerialName("today_hours_label") val todayHoursLabel: String? = null,
    val directions: PlaceDirectionsDto? = null,
    val pattern: PlacePatternDto? = null,
    @SerialName("upcoming_events") val upcomingEvents: List<JsonElement> = emptyList(),
    @SerialName("here_now_connections") val hereNowConnections: List<JsonElement> = emptyList(),
    @SerialName("clicks_been_here") val clicksBeenHere: ClicksBeenHereDto? = null,
    @SerialName("you_met_here") val youMetHere: JsonObject? = null,
    @SerialName("own_history") val ownHistory: PlaceOwnHistoryDto? = null,
    @SerialName("check_in") val checkIn: PlaceCheckInDto? = null,
    @SerialName("pulse_eligibility") val pulseEligibility: PulseEligibilityDto? = null,
    val hub: PlaceHubRefDto? = null,
    @SerialName("is_manager") val isManager: Boolean = false,
)

/** One row of `GET /api/me/places`. */
data class MyPlaceVisit(
    val place: PlaceSummaryDto,
    val checkInCount: Int,
    val lastCheckInAt: String?,
    val encounterCount: Int,
) {
    val lastCheckInAtMs: Long? get() = parseInstantMs(lastCheckInAt)
}

/** `POST /api/places/{id}/pulse` → `{ pulse: { id, editable_until }, summary }`. */
data class PulseSubmitResult(
    val pulseId: String,
    val editableUntilMs: Long?,
    /** null when the response had no usable summary: keep the one on screen. */
    val summary: PulseSummaryDto?,
)

/** `PATCH /api/places/{id}/pulse/{pulseId}` → `{ summary }`; null summary keeps the one on screen. */
data class PulseUpdateResult(
    val summary: PulseSummaryDto?,
)

private inline fun <reified T> decodeOrNull(element: JsonElement?): T? =
    element?.takeIf { it !is JsonPrimitive }?.let { runCatching { placesJson.decodeFromJsonElement<T>(it) }.getOrNull() }

private fun JsonElement?.rows(): List<JsonElement> = (this as? JsonArray)?.toList().orEmpty()

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun lenientNullPattern(pattern: PlacePatternDto?): PlacePatternDto? = pattern?.takeIf { it.energy != null }

/** Optional nested objects decoded on their own, so a malformed one never drops the row. */
private val OPTIONAL_SUMMARY_OBJECTS = setOf("pulse", "next_event", "viewer")

/** One summary row; null when the row has no id or coordinates (iOS drops those). */
fun decodePlaceSummary(element: JsonElement?): PlaceSummaryDto? {
    val row = element as? JsonObject ?: return null
    val core = JsonObject(row.filterKeys { it !in OPTIONAL_SUMMARY_OBJECTS })
    val summary = decodeOrNull<PlaceSummaryDto>(core)?.takeIf { it.id.isNotBlank() } ?: return null
    return summary.copy(
        pulse = decodeOrNull<PulseSummaryDto>(row["pulse"]) ?: PulseSummaryDto(),
        nextEvent = decodeOrNull<PlaceEventRefDto>(row["next_event"]),
        viewer = decodeOrNull<PlaceViewerFlagsDto>(row["viewer"]),
    )
}

/** The `places` array, or null when the body isn't `{ places: [...] }` (a load failure, not "none"). */
private fun placesArray(root: JsonElement): JsonArray? = (root as? JsonObject)?.get("places") as? JsonArray

/** `{ places: [...] }` from `/api/places/nearby`; null for a malformed body. */
fun decodeNearbyPlaces(root: JsonElement): List<PlaceSummaryDto>? = placesArray(root)?.mapNotNull(::decodePlaceSummary)

/** `{ place: {...} }` from `/api/places/{idOrSlug}`. */
fun decodePlaceDetail(root: JsonElement): PlaceDetail? {
    val row = (root as? JsonObject)?.obj("place") ?: return null
    val summary = decodePlaceSummary(row) ?: return null
    val extras = decodeOrNull<PlaceDetailExtrasDto>(row) ?: PlaceDetailExtrasDto()
    val met =
        extras.youMetHere?.let { obj ->
            YouMetHereDto(
                total = (obj["total"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                people = obj["people"].rows().mapNotNull { decodeOrNull<PlacePersonDto>(it) },
            )
        }
    return PlaceDetail(
        summary = summary,
        description = extras.description?.takeIf { it.isNotBlank() },
        websiteUrl = extras.websiteUrl?.takeIf { it.isNotBlank() },
        todayHoursLabel = extras.todayHoursLabel?.takeIf { it.isNotBlank() },
        directions = extras.directions ?: PlaceDirectionsDto(),
        pattern = lenientNullPattern(extras.pattern),
        upcomingEvents = extras.upcomingEvents.mapNotNull { decodeOrNull<PlaceEventRefDto>(it) },
        hereNowConnections = extras.hereNowConnections.mapNotNull { decodeOrNull<PlacePersonDto>(it) },
        clicksBeenHere = extras.clicksBeenHere,
        youMetHere = met,
        ownHistory = extras.ownHistory,
        checkIn = extras.checkIn?.takeIf { it.isActive },
        pulseEligibility = extras.pulseEligibility,
        hub = extras.hub,
        isManager = extras.isManager,
    )
}

/** `{ places: [{ place, check_in_count, last_check_in_at, encounter_count }] }` from `/api/me/places`. */
fun decodeMyPlaces(root: JsonElement): List<MyPlaceVisit>? =
    placesArray(root)?.mapNotNull { row ->
        val obj = row as? JsonObject ?: return@mapNotNull null
        val place = decodePlaceSummary(obj["place"]) ?: return@mapNotNull null
        MyPlaceVisit(
            place = place,
            checkInCount = (obj["check_in_count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
            lastCheckInAt = (obj["last_check_in_at"] as? JsonPrimitive)?.contentOrNull,
            encounterCount = (obj["encounter_count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
        )
    }

/** A check-in body (POST, GET or DELETE); null when not checked in. */
fun decodeCheckIn(root: JsonElement): PlaceCheckInDto? = decodeOrNull<PlaceCheckInDto>(root)?.takeIf { it.isActive }

/** DELETE check-in: whether to ask "Come back at this time?". */
fun decodeAskWouldReturn(root: JsonElement): Boolean = decodeOrNull<PlaceCheckInDto>(root)?.askWouldReturn ?: false

fun decodePulseSubmit(root: JsonElement): PulseSubmitResult? {
    val obj = root as? JsonObject ?: return null
    val pulse = obj.obj("pulse") ?: return null
    val id = (pulse["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    return PulseSubmitResult(
        pulseId = id,
        editableUntilMs = parseInstantMs((pulse["editable_until"] as? JsonPrimitive)?.contentOrNull),
        summary = decodeOrNull<PulseSummaryDto>(obj["summary"]),
    )
}

fun decodePulseUpdate(root: JsonElement): PulseUpdateResult =
    PulseUpdateResult(decodeOrNull<PulseSummaryDto>((root as? JsonObject)?.get("summary")))

/**
 * Errors from the Places API, keyed on the server's `code` (§5.4–§5.5; iOS `PlaceError`). Android
 * gets every error body, so status is only the fallback.
 */
sealed class PlaceError {
    data object NotFound : PlaceError()

    data object NoLocation : PlaceError()

    data object LowAccuracy : PlaceError()

    data class OutOfBounds(
        val distanceMeters: Double?,
    ) : PlaceError()

    data object InvalidAnchor : PlaceError()

    data object NotPresent : PlaceError()

    data class Cooldown(
        val untilMs: Long?,
    ) : PlaceError()

    data object ManagerPulseNotAllowed : PlaceError()

    data object EmptyPulse : PlaceError()

    data object NotEditable : PlaceError()

    data object Network : PlaceError()

    /** A 401 the bearer refresh couldn't fix: only signing in again helps. */
    data object SessionExpired : PlaceError()

    /** §6.7 copy (iOS `PlaceError.message`). */
    fun message(metric: Boolean = false): String =
        when (this) {
            NoLocation -> "Turn on location to check in."
            LowAccuracy -> "We couldn't get a precise location. Step outside or near a window and try again."
            is OutOfBounds -> PlaceCopy.outOfBounds(distanceMeters, metric)
            InvalidAnchor -> "This code is no longer active. Ask staff for the current one."
            NotFound -> "This Place isn't available."
            NotPresent -> "Check in here to share the Pulse."
            is Cooldown -> "You can update your Pulse a little later."
            ManagerPulseNotAllowed -> "You manage this Place, so you can't Pulse it."
            SessionExpired -> "Session expired. Sign in again."
            else -> "Something went wrong. Try again."
        }

    companion object {
        /** Maps a failed response: the body's `code` first, then the status ([forbidden] for a bare 403). */
        fun from(
            status: Int,
            body: String?,
            forbidden: PlaceError = NotPresent,
        ): PlaceError {
            val obj = body?.let { runCatching { placesJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            val code = (obj?.get("code") as? JsonPrimitive)?.contentOrNull
            when (code) {
                "no_location" -> return NoLocation
                "low_accuracy" -> return LowAccuracy
                "out_of_bounds" -> return OutOfBounds((obj["distance_meters"] as? JsonPrimitive)?.doubleOrNull)
                "invalid_anchor" -> return InvalidAnchor
                "empty_pulse" -> return EmptyPulse
                "place_not_found", "pulse_not_found" -> return NotFound
                "not_present" -> return NotPresent
                "manager_pulse_not_allowed" -> return ManagerPulseNotAllowed
                "pulse_cooldown" -> return Cooldown(parseInstantMs((obj["cooldown_until"] as? JsonPrimitive)?.contentOrNull))
                "pulse_not_editable" -> return NotEditable
            }
            return when (status) {
                401 -> SessionExpired
                404 -> NotFound
                403 -> forbidden
                409 -> NotEditable
                429 -> Cooldown(null)
                else -> Network
            }
        }
    }
}

/** Thrown by [PlacesApi] calls so callers can read the mapped [PlaceError]. */
class PlaceApiException(
    val error: PlaceError,
) : Exception(error.message())
