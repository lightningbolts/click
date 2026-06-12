package compose.project.click.click.sensors

import compose.project.click.click.data.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps a platform [BarometricHeightMonitor] and recalibrates elevation using Open-Meteo MSL pressure.
 * Weather fetch and altitude math run on [Dispatchers.Default] to avoid blocking the UI thread.
 */
class CalibratedBarometricHeightMonitor(
    private val delegate: BarometricHeightMonitor,
    private val weatherService: WeatherService,
) : BarometricHeightMonitor {

    override val isAvailable: Boolean
        get() = delegate.isAvailable

    override fun ensureBackgroundCaching() {
        delegate.ensureBackgroundCaching()
    }

    override fun releaseBackgroundCaching() {
        delegate.releaseBackgroundCaching()
    }

    override suspend fun sampleHeightReading(
        durationMs: Int,
        latitude: Double?,
        longitude: Double?,
    ): BarometricHeightSample? = withContext(Dispatchers.Default) {
        val raw = delegate.sampleHeightReading(durationMs) ?: return@withContext null
        calibrateSample(raw, latitude, longitude)
    }

    private suspend fun calibrateSample(
        raw: BarometricHeightSample,
        latitude: Double?,
        longitude: Double?,
    ): BarometricHeightSample? {
        val pressureHpa = raw.pressureHpa ?: return raw
        val weather = fetchWeatherIfCoordinatesValid(latitude, longitude)
        return barometricHeightSampleFromPressure(
            pressureHpa = pressureHpa,
            pressureMslHpa = weather?.pressureMslHpa,
        ) ?: raw
    }

    private suspend fun fetchWeatherIfCoordinatesValid(latitude: Double?, longitude: Double?) =
        if (
            latitude != null &&
            longitude != null &&
            latitude.isFinite() &&
            longitude.isFinite() &&
            !(latitude == 0.0 && longitude == 0.0)
        ) {
            weatherService.fetchWeather(latitude, longitude)
        } else {
            null
        }
}
