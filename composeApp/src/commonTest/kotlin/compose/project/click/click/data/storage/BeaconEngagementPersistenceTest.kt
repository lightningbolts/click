package compose.project.click.click.data.storage

import compose.project.click.click.viewmodel.BeaconEngagementCacheEntry
import compose.project.click.click.viewmodel.mergeEngagementFromServer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeaconEngagementPersistenceTest {
    @Test
    fun rejectedLegacyEarlyCheckInCannotReturnAfterRestart() =
        runTest {
            val storage = FakeTokenStorage()
            storage.saveBeaconEngagementSnapshot(
                """{"user_id":"viewer","entries":[{"beacon_id":"event","bookmarked":true,"checked_in":true,"checked_in_at":"old","local_early_check_in":true}]}""",
            )
            val restored = BeaconEngagementPersistence.load(storage, "viewer").getValue("event")
            assertFalse(restored.checkedIn)
            assertNull(restored.checkedInAt)
            assertTrue(restored.bookmarked)
            BeaconEngagementPersistence.save(storage, "viewer", mapOf("event" to restored))
            assertEquals(restored, BeaconEngagementPersistence.load(storage, "viewer")["event"])
            assertTrue(BeaconEngagementPersistence.load(storage, "another-user").isEmpty())
        }

    @Test
    fun confirmedPresenceAndBookmarksSurviveRestart() =
        runTest {
            val storage = FakeTokenStorage()
            val confirmed = BeaconEngagementCacheEntry(bookmarked = true, checkedIn = true, checkedInAt = "server-time", hubId = "hub")
            BeaconEngagementPersistence.save(storage, "viewer", mapOf("event" to confirmed))
            assertEquals(confirmed, BeaconEngagementPersistence.load(storage, "viewer")["event"])
        }

    @Test
    fun serverRefreshClearsStalePresenceAndTimestamp() {
        val result =
            mergeEngagementFromServer(
                existing = BeaconEngagementCacheEntry(checkedIn = true, checkedInAt = "stale", hubId = "hub"),
                bookmarked = true,
                checkedIn = false,
                checkedInAt = null,
                checkInCount = 3,
            )
        assertFalse(result.checkedIn)
        assertNull(result.checkedInAt)
        assertTrue(result.bookmarked)
        assertEquals("hub", result.hubId)
    }
}
