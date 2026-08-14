@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import compose.project.click.click.data.api.ActivityRecapDto // pragma: allowlist secret
import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionArchiveNotice // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionInsights // pragma: allowlist secret
import compose.project.click.click.data.models.PollPairSuggestion // pragma: allowlist secret
import compose.project.click.click.data.models.ReconnectReminder // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.events.HomeEventReminder // pragma: allowlist secret
import compose.project.click.click.viewmodel.UserStats // pragma: allowlist secret

data class HomePileBoardData(
    val intents: List<AvailabilityIntentRow>,
    val featuredEvent: HomeEventReminder?,
    val recap: ActivityRecapDto?,
    val savedBookmarks: List<EventBookmarkItemDto>,
    val exploreTiles: List<HomeExploreTile>,
    val archiveNotice: ConnectionArchiveNotice?,
    val pollPair: PollPairSuggestion?,
    val reconnectReminders: List<ReconnectReminder>,
    val eventReminders: List<HomeEventReminder>,
    val locationGroups: Map<String, List<Connection>>,
    val insights: ConnectionInsights?,
    val stats: UserStats,
    val connectedUsers: Map<String, User>,
)

data class HomePileActions(
    val onCreateIntent: () -> Unit,
    val onEditIntent: (AvailabilityIntentRow) -> Unit,
    val onFeaturedMap: (HomeEventReminder) -> Unit,
    val onSavedEventClick: (EventBookmarkItemDto) -> Unit,
    val onExploreClick: (HomeExploreTile) -> Unit,
    val onArchiveOpenChat: (ConnectionArchiveNotice) -> Unit,
    val onArchiveIcebreaker: (ConnectionArchiveNotice) -> Unit,
    val onPollPairOpenChat: (PollPairSuggestion) -> Unit,
    val onPollPairIcebreaker: (PollPairSuggestion) -> Unit,
    val onReconnect: (ReconnectReminder) -> Unit,
    val onDismissReconnect: (ReconnectReminder) -> Unit,
    val onEventReminderMap: (HomeEventReminder) -> Unit,
    val onLocationClick: (String) -> Unit,
)

@Composable
fun HomePhotoPileBoard(
    data: HomePileBoardData,
    actions: HomePileActions,
    modifier: Modifier = Modifier,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    PlatformBackHandler(enabled = expandedId != null) {
        expandedId = null
    }
    val clusters = remember(data, actions) { buildHomePileClusters(data, actions) }
    Box(modifier = modifier.fillMaxSize()) {
        PileBoardScrim(
            visible = expandedId != null,
            onDismiss = { expandedId = null },
            modifier = Modifier.zIndex(1f),
        )
        clusters.forEachIndexed { index, cluster ->
            val photos =
                cluster.photos.map { spec ->
                    PilePhoto(
                        id = spec.id,
                        title = spec.title,
                        subtitle = spec.subtitle,
                        imageUrl = spec.imageUrl,
                        onClick = {
                            if (expandedId == cluster.id) {
                                spec.onOpen()
                            } else {
                                expandedId = cluster.id
                            }
                        },
                        onLongClick =
                            spec.onLongOpen?.let { longOpen ->
                                {
                                    if (expandedId == cluster.id) longOpen()
                                }
                            },
                    )
                }
            PileCluster(
                clusterId = cluster.id,
                label = cluster.label,
                photos = photos,
                expanded = expandedId == cluster.id,
                onExpand = { expandedId = cluster.id },
                onCollapse = { expandedId = null },
                index = index,
                totalClusters = clusters.size,
                zPriority = cluster.zPriority,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (expandedId == cluster.id) 20f else cluster.zPriority),
            )
        }
    }
}

internal data class HomePilePhotoSpec(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String? = null,
    val onOpen: () -> Unit,
    val onLongOpen: (() -> Unit)? = null,
)

internal data class HomePileClusterSpec(
    val id: String,
    val label: String,
    val zPriority: Float,
    val photos: List<HomePilePhotoSpec>,
)

