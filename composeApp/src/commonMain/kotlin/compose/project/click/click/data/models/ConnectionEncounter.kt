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
    /** Stringified JSON with structured place data (e.g. `address.neighbourhood`). */
    @SerialName("semantic_location")
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
