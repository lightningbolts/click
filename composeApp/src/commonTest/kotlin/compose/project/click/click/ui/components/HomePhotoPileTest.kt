package compose.project.click.click.ui.components // pragma: allowlist secret

import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionActivityStatus // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionInsights // pragma: allowlist secret
import compose.project.click.click.data.models.ReconnectReminder // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.viewmodel.UserStats // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertTrue

class HomePhotoPileTest {
    @Test
    fun requiredSectionsArePresentWhenDataExists() {
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
                insights =
                    ConnectionInsights(
                        totalConnections = 4,
                        keepRate = 50f,
                        activeConnections = 2,
                        dormantConnections = 1,
                    ),
                stats = UserStats(totalConnections = 4, recentConnections = emptyList(), uniqueLocations = 2),
                connectedUsers = emptyMap<String, User>(),
            )
        val actions =
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
        val ids = buildHomePileClusters(data, actions).map { it.id }.toSet()
        assertTrue("availability" in ids)
        assertTrue("reconnect" in ids)
        assertTrue("recent" in ids)
        assertTrue("insights" in ids)
        assertTrue("stats" in ids)
        assertTrue(ids.containsAll(setOf("availability", "recent", "stats")))
    }

    @Test
    fun availabilityCtaAlwaysPresent() {
        val data =
            HomePileBoardData(
                intents = listOf(AvailabilityIntentRow()),
                featuredEvent = null,
                recap = null,
                savedBookmarks = emptyList(),
                exploreTiles = emptyList(),
                archiveNotice = null,
                pollPair = null,
                reconnectReminders = emptyList(),
                eventReminders = emptyList(),
                locationGroups = emptyMap(),
                insights = null,
                stats = UserStats(0, emptyList(), 0),
                connectedUsers = emptyMap(),
            )
        val actions =
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
        val availability = buildHomePileClusters(data, actions).first { it.id == "availability" }
        assertTrue(availability.photos.isNotEmpty())
        assertTrue(availability.zPriority > 20f)
    }
}
