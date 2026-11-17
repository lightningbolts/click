package compose.project.click.click.ui.utils

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import compose.project.click.click.data.places.PlaceSummaryDto
import kotlin.test.Test
import kotlin.test.assertEquals

/** The one pin list both the drawn map and the tap handler use. */
class MapPinListTest {
    private val lat = 47.6588
    private val lon = -122.3131

    private fun point(
        id: String,
        userIds: List<String>,
    ) = ConnectionMapPoint(
        connection = Connection(id = id, created = 1L, expiry = 9_999_999_999L, user_ids = userIds),
        latitude = lat,
        longitude = lon,
        timeState = TimeState.LIVE,
        opacity = 1f,
        shouldPulse = false,
        displayName = "Alex",
        locationLabel = "Campus",
        formattedDate = "Today",
    )

    private fun event(
        id: String,
        venueId: String?,
    ) = MapBeacon(
        id = id,
        kind = MapBeaconKind.EVENT,
        latitude = lat,
        longitude = lon,
        metadata = MapBeaconMetadata(title = id),
        venueId = venueId,
    )

    private val place = PlaceSummaryDto(id = "p1", name = "Cafe", latitude = lat, longitude = lon, radiusMeters = 75)

    private fun build(
        renderData: MapRenderData,
        places: List<PlaceSummaryDto> = emptyList(),
    ) = buildMapPins(
        renderData = renderData,
        communityHubs = emptyList(),
        places = places,
        currentUserId = "me",
        connectedUsers = emptyMap(),
        coreConnectionIds = emptySet(),
    )

    @Test
    fun duplicateConnectionsForOnePairDrawOnce() {
        val pins =
            build(
                MapRenderData.IndividualPins(
                    points = listOf(point("c1", listOf("me", "u2")), point("c2", listOf("u2", "me")), point("c3", listOf("me", "u3"))),
                ),
            )
        assertEquals(listOf("c1", "c3"), pins.map { it.id }.sorted())
    }

    @Test
    fun eventMergesOnlyIntoAPlaceThatIsDrawn() {
        val data = MapRenderData.IndividualPins(points = emptyList(), beacons = listOf(event("e1", venueId = "p1")))
        // Place filtered out (e.g. "Open now"): the event keeps its own pin.
        assertEquals(listOf("beacon:e1"), build(data, places = emptyList()).map { it.id })
        // Place drawn: the event rides in it.
        assertEquals(listOf("place:p1"), build(data, places = listOf(place)).map { it.id })
    }
}
