package compose.project.click.click.ui.screens

import compose.project.click.click.data.models.Connection
import compose.project.click.click.ui.utils.CommunityHubPin
import compose.project.click.click.ui.utils.ConnectionMapPoint
import compose.project.click.click.ui.utils.TimeState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveryFeedSectionsTest {

    @Test
    fun groupDiscoveryFeedIntoSections_omitsConnectionsSection() {
        val conn = DiscoveryFeedItem.Connection(
            point = ConnectionMapPoint(
                connection = Connection(
                    id = "c1",
                    created = 1L,
                    expiry = 9_999_999_999L,
                    user_ids = listOf("u1", "u2"),
                ),
                latitude = 0.0,
                longitude = 0.0,
                timeState = TimeState.LIVE,
                opacity = 1f,
                shouldPulse = false,
                displayName = "Alex",
                locationLabel = "Campus",
                formattedDate = "Today",
            ),
            distanceM = 10.0,
        )
        val sections = groupDiscoveryFeedIntoSections(listOf(conn))
        assertFalse(sections.any { it.title == "Connections" })
    }

    @Test
    fun groupDiscoveryFeedIntoSections_stillIncludesHubSections() {
        val hub = DiscoveryFeedItem.Hub(
            hub = CommunityHubPin(
                hubId = "h1",
                name = "Test",
                latitude = 0.0,
                longitude = 0.0,
                radiusMeters = 100,
                activeUserCount = 2,
            ),
            distanceM = 5.0,
            ttlLabel = "Ephemeral · 2 here",
        )
        val sections = groupDiscoveryFeedIntoSections(listOf(hub))
        assertTrue(sections.any { it.title == "Community hubs" })
    }
}
