package compose.project.click.click.viewmodel

import compose.project.click.click.data.storage.FakeTokenStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HubChatViewModelTest {

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

        override suspend fun deleteHub(hubId: String, authToken: String): Result<Unit> {
            deleteCalled = true
            return deleteResult
        }

        override suspend fun leaveHub(hubId: String, authToken: String): Result<Unit> {
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
    ): HubChatViewModel = HubChatViewModel(
        hubId = "hub_1",
        realtimeChannelName = "hub:hub_1",
        hubTitle = "Lobby",
        currentUserId = "user_1",
        creatorId = "user_1",
        tokenStorage = FakeTokenStorage(jwt = "jwt"),
        hubLifecycleGateway = gateway,
        activeHubCache = cache,
        mutationDispatcher = mutationDispatcher,
        startRealtime = false,
        loadHubDetails = false,
    )

    @Test
    fun leaveHub_clearsLocalStateAndEmitsPopEvent() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val gateway = FakeHubLifecycleGateway()
            val cache = FakeActiveHubCache()
            val viewModel = buildViewModel(gateway, cache, dispatcher)
            val eventDeferred = async {
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
    fun deleteHub_failureSetsErrorWithoutClearingOrNavigating() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val gateway = FakeHubLifecycleGateway(
                deleteResult = Result.failure(IllegalStateException("network down")),
            )
            val cache = FakeActiveHubCache()
            val viewModel = buildViewModel(gateway, cache, dispatcher)

            viewModel.deleteHub()
            val error = withTimeout(1_000) {
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
}
