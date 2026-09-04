package compose.project.click.click.viewmodel

import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.CachedHubThread
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.storage.FakeTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HubRevocationStartupTest {
    @Test
    fun revokedHub_skipsCachedHydrationAndRealtimeStartup() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            val previousRevocations = AppDataManager._revokedHubIds.value
            val previousCache = AppDataManager._cachedHubThreads.value
            var realtimeStarted = false
            try {
                AppDataManager._revokedHubIds.value = setOf("hub_1")
                AppDataManager._cachedHubThreads.value =
                    mapOf(
                        "hub_1" to
                            CachedHubThread(
                                hubId = "hub_1",
                                realtimeChannel = "hub:hub_1",
                                cachedAtMs = 1L,
                                messages = listOf(Message("message_1", "user_2", "cached", 1L)),
                            ),
                    )

                val viewModel =
                    HubChatViewModel(
                        hubId = "hub_1",
                        realtimeChannelName = "hub:hub_1",
                        hubTitle = "Lobby",
                        currentUserId = "user_1",
                        tokenStorage = FakeTokenStorage(),
                        startRealtime = true,
                        loadHubDetails = false,
                        realtimeSessionOverride = { realtimeStarted = true },
                        hubAccessRevocations = emptyFlow(),
                    )
                val event = withTimeout(1_000) { viewModel.navigationEvents.first() }

                assertEquals(HubChatNavigationEvent.PopBackToConnections, event)
                assertTrue(viewModel.messages.value.isEmpty())
                assertTrue(!realtimeStarted)
                assertTrue(AppDataManager.cachedHubThreadFor("hub_1") == null)
            } finally {
                AppDataManager._revokedHubIds.value = previousRevocations
                AppDataManager._cachedHubThreads.value = previousCache
                Dispatchers.resetMain()
            }
        }
}
