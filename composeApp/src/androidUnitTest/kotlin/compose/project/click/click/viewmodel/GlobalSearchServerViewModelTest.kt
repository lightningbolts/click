package compose.project.click.click.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import compose.project.click.click.data.api.GlobalSearchEvent
import compose.project.click.click.data.api.GlobalSearchOutcome
import compose.project.click.click.data.api.GlobalSearchPayload
import compose.project.click.click.data.api.GlobalSearchPerson
import compose.project.click.click.data.api.GlobalSearchService
import compose.project.click.click.data.repository.ConversationSearchHit // pragma: allowlist secret
import compose.project.click.click.data.storage.FakeTokenStorage
import compose.project.click.click.data.storage.initTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `/api/search` wiring in [GlobalSearchViewModel]: debounce, stale-cancel, merge, error and retry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchServerViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        initTokenStorage(ApplicationProvider.getApplicationContext())
    }

    private fun runVm(body: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                body()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun vm(
        server: GlobalSearchService,
        repo: FakeChatRepository = FakeChatRepository(),
        serverDebounceMs: Long = 250L,
    ) = GlobalSearchViewModel(
        tokenStorage = FakeTokenStorage(),
        chatRepository = repo,
        junctionArchivedConnectionIds = { emptySet() },
        junctionHiddenConnectionIds = { emptySet() },
        searchDebounceMs = 0L,
        fetchOwnAvailabilityIntents = { emptyList() },
        fetchBeaconsForSearch = { _, _ -> emptyList() },
        resolveSearchLocation = { null },
        activeHubs = { emptyList() },
        searchHubMessages = { _, _ -> emptyList() },
        searchLocalMessages = { _, _ -> emptyList() },
        serverSearch = server,
        serverDebounceMs = serverDebounceMs,
    )

    private fun people(vararg names: String) =
        GlobalSearchOutcome.Success(GlobalSearchPayload(people = names.map { GlobalSearchPerson("u-$it", it) }))

    private fun GlobalSearchViewModel.personNames() =
        results.value.items
            .filterIsInstance<SearchResult.SharedContextPerson>()
            .map { it.person.name }

    @Test
    fun typingWithinTheDebounceSendsOnlyTheLatestQuery() =
        runVm {
            val sent = mutableListOf<String>()
            val model =
                vm(server = { q ->
                    sent += q
                    people("Alice")
                })
            model.search("al", VIEWER)
            advanceTimeBy(100)
            model.search("ali", VIEWER)
            advanceTimeBy(100)
            model.search("alic", VIEWER)
            assertEquals(ServerSearchState.Loading, model.serverState.value)
            advanceUntilIdle()
            assertEquals(listOf("alic"), sent)
            assertEquals(ServerSearchState.Loaded, model.serverState.value)
            assertEquals(listOf("Alice"), model.personNames())
        }

    @Test
    fun aResponseForAnOlderQueryNeverReplacesTheNewerOne() =
        runVm {
            val model =
                vm(
                    serverDebounceMs = 0L,
                    server = { q ->
                        if (q == "ol") {
                            // Outlives its keystroke (e.g. a request that can't be cancelled mid-read).
                            withContext(NonCancellable) { delay(1_000) }
                            people("Old")
                        } else {
                            people("Bob")
                        }
                    },
                )
            model.search("ol", VIEWER)
            advanceTimeBy(10)
            model.search("bob", VIEWER)
            advanceUntilIdle()
            assertEquals(listOf("Bob"), model.personNames())
            assertEquals(ServerSearchState.Loaded, model.serverState.value)
        }

    @Test
    fun oneCharacterQueriesNeverReachTheServer() =
        runVm {
            val sent = mutableListOf<String>()
            val model =
                vm(server = { q ->
                    sent += q
                    people()
                })
            model.search("a", VIEWER)
            advanceUntilIdle()
            assertTrue(sent.isEmpty())
            assertEquals(ServerSearchState.Idle, model.serverState.value)
        }

    @Test
    fun serverRowsMergeAndItsMessageHitsReplaceTheChatSearchCall() =
        runVm {
            var chatSearchCalls = 0
            val repo =
                FakeChatRepository(onSearchConversationHits = {
                    chatSearchCalls++
                    emptyList()
                })
            val hit =
                ConversationSearchHit( // pragma: allowlist secret
                    messageId = "m1",
                    chatId = "chat-1",
                    conversationId = "chat-1",
                    connectionId = "conn-1",
                    senderId = "u-2",
                    timestamp = 5L,
                    snippet = "trivia night",
                    chatName = "Rae",
                )
            val payload =
                GlobalSearchPayload(
                    people = listOf(GlobalSearchPerson("u-9", "Trish", context = "In a group with you")),
                    events = listOf(GlobalSearchEvent("b-1", "Trivia Tuesday")),
                    hits = listOf(hit),
                )
            val model = vm(repo = repo, server = { GlobalSearchOutcome.Success(payload) })
            model.search("tri", VIEWER)
            advanceUntilIdle()
            val items = model.results.value.items
            assertTrue(items.any { it is SearchResult.SharedContextPerson && it.person.userId == "u-9" })
            assertTrue(items.any { it is SearchResult.RemoteEvent && it.event.beaconId == "b-1" })
            assertTrue(items.any { it is SearchResult.MessageHit && it.result.message.id == "m1" })
            assertEquals(0, chatSearchCalls)
        }

    @Test
    fun aFailureIsAnErrorWithRetryAndFallsBackToChatSearch() =
        runVm {
            var chatSearchCalls = 0
            var fail = true
            val repo =
                FakeChatRepository(onSearchConversationHits = {
                    chatSearchCalls++
                    emptyList()
                })
            val model =
                vm(
                    repo = repo,
                    server = { if (fail) GlobalSearchOutcome.Failed("Click is having trouble right now.") else people("Tess") },
                )
            model.search("tes", VIEWER)
            advanceUntilIdle()
            assertEquals(ServerSearchState.Failed("Click is having trouble right now."), model.serverState.value)
            assertEquals(1, chatSearchCalls)
            fail = false
            model.retryServerSearch()
            advanceUntilIdle()
            assertEquals(ServerSearchState.Loaded, model.serverState.value)
            assertEquals(listOf("Tess"), model.personNames())
        }

    @Test
    fun offlineIsReportedAsOffline() =
        runVm {
            val model = vm(server = { GlobalSearchOutcome.Offline })
            model.search("tes", VIEWER)
            advanceUntilIdle()
            assertEquals(ServerSearchState.Offline, model.serverState.value)
        }

    @Test
    fun clearingTheQueryCancelsThePendingRequest() =
        runVm {
            val sent = mutableListOf<String>()
            val model =
                vm(server = { q ->
                    sent += q
                    people("X")
                })
            model.search("xa", VIEWER)
            advanceTimeBy(100)
            model.clear()
            advanceUntilIdle()
            assertTrue(sent.isEmpty())
            assertEquals(ServerSearchState.Idle, model.serverState.value)
            assertTrue(model.results.value.isEmpty)
        }

    private companion object {
        const val VIEWER = "viewer-server"
    }
}
