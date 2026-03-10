package compose.project.click.click.sensors

import compose.project.click.click.data.models.HeightCategory

interface BarometricHeightMonitor {
    val isAvailable: Boolean

    suspend fun sampleHeightCategory(durationMs: Int = 1500): HeightCategory?
}

object NoOpBarometricHeightMonitor : BarometricHeightMonitor {
    override val isAvailable: Boolean = false

    override suspend fun sampleHeightCategory(durationMs: Int): HeightCategory? = null
}