package compose.project.click.click.sensors

import compose.project.click.click.data.models.HeightCategory

data class BarometricHeightSample(
    val category: HeightCategory,
    val elevationMeters: Double,
    val pressureHpa: Double? = null,
    /** False when elevation used the 1013.25 hPa ISA fallback because live MSL pressure was unavailable. */
    val isCalibrated: Boolean = true,
)

interface BarometricHeightMonitor {
    val isAvailable: Boolean

    /**
     * Starts low-rate barometric sampling so a short [sampleHeightReading] window can reuse
     * recent data instead of cold-starting the driver (especially important on iOS).
     */
    fun ensureBackgroundCaching() {}

    /** Stops background sampling started by [ensureBackgroundCaching]. */
    fun releaseBackgroundCaching() {}

    suspend fun sampleHeightReading(
        durationMs: Int = 1500,
        latitude: Double? = null,
        longitude: Double? = null,
    ): BarometricHeightSample?

    suspend fun sampleHeightCategory(
        durationMs: Int = 1500,
        latitude: Double? = null,
        longitude: Double? = null,
    ): HeightCategory? = sampleHeightReading(durationMs, latitude, longitude)?.category
}

object NoOpBarometricHeightMonitor : BarometricHeightMonitor {
    override val isAvailable: Boolean = false

    override suspend fun sampleHeightReading(
        durationMs: Int,
        latitude: Double?,
        longitude: Double?,
    ): BarometricHeightSample? = null
}