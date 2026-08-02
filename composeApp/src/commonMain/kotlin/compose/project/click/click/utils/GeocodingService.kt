package compose.project.click.click.utils

import compose.project.click.click.util.redactedRestMessage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A place resolved from address search (Nominatim forward geocode) or current GPS.
 */
data class GeocodedPlace(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val shortLabel: String = displayName,
)

/**
 * Pure parse of Nominatim `/search` JSON → [GeocodedPlace] list (no network).
 */
fun parseNominatimSearchResults(body: String, limit: Int = 8): List<GeocodedPlace> {
    if (body.isBlank()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
    val rows = when (root) {
        is JsonArray -> root
        is JsonObject -> root["features"] as? JsonArray ?: return emptyList()
        else -> return emptyList()
    }
    return rows.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val lat = obj["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: return@mapNotNull null
        val lon = obj["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: return@mapNotNull null
        if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
        val display = obj["display_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (display.isEmpty()) return@mapNotNull null
        val short = shortLabelFromNominatim(obj, display)
        GeocodedPlace(
            latitude = lat,
            longitude = lon,
            displayName = display,
            shortLabel = short,
        )
    }.take(limit.coerceAtLeast(0))
}

/**
 * Rank geocode hits by word-search relevance and proximity to [nearLat]/[nearLon].
 * Higher score = better. Pure / unit-testable.
 */
fun rankGeocodedPlaces(
    places: List<GeocodedPlace>,
    query: String,
    nearLat: Double? = null,
    nearLon: Double? = null,
    limit: Int = 5,
): List<GeocodedPlace> {
    if (places.isEmpty()) return emptyList()
    val q = query.trim().lowercase()
    val tokens = q.split(Regex("\\s+")).filter { it.length >= 2 }
    val hasNear = nearLat != null && nearLon != null &&
        nearLat.isFinite() && nearLon.isFinite()
    return places
        .distinctBy { "${it.latitude},${it.longitude},${it.displayName}" }
        .map { place ->
            val hay = "${place.shortLabel} ${place.displayName}".lowercase()
            var textScore = 0.0
            if (q.isNotEmpty() && hay.contains(q)) textScore += 40.0
            if (place.shortLabel.lowercase().startsWith(q) || hay.startsWith(q)) textScore += 20.0
            tokens.forEach { token ->
                if (hay.contains(token)) textScore += 12.0
            }
            val distanceScore = if (hasNear) {
                val meters = haversineMeters(nearLat!!, nearLon!!, place.latitude, place.longitude)
                // 0–40: nearer places rank higher (full score within ~1km, fades by ~50km)
                (40.0 * (1.0 - (meters / 50_000.0).coerceIn(0.0, 1.0)))
            } else {
                0.0
            }
            place to (textScore + distanceScore)
        }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(limit.coerceAtLeast(0))
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    fun rad(deg: Double) = deg * PI / 180.0
    val p1 = rad(lat1)
    val p2 = rad(lat2)
    val dLat = rad(lat2 - lat1)
    val dLon = rad(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(p1) * cos(p2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}

private fun shortLabelFromNominatim(obj: JsonObject, displayName: String): String {
    val address = obj["address"]?.jsonObject
    if (address != null) {
        fun part(key: String): String? =
            address[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val houseNum = part("house_number")
        val road = part("road")
        val houseAndRoad = if (houseNum != null && road != null) "$houseNum $road" else null
        val short = houseAndRoad
            ?: part("building")
            ?: part("amenity")
            ?: part("leisure")
            ?: part("tourism")
            ?: listOfNotNull(road, part("neighbourhood") ?: part("suburb") ?: part("city"))
                .joinToString(", ")
                .ifEmpty { null }
        if (!short.isNullOrBlank()) return short
    }
    return displayName.substringBefore(',').trim().ifEmpty { displayName }
}

/**
 * Forward-geocode an address query via OpenStreetMap Nominatim.
 * Prefer results near [nearLat]/[nearLon] when provided (viewbox bias + client rank).
 */
object GeocodingService {
    suspend fun searchAddresses(
        query: String,
        limit: Int = 5,
        nearLat: Double? = null,
        nearLon: Double? = null,
    ): List<GeocodedPlace> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return try {
            val client = HttpClient()
            try {
                val fetchLimit = (limit * 2).coerceIn(5, 12)
                val response = client.get("https://nominatim.openstreetmap.org/search") {
                    parameter("q", trimmed)
                    parameter("format", "json")
                    parameter("addressdetails", "1")
                    parameter("limit", fetchLimit)
                    parameter("dedupe", "1")
                    if (
                        nearLat != null && nearLon != null &&
                        nearLat.isFinite() && nearLon.isFinite()
                    ) {
                        // ~0.35° box (~25–40km) bias without hard bounding.
                        val delta = 0.35
                        parameter(
                            "viewbox",
                            "${nearLon - delta},${nearLat + delta},${nearLon + delta},${nearLat - delta}",
                        )
                        parameter("bounded", "0")
                    }
                    headers {
                        append("User-Agent", "ClickApp/1.0")
                    }
                }
                val parsed = parseNominatimSearchResults(response.bodyAsText(), limit = fetchLimit)
                rankGeocodedPlaces(
                    places = parsed,
                    query = trimmed,
                    nearLat = nearLat,
                    nearLon = nearLon,
                    limit = limit,
                )
            } finally {
                client.close()
            }
        } catch (e: Exception) {
            println("GeocodingService: Nominatim search failed: ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    /**
     * Reverse-geocode GPS coordinates to the closest address label.
     * Used when dropping an event at “my location” so we never persist “Current location”.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodedPlace? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude == 0.0 && longitude == 0.0) return null
        return try {
            val client = HttpClient()
            try {
                val response = client.get("https://nominatim.openstreetmap.org/reverse") {
                    parameter("lat", latitude)
                    parameter("lon", longitude)
                    parameter("format", "json")
                    parameter("addressdetails", "1")
                    parameter("zoom", "18")
                    headers {
                        append("User-Agent", "ClickApp/1.0")
                    }
                }
                val body = response.bodyAsText()
                val obj = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                    ?: return null
                val display = obj["display_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (display.isEmpty()) return null
                val short = shortLabelFromNominatim(obj, display)
                GeocodedPlace(
                    latitude = latitude,
                    longitude = longitude,
                    displayName = display,
                    shortLabel = short.ifBlank { display.substringBefore(',').trim() },
                )
            } finally {
                client.close()
            }
        } catch (e: Exception) {
            println("GeocodingService: Nominatim reverse failed: ${e.redactedRestMessage()}")
            null
        }
    }
}

/** Metadata keys for event beacon address labels. */
const val EVENT_LOCATION_NAME_METADATA_KEY = "location_name"
const val EVENT_FORMATTED_ADDRESS_METADATA_KEY = "formatted_address"
