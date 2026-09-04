package compose.project.click.click.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppDataManagerSnapshotRestoreTest {
    @Test
    fun queuedRestore_doesNotClearReadyGateBeforeAcquiringMutex() =
        runTest {
            val previousGate = AppDataManager.hubAccessStateRestored.value
            AppDataManager._hubAccessStateRestored.value = true
            AppDataManager.snapshotRestoreMutex.lock()
            var restoreJob: Job? = null
            try {
                restoreJob =
                    launch(UnconfinedTestDispatcher(testScheduler)) {
                        AppDataManager.restoreCachedSnapshot()
                    }
                runCurrent()

                assertTrue(AppDataManager.hubAccessStateRestored.value)
            } finally {
                restoreJob?.cancel()
                restoreJob?.join()
                AppDataManager.snapshotRestoreMutex.unlock()
                AppDataManager._hubAccessStateRestored.value = previousGate
            }
        }
}
