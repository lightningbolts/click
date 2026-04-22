package compose.project.click.click.sensors // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import compose.project.click.click.data.models.HeightCategory // pragma: allowlist secret
import compose.project.click.click.data.models.NoiseLevelCategory // pragma: allowlist secret
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Short capture window for connection context so the "Sparking a new connection" state appears quickly.
 * Full-length sampling remains the default on [AmbientNoiseMonitor] / [BarometricHeightMonitor] for settings flows.
 */
const val CONNECTION_CONTEXT_SENSOR_NOISE_SAMPLE_MS: Int = 350

const val CONNECTION_CONTEXT_SENSOR_BAROMETRIC_SAMPLE_MS: Int = 350

data class ConnectionSensorContext(
    val noiseLevelCategory: NoiseLevelCategory?,
    val exactNoiseLevelDb: Double?,
    val heightCategory: HeightCategory?,
    val exactBarometricElevationMeters: Double?,
    val exactBarometricPressureHpa: Double? = null,
)

private val LocalAmbientNoiseMonitor = staticCompositionLocalOf<AmbientNoiseMonitor> {
    error("AmbientNoiseMonitor not provided; wrap with AmbientNoiseMonitorProvider")
}

private val LocalBarometricHeightMonitor = staticCompositionLocalOf<BarometricHeightMonitor> {
    error("BarometricHeightMonitor not provided; wrap with BarometricHeightMonitorProvider")
}

object AmbientNoiseMonitorProvider {
    val current: AmbientNoiseMonitor
        @Composable
        get() = LocalAmbientNoiseMonitor.current
}

object BarometricHeightMonitorProvider {
    val current: BarometricHeightMonitor
        @Composable
        get() = LocalBarometricHeightMonitor.current
}

@Composable
fun AmbientNoiseMonitorProvider(
    monitor: AmbientNoiseMonitor,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAmbientNoiseMonitor provides monitor, content = content)
}

@Composable
fun BarometricHeightMonitorProvider(
    monitor: BarometricHeightMonitor,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBarometricHeightMonitor provides monitor, content = content)
}

@Composable
fun ConnectionSensorMonitorsProvider(
    ambientNoiseMonitor: AmbientNoiseMonitor,
    barometricHeightMonitor: BarometricHeightMonitor,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAmbientNoiseMonitor provides ambientNoiseMonitor,
        LocalBarometricHeightMonitor provides barometricHeightMonitor,
        content = content,
    )
}

suspend fun captureConnectionSensorContext(
    ambientNoiseMonitor: AmbientNoiseMonitor,
    barometricHeightMonitor: BarometricHeightMonitor,
    ambientNoiseOptIn: Boolean,
    barometricContextOptIn: Boolean,
): ConnectionSensorContext {
    if (!ambientNoiseOptIn && !barometricContextOptIn) {
        return ConnectionSensorContext(
            noiseLevelCategory = null,
            exactNoiseLevelDb = null,
            heightCategory = null,
            exactBarometricElevationMeters = null,
            exactBarometricPressureHpa = null,
        )
    }
    return coroutineScope {
        val noiseDeferred = async {
            if (!ambientNoiseOptIn) {
                null
            } else {
                ambientNoiseMonitor.sampleNoiseReading(CONNECTION_CONTEXT_SENSOR_NOISE_SAMPLE_MS)
            }
        }
        val baroDeferred = async {
            if (!barometricContextOptIn) {
                null
            } else {
                barometricHeightMonitor.sampleHeightReading(CONNECTION_CONTEXT_SENSOR_BAROMETRIC_SAMPLE_MS)
            }
        }
        val noise = noiseDeferred.await()
        val baro = baroDeferred.await()
        ConnectionSensorContext(
            noiseLevelCategory = noise?.category,
            exactNoiseLevelDb = noise?.decibels,
            heightCategory = baro?.category,
            exactBarometricElevationMeters = baro?.elevationMeters,
            exactBarometricPressureHpa = baro?.pressureHpa,
        )
    }
}
