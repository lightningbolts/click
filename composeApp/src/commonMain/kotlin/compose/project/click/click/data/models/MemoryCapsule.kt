package compose.project.click.click.data.models

import kotlinx.serialization.Serializable

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
    val heightCategory: HeightCategory? = null
)

@Serializable
data class WeatherSnapshot(
    val condition: String,
    val temperatureCelsius: Float,
    val iconCode: String? = null
)

@Serializable
enum class NoiseLevelCategory {
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

@Serializable
data class ContextTag(
    val id: String,
    val label: String,
    val emoji: String
)