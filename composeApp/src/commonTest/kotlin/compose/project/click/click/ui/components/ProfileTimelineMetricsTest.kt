package compose.project.click.click.ui.components // pragma: allowlist secret

import compose.project.click.click.data.models.ConnectionEncounter // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileTimelineMetricsTest {

    private fun encounter(
        elevationCategory: String? = null,
        relativeAltitudeM: Double? = null,
        exactBarometricElevationM: Double? = null,
    ) = ConnectionEncounter(
        id = "e1",
        connectionId = "c1",
        encounteredAt = "2026-08-18T12:00:00Z",
        elevationCategory = elevationCategory,
        relativeAltitudeM = relativeAltitudeM,
        exactBarometricElevationM = exactBarometricElevationM,
    )

    @Test
    fun metricElevationLabel_showsMetersOnlyForAgl() {
        val withAgl = encounter(elevationCategory = "GROUND_LEVEL", relativeAltitudeM = 2.4)
        assertEquals("Ground level · 2 m", withAgl.metricElevationLabel())
    }

    @Test
    fun metricElevationLabel_omitsAmslMeters() {
        val amslOnly = encounter(elevationCategory = "GROUND_LEVEL", exactBarometricElevationM = 34.0)
        assertEquals("Ground level", amslOnly.metricElevationLabel())
    }

    @Test
    fun metricElevationLabel_nullWhenNothingToShow() {
        assertNull(encounter().metricElevationLabel())
    }
}