internal fun buildHomePileClusters(
    data: HomePileBoardData,
    actions: HomePileActions,
): List<HomePileClusterSpec> {
    val out = mutableListOf<HomePileClusterSpec>()
    val availabilityPhotos =
        if (data.intents.isEmpty()) {
            listOf(
                HomePilePhotoSpec(
                    id = "availability-create",
                    title = "I'm down for…",
                    subtitle = "Set what you're down for",
                    onOpen = actions.onCreateIntent,
                ),
            )
        } else {
            listOf(
                HomePilePhotoSpec(
                    id = "availability-add",
                    title = "I'm down for…",
                    subtitle = "Add intent",
                    onOpen = actions.onCreateIntent,
                ),
            ) +
                data.intents.map { row ->
                    HomePilePhotoSpec(
                        id = row.id ?: "intent-${row.intentTag}",
                        title =
                            row.intentTag
                                ?.trim()
                                .orEmpty()
                                .ifEmpty { "Intent" },
                        subtitle = "Tap to edit",
                        onOpen = { actions.onEditIntent(row) },
                    )
                }
        }
    out +=
        HomePileClusterSpec(
            id = "availability",
            label = "I'm down for…",
            zPriority = 40f,
            photos = availabilityPhotos,
        )
    data.featuredEvent?.let { featured ->
        out +=
            HomePileClusterSpec(
                id = "featured",
                label = "Featured",
                zPriority = 18f,
                photos =
                    listOf(
                        HomePilePhotoSpec(
                            id = "featured-${featured.beaconId}",
                            title =
                                featured.title
                                    ?.trim()
                                    .orEmpty()
                                    .ifEmpty { featured.description },
                            subtitle = "View on map",
                            onOpen = { actions.onFeaturedMap(featured) },
                        ),
                    ),
            )
    }
    data.recap?.let { recap ->
        out +=
            HomePileClusterSpec(
                id = "recap",
                label = "Recap",
                zPriority = 12f,
                photos =
                    listOf(
                        HomePilePhotoSpec(
                            id = "recap-card",
                            title = "Activity recap",
                            subtitle = "${recap.connectionsFormed} clicks · ${recap.messagesSent} messages",
                            onOpen = {},
                        ),
                    ),
            )
    }
    if (data.savedBookmarks.isNotEmpty()) {
        out +=
            HomePileClusterSpec(
                id = "saved",
                label = "Saved events",
                zPriority = 16f,
                photos =
                    data.savedBookmarks.map { bookmark ->
                        HomePilePhotoSpec(
                            id = "saved-${bookmark.beaconId}",
                            title =
                                bookmark.title
                                    ?.trim()
                                    .orEmpty()
                                    .ifEmpty { "Saved event" },
                            subtitle = bookmark.locationName,
                            onOpen = { actions.onSavedEventClick(bookmark) },
                        )
                    },
            )
    }
    if (data.exploreTiles.isNotEmpty()) {
        out +=
            HomePileClusterSpec(
                id = "explore",
                label = "Explore nearby",
                zPriority = 15f,
                photos =
                    data.exploreTiles.map { tile ->
                        HomePilePhotoSpec(
                            id = tile.id,
                            title = tile.label,
                            subtitle = "${tile.count} nearby",
                            onOpen = { actions.onExploreClick(tile) },
                        )
                    },
            )
    }
    val stayPhotos = mutableListOf<HomePilePhotoSpec>()
    data.archiveNotice?.let { notice ->
        stayPhotos +=
            HomePilePhotoSpec(
                id = "stay-${notice.connectionId}",
                title = notice.headline,
                subtitle = notice.body,
                onOpen = { actions.onArchiveOpenChat(notice) },
                onLongOpen = { actions.onArchiveIcebreaker(notice) },
            )
    }
    data.pollPair?.let { suggestion ->
        stayPhotos +=
            HomePilePhotoSpec(
                id = "poll-${suggestion.connectionId}",
                title = "Poll-Pair",
                subtitle = suggestion.otherUserName ?: "Reconnect",
                onOpen = { actions.onPollPairOpenChat(suggestion) },
                onLongOpen = { actions.onPollPairIcebreaker(suggestion) },
            )
    }
    if (stayPhotos.isNotEmpty()) {
        out +=
            HomePileClusterSpec(
                id = "stay",
                label = "Stay in touch",
                zPriority = 22f,
                photos = stayPhotos,
            )
    }
    if (data.reconnectReminders.isNotEmpty()) {
        out +=
            HomePileClusterSpec(
                id = "reconnect",
                label = "Reconnect",
                zPriority = 14f,
                photos =
                    data.reconnectReminders.map { reminder ->
                        HomePilePhotoSpec(
                            id = "reconnect-${reminder.connectionId}",
                            title = reminder.userName ?: "Someone",
                            subtitle = "${reminder.daysSinceContact} days since last chat",
                            imageUrl = data.connectedUsers[reminder.userId]?.image,
                            onOpen = { actions.onReconnect(reminder) },
                            onLongOpen = { actions.onDismissReconnect(reminder) },
                        )
                    },
            )
    }
    if (data.eventReminders.isNotEmpty()) {
        out +=
            HomePileClusterSpec(
                id = "event-reminders",
                label = "Event reminders",
                zPriority = 11f,
                photos =
                    data.eventReminders.map { reminder ->
                        HomePilePhotoSpec(
                            id = "reminder-${reminder.beaconId}-${reminder.kind.name}",
                            title =
                                reminder.title
                                    ?.trim()
                                    .orEmpty()
                                    .ifEmpty { reminder.description },
                            subtitle = "View on map",
                            onOpen = { actions.onEventReminderMap(reminder) },
                        )
                    },
            )
    }
    if (data.locationGroups.isNotEmpty()) {
        out +=
            HomePileClusterSpec(
                id = "recent",
                label = "Recent Connections",
                zPriority = 10f,
                photos =
                    data.locationGroups.entries.map { (location, connections) ->
                        HomePilePhotoSpec(
                            id = "loc-$location",
                            title = location.ifBlank { "Somewhere" },
                            subtitle = "${connections.size} connections",
                            onOpen = { actions.onLocationClick(location) },
                        )
                    },
            )
    } else {
        out +=
            HomePileClusterSpec(
                id = "recent",
                label = "Recent Connections",
                zPriority = 10f,
                photos =
                    listOf(
                        HomePilePhotoSpec(
                            id = "recent-empty",
                            title = "No Connections Yet",
                            subtitle = "Start making connections by tapping Add Click",
                            onOpen = {},
                        ),
                    ),
            )
    }
    data.insights?.let { insights ->
        out +=
            HomePileClusterSpec(
                id = "insights",
                label = "Connection Insights",
                zPriority = 8f,
                photos =
                    listOf(
                        HomePilePhotoSpec(
                            id = "insights-card",
                            title = "Connection Insights",
                            subtitle =
                                "Keep ${insights.keepRate.toInt()}% · Active ${insights.activeConnections} · Need attention ${insights.dormantConnections}",
                            onOpen = {},
                        ),
                    ),
            )
    }
    out +=
        HomePileClusterSpec(
            id = "stats",
            label = "Your Stats",
            zPriority = 6f,
            photos =
                listOf(
                    HomePilePhotoSpec(
                        id = "stats-card",
                        title = "Your Stats",
                        subtitle = "${data.stats.totalConnections} clicks · ${data.stats.uniqueLocations} locations",
                        onOpen = {},
                    ),
                ),
        )
    return out
}

fun homePileRequiredClusterIds(): Set<String> =
    setOf(
        "availability",
        "saved",
        "explore",
        "stay",
        "reconnect",
        "recent",
        "insights",
        "stats",
    )
