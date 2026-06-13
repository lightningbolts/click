package compose.project.click.click.ui.utils // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret

/** UI label for the stored `beacon_type` enum / string. */
fun beaconTypeDisplayLabel(raw: String?, kind: MapBeaconKind): String =
    when (raw?.lowercase()) {
        "soundtrack" -> "Soundtrack"
        "sos" -> "SOS"
        "study" -> "Study"
        "hazard" -> "Hazard"
        "utility" -> "Utility"
        "hazard_utility" -> "Hazard / utility (legacy)"
        "transit" -> "Transit"
        "recreation" -> "Recreation"
        "hobby" -> "Hobby"
        "swag" -> "Swag"
        "capacity" -> "Capacity"
        "scavenger" -> "Scavenger"
        "event" -> "Event"
        null -> kind.userFacingLabel()
        else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

fun MapBeaconKind.userFacingLabel(): String =
    when (this) {
        MapBeaconKind.SOUNDTRACK -> "Soundtrack"
        MapBeaconKind.SOS -> "SOS"
        MapBeaconKind.HAZARD -> "Hazard"
        MapBeaconKind.UTILITY -> "Utility"
        MapBeaconKind.STUDY -> "Study"
        MapBeaconKind.EVENT -> "Event"
        MapBeaconKind.SOCIAL_VIBE -> "Social vibe"
        MapBeaconKind.OTHER -> "Beacon"
    }

fun MapBeacon.displayTypeTitle(): String =
    beaconTypeDisplayLabel(sourceBeaconType, kind)

/**
 * Title shown on the beacon sheet header / cards. Unlike [displayTypeTitle] this evaluates the
 * beacon's parsed metadata so Events show their name and Soundtracks show the song / artist,
 * rather than the generic category label. Falls back to the type label when metadata is sparse.
 */
private fun truncateDynamicTitle(text: String, maxLen: Int = 60): String =
    if (text.length > maxLen) text.take(maxLen - 1).trimEnd() + "…" else text

fun MapBeacon.displayDynamicTitle(): String {
    return when (kind) {
        MapBeaconKind.SOUNDTRACK -> {
            val track = metadata.trackName ?: metadata.title
            val artist = metadata.artistName ?: metadata.artist
            when {
                !track.isNullOrBlank() && !artist.isNullOrBlank() ->
                    truncateDynamicTitle("$track — $artist", 72)
                !track.isNullOrBlank() -> truncateDynamicTitle(track, 72)
                !artist.isNullOrBlank() -> truncateDynamicTitle(artist, 72)
                else -> displayTypeTitle()
            }
        }
        MapBeaconKind.EVENT -> {
            val name = metadata.title?.takeIf { it.isNotBlank() }
                ?: metadata.description
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
            when {
                name.isNullOrBlank() -> displayTypeTitle()
                else -> truncateDynamicTitle(name)
            }
        }
        else -> {
            val label = metadata.title?.trim()?.takeIf { it.isNotEmpty() }
            val desc = metadata.description?.trim()?.takeIf { it.isNotEmpty() }
            when {
                !label.isNullOrBlank() -> truncateDynamicTitle(label)
                !desc.isNullOrBlank() -> truncateDynamicTitle(desc)
                else -> displayTypeTitle()
            }
        }
    }
}

/** Remaining TTL copy for discovery cards and beacon subtitles. */
fun formatBeaconExpiresIn(remainingMs: Long): String {
    val mins = (remainingMs / 60_000L).coerceAtLeast(0L)
    return when {
        mins < 60 -> "Expires in $mins min"
        mins < 24 * 60 -> {
            val hours = mins / 60
            if (hours == 1L) "Expires in 1 hour" else "Expires in ${hours} hours"
        }
        else -> {
            val days = mins / (24 * 60)
            if (days == 1L) "Expires in 1 day" else "Expires in ${days} days"
        }
    }
}

/** Subtitle row for non-event discovery beacons: description plus TTL when available. */
fun MapBeacon.discoveryFeedSubtitle(nowMs: Long): String {
    val desc = metadata.description?.trim()?.takeIf { it.isNotEmpty() }
    val expiry = expiresAtEpochMs?.let { formatBeaconExpiresIn((it - nowMs).coerceAtLeast(0)) }
    val truncatedDesc = desc?.let { if (it.length > 48) it.take(47) + "…" else it }
    return when {
        truncatedDesc != null && expiry != null -> "$truncatedDesc · $expiry"
        truncatedDesc != null -> truncatedDesc
        expiry != null -> expiry
        else -> "Active beacon"
    }
}
