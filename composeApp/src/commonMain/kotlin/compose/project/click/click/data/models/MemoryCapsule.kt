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
    val noiseLevelCategory: NoiseLevelCategory? = null
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
data class ContextTag(
    val id: String,
    val label: String,
    val emoji: String
)