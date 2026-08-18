package compose.project.click.click.data.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionRefreshCoordinatorTest {
    @Test
    fun singleFlightRefresh_concurrentCallersShareOneInvocation() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            val invokeCount = intArrayOf(0)
            val countMutex = Mutex()

            val results =
                (1..8)
                    .map {
                        async {
                            SessionRefreshCoordinator.singleFlightRefresh {
                                countMutex.withLock { invokeCount[0] += 1 }
                                delay(40)
                                Result.success(Unit)
                            }
                        }
                    }.awaitAll()

            assertEquals(8, results.size)
            assertEquals(true, results.all { it.isSuccess })
            assertEquals(1, invokeCount[0], "expected exactly one network refresh for concurrent callers")
            SessionRefreshCoordinator.resetForTests()
        }

    @Test
    fun singleFlightRefresh_sequentialCallsEachRun() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            val invokeCount = intArrayOf(0)

            repeat(3) {
                SessionRefreshCoordinator.singleFlightRefresh {
                    invokeCount[0] += 1
                    Result.success(Unit)
                }
            }

            assertEquals(3, invokeCount[0])
            SessionRefreshCoordinator.resetForTests()
        }

    @Test
    fun recentlyRefreshed_tracksSuccessfulRefreshUntilCleared() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            assertFalse(SessionRefreshCoordinator.recentlyRefreshed())
            SessionRefreshCoordinator.markSuccessfulRefresh()
            assertTrue(SessionRefreshCoordinator.recentlyRefreshed())
            SessionRefreshCoordinator.clearSuccessfulRefresh()
            assertFalse(SessionRefreshCoordinator.recentlyRefreshed())
            SessionRefreshCoordinator.resetForTests()
        }
}
