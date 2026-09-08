package compose.project.click.click.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionRefreshCoordinatorTest {
    @Test
    fun newCredentialDoesNotInheritBurnedTokenCooldown() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            try {
                val failure =
                    SessionRefreshCoordinator.singleFlightRefresh("old-credential") {
                        Result.failure<Unit>(IllegalStateException("Invalid refresh token"))
                    }
                var oldRetried = false
                assertEquals(
                    failure,
                    SessionRefreshCoordinator.singleFlightRefresh("old-credential") {
                        oldRetried = true
                        Result.success(Unit)
                    },
                )
                assertFalse(oldRetried)
                var newInvoked = false
                assertTrue(
                    SessionRefreshCoordinator
                        .singleFlightRefresh("new-credential") {
                            newInvoked = true
                            Result.success(Unit)
                        }.isSuccess,
                )
                assertTrue(newInvoked)
            } finally {
                SessionRefreshCoordinator.resetForTests()
            }
        }

    @Test
    fun newCredentialWaitsForOldFlightButRunsItsOwnRefresh() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            try {
                val started = CompletableDeferred<Unit>()
                val finishOld = CompletableDeferred<Unit>()
                val old =
                    async {
                        SessionRefreshCoordinator.singleFlightRefresh("old-credential") {
                            started.complete(Unit)
                            finishOld.await()
                            Result.failure<Unit>(IllegalStateException("Invalid refresh token"))
                        }
                    }
                started.await()
                var newInvocations = 0
                val fresh =
                    async {
                        SessionRefreshCoordinator.singleFlightRefresh("new-credential") {
                            newInvocations++
                            Result.success(Unit)
                        }
                    }
                yield()
                assertEquals(0, newInvocations)
                finishOld.complete(Unit)
                assertTrue(old.await().isFailure)
                assertTrue(fresh.await().isSuccess)
                assertEquals(1, newInvocations)
            } finally {
                SessionRefreshCoordinator.resetForTests()
            }
        }

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

    @Test
    fun singleFlightRefresh_failedRefreshCoalescesUntilCooldown() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            val invokeCount = intArrayOf(0)
            val boom = Exception("Request rate limit reached")

            val first =
                SessionRefreshCoordinator.singleFlightRefresh {
                    invokeCount[0] += 1
                    Result.failure(boom)
                }
            val second =
                SessionRefreshCoordinator.singleFlightRefresh {
                    invokeCount[0] += 1
                    Result.success(Unit)
                }

            assertTrue(first.isFailure)
            assertTrue(second.isFailure)
            assertEquals(1, invokeCount[0], "failed refresh must not retry during cooldown")
            assertTrue(SessionRefreshCoordinator.recentlyFailed())
            SessionRefreshCoordinator.resetForTests()
            assertFalse(SessionRefreshCoordinator.recentlyFailed())
        }

    @Test
    fun singleFlightRefresh_successClearsFailureCooldown() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            SessionRefreshCoordinator.singleFlightRefresh {
                Result.failure(Exception("Request rate limit reached"))
            }
            assertTrue(SessionRefreshCoordinator.recentlyFailed())
            SessionRefreshCoordinator.resetForTests()
            val result =
                SessionRefreshCoordinator.singleFlightRefresh {
                    Result.success(Unit)
                }
            assertTrue(result.isSuccess)
            assertFalse(SessionRefreshCoordinator.recentlyFailed())
            SessionRefreshCoordinator.resetForTests()
        }

    @Test
    fun failureCooldown_isLongerThanSuccessCoalesce() {
        assertTrue(SessionRefreshCoordinator.FAILURE_COOLDOWN_MS >= 15_000L)
        assertTrue(
            SessionRefreshCoordinator.HARD_FAILURE_COOLDOWN_MS >
                SessionRefreshCoordinator.FAILURE_COOLDOWN_MS,
        )
    }

    @Test
    fun singleFlightRefresh_cancelledLeaderCompletesFollowerAndAllowsRetry() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            val leaderStarted = CompletableDeferred<Unit>()
            val releaseLeader = CompletableDeferred<Unit>()
            var invokeCount = 0
            val leader =
                async {
                    SessionRefreshCoordinator.singleFlightRefresh {
                        invokeCount += 1
                        leaderStarted.complete(Unit)
                        releaseLeader.await()
                        Result.success(Unit)
                    }
                }
            leaderStarted.await()
            val follower =
                async {
                    SessionRefreshCoordinator.singleFlightRefresh {
                        invokeCount += 1
                        Result.success(Unit)
                    }
                }
            yield()

            leader.cancel()
            assertFailsWith<CancellationException> { leader.await() }
            val followerResult = withTimeout(1_000L) { follower.await() }
            assertTrue(followerResult.isFailure)
            assertTrue(followerResult.exceptionOrNull() is CancellationException)
            assertFalse(SessionRefreshCoordinator.recentlyFailed())

            val retry =
                SessionRefreshCoordinator.singleFlightRefresh {
                    invokeCount += 1
                    Result.success(Unit)
                }
            assertTrue(retry.isSuccess)
            assertEquals(2, invokeCount, "a cancelled leader must not poison the next refresh")
            SessionRefreshCoordinator.resetForTests()
        }

    @Test
    fun singleFlightRefresh_followerCancellationDoesNotAbortLeader() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            val leaderStarted = CompletableDeferred<Unit>()
            val releaseLeader = CompletableDeferred<Unit>()
            var invokeCount = 0
            val leader =
                async {
                    SessionRefreshCoordinator.singleFlightRefresh {
                        invokeCount += 1
                        leaderStarted.complete(Unit)
                        releaseLeader.await()
                        Result.success(Unit)
                    }
                }
            leaderStarted.await()
            val follower =
                async {
                    SessionRefreshCoordinator.singleFlightRefresh {
                        invokeCount += 1
                        Result.success(Unit)
                    }
                }
            yield()

            follower.cancel()
            withTimeout(1_000L) {
                assertFailsWith<CancellationException> { follower.await() }
            }
            releaseLeader.complete(Unit)
            assertTrue(leader.await().isSuccess)
            assertEquals(1, invokeCount, "cancelling a follower must not cancel the leader")
            SessionRefreshCoordinator.resetForTests()
        }

    @Test
    fun singleFlightRefresh_returnedCancellationFailureCleansUpWithoutCooldown() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            val cancellation = CancellationException("refresh caller cancelled")
            val first =
                SessionRefreshCoordinator.singleFlightRefresh {
                    Result.failure(cancellation)
                }

            assertTrue(first.isFailure)
            assertEquals(cancellation, first.exceptionOrNull())
            assertFalse(SessionRefreshCoordinator.recentlyFailed())
            val second =
                SessionRefreshCoordinator.singleFlightRefresh {
                    Result.success(Unit)
                }
            assertTrue(second.isSuccess)
            SessionRefreshCoordinator.resetForTests()
        }

    @Test
    fun singleFlightRefresh_thrownOrdinaryFailureIsSharedAndCooledDown() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            val leaderStarted = CompletableDeferred<Unit>()
            val releaseLeader = CompletableDeferred<Unit>()
            val boom = IllegalStateException("refresh failed")
            var invokeCount = 0
            val leader =
                async {
                    SessionRefreshCoordinator.singleFlightRefresh {
                        invokeCount += 1
                        leaderStarted.complete(Unit)
                        releaseLeader.await()
                        throw boom
                    }
                }
            leaderStarted.await()
            val follower =
                async {
                    SessionRefreshCoordinator.singleFlightRefresh {
                        invokeCount += 1
                        Result.success(Unit)
                    }
                }
            yield()
            releaseLeader.complete(Unit)

            val leaderResult = leader.await()
            val followerResult = withTimeout(1_000L) { follower.await() }
            assertTrue(leaderResult.isFailure)
            assertTrue(followerResult.isFailure)
            assertEquals(boom, leaderResult.exceptionOrNull())
            assertEquals(boom, followerResult.exceptionOrNull())
            assertEquals(1, invokeCount)
            assertTrue(SessionRefreshCoordinator.recentlyFailed())

            val cooledResult =
                SessionRefreshCoordinator.singleFlightRefresh {
                    invokeCount += 1
                    Result.success(Unit)
                }
            assertTrue(cooledResult.isFailure)
            assertEquals(1, invokeCount)
            SessionRefreshCoordinator.resetForTests()
        }

    @Test
    fun singleFlightRefresh_cancellationDuringCompletionIsRethrownAndRetryWorks() =
        runBlocking {
            SessionRefreshCoordinator.resetForTests()
            val leaderStarted = CompletableDeferred<Unit>()
            val releaseLeader = CompletableDeferred<Unit>()
            var invokeCount = 0
            var afterSingleFlight = false
            val leader =
                async {
                    SessionRefreshCoordinator.singleFlightRefresh {
                        invokeCount += 1
                        leaderStarted.complete(Unit)
                        releaseLeader.await()
                        currentCoroutineContext().cancel()
                        Result.success(Unit)
                    }
                    afterSingleFlight = true
                }
            leaderStarted.await()
            val follower =
                async {
                    SessionRefreshCoordinator.singleFlightRefresh {
                        invokeCount += 1
                        Result.success(Unit)
                    }
                }
            yield()
            releaseLeader.complete(Unit)

            assertFailsWith<CancellationException> { leader.await() }
            assertFalse(afterSingleFlight)
            assertTrue(withTimeout(1_000L) { follower.await() }.isSuccess)
            assertTrue(
                SessionRefreshCoordinator
                    .singleFlightRefresh {
                        invokeCount += 1
                        Result.success(Unit)
                    }.isSuccess,
            )
            assertEquals(2, invokeCount)
            SessionRefreshCoordinator.resetForTests()
        }
}
