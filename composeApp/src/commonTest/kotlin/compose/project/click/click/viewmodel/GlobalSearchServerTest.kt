package compose.project.click.click.viewmodel

import compose.project.click.click.data.ActiveHubEntry // pragma: allowlist secret
import compose.project.click.click.data.api.GlobalSearchEvent
import compose.project.click.click.data.api.GlobalSearchHub
import compose.project.click.click.data.api.GlobalSearchPayload
import compose.project.click.click.data.api.GlobalSearchPerson
import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GeoLocation
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import compose.project.click.click.data.models.User
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GlobalSearchServerTest {
    @Test
    fun withoutAServerPayloadTheLocalRowsAreReturnedAsIs() {
        val local = listOf(SearchResult.ActiveConnection(direct("c1", "u1", "Ann")))
        assertSame(local, mergeServerResults(local, null))
    }

    @Test
    fun serverRowsComeAfterLocalOnesAndLocalWinsOnDuplicates() {
        val local =
            listOf<SearchResult>(
                SearchResult.ActiveConnection(direct("c1", "u1", "Ann")),
                SearchResult.BeaconMatch(beacon("b1"), distanceMeters = null),
            )
        val server =
            GlobalSearchPayload(
                people = listOf(GlobalSearchPerson("u1", "Ann"), GlobalSearchPerson("u2", "Anya"), GlobalSearchPerson("u2", "Anya")),
                events = listOf(GlobalSearchEvent("b1", "Dup"), GlobalSearchEvent("b2", "Annual fair")),
            )
        val merged = mergeServerResults(local, server)
        assertEquals(local, merged.take(2))
        assertEquals(listOf("u2"), merged.filterIsInstance<SearchResult.SharedContextPerson>().map { it.person.userId })
        assertEquals(listOf("b2"), merged.filterIsInstance<SearchResult.RemoteEvent>().map { it.event.beaconId })
    }

    @Test
    fun joinedHubsUseTheKnownRealtimeChannelOrTheDefault() {
        val server = GlobalSearchPayload(hubs = listOf(GlobalSearchHub("h1", "Climbers", "sports"), GlobalSearchHub("h2", "Runners")))
        val active = listOf(ActiveHubEntry(hubId = "h1", name = "Climbers", realtimeChannel = "hub:h1:v2", creatorId = "owner"))
        val hubs = mergeServerResults(emptyList(), server, active).filterIsInstance<SearchResult.JoinedHub>()
        assertEquals("hub:h1:v2", hubs[0].realtimeChannel)
        assertEquals("owner", hubs[0].creatorId)
        assertEquals("hub:h2", hubs[1].realtimeChannel)
        val target = hubs[0].toChatOpenTarget()!!
        assertTrue(target.isHub)
        assertEquals("h1", target.hubId)
        assertEquals("sports", target.hubCategory)
        assertEquals("general", hubs[1].toChatOpenTarget()!!.hubCategory)
    }

    @Test
    fun peopleAndEventsDontOpenAChat() {
        assertNull(SearchResult.SharedContextPerson(GlobalSearchPerson("u", "A")).toChatOpenTarget())
        assertNull(SearchResult.RemoteEvent(GlobalSearchEvent("b", "E")).toChatOpenTarget())
    }

    @Test
    fun filtersPlacePeopleUnderActiveAndEventsUnderBeacons() {
        val results =
            GlobalSearchResults(
                listOf(
                    SearchResult.SharedContextPerson(GlobalSearchPerson("u", "A")),
                    SearchResult.RemoteEvent(GlobalSearchEvent("b", "E")),
                    SearchResult.JoinedHub(GlobalSearchHub("h", "H"), "hub:h"),
                ),
            )
        assertEquals(1, results.visible(setOf(SearchResultCategory.Active)).size)
        assertTrue(results.visible(setOf(SearchResultCategory.Beacons)).single() is SearchResult.RemoteEvent)
        assertEquals(2, results.visible(setOf(SearchResultCategory.Nearby)).size)
    }

    @Test
    fun eventsSortBySoonestStartAndUndatedLast() {
        val results =
            GlobalSearchResults(
                listOf(
                    SearchResult.RemoteEvent(GlobalSearchEvent("late", "L", startAt = "2026-12-01T00:00:00Z")),
                    SearchResult.RemoteEvent(GlobalSearchEvent("none", "N")),
                    SearchResult.RemoteEvent(GlobalSearchEvent("soon", "S", startAt = "2026-11-05T00:00:00Z")),
                ),
            ).visible(SearchResultCategory.entries.toSet())
        assertEquals(listOf("soon", "late", "none"), results.map { (it as SearchResult.RemoteEvent).event.beaconId })
    }

    @Test
    fun eventSubtitleMatchesTheIosRow() {
        val event = GlobalSearchEvent("b", "Trivia", locationName = " The Pub ", startAt = "2026-11-03T19:05:00Z")
        assertEquals("Event · Tue Nov 3, 7:05 PM · The Pub", remoteEventSubtitle(event, TimeZone.UTC))
        assertEquals("Event", remoteEventSubtitle(GlobalSearchEvent("b", "T", startAt = "not a date"), TimeZone.UTC))
        assertEquals(
            "Event · Wed Nov 4, 12:00 AM",
            remoteEventSubtitle(GlobalSearchEvent("b", "T", startAt = "2026-11-04T00:00:00Z"), TimeZone.UTC),
        )
    }

    @Test
    fun hubSubtitle() {
        assertEquals("Hub · Coffee", joinedHubSubtitle("Coffee"))
        assertEquals("Hub", joinedHubSubtitle(" "))
    }

    @Test
    fun serverStatusCopyKeepsErrorsApartFromNoResults() {
        assertEquals("Searching Click…", serverStatusCopy(ServerSearchState.Loading, hasResults = false))
        assertNull(serverStatusCopy(ServerSearchState.Loaded, hasResults = false))
        assertNull(serverStatusCopy(ServerSearchState.Idle, hasResults = true))
        assertEquals(
            "Couldn't search. Click is having trouble right now. Tap to retry.",
            serverStatusCopy(ServerSearchState.Failed("Click is having trouble right now."), hasResults = false),
        )
        assertEquals(
            "Showing results on this device. Tap to retry the rest.",
            serverStatusCopy(ServerSearchState.Failed("x"), hasResults = true),
        )
        assertEquals("Couldn't search. You're offline. Tap to retry.", serverStatusCopy(ServerSearchState.Offline, hasResults = false))
    }

    private fun direct(
        connectionId: String,
        peer: String,
        name: String,
    ) = ChatWithDetails(
        chat = Chat(id = "chat-$connectionId", connectionId = connectionId, messages = emptyList()),
        connection =
            Connection(
                id = connectionId,
                created = 1L,
                expiry = Long.MAX_VALUE,
                geo_location = GeoLocation(0.0, 0.0),
                user_ids = listOf("me", peer),
                status = "kept",
            ),
        otherUser = User(id = peer, name = name),
        lastMessage = null,
        unreadCount = 0,
    )

    private fun beacon(id: String) =
        MapBeacon(id = id, kind = MapBeaconKind.STUDY, latitude = 0.0, longitude = 0.0, metadata = MapBeaconMetadata(title = "B"))
}
