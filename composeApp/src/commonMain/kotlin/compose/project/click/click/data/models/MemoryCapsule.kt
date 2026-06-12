package compose.project.click.click.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import compose.project.click.click.sensors.computeBarometricElevationMeters

@Serializable
data class MemoryCapsule(
    val connectionId: String,
    val locationName: String? = null,
    val geoLocation: GeoLocation? = null,
    val connectedAtMs: Long,
    val weatherSnapshot: WeatherSnapshot? = null,
    val contextTag: ContextTag? = null,
    val photoUri: String? = null,
    val noiseLevelCategory: NoiseLevelCategory? = null,
    val exactNoiseLevelDb: Double? = null,
    val heightCategory: HeightCategory? = null,
    val exactBarometricElevationMeters: Double? = null
)

/**
 * Canonical wire format for [connection_encounters.weather_snapshot] and QR / proximity payloads:
 * a stringified JSON object with these keys (camelCase).
 */
@Serializable
data class WeatherSnapshot(
    val iconCode: String = "",
    val condition: String = "",
    val windSpeedKph: Double? = null,
    val pressureMslHpa: Double? = null,
    val temperatureCelsius: Double? = null,
    val windDirectionDegrees: Int? = null,
)

private val weatherPayloadJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
}

/** Stringified JSON for API / DB `weather_snapshot` text or jsonb. */
fun WeatherSnapshot.toConnectionPayloadWeatherJson(): String =
    weatherPayloadJson.encodeToString(WeatherSnapshot.serializer(), this)

@Serializable
enum class NoiseLevelCategory {
    VERY_QUIET,
    QUIET,
    MODERATE,
    LOUD,
    VERY_LOUD
}

@Serializable
enum class HeightCategory {
    BELOW_GROUND,
    GROUND_LEVEL,
    ELEVATED,
    HIGH_RISE
}

fun deriveHeightCategory(altitudeMeters: Double?): HeightCategory? {
    val altitude = altitudeMeters ?: return null
    return when {
        altitude < -3.0 -> HeightCategory.BELOW_GROUND
        altitude < 8.0 -> HeightCategory.GROUND_LEVEL
        altitude < 35.0 -> HeightCategory.ELEVATED
        else -> HeightCategory.HIGH_RISE
    }
}

fun calibrateBarometricElevationMeters(
    stationPressureHpa: Double?,
    seaLevelPressureHpa: Double?,
): Double? = computeBarometricElevationMeters(stationPressureHpa, seaLevelPressureHpa)?.elevationMeters

@Serializable
data class ContextTag(
    val id: String,
    val label: String,
    val emoji: String
)