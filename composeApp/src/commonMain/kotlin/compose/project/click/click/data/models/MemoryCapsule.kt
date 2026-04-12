package compose.project.click.click.data.models

import kotlinx.serialization.Serializable
import kotlin.math.pow

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

@Serializable
data class WeatherSnapshot(
    val condition: String? = null,
    val temperatureCelsius: Float? = null,
    val iconCode: String? = null,
    val windSpeedKph: Float? = null,
    val windDirectionDegrees: Int? = null,
    val pressureMslHpa: Double? = null,
)

/** Compact label for API payloads and jsonb `weather_snapshot` string storage. */
fun WeatherSnapshot.toSnapshotLabel(): String {
    val cond = condition?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown"
    val t = temperatureCelsius
    return if (t != null && t.isFinite()) "${t.toInt()}°C, $cond" else cond
}

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
    seaLevelPressureHpa: Double?
): Double? {
    val stationPressure = stationPressureHpa?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val seaLevelPressure = seaLevelPressureHpa?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val pressureRatio = stationPressure / seaLevelPressure
    if (!pressureRatio.isFinite() || pressureRatio <= 0.0) return null

    val altitude = 44330.0 * (1.0 - pressureRatio.pow(0.1903))
    return altitude.takeIf { it.isFinite() && it in -500.0..12000.0 }
}

@Serializable
data class ContextTag(
    val id: String,
    val label: String,
    val emoji: String
)