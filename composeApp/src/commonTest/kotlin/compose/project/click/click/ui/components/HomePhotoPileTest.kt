package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionActivityStatus // pragma: allowlist secret
import compose.project.click.click.data.models.ReconnectReminder // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.viewmodel.UserStats // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomePhotoPileTest {
    private fun emptyActions() =
        HomePileActions(
            onCreateIntent = {},
            onEditIntent = {},
            onFeaturedMap = {},
            onSavedEventClick = {},
            onExploreClick = {},
            onArchiveOpenChat = {},
            onArchiveIcebreaker = {},
            onPollPairOpenChat = {},
            onPollPairIcebreaker = {},
            onReconnect = {},
            onDismissReconnect = {},
            onEventReminderMap = {},
            onLocationClick = {},
        )

    @Test
    fun pileClustersExcludeAvailabilityInsightsAndStats() {
        val data =
            HomePileBoardData(
                intents = emptyList(),
                featuredEvent = null,
                recap = null,
                savedBookmarks = emptyList(),
                exploreTiles = emptyList(),
                archiveNotice = null,
                pollPair = null,
                reconnectReminders =
                    listOf(
                        ReconnectReminder(
                            connectionId = "c1",
                            userId = "u1",
                            userName = "Alex",
                            lastMessageTime = 0L,
                            daysSinceContact = 12,
                            activityStatus = ConnectionActivityStatus.DORMANT,
                            suggestedMessage = "Hey",
                        ),
                    ),
                eventReminders = emptyList(),
                locationGroups = emptyMap(),
                insights = null,
                stats = UserStats(totalConnections = 4, recentConnections = emptyList(), uniqueLocations = 2),
                connectedUsers = emptyMap<String, User>(),
            )
        val ids = buildHomePileClusters(data, emptyActions()).map { it.id }.toSet()
        assertTrue("stay" in ids)
        assertTrue("recent" in ids)
        assertFalse("availability" in ids)
        assertFalse("insights" in ids)
        assertFalse("stats" in ids)
        assertFalse("reconnect" in ids, "reconnect merges into stay")
    }

    @Test
    fun requiredClusterIdsMatchPileSections() {
        assertEquals(
            setOf("saved", "explore", "stay", "recent"),
            homePileRequiredClusterIds(),
        )
    }

    @Test
    fun clustersSortByZPriorityDescending() {
        val data =
            HomePileBoardData(
                intents = emptyList(),
                featuredEvent = null,
                recap = null,
                savedBookmarks =
                    List(2) { index ->
                        EventBookmarkItemDto(beaconId = "b$index", title = "Saved $index")
                    },
                exploreTiles =
                    listOf(
                        HomeExploreTile(
                            id = "explore-events",
                            label = "Events",
                            count = 3,
                            layerFilter = MapLayerFilter.EVENTS,
                            icon = Icons.Filled.Event,
                        ),
                    ),
                archiveNotice = null,
                pollPair = null,
                reconnectReminders = emptyList(),
                eventReminders = emptyList(),
                locationGroups = emptyMap(),
                insights = null,
                stats = UserStats(0, emptyList(), 0),
                connectedUsers = emptyMap(),
            )
        val priorities = buildHomePileClusters(data, emptyActions()).map { it.zPriority }
        assertEquals(priorities, priorities.sortedDescending())
    }

    @Test
    fun savedExploreStayAndRecentClustersRespectCaps() {
        val saved =
            List(8) { index ->
                EventBookmarkItemDto(beaconId = "saved-$index", title = "Saved $index")
            }
        val explore =
            List(12) { index ->
                HomeExploreTile(
                    id = "tile-$index",
                    label = "Tile $index",
                    count = index + 1,
                    layerFilter = MapLayerFilter.EVENTS,
                    icon = Icons.Filled.Event,
                )
            }
        val reconnect =
            List(12) { index ->
                ReconnectReminder(
                    connectionId = "c$index",
                    userId = "u$index",
                    userName = "User $index",
                    lastMessageTime = 0L,
                    daysSinceContact = index,
                    activityStatus = ConnectionActivityStatus.DORMANT,
                    suggestedMessage = "Hey",
                )
            }
        val locations =
            List(12) { index ->
                "Loc $index" to
                    listOf(
                        Connection(
                            id = "conn-$index",
                            created = index.toLong(),
                            expiry = 9_999_999_999L,
                            user_ids = listOf("me", "peer-$index"),
                            semantic_location = "Loc $index",
                        ),
                    )
            }.toMap()
        val data =
            HomePileBoardData(
                intents = emptyList(),
                featuredEvent = null,
                recap = null,
                savedBookmarks = saved,
                exploreTiles = explore,
                archiveNotice = null,
                pollPair = null,
                reconnectReminders = reconnect,
                eventReminders = emptyList(),
                locationGroups = locations,
                insights = null,
                stats = UserStats(0, emptyList(), 0),
                connectedUsers = emptyMap(),
            )
        val clusters = buildHomePileClusters(data, emptyActions()).associateBy { it.id }
        assertEquals(5, clusters.getValue("saved").photos.size)
        assertEquals(10, clusters.getValue("explore").photos.size)
        assertEquals(10, clusters.getValue("stay").photos.size)
        assertEquals(10, clusters.getValue("recent").photos.size)
    }

    /**
     * List keys stay prefixed and unique, but the visual seed must be the raw entity id — otherwise
     * the same connection gets one gradient on the pile and a different one everywhere else.
     */
    @Test
    fun visualIdCarriesTheRawEntityId() {
        val reminder =
            ReconnectReminder(
                connectionId = "conn-77",
                userId = "user-77",
                userName = "Alex",
                lastMessageTime = 0L,
                daysSinceContact = 9,
                activityStatus = ConnectionActivityStatus.DORMANT,
                suggestedMessage = "Hey",
            )
        val data =
            HomePileBoardData(
                intents = emptyList(),
                featuredEvent = null,
                recap = null,
                savedBookmarks = emptyList(),
                exploreTiles = emptyList(),
                archiveNotice = null,
                pollPair = null,
                reconnectReminders = listOf(reminder),
                eventReminders = emptyList(),
                locationGroups = emptyMap(),
                insights = null,
                stats = UserStats(0, emptyList(), 0),
                connectedUsers = emptyMap(),
            )
        val photo =
            buildHomePileClusters(data, emptyActions())
                .first { it.id == "stay" }
                .photos
                .first()
        assertEquals("reconnect-conn-77", photo.id)
        assertEquals("conn-77", photo.visualId)
    }
}
