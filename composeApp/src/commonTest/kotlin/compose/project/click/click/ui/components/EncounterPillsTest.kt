package compose.project.click.click.ui.components

import compose.project.click.click.data.models.ConnectionEncounter
import compose.project.click.click.data.models.WeatherSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** G4-12: pressure, height and noise-band pills. */
class EncounterPillsTest {
    private fun enc(
        noise: String? = null,
        db: Double? = null,
        kpa: Double? = null,
        msl: Double? = null,
        baro: Double? = null,
        gpsAlt: Double? = null,
        gpsVert: Double? = null,
        band: String? = null,
        rel: Double? = null,
    ) = ConnectionEncounter(
        id = "e",
        connectionId = "c",
        encounteredAt = "2026-10-06T12:00:00.000Z",
        noiseLevel = noise,
        exactNoiseLevelDb = db,
        barometricPressureKpa = kpa,
        weatherSnapshot = msl?.let { WeatherSnapshot(pressureMslHpa = it) },
        exactBarometricElevationM = baro,
        gpsAltitudeM = gpsAlt,
        gpsVerticalAccuracyM = gpsVert,
        elevationCategory = band,
        relativeAltitudeM = rel,
    )

    @Test
    fun pressurePrefersThePhonesBarometer() {
        assertEquals("1016 hPa", enc(kpa = 101.6).metricPressureLabel())
        assertEquals("1013 hPa", enc(msl = 1013.2).metricPressureLabel())
        assertNull(enc().metricPressureLabel())
    }

    @Test
    fun heightUsesBandThenBarometerThenPressureThenAccurateGps() {
        assertEquals("Elevated · 12 m", enc(band = "ELEVATED", rel = 12.0, baro = 40.0).metricHeightLabel())
        assertEquals("Alt. 40 m", enc(baro = 40.4).metricHeightLabel())
        // 44330·(1−(1008.25/1013.25)^(1/5.255)) ≈ 41.6 m
        assertEquals("Alt. 42 m", enc(kpa = 100.825, msl = 1013.25).metricHeightLabel())
        assertEquals("Alt. 55 m", enc(gpsAlt = 55.0, gpsVert = 8.0).metricHeightLabel())
        assertNull(enc(gpsAlt = 55.0, gpsVert = 12.0).metricHeightLabel())
    }

    @Test
    fun noiseShowsTheBandNeverDecibels() {
        assertEquals("Quiet", enc(noise = "QUIET").metricNoiseLabel())
        val banded = enc(db = 72.0).metricNoiseLabel()!!
        assertEquals(false, banded.contains("dB"))
    }
}
