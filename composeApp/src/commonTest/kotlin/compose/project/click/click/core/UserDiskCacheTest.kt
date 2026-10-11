package compose.project.click.click.core

import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDiskCacheTest {
    @Serializable
    private data class Region(
        val lat: Double,
        val lng: Double,
        val zoom: Double,
    )

    private val backend = InMemoryDiskCacheBackend()

    @BeforeTest
    fun install() {
        UserDiskCache.backendFactory = { backend }
    }

    @AfterTest
    fun restore() {
        UserDiskCache.backendFactory = ::platformDiskCacheBackend
    }

    @Test
    fun roundTripsPerUser() {
        val region = Region(40.75, -73.99, 12.0)
        UserDiskCache.write("u1", UserDiskCache.Keys.MAP_REGION, Region.serializer(), region)
        assertEquals(region, UserDiskCache.read("u1", UserDiskCache.Keys.MAP_REGION, Region.serializer()))
        assertNull(UserDiskCache.read("u2", UserDiskCache.Keys.MAP_REGION, Region.serializer()))
    }

    @Test
    fun undecodableFileIsDropped() {
        UserDiskCache.writeRaw("u1", UserDiskCache.Keys.WEATHER, "{broken")
        assertNull(UserDiskCache.read("u1", UserDiskCache.Keys.WEATHER, Region.serializer()))
        assertNull(UserDiskCache.readRaw("u1", UserDiskCache.Keys.WEATHER))
    }

    @Test
    fun rejectsUnsafeNamesAndMissingUser() {
        UserDiskCache.writeRaw("../etc", UserDiskCache.Keys.HOME_FEED, "{}")
        UserDiskCache.writeRaw("u1", "../../x", "{}")
        UserDiskCache.writeRaw(null, UserDiskCache.Keys.HOME_FEED, "{}")
        assertTrue(backend.files.isEmpty())
    }

    @Test
    fun signOutWipesEveryUser() {
        UserDiskCache.writeRaw("u1", UserDiskCache.Keys.EVENTS_ENGAGEMENT, "{}")
        UserDiskCache.writeRaw("u2", UserDiskCache.Keys.ACTIVITY, "[]")
        UserDiskCache.wipeAll()
        assertTrue(backend.files.isEmpty())
    }
}
