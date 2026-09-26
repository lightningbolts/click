package compose.project.click.click.viewmodel

import compose.project.click.click.data.api.ActivityRecapDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityRecapControllerTest {
    private class MemoryCache(
        var stored: Map<String, Map<String, ActivityRecapDto>> = emptyMap(),
    ) : RecapDiskCache {
        override suspend fun load(userId: String) = stored[userId].orEmpty()

        override suspend fun save(
            userId: String,
            recaps: Map<String, ActivityRecapDto>,
        ) {
            stored = stored + (userId to recaps)
        }
    }

    private fun recap(
        window: String,
        sent: Int,
    ) = ActivityRecapDto(window = window, since = "2026-09-20T00:00:00Z", messagesSent = sent)

    private fun TestScope.controller(
        cache: RecapDiskCache,
        fetch: suspend (String) -> Result<ActivityRecapDto>,
    ) = ActivityRecapController(scope = this, fetch = fetch, cache = cache)

    @Test
    fun startsLoadingNeverZeroAndLoadsOnlyTheSelectedWindow() =
        runTest {
            val calls = mutableListOf<String>()
            val c =
                controller(MemoryCache()) { w ->
                    calls += w
                    Result.success(recap(w, 3))
                }
            assertEquals(RecapState.Loading, c.state.value)
            c.start("u1")
            advanceUntilIdle()
            assertEquals(listOf("week"), calls)
            val loaded = assertIs<RecapState.Loaded>(c.state.value)
            assertEquals(3, loaded.recap.messagesSent)
            assertEquals(false, loaded.stale)
        }

    @Test
    fun failureWithoutCacheOffersRetryInsteadOfZeros() =
        runTest {
            var fail = true
            val c = controller(MemoryCache()) { w -> if (fail) Result.failure(Exception("boom")) else Result.success(recap(w, 1)) }
            c.start("u1")
            advanceUntilIdle()
            assertEquals(RecapState.Failed, c.state.value)
            fail = false
            c.retry()
            advanceUntilIdle()
            assertIs<RecapState.Loaded>(c.state.value)
        }

    @Test
    fun failureWithCacheShowsStaleSavedValue() =
        runTest {
            val cache = MemoryCache(mapOf("u1" to mapOf("week" to recap("week", 7))))
            val c = controller(cache) { Result.failure(Exception("offline")) }
            c.start("u1")
            advanceUntilIdle()
            val loaded = assertIs<RecapState.Loaded>(c.state.value)
            assertEquals(7, loaded.recap.messagesSent)
            assertTrue(loaded.stale)
        }

    @Test
    fun cacheIsPerUser() =
        runTest {
            val cache = MemoryCache(mapOf("other" to mapOf("week" to recap("week", 9))))
            val c = controller(cache) { Result.failure(Exception("offline")) }
            c.start("u1")
            advanceUntilIdle()
            assertEquals(RecapState.Failed, c.state.value)
        }

    @Test
    fun toggleFetchesOtherWindowOnceAndRefreshReloadsOnlySelected() =
        runTest {
            val calls = mutableListOf<String>()
            val c =
                controller(MemoryCache()) { w ->
                    calls += w
                    Result.success(recap(w, calls.size))
                }
            c.start("u1")
            advanceUntilIdle()
            c.select("day")
            advanceUntilIdle()
            c.select("week")
            c.select("day")
            advanceUntilIdle()
            assertEquals(listOf("week", "day"), calls)
            c.refresh()
            advanceUntilIdle()
            assertEquals(listOf("week", "day", "day"), calls)
        }

    @Test
    fun successIsSavedForTheNextLaunch() =
        runTest {
            val cache = MemoryCache()
            val c = controller(cache) { w -> Result.success(recap(w, 2)) }
            c.start("u1")
            advanceUntilIdle()
            assertEquals(2, cache.stored["u1"]?.get("week")?.messagesSent)
        }

    @Test
    fun visibleRowsFollowIosOrderAndHideZeros() {
        val dto =
            ActivityRecapDto(
                window = "week",
                since = "",
                connectionsFormed = 1,
                messagesSent = 0,
                messagesReceived = 4,
                beaconsCreated = 2,
                eventsRsvped = 0,
                eventsCheckedIn = 1,
                eventsSaved = 0,
            )
        assertEquals(
            listOf("New Clicks" to 1, "Messages received" to 4, "Check-ins" to 1, "Beacons dropped" to 2),
            dto.visibleRows(),
        )
    }
}
