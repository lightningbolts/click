package compose.project.click.click.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionEncounter(
    val id: String,
    @SerialName("connection_id")
    val connectionId: String,
    @SerialName("encountered_at")
    val encounteredAt: String,
    @SerialName("location_name")
    val locationName: String? = null,
    @SerialName("display_location")
    val displayLocation: String? = null,
    /** Structured place payload from PostgREST as JSON and/or string — see [SemanticLocationWireSerializer]. */
    @SerialName("semantic_location")
    @Serializable(with = SemanticLocationWireSerializer::class)
    val semanticLocation: String? = null,
    @SerialName("gps_lat")
    val gpsLat: Double? = null,
    @SerialName("gps_lon")
    val gpsLon: Double? = null,
    @SerialName("weather_snapshot")
    @Serializable(with = FlexibleWeatherSnapshotSerializer::class)
    val weatherSnapshot: WeatherSnapshot? = null,
    @SerialName("noise_level")
    val noiseLevel: String? = null,
    @SerialName("elevation_category")
    val elevationCategory: String? = null,
    @SerialName("exact_noise_level_db")
    val exactNoiseLevelDb: Double? = null,
    @SerialName("exact_barometric_elevation_m")
    val exactBarometricElevationM: Double? = null,
    @SerialName("relative_altitude_m")
    val relativeAltitudeM: Double? = null,
    @SerialName("lux_level")
    val luxLevel: Double? = null,
    @SerialName("motion_variance")
    val motionVariance: Double? = null,
    @SerialName("compass_azimuth")
    val compassAzimuth: Double? = null,
    @SerialName("battery_level")
    val batteryLevel: Int? = null,
    @SerialName("context_tags")
    val contextTags: List<String> = emptyList(),
) {
    fun encounteredAtInstant(): Instant? =
        encounteredAt.trim().takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
}

private fun ConnectionEncounter.richnessScore(): Int {
    var score = 0
    if (!locationName.isNullOrBlank()) score += 2
    if (!displayLocation.isNullOrBlank()) score += 1
    if (!semanticLocation.isNullOrBlank()) score += 2
    if (gpsLat != null && gpsLon != null) score += 3
    if (weatherSnapshot != null) score += 3
    if (!noiseLevel.isNullOrBlank()) score += 1
    if (!elevationCategory.isNullOrBlank()) score += 1
    if (exactNoiseLevelDb != null) score += 2
    if (exactBarometricElevationM != null) score += 2
    if (relativeAltitudeM != null) score += 1
    if (luxLevel != null) score += 1
    if (motionVariance != null) score += 1
    if (compassAzimuth != null) score += 1
    if (batteryLevel != null) score += 1
    score += contextTags.size
    return score
}

private inline fun <T> Iterable<ConnectionEncounter>.firstValue(select: (ConnectionEncounter) -> T?): T? =
    firstNotNullOfOrNull(select)

private fun String?.ifPresent(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun mergeEncounterRows(rows: List<ConnectionEncounter>): ConnectionEncounter {
    val ranked = rows.sortedWith(
        compareByDescending<ConnectionEncounter> { it.richnessScore() }
            .thenByDescending { it.encounteredAtInstant() ?: Instant.DISTANT_PAST }
            .thenBy { it.id },
    )
    val base = ranked.first()
    val tags = rows.flatMap { it.contextTags }.mapNotNull { it.ifPresent() }.distinct()
    val gpsRow = ranked.firstOrNull { it.gpsLat != null && it.gpsLon != null }
    return base.copy(
        locationName = ranked.firstValue { it.locationName.ifPresent() },
        displayLocation = ranked.firstValue { it.displayLocation.ifPresent() },
        semanticLocation = ranked.firstValue { it.semanticLocation.ifPresent() },
        gpsLat = gpsRow?.gpsLat,
        gpsLon = gpsRow?.gpsLon,
        weatherSnapshot = ranked.firstValue { it.weatherSnapshot },
        noiseLevel = ranked.firstValue { it.noiseLevel.ifPresent() },
        elevationCategory = ranked.firstValue { it.elevationCategory.ifPresent() },
        exactNoiseLevelDb = ranked.firstValue { it.exactNoiseLevelDb?.takeIf { v -> v.isFinite() } },
        exactBarometricElevationM = ranked.firstValue { it.exactBarometricElevationM?.takeIf { v -> v.isFinite() } },
        relativeAltitudeM = ranked.firstValue { it.relativeAltitudeM?.takeIf { v -> v.isFinite() } },
        luxLevel = ranked.firstValue { it.luxLevel?.takeIf { v -> v.isFinite() } },
        motionVariance = ranked.firstValue { it.motionVariance?.takeIf { v -> v.isFinite() } },
        compassAzimuth = ranked.firstValue { it.compassAzimuth?.takeIf { v -> v.isFinite() } },
        batteryLevel = ranked.firstValue { it.batteryLevel?.takeIf { v -> v in 0..100 } },
        contextTags = tags,
    )
}

fun List<ConnectionEncounter>.mergeRichestEncounterEvents(): List<ConnectionEncounter> =
    groupBy { "${it.connectionId}|${it.encounteredAt}" }
        .values
        .map { rows -> mergeEncounterRows(rows) }

fun ConnectionEncounter.toMemoryCapsule(): MemoryCapsule {
    val at = encounteredAtInstant()?.toEpochMilliseconds() ?: 0L
    val tag = contextTags.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let {
        ContextTag(id = "custom", label = it, emoji = "✏️")
    }
    val noise = noiseLevel?.trim()?.takeIf { it.isNotEmpty() }?.let {
        runCatching { NoiseLevelCategory.valueOf(it.uppercase().replace(' ', '_')) }.getOrNull()
    }
    val height = elevationCategory?.trim()?.takeIf { it.isNotEmpty() }?.let {
        runCatching { HeightCategory.valueOf(it.uppercase().replace(' ', '_')) }.getOrNull()
    }
    val geo = gpsLat?.let { la ->
        gpsLon?.let { lo ->
            if (la.isFinite() && lo.isFinite() && !(la == 0.0 && lo == 0.0)) GeoLocation(lat = la, lon = lo) else null
        }
    }
    return MemoryCapsule(
        connectionId = this.connectionId,
        locationName = locationName?.trim()?.takeIf { it.isNotEmpty() }
            ?: displayLocation?.trim()?.takeIf { it.isNotEmpty() },
        geoLocation = geo,
        connectedAtMs = at,
        weatherSnapshot = weatherSnapshot,
        contextTag = tag,
        photoUri = null,
        noiseLevelCategory = noise,
        exactNoiseLevelDb = exactNoiseLevelDb?.takeIf { it.isFinite() },
        heightCategory = height,
        exactBarometricElevationMeters = exactBarometricElevationM?.takeIf { it.isFinite() },
    )
}
