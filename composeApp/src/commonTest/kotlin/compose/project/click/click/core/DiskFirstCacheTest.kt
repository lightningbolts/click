package compose.project.click.click.core

import compose.project.click.click.data.HomeDiskCaches
import compose.project.click.click.data.HomeFeedSnapshot
import compose.project.click.click.data.api.EventBookmarkItemDto
import compose.project.click.click.data.storage.BeaconRsvpPersistence
import compose.project.click.click.data.storage.FakeTokenStorage
import compose.project.click.click.viewmodel.BeaconRsvpCacheEntry
import compose.project.click.click.viewmodel.SharedDropsViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiskFirstCacheTest {
    private val backend = InMemoryDiskCacheBackend()
    private val strip =
        DiskFirstCache(UserDiskCache.Keys.SHARED_DROPS_STRIP, ListSerializer(Int.serializer())) {
            it.take(SharedDropsViewModel.STRIP_CACHE_MAX)
        }

    @BeforeTest
    fun install() {
        UserDiskCache.backendFactory = { backend }
    }

    @AfterTest
    fun restore() {
        UserDiskCache.backendFactory = ::platformDiskCacheBackend
    }

    @Test
    fun seedsFromDiskThenRevalidates() =
        runTest {
            strip.store("u1", listOf(1, 2))
            val painted = mutableListOf<List<Int>>()

            // First frame: the disk copy, synchronously.
            strip.seed("u1")?.let(painted::add)
            // Then the network replaces it and is written through.
            val result = strip.revalidate("u1", fetch = { Result.success(listOf(3, 4, 5)) }, publish = { painted += it })

            assertTrue(result.isSuccess)
            assertEquals(listOf(listOf(1, 2), listOf(3, 4, 5)), painted)
            assertEquals(listOf(3, 4, 5), strip.seed("u1"))
        }

    @Test
    fun aFailedRevalidationKeepsTheDiskCopy() =
        runTest {
            strip.store("u1", listOf(7))
            var published = false

            val result = strip.revalidate("u1", fetch = { Result.failure(Exception("offline")) }, publish = { published = true })
            val thrown = strip.revalidate("u1", fetch = { throw IllegalStateException("boom") }, publish = { published = true })

            assertTrue(result.isFailure)
            assertTrue(thrown.isFailure)
            assertEquals(false, published)
            assertEquals(listOf(7), strip.seed("u1"))
        }

    @Test
    fun theSharedDropsStripKeepsAtMost240() =
        runTest {
            strip.revalidate("u1", fetch = { Result.success((1..300).toList()) }, publish = {})
            assertEquals(240, strip.seed("u1")?.size)
            assertEquals(240, SharedDropsViewModel.STRIP_CACHE_MAX)
        }

    @Test
    fun aCorruptCacheIsIgnoredAndDropped() {
        UserDiskCache.writeRaw("u1", UserDiskCache.Keys.HOME_FEED, "{\"nudges\": 42")
        assertNull(HomeDiskCaches.homeFeed.seed("u1"))
        assertNull(UserDiskCache.readRaw("u1", UserDiskCache.Keys.HOME_FEED))

        UserDiskCache.writeRaw("u1", UserDiskCache.Keys.SHARED_DROPS_STRIP, "not json")
        assertNull(strip.seed("u1"))
    }

    @Test
    fun eachUserSeesOnlyTheirOwnCache() {
        HomeDiskCaches.homeFeed.store("u1", HomeFeedSnapshot(availabilityOverlaps = listOf("You and Maya are both available right now!")))
        assertNull(HomeDiskCaches.homeFeed.seed("u2"))
        assertNull(HomeDiskCaches.homeFeed.seed(null))
        assertEquals(
            listOf("You and Maya are both available right now!"),
            HomeDiskCaches.homeFeed.seed("u1")?.availabilityOverlaps,
        )
    }

    @Test
    fun savedEventsAndRsvpsRoundTripAndAreWipedAtSignOut() =
        runTest {
            val saved = listOf(EventBookmarkItemDto(beaconId = "b1", title = "Rooftop"))
            HomeDiskCaches.savedEvents.store("u1", saved)
            val tokens = FakeTokenStorage()
            BeaconRsvpPersistence.save(tokens, "u1", mapOf("b1" to BeaconRsvpCacheEntry(emptyList(), currentUserSignedUp = true)))
            strip.store("u1", listOf(1))
            HomeDiskCaches.homeFeed.store("u1", HomeFeedSnapshot())

            assertEquals(saved, HomeDiskCaches.savedEvents.seed("u1"))
            assertEquals(true, BeaconRsvpPersistence.cached("u1")["b1"]?.currentUserSignedUp)

            AppSessionHooks.onSessionEnded()

            assertTrue(backend.files.isEmpty())
            assertNull(HomeDiskCaches.savedEvents.seed("u1"))
            assertNull(HomeDiskCaches.homeFeed.seed("u1"))
            assertNull(strip.seed("u1"))
            assertTrue(BeaconRsvpPersistence.cached("u1").isEmpty())
        }

    @Test
    fun rsvpsMoveOverFromLegacyTokenStorageOnce() =
        runTest {
            val tokens = FakeTokenStorage()
            tokens.saveBeaconRsvpSnapshot(
                """{"user_id":"u1","entries":[{"beacon_id":"b9","attendees":[],"current_user_signed_up":true,"updated_at_ms":1}]}""",
            )

            val loaded = BeaconRsvpPersistence.load(tokens, "u1")

            assertEquals(true, loaded["b9"]?.currentUserSignedUp)
            assertNull(tokens.getBeaconRsvpSnapshot())
            assertEquals(true, BeaconRsvpPersistence.cached("u1")["b9"]?.currentUserSignedUp)
        }

    @Test
    fun anotherUsersLegacyRsvpsAreNotAdopted() =
        runTest {
            val tokens = FakeTokenStorage()
            tokens.saveBeaconRsvpSnapshot("""{"user_id":"someone-else","entries":[{"beacon_id":"b9"}]}""")
            assertTrue(BeaconRsvpPersistence.load(tokens, "u1").isEmpty())
            assertTrue(BeaconRsvpPersistence.cached("u1").isEmpty())
        }
}
