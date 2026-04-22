package compose.project.click.click.sensors

import compose.project.click.click.data.models.NoiseLevelCategory

/**
 * Maps an approximate calibrated dB reading to a telemetry tier.
 * Thresholds: below 35 very quiet; 35 until 55 quiet; 55 until 75 moderate; 75 until 90 loud; 90 and above very loud.
 */
fun noiseLevelCategoryFromApproximateDb(decibels: Double): NoiseLevelCategory = when {
    decibels < 35.0 -> NoiseLevelCategory.VERY_QUIET
    decibels < 55.0 -> NoiseLevelCategory.QUIET
    decibels < 75.0 -> NoiseLevelCategory.MODERATE
    decibels < 90.0 -> NoiseLevelCategory.LOUD
    else -> NoiseLevelCategory.VERY_LOUD
}

data class AmbientNoiseSample(
    val category: NoiseLevelCategory,
    val decibels: Double
)

interface AmbientNoiseMonitor {
    val hasPermission: Boolean

    suspend fun sampleNoiseReading(durationMs: Int = 2000): AmbientNoiseSample?

    suspend fun sampleNoiseLevel(durationMs: Int = 2000): NoiseLevelCategory? =
        sampleNoiseReading(durationMs)?.category
}

object NoOpAmbientNoiseMonitor : AmbientNoiseMonitor {
    override val hasPermission: Boolean = false

    override suspend fun sampleNoiseReading(durationMs: Int): AmbientNoiseSample? = null
}