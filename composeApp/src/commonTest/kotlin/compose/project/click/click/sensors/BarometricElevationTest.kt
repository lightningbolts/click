package compose.project.click.click.sensors

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BarometricElevationTest {

    @Test
    fun computeElevation_usesMslPressureWhenAvailable() {
        val result = computeBarometricElevationMeters(
            rawPressureHpa = 950.0,
            pressureMslHpa = 1010.0,
        )
        assertNotNull(result)
        assertTrue(result.isCalibrated)
        assertTrue(result.elevationMeters > 400.0)
        assertTrue(result.elevationMeters < 700.0)
    }

    @Test
    fun computeElevation_fallsBackToStandardPressureWhenMslUnavailable() {
        val withMsl = computeBarometricElevationMeters(980.0, 1013.25)
        val withoutMsl = computeBarometricElevationMeters(980.0, null)
        assertNotNull(withMsl)
        assertNotNull(withoutMsl)
        assertFalse(withoutMsl.isCalibrated)
        assertEquals(withMsl!!.elevationMeters, withoutMsl.elevationMeters, absoluteTolerance = 0.001)
    }

    @Test
    fun barometricFormula_matchesSpecifiedExponent() {
        val ratio = 980.0 / 1013.25
        val expected = 44330.0 * (1.0 - ratio.pow(1.0 / 5.255))
        val actual = computeBarometricElevationMeters(980.0, null)?.elevationMeters
        assertNotNull(actual)
        assertEquals(expected, actual, absoluteTolerance = 0.001)
    }

    @Test
    fun fallbackSample_isMarkedUncalibrated() {
        val sample = fallbackBarometricHeightSampleFromPressure(990.0)
        assertNotNull(sample)
        assertFalse(sample.isCalibrated)
        assertEquals(990.0, sample.pressureHpa)
    }
}
