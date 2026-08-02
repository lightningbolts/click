package compose.project.click.click.data.models

import compose.project.click.click.events.buildEventShareUrl
import compose.project.click.click.events.eventSchedule
import compose.project.click.click.events.formatEventScheduleRange
import compose.project.click.click.ui.utils.displayDynamicTitle
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Plaintext body + metadata for [ChatMessageType.BEACON] messages. */
fun MapBeacon.toBeaconChatContent(): String {
    val title = displayDynamicTitle()
    return "Beacon: $title"
}

fun MapBeacon.toBeaconChatMetadata(): JsonObject {
    val title = displayDynamicTitle()
    val shareUrl = buildEventShareUrl(id)
    val typeRaw = sourceBeaconType?.trim()?.takeIf { it.isNotEmpty() } ?: kind.apiValue
    val description = metadata.description?.trim()?.takeIf { it.isNotEmpty() && it != title }
        ?: listOfNotNull(metadata.artistName, metadata.trackName)
            .joinToString(" · ")
            .takeIf { it.isNotBlank() && it != title }
    val scheduleLabel = eventSchedule()?.let { formatEventScheduleRange(it) }
    val albumArt = metadata.albumArtUrl?.trim()?.takeIf {
        kind == MapBeaconKind.SOUNDTRACK && it.isNotEmpty()
    }
    val locationLabel = metadata.formattedAddress?.trim()?.takeUnless {
        it.isEmpty() || it.equals("Current location", ignoreCase = true)
    } ?: metadata.locationName?.trim()?.takeUnless {
        it.isEmpty() || it.equals("Current location", ignoreCase = true)
    }
    val schedule = eventSchedule()
    return buildJsonObject {
        put("beacon_id", id)
        put("beacon_type", typeRaw)
        put("title", title)
        put("lat", latitude)
        put("lng", longitude)
        put("share_url", shareUrl)
        description?.let { put("description", it) }
        scheduleLabel?.let { put("schedule_label", it) }
        albumArt?.let { put("album_art_url", it) }
        locationLabel?.let { put("location_name", it) }
        schedule?.startEpochMs?.let { put("event_start_at", it) }
        schedule?.endEpochMs?.let { put("event_end_at", it) }
        expiresAtEpochMs?.let { put("expires_at", it) }
    }
}

/**
 * Reconstruct a [MapBeacon] from chat message metadata when the live map row is
 * expired / out of discovery scope. Coordinates stay exact for routing.
 */
fun mapBeaconFromChatMetadata(
    beaconId: String,
    metadata: JsonObject?,
    contentFallback: String = "",
): MapBeacon? {
    val id = beaconId.trim().ifEmpty {
        metadata?.get("beacon_id")?.let {
            (it as? JsonPrimitive)?.contentOrNull
        }.orEmpty()
    }
    if (id.isBlank()) return null
    val title = metadata?.get("title")?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }?.trim()?.takeIf { it.isNotEmpty() }
        ?: contentFallback.removePrefix("Beacon:").trim().ifBlank { "Beacon" }
    val typeRaw = metadata?.get("beacon_type")?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }
    val kind = MapBeaconKind.fromRaw(typeRaw)
    val lat = metadata?.get("lat")?.let {
        (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    } ?: 0.0
    val lng = metadata?.get("lng")?.let {
        (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    } ?: 0.0
    val locationName = metadata?.get("location_name")?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }?.trim()?.takeUnless { it.isEmpty() || it.equals("Current location", ignoreCase = true) }
    val description = metadata?.get("description")?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }?.trim()
    val scheduleLabel = metadata?.get("schedule_label")?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }
    fun metaLong(key: String): Long? {
        val prim = metadata?.get(key) as? JsonPrimitive ?: return null
        return prim.contentOrNull?.toLongOrNull()
            ?: prim.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
    }
    val startMs = metaLong("event_start_at")
    val endMs = metaLong("event_end_at")
    val expiresMs = metaLong("expires_at")
    val rawMeta = buildJsonObject {
        put("title", title)
        description?.let { put("description", it) }
        locationName?.let { put("location_name", it) }
        scheduleLabel?.let { put("schedule_label", it) }
        startMs?.let { put("event_start_at", Instant.fromEpochMilliseconds(it).toString()) }
        endMs?.let { put("event_end_at", Instant.fromEpochMilliseconds(it).toString()) }
    }
    return MapBeacon(
        id = id,
        kind = kind,
        latitude = lat,
        longitude = lng,
        metadata = MapBeaconMetadata(
            title = title,
            description = description,
            locationName = locationName,
            raw = rawMeta,
        ),
        expiresAtEpochMs = expiresMs,
        sourceBeaconType = typeRaw,
    )
}
