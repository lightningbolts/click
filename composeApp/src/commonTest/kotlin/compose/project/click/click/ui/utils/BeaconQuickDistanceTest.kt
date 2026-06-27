package compose.project.click.click.ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BeaconQuickDistanceTest {

    @Test
    fun resolveBeaconQuickDistanceMeters_prefersSeedDistance() {
        val resolved = resolveBeaconQuickDistanceMeters(
            seedDistanceMeters = 18_300.0,
            beaconLat = 37.0,
            beaconLon = -122.0,
            cachedUserLatLon = 37.1 to -122.1,
        )
        assertEquals(18_300.0, resolved)
    }

    @Test
    fun resolveBeaconQuickDistanceMeters_fallsBackToCachedLocation() {
        val resolved = resolveBeaconQuickDistanceMeters(
            seedDistanceMeters = null,
            beaconLat = 37.0,
            beaconLon = -122.0,
            cachedUserLatLon = 37.0 to -122.0,
        )
        assertEquals(0.0, resolved)
    }

    @Test
    fun resolveBeaconQuickDistanceMeters_returnsNullWhenNoSeedOrCache() {
        val resolved = resolveBeaconQuickDistanceMeters(
            seedDistanceMeters = null,
            beaconLat = 37.0,
            beaconLon = -122.0,
            cachedUserLatLon = null,
        )
        assertNull(resolved)
    }

    @Test
    fun resolveBeaconQuickDistanceMeters_ignoresInvalidSeed() {
        val resolved = resolveBeaconQuickDistanceMeters(
            seedDistanceMeters = Double.POSITIVE_INFINITY,
            beaconLat = 37.0,
            beaconLon = -122.0,
            cachedUserLatLon = 37.0 to -122.0,
        )
        assertEquals(0.0, resolved)
    }
}
