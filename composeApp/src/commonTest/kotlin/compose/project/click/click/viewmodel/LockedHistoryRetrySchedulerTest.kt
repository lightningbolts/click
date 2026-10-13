package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.LOCKED_HISTORY_PREVIEW_TEXT
import compose.project.click.click.data.models.LOCKED_HISTORY_TEXT
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.isLockedHistory
import compose.project.click.click.data.models.previewLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LockedHistoryRetrySchedulerTest {
    /** A scheduler whose reload records the virtual time and, like the real one, reports back as still locked. */
    private fun TestScope.lockedForever(reloadsAt: MutableList<Long>): LockedHistoryRetryScheduler {
        lateinit var scheduler: LockedHistoryRetryScheduler
        scheduler =
            LockedHistoryRetryScheduler(backgroundScope) {
                reloadsAt += currentTime
                scheduler.onLoaded(visible = true, anyLocked = true)
            }
        return scheduler
    }

    @Test
    fun retriesAfter2_5_10_20SecondsThenStops() =
        runTest {
            val reloadsAt = mutableListOf<Long>()
            val scheduler = lockedForever(reloadsAt)

            scheduler.onLoaded(visible = true, anyLocked = true)
            advanceTimeBy(120_000L)
            runCurrent()

            // 2 s, then +5, +10, +20 after each reload: at most four tries.
            assertEquals(listOf(2_000L, 7_000L, 17_000L, 37_000L), reloadsAt)
            assertEquals(4, scheduler.attempt)
            assertFalse(scheduler.isPending)
        }

    @Test
    fun nothingLockedResetsTheCount() =
        runTest {
            val reloadsAt = mutableListOf<Long>()
            val scheduler = LockedHistoryRetryScheduler(backgroundScope) { reloadsAt += currentTime }

            scheduler.onLoaded(visible = true, anyLocked = true)
            advanceTimeBy(2_001L)
            scheduler.onLoaded(visible = true, anyLocked = true)
            assertEquals(2, scheduler.attempt)

            // The reload unlocked everything: back to zero, nothing pending.
            scheduler.onLoaded(visible = true, anyLocked = false)
            assertEquals(0, scheduler.attempt)
            assertFalse(scheduler.isPending)

            // The next locked load starts from 2 s again.
            val start = currentTime
            scheduler.onLoaded(visible = true, anyLocked = true)
            advanceTimeBy(1_999L)
            assertEquals(listOf(2_000L), reloadsAt)
            advanceTimeBy(2L)
            assertEquals(listOf(2_000L, start + 2_000L), reloadsAt)
        }

    @Test
    fun leavingTheChatCancelsThePendingRetry() =
        runTest {
            val reloadsAt = mutableListOf<Long>()
            val scheduler = LockedHistoryRetryScheduler(backgroundScope) { reloadsAt += currentTime }

            scheduler.onLoaded(visible = true, anyLocked = true)
            assertTrue(scheduler.isPending)
            advanceTimeBy(1_000L)
            scheduler.cancel()
            advanceTimeBy(60_000L)

            assertTrue(reloadsAt.isEmpty())
            assertEquals(0, scheduler.attempt)
            assertFalse(scheduler.isPending)
        }

    @Test
    fun aLoadWhileHiddenNeverSchedules() =
        runTest {
            val reloadsAt = mutableListOf<Long>()
            val scheduler = LockedHistoryRetryScheduler(backgroundScope) { reloadsAt += currentTime }

            scheduler.onLoaded(visible = true, anyLocked = true)
            scheduler.onLoaded(visible = false, anyLocked = true)
            advanceTimeBy(60_000L)

            assertTrue(reloadsAt.isEmpty())
            assertEquals(0, scheduler.attempt)
        }

    @Test
    fun lockedRowIsDistinctFromTheNewMessageFallback() {
        val locked = Message(id = "m1", user_id = "u1", content = LOCKED_HISTORY_TEXT, timeCreated = 1L)
        val fallback = locked.copy(content = "New message")

        assertTrue(locked.isLockedHistory())
        assertFalse(fallback.isLockedHistory())
        assertEquals("Encrypted message unavailable on this device", LOCKED_HISTORY_TEXT)
        assertEquals(LOCKED_HISTORY_PREVIEW_TEXT, locked.previewLabel())
        assertEquals("New message", fallback.previewLabel())
    }
}
