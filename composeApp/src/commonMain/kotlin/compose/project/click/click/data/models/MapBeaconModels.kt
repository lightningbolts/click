@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data.models // pragma: allowlist secret

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

// --- Map beacons (community pins: soundtracks, SOS, utilities, social vibes) ---

/**
 * Beacon category from API / DB `kind` column.
 * Values are normalized to lowercase for comparison.
 */
enum class MapBeaconKind(
    val apiValue: String,
) {
    SOUNDTRACK("soundtrack"),
    SOS("sos"),
    HAZARD("hazard"),
    UTILITY("utility"),
    STUDY("study"),
    SOCIAL_VIBE("social_vibe"),
    EVENT("event"),
    OTHER("other"),
    ;

    companion object {
        fun fromRaw(value: String?): MapBeaconKind {
            val v = value?.trim()?.lowercase().orEmpty()
            if (v.isEmpty()) return OTHER
            // Legacy combined DB enum / API string (migration maps rows to `hazard`; keep parse path).
            if (v == "hazard_utility") return HAZARD
            entries.firstOrNull { it.apiValue == v }?.let { return it }
            return when {
                v.contains("sound") || v == "music" -> SOUNDTRACK
                v.contains("sos") || v.contains("emergency") -> SOS
                v == "hazard" || v.contains("danger") -> HAZARD
                v == "utility" || v.contains("util") || v.contains("amenity") -> UTILITY
                v.contains("study") -> STUDY
                v.contains("social") || v.contains("vibe") -> SOCIAL_VIBE
                v == "event" || v.contains("activity") -> EVENT
                else -> OTHER
            }
        }
    }
}

/**
 * Parsed JSONB `metadata` for map beacons (flexible keys).
 */
data class MapBeaconMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val musicUrl: String? = null,
    val description: String? = null,
    /** Canonical share URL persisted by click-web for soundtrack beacons. */
    val originalUrl: String? = null,
    /** iTunes / Apple Music 30-second preview stream (.m4a). */
    val previewUrl: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumArtUrl: String? = null,
    /** Event taxonomy chips from metadata `event_categories`. */
    val eventCategories: List<String> = emptyList(),
    /** Short place label from address search or reverse geocode. */
    val locationName: String? = null,
    /** Full formatted address for event detail / share. */
    val formattedAddress: String? = null,
    val raw: JsonObject? = null,
)

/**
 * Cover/hero photo for a beacon card. User-uploaded `image_url` is parsed into [albumArtUrl]
 * alongside soundtrack art; raw keys are a safety net for unsanitized payloads.
 */
