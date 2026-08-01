package compose.project.click.click.data.models

import compose.project.click.click.events.buildEventShareUrl
import compose.project.click.click.events.eventSchedule
import compose.project.click.click.events.formatEventScheduleRange
import compose.project.click.click.ui.utils.displayDynamicTitle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
        expiresAtEpochMs?.let { put("expires_at", it) }
    }
}
