package compose.project.click.click.sensors

import compose.project.click.click.data.models.HeightCategory

data class BarometricHeightSample(
    val category: HeightCategory,
    val elevationMeters: Double,
    val pressureHpa: Double? = null
)

interface BarometricHeightMonitor {
    val isAvailable: Boolean

    suspend fun sampleHeightReading(durationMs: Int = 1500): BarometricHeightSample?

    suspend fun sampleHeightCategory(durationMs: Int = 1500): HeightCategory? =
        sampleHeightReading(durationMs)?.category
}

object NoOpBarometricHeightMonitor : BarometricHeightMonitor {
    override val isAvailable: Boolean = false

    override suspend fun sampleHeightReading(durationMs: Int): BarometricHeightSample? = null
}