package compose.project.click.click.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HostSelectionExclusionSyncTest {
    @Test
    fun coalescesQuickChangesIntoOneSave() =
        runTest {
            val sent = mutableListOf<Pair<String, List<String>>>()
            val sync = HostSelectionExclusionSync(this, debounceMs = 300) { id, ex -> sent += id to ex; Result.success(Unit) }
            sync.submit("ph", listOf("u3"))
            advanceTimeBy(100)
            sync.submit("ph", listOf("u4", "u3"))
            advanceUntilIdle()
            assertEquals(listOf("ph" to listOf("u3", "u4")), sent)
        }

    @Test
    fun removeThenAddBackBeforeDebounceSendsNothing() =
        runTest {
            val sent = mutableListOf<List<String>>()
            val sync = HostSelectionExclusionSync(this, debounceMs = 300) { _, ex -> sent += ex; Result.success(Unit) }
            sync.submit("ph", listOf("u3"))
            advanceTimeBy(100)
            sync.submit("ph", emptyList())
            advanceUntilIdle()
            assertEquals(emptyList(), sent)
        }

    @Test
    fun addingBackAfterASaveClearsTheExclusionOnTheServer() =
        runTest {
            val sent = mutableListOf<List<String>>()
            val sync = HostSelectionExclusionSync(this, debounceMs = 300) { _, ex -> sent += ex; Result.success(Unit) }
            sync.submit("ph", listOf("u3"))
            advanceUntilIdle()
            sync.submit("ph", emptyList())
            advanceUntilIdle()
            assertEquals(listOf(listOf("u3"), emptyList()), sent)
        }

    @Test
    fun repeatedSameChoiceIsNotResent() =
        runTest {
            var calls = 0
            val sync = HostSelectionExclusionSync(this, debounceMs = 300) { _, _ -> calls++; Result.success(Unit) }
            sync.submit("ph", listOf("u3"))
            advanceUntilIdle()
            sync.submit("ph", listOf("u3"))
            advanceUntilIdle()
            assertEquals(1, calls)
        }

    @Test
    fun failedSaveIsRetriedOnTheNextSubmit() =
        runTest {
            var calls = 0
            val sync =
                HostSelectionExclusionSync(this, debounceMs = 300) { _, _ ->
                    calls++
                    if (calls == 1) Result.failure(IllegalStateException("offline")) else Result.success(Unit)
                }
            sync.submit("ph", listOf("u3"))
            advanceUntilIdle()
            sync.submit("ph", listOf("u3"))
            advanceUntilIdle()
            assertEquals(2, calls)
        }

    @Test
    fun resetCancelsAQueuedSave() =
        runTest {
            var calls = 0
            val sync = HostSelectionExclusionSync(this, debounceMs = 300) { _, _ -> calls++; Result.success(Unit) }
            sync.submit("ph", listOf("u3"))
            runCurrent()
            sync.reset()
            advanceUntilIdle()
            assertEquals(0, calls)
        }
}
