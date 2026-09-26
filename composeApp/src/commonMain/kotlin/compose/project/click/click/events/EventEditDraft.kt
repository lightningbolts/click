package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.data.api.MapBeaconPatchBody // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.utils.EVENT_FORMATTED_ADDRESS_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.utils.EVENT_LOCATION_NAME_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.utils.GeocodedPlace // pragma: allowlist secret
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

const val EVENT_TITLE_MAX_LENGTH = 80
const val EVENT_DESCRIPTION_MAX_LENGTH = 500

/** Everything the creator can change on an event (iOS `CreateBeaconSheet` in edit mode, F83). */
data class EventEditDraft(
    val title: String,
    val description: String,
    /** Null keeps the current place. */
    val newPlace: GeocodedPlace?,
    val schedule: EventSchedule,
    val categories: List<String>,
    val listing: EventListingOptions,
    val capacityText: String,
    val removePhoto: Boolean = false,
) {
    companion object {
        fun from(beacon: MapBeacon): EventEditDraft? {
            val schedule = beacon.eventSchedule() ?: return null
            val listing = parseEventListingOptions(beacon.metadata.raw)
            return EventEditDraft(
                title = beacon.metadata.title.orEmpty(),
                description = beacon.metadata.description.orEmpty(),
                newPlace = null,
                schedule = schedule,
                categories = beacon.metadata.eventCategories,
                listing = listing,
                capacityText = listing.eventCapacity?.toString().orEmpty(),
            )
        }
    }
}

/**
 * The first problem to show, or null when the draft can be saved. A start time already in the past
 * is fine when it is unchanged (editing an event that has started).
 */
fun eventEditValidationError(
    draft: EventEditDraft,
    originalStartEpochMs: Long,
    nowEpochMs: Long,
): String? {
    val title = draft.title.trim()
    if (title.isEmpty()) return "Please add a title."
    if (title.length > EVENT_TITLE_MAX_LENGTH) return "Title must be $EVENT_TITLE_MAX_LENGTH characters or less."
    if (draft.description.trim().length > EVENT_DESCRIPTION_MAX_LENGTH) {
        return "Description must be $EVENT_DESCRIPTION_MAX_LENGTH characters or less."
    }
    val capacity = draft.capacityText.trim()
    if (capacity.isNotEmpty() && (capacity.toIntOrNull() ?: 0) < 1) return "Capacity must be a whole number, or blank for unlimited."
    val scheduleNow = if (draft.schedule.startEpochMs == originalStartEpochMs) minOf(nowEpochMs, originalStartEpochMs) else nowEpochMs
    return when (validateEventSchedule(draft.schedule.startEpochMs, draft.schedule.endEpochMs, scheduleNow)) {
        EventScheduleValidationError.EndBeforeStart -> "Event end must be after start."
        EventScheduleValidationError.StartInPast -> "Event start must be in the future."
        EventScheduleValidationError.DurationExceedsOneMonth -> "Events can last at most 1 month."
        null -> null
    }
}

/** `PATCH /api/beacons/{id}` body; [newImageUrl] is set after a replacement photo uploads. */
fun buildEventEditPatch(
    draft: EventEditDraft,
    newImageUrl: String?,
): MapBeaconPatchBody {
    val listing =
        draft.listing.copy(
            eventCapacity =
                draft.capacityText
                    .trim()
                    .toIntOrNull()
                    ?.takeIf { it > 0 },
        )
    val place = draft.newPlace
    val metadata =
        buildJsonObject {
            put("title", draft.title.trim())
            put("description", draft.description.trim())
            eventScheduleMetadata(draft.schedule).forEach { (k, v) -> put(k, v) }
            putJsonArray(EVENT_CATEGORIES_METADATA_KEY) { sanitizeEventCategories(draft.categories).forEach { add(it) } }
            listing.toMetadataPatch().forEach { (k, v) -> put(k, v) }
            if (listing.eventCapacity == null) put("event_capacity", JsonNull)
            if (place != null) {
                val name = place.shortLabel.trim().ifEmpty { place.displayName.trim() }
                if (name.isNotEmpty()) put(EVENT_LOCATION_NAME_METADATA_KEY, name)
                place.displayName
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { put(EVENT_FORMATTED_ADDRESS_METADATA_KEY, it) }
            }
            when {
                newImageUrl != null -> put("image_url", newImageUrl)
                draft.removePhoto -> put("image_url", JsonNull)
            }
        }
    return MapBeaconPatchBody(
        metadata = metadata,
        lat = place?.latitude,
        lon = place?.longitude,
    )
}
