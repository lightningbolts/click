package compose.project.click.click.viewmodel

import compose.project.click.click.data.auth.SessionResumeGate // pragma: allowlist secret
import compose.project.click.click.data.storage.FakeTokenStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HubChatViewModelTest {
    @Before
    fun markSessionResumed() {
        SessionResumeGate.markCompleted()
    }

    @After
    fun resetSessionGate() {
        SessionResumeGate.resetForTests()
    }

    private class FakeHubLifecycleGateway(
        private val leaveResult: Result<Unit> = Result.success(Unit),
        private val deleteResult: Result<Unit> = Result.success(Unit),
        private val updateResult: Result<Unit> = Result.success(Unit),
    ) : HubLifecycleGateway {
        var leaveCalled = false
        var deleteCalled = false

        override suspend fun updateHub(
            hubId: String,
            name: String,
            category: String,
            authToken: String,
        ): Result<Unit> = updateResult

        override suspend fun deleteHub(
            hubId: String,
            authToken: String,
        ): Result<Unit> {
            deleteCalled = true
            return deleteResult
        }

        override suspend fun leaveHub(
            hubId: String,
            authToken: String,
        ): Result<Unit> {
            leaveCalled = true
            return leaveResult
        }
    }

    private class FakeActiveHubCache : ActiveHubCache {
        val removedHubIds = mutableListOf<String>()

        override fun removeActiveHub(hubId: String) {
            removedHubIds += hubId
        }
    }

    private fun buildViewModel(
        gateway: FakeHubLifecycleGateway,
        cache: FakeActiveHubCache,
        mutationDispatcher: CoroutineDispatcher,
    ): HubChatViewModel =
        HubChatViewModel(
            hubId = "hub_1",
            realtimeChannelName = "hub:hub_1",
            hubTitle = "Lobby",
            currentUserId = "user_1",
            creatorId = "user_1",
            tokenStorage =
                FakeTokenStorage(
                    jwt = TEST_HUB_JWT,
                    expiresAtEpochMs = TEST_HUB_JWT_EXPIRES_AT_MS,
                ),
            hubLifecycleGateway = gateway,
            activeHubCache = cache,
            mutationDispatcher = mutationDispatcher,
            startRealtime = false,
            loadHubDetails = false,
            hubAccessRevocations = emptyFlow(),
        )

    @Test
    fun startRealtimeDisabled_marksChannelReady() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val viewModel = buildViewModel(FakeHubLifecycleGateway(), FakeActiveHubCache(), dispatcher)
                assertTrue(viewModel.channelReady.value)
                assertEquals(HubRealtimeState.Ready, viewModel.realtimeState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun realtimeSessionFailure_setsErrorAndNotReady() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val viewModel =
                    HubChatViewModel(
                        hubId = "hub_1",
                        realtimeChannelName = "hub:hub_1",
                        hubTitle = "Lobby",
                        currentUserId = "user_1",
                        creatorId = "user_1",
                        tokenStorage =
                            FakeTokenStorage(
                                jwt = TEST_HUB_JWT,
                                expiresAtEpochMs = TEST_HUB_JWT_EXPIRES_AT_MS,
                            ),
                        hubLifecycleGateway = FakeHubLifecycleGateway(),
                        activeHubCache = FakeActiveHubCache(),
                        mutationDispatcher = dispatcher,
                        startRealtime = true,
                        loadHubDetails = false,
                        realtimeSessionOverride = { error("subscribe failed") },
                        hubAccessRevocations = emptyFlow(),
                    )
                val error =
                    withTimeout(1_000) {
                        viewModel.realtimeState.first { it is HubRealtimeState.Error }
                    }
                assertTrue(error is HubRealtimeState.Error)
                assertEquals("subscribe failed", error.message)
                assertFalse(viewModel.channelReady.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun leaveHub_clearsLocalStateAndEmitsPopEvent() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val gateway = FakeHubLifecycleGateway()
                val cache = FakeActiveHubCache()
                val viewModel = buildViewModel(gateway, cache, dispatcher)
                val eventDeferred =
                    async {
                        withTimeout(1_000) {
                            viewModel.navigationEvents.first()
                        }
                    }

                viewModel.updateDraft("hello")
                viewModel.leaveHub()

                val event = eventDeferred.await()

                assertEquals(HubChatNavigationEvent.PopBackToConnections, event)
                assertTrue(gateway.leaveCalled)
                assertEquals(listOf("hub_1"), cache.removedHubIds)
                assertEquals("", viewModel.draft.value)
                assertEquals(emptyList(), viewModel.messages.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun deleteHub_failureSetsErrorWithoutClearingOrNavigating() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val gateway =
                    FakeHubLifecycleGateway(
                        deleteResult = Result.failure(IllegalStateException("network down")),
                    )
                val cache = FakeActiveHubCache()
                val viewModel = buildViewModel(gateway, cache, dispatcher)

                viewModel.deleteHub()
                val error =
                    withTimeout(1_000) {
                        viewModel.sendError.filterNotNull().first()
                    }

                assertTrue(gateway.deleteCalled)
                assertEquals("network down", error)
                assertTrue(cache.removedHubIds.isEmpty())
                assertFalse(viewModel.messages.value.isNotEmpty())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun externalRevocation_clearsStateAndClosesOpenHub() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val revocations = MutableSharedFlow<String>(extraBufferCapacity = 1)
                val cache = FakeActiveHubCache()
                val viewModel =
                    HubChatViewModel(
                        hubId = "hub_1",
                        realtimeChannelName = "hub:hub_1",
                        hubTitle = "Lobby",
                        currentUserId = "user_1",
                        tokenStorage =
                            FakeTokenStorage(
                                jwt = TEST_HUB_JWT,
                                expiresAtEpochMs = TEST_HUB_JWT_EXPIRES_AT_MS,
                            ),
                        hubLifecycleGateway = FakeHubLifecycleGateway(),
                        activeHubCache = cache,
                        mutationDispatcher = dispatcher,
                        startRealtime = false,
                        loadHubDetails = false,
                        hubAccessRevocations = revocations,
                    )
                viewModel.updateDraft("must be cleared")
                val eventDeferred = async { withTimeout(1_000) { viewModel.navigationEvents.first() } }

                revocations.emit("hub_1")

                assertEquals(HubChatNavigationEvent.PopBackToConnections, eventDeferred.await())
                assertEquals("", viewModel.draft.value)
                assertEquals(listOf("hub_1"), cache.removedHubIds)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private companion object {
        /** `{"exp":4102444800}` (year 2100) so EnsureFreshAccessToken skips GoTrue. */
        const val TEST_HUB_JWT = "hdr.eyJleHAiOjQxMDI0NDQ4MDB9.sig"
        const val TEST_HUB_JWT_EXPIRES_AT_MS = 4_102_444_800L * 1_000L
    }
}
