package compose.project.click.click.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import compose.project.click.click.data.models.AvailabilityIntentRow
import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GeoLocation
import compose.project.click.click.data.models.GroupCliqueDetails
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.syntheticConnectionForGroupClique
import compose.project.click.click.data.repository.UnifiedSearchSupplement
import compose.project.click.click.data.storage.FakeTokenStorage
import compose.project.click.click.data.storage.initTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun runVmTest(testBody: suspend TestScope.() -> Unit) = runTest {
    val mainDispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(mainDispatcher)
    try {
        testBody()
    } finally {
        Dispatchers.resetMain()
    }
}

private fun TestScope.drainSearchWork() {
    advanceUntilIdle()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        initTokenStorage(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun blankQuery_clearsResultsAndIdle() = runVmTest {
        val active = listOf(directChat(VIEWER, PEER_A, "conn-x", "Zoe Zebra"))
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { if (it == VIEWER) active else emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
        )
        val vm = newVm(fake)
        vm.search("zoe", VIEWER)
        drainSearchWork()
        assertFalse(vm.results.value.isEmpty)
        vm.search("", VIEWER)
        advanceUntilIdle()
        assertTrue(vm.results.value.isEmpty)
        assertFalse(vm.isSearching.value)
    }

    @Test
    fun nameSearch_findsActiveConnection() = runVmTest {
        val active = listOf(directChat(VIEWER, PEER_A, "conn-a", "Alice Anderson"))
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { if (it == VIEWER) active else emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
        )
        val vm = newVm(fake)
        vm.search("alice", VIEWER)
        drainSearchWork()
        assertTrue(vm.results.value.items.any { it is SearchResult.ActiveConnection })
    }

    @Test
    fun interestSearch_findsInterestMatch() = runVmTest {
        val active = listOf(directChat(VIEWER, PEER_A, "conn-a", "Sam"))
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { if (it == VIEWER) active else emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
            onUnifiedSearchSupplement = { _, _ ->
                UnifiedSearchSupplement(
                    peerInterestTagsByUserId = mapOf(PEER_A to listOf("Hiking", "Music")),
                    activePeerIntentsByUserId = emptyMap(),
                )
            },
        )
        val vm = newVm(fake)
        vm.search("hiking", VIEWER)
        drainSearchWork()
        assertTrue(vm.results.value.items.any { it is SearchResult.InterestMatch })
    }

    @Test
    fun intentSearch_findsIntentMatch() = runVmTest {
        val active = listOf(directChat(VIEWER, PEER_A, "conn-a", "Jordan"))
        val intent = AvailabilityIntentRow(
            userId = PEER_A,
            intentTag = "Coffee",
            timeframe = "30 min",
            expiresAt = "2099-01-01T00:00:00Z",
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { if (it == VIEWER) active else emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
            onUnifiedSearchSupplement = { _, _ ->
                UnifiedSearchSupplement(
                    peerInterestTagsByUserId = emptyMap(),
                    activePeerIntentsByUserId = mapOf(PEER_A to listOf(intent)),
                )
            },
        )
        val vm = newVm(fake)
        vm.search("coffee", VIEWER)
        drainSearchWork()
        assertTrue(vm.results.value.items.any { it is SearchResult.IntentMatch })
    }

    @Test
    fun cliqueNameSearch_findsClique() = runVmTest {
        val clique = cliqueChat(VIEWER, "Weekend Hikers")
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { if (it == VIEWER) listOf(clique) else emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
        )
        val vm = newVm(fake)
        vm.search("hikers", VIEWER)
        drainSearchWork()
        assertTrue(vm.results.value.items.any { it is SearchResult.Clique })
    }

    @Test
    fun ownIntentSearch_findsOwnAvailabilityIntentMatch() = runVmTest {
        val ownIntent = AvailabilityIntentRow(
            userId = VIEWER,
            intentTag = "Study session",
            timeframe = "Tonight",
            expiresAt = "2099-01-01T00:00:00Z",
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
        )
        val vm = newVm(
            repo = fake,
            fetchOwnAvailabilityIntents = { listOf(ownIntent) },
            fetchBeaconsForSearch = { _, _ -> emptyList() },
            resolveSearchLocation = { null },
        )
        vm.search("study", VIEWER)
        drainSearchWork()
        assertTrue(vm.results.value.items.any { it is SearchResult.OwnAvailabilityIntentMatch })
    }

    @Test
    fun beaconSearch_findsBeaconMatch() = runVmTest {
        val beacon = MapBeacon(
            id = "beacon-1",
            kind = MapBeaconKind.SOUNDTRACK,
            latitude = 37.0,
            longitude = -122.0,
            metadata = MapBeaconMetadata(
                trackName = "Midnight City",
                artistName = "M83",
            ),
            expiresAtEpochMs = Long.MAX_VALUE,
            sourceBeaconType = "soundtrack",
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
        )
        val vm = newVm(
            repo = fake,
            fetchOwnAvailabilityIntents = { emptyList() },
            fetchBeaconsForSearch = { _, _ -> listOf(beacon) },
            resolveSearchLocation = { 37.0 to -122.0 },
        )
        vm.search("midnight", VIEWER)
        drainSearchWork()
        assertTrue(vm.results.value.items.any { it is SearchResult.BeaconMatch })
    }

    @Test
    fun toggleCategory_filtersBeaconResults() = runVmTest {
        val beacon = MapBeacon(
            id = "beacon-2",
            kind = MapBeaconKind.SOS,
            latitude = 37.0,
            longitude = -122.0,
            metadata = MapBeaconMetadata(description = "Need help"),
            expiresAtEpochMs = Long.MAX_VALUE,
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
        )
        val vm = newVm(
            repo = fake,
            fetchOwnAvailabilityIntents = { emptyList() },
            fetchBeaconsForSearch = { _, _ -> listOf(beacon) },
            resolveSearchLocation = { 37.0 to -122.0 },
        )
        vm.search("help", VIEWER)
        drainSearchWork()
        assertTrue(vm.results.value.visible(vm.visibleCategories.value).any { it is SearchResult.BeaconMatch })
        vm.toggleCategory(SearchResultCategory.Beacons)
        vm.toggleCategory(SearchResultCategory.Nearby)
        val filtered = vm.results.value.visible(vm.visibleCategories.value)
        assertTrue(filtered.none { it is SearchResult.BeaconMatch })
    }

    @Test
    fun semanticLocationSearch_findsLocationBucket() = runVmTest {
        val active = listOf(
            directChat(VIEWER, PEER_A, "c1", "Ann", semanticLocation = "Terry Hall"),
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { if (it == VIEWER) active else emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
        )
        val vm = newVm(fake)
        vm.search("terry", VIEWER)
        drainSearchWork()
        assertTrue(vm.results.value.items.any { it is SearchResult.LocationBucket })
    }

    @Test
    fun messageSearch_findsMessageHit() = runVmTest {
        val active = listOf(directChat(VIEWER, PEER_A, "conn-msg", "Bo"))
        val hit = Message(
            id = "m1",
            user_id = PEER_A,
            content = "the blue notebook is on the desk",
            timeCreated = 5000L,
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { if (it == VIEWER) active else emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> "chat-conn-msg" to listOf(hit) },
        )
        val vm = newVm(fake)
        vm.search("notebook", VIEWER)
        drainSearchWork()
        assertTrue(vm.results.value.items.any { it is SearchResult.MessageHit })
    }

    @Test
    fun toggleCategory_filtersVisibleResults() = runVmTest {
        val active = listOf(
            directChat(VIEWER, PEER_A, "c-loc", "Lee", semanticLocation = "Library Plaza"),
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { if (it == VIEWER) active else emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
            onSearchMessagesByConnectionId = { _, _ -> null to emptyList() },
        )
        val vm = newVm(fake)
        vm.search("library", VIEWER)
        drainSearchWork()
        val full = vm.results.value.visible(vm.visibleCategories.value)
        assertTrue(full.any { it is SearchResult.LocationBucket })
        vm.toggleCategory(SearchResultCategory.Nearby)
        vm.toggleCategory(SearchResultCategory.Active)
        val filtered = vm.results.value.visible(vm.visibleCategories.value)
        assertTrue(filtered.none { it is SearchResult.LocationBucket })
    }

    @Test
    fun toggleCategory_cannotDeselectLastChip() = runVmTest {
        val vm = newVm(FakeChatRepository())
        for (cat in SearchResultCategory.entries) {
            if (cat == SearchResultCategory.Active) continue
            vm.toggleCategory(cat)
        }
        assertEquals(setOf(SearchResultCategory.Active), vm.visibleCategories.value)
        vm.toggleCategory(SearchResultCategory.Active)
        assertEquals(setOf(SearchResultCategory.Active), vm.visibleCategories.value)
    }

    @Test
    fun clear_resetsCategoriesToAll() = runVmTest {
        val vm = newVm(FakeChatRepository())
        vm.toggleCategory(SearchResultCategory.Archived)
        vm.clear()
        assertEquals(SearchResultCategory.entries.toSet(), vm.visibleCategories.value)
    }

    private companion object {
        const val VIEWER = "viewer-test"
        const val PEER_A = "peer-a"

        fun newVm(
            repo: FakeChatRepository,
            fetchOwnAvailabilityIntents: suspend (String) -> List<AvailabilityIntentRow> = { emptyList() },
            fetchBeaconsForSearch: suspend (Double, Double) -> List<MapBeacon> = { _, _ -> emptyList() },
            resolveSearchLocation: suspend () -> Pair<Double, Double>? = { null },
        ): GlobalSearchViewModel = GlobalSearchViewModel(
            tokenStorage = FakeTokenStorage(),
            chatRepository = repo,
            junctionArchivedConnectionIds = { emptySet() },
            junctionHiddenConnectionIds = { emptySet() },
            searchDebounceMs = 0L,
            fetchOwnAvailabilityIntents = fetchOwnAvailabilityIntents,
            fetchBeaconsForSearch = fetchBeaconsForSearch,
            resolveSearchLocation = resolveSearchLocation,
        )

        fun directChat(
            viewer: String,
            peer: String,
            connectionId: String,
            peerName: String,
            semanticLocation: String? = null,
        ): ChatWithDetails {
            val conn = Connection(
                id = connectionId,
                created = 10L,
                expiry = Long.MAX_VALUE,
                geo_location = GeoLocation(0.0, 0.0),
                user_ids = listOf(viewer, peer),
                semantic_location = semanticLocation,
                status = "kept",
            )
            return ChatWithDetails(
                chat = Chat(id = "chat-$connectionId", connectionId = connectionId, messages = emptyList()),
                connection = conn,
                otherUser = User(id = peer, name = peerName),
                lastMessage = null,
                unreadCount = 0,
            )
        }

        fun cliqueChat(viewer: String, groupTitle: String): ChatWithDetails {
            val gid = "group-1"
            val peer = "peer-b"
            val conn = syntheticConnectionForGroupClique(gid, listOf(viewer, peer, "peer-c"))
            val gc = GroupCliqueDetails(
                groupId = gid,
                name = groupTitle,
                createdByUserId = viewer,
                keyAnchorUserId = peer,
                memberUserIds = conn.user_ids,
            )
            return ChatWithDetails(
                chat = Chat(id = "chatg-1", groupId = gid, messages = emptyList()),
                connection = conn,
                otherUser = User(id = peer, name = "Member"),
                lastMessage = null,
                unreadCount = 0,
                groupClique = gc,
                groupMemberUsers = listOf(User(id = peer, name = "Zed")),
            )
        }
    }
}
