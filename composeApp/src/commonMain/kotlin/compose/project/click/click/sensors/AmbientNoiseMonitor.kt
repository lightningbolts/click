package compose.project.click.click.sensors

import compose.project.click.click.data.models.NoiseLevelCategory

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