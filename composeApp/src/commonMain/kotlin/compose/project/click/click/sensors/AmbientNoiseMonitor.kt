package compose.project.click.click.sensors

import compose.project.click.click.data.models.NoiseLevelCategory

interface AmbientNoiseMonitor {
    val hasPermission: Boolean

    suspend fun sampleNoiseLevel(durationMs: Int = 2000): NoiseLevelCategory?
}

object NoOpAmbientNoiseMonitor : AmbientNoiseMonitor {
    override val hasPermission: Boolean = false

    override suspend fun sampleNoiseLevel(durationMs: Int): NoiseLevelCategory? = null
}