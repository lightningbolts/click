package compose.project.click.click.sensors

import compose.project.click.click.data.models.HeightCategory
import compose.project.click.click.data.models.deriveHeightCategory
import kotlin.math.pow

/** ISA standard sea-level pressure (hPa) — fallback when Open-Meteo MSL is unavailable. */
const val STANDARD_SEA_LEVEL_PRESSURE_HPA = 1013.25

/** Barometric formula exponent: 1 / 5.255 */
const val BAROMETRIC_ALTITUDE_EXPONENT = 1.0 / 5.255

const val BAROMETRIC_ALTITUDE_SCALE_M = 44330.0

const val BAROMETRIC_ALTITUDE_MIN_M = -500.0

const val BAROMETRIC_ALTITUDE_MAX_M = 12000.0

data class BarometricElevationResult(
    val elevationMeters: Double,
    /** True when elevation used live Open-Meteo mean sea-level pressure; false when falling back to [STANDARD_SEA_LEVEL_PRESSURE_HPA]. */
    val isCalibrated: Boolean,
)

/**
 * Altitude = 44330 * (1 - (rawPressureHpa / pressureMslHpa)^(1/5.255))
 *
 * When [pressureMslHpa] is unavailable, uses [STANDARD_SEA_LEVEL_PRESSURE_HPA] and marks the result uncalibrated.
 */
fun computeBarometricElevationMeters(
    rawPressureHpa: Double?,
    pressureMslHpa: Double?,
): BarometricElevationResult? {
    val stationPressure = rawPressureHpa?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val liveMsl = pressureMslHpa?.takeIf { it.isFinite() && it > 0.0 }
    val seaLevelPressure = liveMsl ?: STANDARD_SEA_LEVEL_PRESSURE_HPA
    val pressureRatio = stationPressure / seaLevelPressure
    if (!pressureRatio.isFinite() || pressureRatio <= 0.0) return null

    val altitude = BAROMETRIC_ALTITUDE_SCALE_M * (1.0 - pressureRatio.pow(BAROMETRIC_ALTITUDE_EXPONENT))
    val elevationMeters = altitude.takeIf { it.isFinite() && it in BAROMETRIC_ALTITUDE_MIN_M..BAROMETRIC_ALTITUDE_MAX_M }
        ?: return null

    return BarometricElevationResult(
        elevationMeters = elevationMeters,
        isCalibrated = liveMsl != null,
    )
}

fun barometricHeightSampleFromPressure(
    pressureHpa: Double,
    pressureMslHpa: Double?,
): BarometricHeightSample? {
    val result = computeBarometricElevationMeters(pressureHpa, pressureMslHpa) ?: return null
    val category = deriveHeightCategory(result.elevationMeters) ?: return null
    return BarometricHeightSample(
        category = category,
        elevationMeters = result.elevationMeters,
        pressureHpa = pressureHpa,
        isCalibrated = result.isCalibrated,
    )
}

fun fallbackBarometricHeightSampleFromPressure(pressureHpa: Double): BarometricHeightSample? =
    barometricHeightSampleFromPressure(pressureHpa, pressureMslHpa = null)
