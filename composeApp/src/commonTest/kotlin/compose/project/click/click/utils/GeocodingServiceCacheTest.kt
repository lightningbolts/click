package compose.project.click.click.utils

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GeocodingServiceCacheTest {

    @AfterTest
    fun tearDown() {
        GeocodingService.clearCachesForTests()
    }

    @Test
    fun reverseCacheKey_roundsToFiveDecimals() {
        assertEquals(
            GeocodingService.reverseCacheKey(37.774929, -122.419418),
            GeocodingService.reverseCacheKey(37.7749291, -122.4194182),
        )
    }

    @Test
    fun reverseCacheKey_distinguishesNearbyPoints() {
        val a = GeocodingService.reverseCacheKey(37.77490, -122.41940)
        val b = GeocodingService.reverseCacheKey(37.77500, -122.41940)
        assertEquals(false, a == b)
    }
}