fun MapBeaconMetadata.heroImageUrl(): String? {
    albumArtUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val obj = raw ?: return null

    fun key(vararg names: String): String? {
        for (name in names) {
            val s = (obj[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            if (s != null) return s
        }
        return null
    }
    return key("image_url", "cover_url", "album_art_url", "artworkUrl100", "artwork_url")
}

fun beaconHeroImageUrl(metadata: MapBeaconMetadata): String? = metadata.heroImageUrl()

private val beaconMetadataJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

fun parseMapBeaconMetadata(element: JsonElement?): MapBeaconMetadata {
    if (element == null) return MapBeaconMetadata()
    val obj =
        when (element) {
            is JsonObject -> element
            is JsonPrimitive -> {
                val text = element.contentOrNull?.trim().orEmpty()
                if (text.isEmpty()) return MapBeaconMetadata()
                runCatching { beaconMetadataJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
                    ?: return MapBeaconMetadata()
            }
            else -> return MapBeaconMetadata()
        }

    fun str(vararg keys: String): String? {
        for (k in keys) {
            val p = obj[k] as? JsonPrimitive ?: continue
            val s = p.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            if (s != null) return s
        }
        return null
    }
    return MapBeaconMetadata(
        title = str("title", "event_title", "track_title", "name", "track", "track_name"),
        artist = str("artist", "track_artist", "artist_name"),
        album = str("album"),
        musicUrl = str("music_url", "url", "link", "spotify_url", "apple_music_url", "original_url"),
        description = str("description", "text", "body", "message"),
        originalUrl = str("original_url"),
        previewUrl = str("preview_url"),
        trackName = str("track_name"),
        artistName = str("artist_name"),
        albumArtUrl = str("album_art_url", "artworkUrl100", "image_url", "cover_url"),
        eventCategories = parseEventCategories(obj),
        locationName = str("location_name", "place_name", "venue_name"),
        formattedAddress = str("formatted_address", "address", "display_address"),
        raw = obj,
    )
}

private fun parseEventCategories(obj: JsonObject): List<String> {
    val el = obj["event_categories"] ?: obj["eventCategories"] ?: return emptyList()
    return when (el) {
        is JsonArray ->
            el.mapNotNull { item ->
                (item as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            }
        is JsonPrimitive ->
            el.contentOrNull
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        else -> emptyList()
    }.distinct()
}

/** Who may see a dropped beacon on the map (mirrors `beacon_visibility_audience` on click-web). */
enum class BeaconVisibilityAudience(
    val apiValue: String,
) {
    EVERYONE("everyone"),
    CONNECTIONS("connections"),
    CORE_CONNECTIONS("core_connections"),
    ;

    companion object {
        fun fromRaw(value: String?): BeaconVisibilityAudience =
            when (value?.trim()?.lowercase()) {
                "connections", "connection" -> CONNECTIONS
                "core_connections", "core", "core_connections_only" -> CORE_CONNECTIONS
                else -> EVERYONE
            }
    }
}

@Serializable
data class MapBeaconInsert(
    val kind: String,
    val lat: Double,
    val lon: Double,
    val metadata: JsonObject? = null,
    /** For non-soundtrack beacons: TTL from creation; omit for soundtrack (server default 7 days). */
    @SerialName("ttl_ms") val ttlMs: Long? = null,
    /** Explicit expiry ISO-8601 (event beacons use scheduled end time). */
    @SerialName("expires_at") val expiresAtIso: String? = null,
    @SerialName("show_creator_name") val showCreatorName: Boolean = false,
    @SerialName("visibility_audience") val visibilityAudience: String = BeaconVisibilityAudience.EVERYONE.apiValue,
    /** Active collaboration session — server applies Squad pin 2× radius/TTL when valid. */
    @SerialName("encounter_id") val encounterId: String? = null,
)

/**
 * One row from `public.map_beacons` (or Edge Function projection).
 */
data class MapBeacon(
    val id: String,
    val kind: MapBeaconKind,
    val latitude: Double,
    val longitude: Double,
    val metadata: MapBeaconMetadata,
    val createdByUserId: String? = null,
    val createdAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    /** Raw `beacon_type` from PostgREST / API (e.g. `hazard`, `utility`) for tint + labels. */
    val sourceBeaconType: String? = null,
    val showCreatorName: Boolean = false,
    val creatorDisplayName: String? = null,
    val visibilityAudience: BeaconVisibilityAudience = BeaconVisibilityAudience.EVERYONE,
)

fun parseMapBeaconRows(element: JsonElement): List<MapBeacon> =
    when (element) {
        is JsonArray -> element.mapNotNull { parseMapBeaconRow(it) }
        is JsonObject -> {
            val single = parseMapBeaconRow(element)
            if (single != null) listOf(single) else emptyList()
        }
        is JsonPrimitive -> {
            val text = element.contentOrNull?.trim().orEmpty()
            if (text.isEmpty()) {
                emptyList()
            } else {
                val parsed = beaconMetadataJson.parseToJsonElement(text)
                if (parsed is JsonPrimitive) {
                    emptyList()
                } else {
                    parseMapBeaconRows(parsed)
                }
            }
        }
        else -> emptyList()
    }

private fun parseMapBeaconRow(element: JsonElement): MapBeacon? {
    val obj = element as? JsonObject ?: return null

    fun dbl(vararg keys: String): Double? {
        for (k in keys) {
            when (val v = obj[k]) {
                is JsonPrimitive ->
                    v.contentOrNull
                        ?.toDoubleOrNull()
                        ?.takeIf { it.isFinite() }
                        ?.let { return it }
                else -> continue
            }
        }
        return null
    }

    fun strKey(vararg keys: String): String? {
        for (k in keys) {
            val p = obj[k] as? JsonPrimitive
            val s = p?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            if (s != null) return s
        }
        return null
    }
    val id = strKey("id", "beacon_id", "beaconId") ?: return null
    // Prefer canonical `beacon_type` over a generic `kind` column — some projections send the same
    // `kind` for every row while `beacon_type` stays specific (fixes uniform marker tint on the map).
    val sourceBeaconType = strKey("beacon_type", "beaconType")
    val kindRaw = sourceBeaconType ?: strKey("kind", "type", "category")
    val kind = MapBeaconKind.fromRaw(kindRaw)
    val lat = dbl("lat", "latitude") ?: return null
    val lon = dbl("lon", "longitude", "lng") ?: return null
    val metaEl = obj["metadata"] ?: obj["meta"]
    val meta = parseMapBeaconMetadata(metaEl as? JsonElement)
    val createdBy = strKey("created_by", "user_id", "author_id", "creator_id")
    val createdAt =
        strKey("created_at")?.let { parseEpochMs(it) }
            ?: (obj["created_at"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
    val expiresAt = strKey("expires_at", "expiresAt")?.let { parseEpochMs(it) }
    val showCreatorName =
        (obj["show_creator_name"] as? JsonPrimitive)?.let { prim ->
            prim.booleanOrNull ?: when (prim.contentOrNull?.trim()?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
        } ?: false
    val creatorDisplayName = strKey("creator_name", "creatorName")
    val visibilityAudience = BeaconVisibilityAudience.fromRaw(strKey("visibility_audience", "visibilityAudience"))
    return MapBeacon(
        id = id,
        kind = kind,
        latitude = lat,
        longitude = lon,
        metadata = meta,
        createdByUserId = createdBy,
        createdAtEpochMs = createdAt,
        expiresAtEpochMs = expiresAt,
        sourceBeaconType = sourceBeaconType,
        showCreatorName = showCreatorName,
        creatorDisplayName = creatorDisplayName,
        visibilityAudience = visibilityAudience,
    )
}

/**
 * Accepts epoch millis strings and ISO-8601, plus common Postgres timestamptz
 * forms like `2026-05-30 01:40:47.869682+00` (space separator, short offset).
 */
internal fun parseEpochMs(value: String): Long? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toLongOrNull()?.let { return it }
    runCatching { Instant.parse(trimmed).toEpochMilliseconds() }.getOrNull()?.let { return it }
    val withT = trimmed.replace(' ', 'T')
    runCatching { Instant.parse(withT).toEpochMilliseconds() }.getOrNull()?.let { return it }
    // `…+00` / `…-07` → `…+00:00` / `…-07:00`
    val withColonOffset =
        Regex("""([+-]\d{2})$""").replace(withT) { match ->
            "${match.groupValues[1]}:00"
        }
    runCatching { Instant.parse(withColonOffset).toEpochMilliseconds() }.getOrNull()?.let { return it }
    // `…+0000` → `…+00:00`
    val withSplitOffset =
        Regex("""([+-])(\d{2})(\d{2})$""").replace(withColonOffset) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}:${match.groupValues[3]}"
        }
    return runCatching { Instant.parse(withSplitOffset).toEpochMilliseconds() }.getOrNull()
}
