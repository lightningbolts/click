@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/**
 * Emits one full-width row per pile cluster into the Home [LazyColumn]: a [SectionHeader] label with
 * its own [PileCluster] stack underneath.
 *
 * Rows are deliberately stacked vertically instead of scattered across a shared canvas. The previous
 * free-floating board positioned every cluster absolutely inside one viewport-sized box, so clusters
 * overlapped and sliced each other's labels in half.
 *
 * [expandedClusterId] is hoisted so the caller can own system back and cross-cluster exclusivity.
 */
fun LazyListScope.homePhotoPileItems(
    data: HomePileBoardData,
    actions: HomePileActions,
    expandedClusterId: String?,
    onExpandedClusterChange: (String?) -> Unit,
) {
    val clusters = buildHomePileClusters(data, actions)
    clusters.forEach { cluster ->
        item(key = "pile_${cluster.id}", contentType = "pile_cluster") {
            val expanded = expandedClusterId == cluster.id
            val photos =
                cluster.photos.map { spec ->
                    PilePhoto(
                        id = spec.id,
                        visualId = spec.visualId,
                        title = spec.title,
                        subtitle = spec.subtitle,
                        imageUrl = spec.imageUrl,
                        categoryBadge = spec.categoryBadge,
                        onClick = {
                            if (expanded) spec.onOpen() else onExpandedClusterChange(cluster.id)
                        },
                        onLongClick =
                            spec.onLongOpen?.let { longOpen ->
                                { if (expanded) longOpen() }
                            },
                    )
                }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    text = cluster.label,
                    trailing =
                        if (expanded) {
                            {
                                TextButton(onClick = { onExpandedClusterChange(null) }) {
                                    Text(
                                        text = "Show less",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )
                PileCluster(
                    clusterId = cluster.id,
                    label = cluster.label,
                    photos = photos,
                    expanded = expanded,
                    onExpand = { onExpandedClusterChange(cluster.id) },
                    onCollapse = { onExpandedClusterChange(null) },
                )
            }
        }
    }
}

internal data class HomePilePhotoSpec(
    /** Unique within the cluster, used as an animation / list identity. */
    val id: String,
    /**
     * Seed for the shared card-visual generator. Must be the raw entity id (`beacon.id`) where one
     * exists, so a beacon keeps the same gradient on the pile, in the Events list, on its map pin,
     * and in its detail sheet. Defaults to [id] for synthetic cards with no entity behind them.
     */
    val visualId: String = id,
    val title: String,
    val subtitle: String?,
    val imageUrl: String? = null,
    val categoryBadge: String? = null,
    val onOpen: () -> Unit,
    val onLongOpen: (() -> Unit)? = null,
)

internal data class HomePileClusterSpec(
    val id: String,
    val label: String,
    /** Order weight: higher clusters appear nearer the top of the page. */
    val zPriority: Float,
    val photos: List<HomePilePhotoSpec>,
)

internal fun buildHomePileClusters(
    data: HomePileBoardData,
    actions: HomePileActions,
): List<HomePileClusterSpec> {
    val out = mutableListOf<HomePileClusterSpec>()
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
                            visualId = featured.beaconId,
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
    if (data.savedBookmarks.isNotEmpty()) {
        out +=
            HomePileClusterSpec(
                id = "saved",
                label = "Saved events",
                zPriority = 16f,
                photos =
                    data.savedBookmarks.take(5).map { bookmark ->
                        HomePilePhotoSpec(
                            id = "saved-${bookmark.beaconId}",
                            visualId = bookmark.beaconId,
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
                    data.exploreTiles.take(10).map { tile ->
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
                visualId = notice.connectionId,
                title = notice.headline,
                subtitle = notice.body,
                categoryBadge = "Stay in touch",
                onOpen = { actions.onArchiveOpenChat(notice) },
                onLongOpen = { actions.onArchiveIcebreaker(notice) },
            )
    }
    data.pollPair?.let { suggestion ->
        stayPhotos +=
            HomePilePhotoSpec(
                id = "poll-${suggestion.connectionId}",
                visualId = suggestion.connectionId,
                title = "Poll-Pair",
                subtitle = suggestion.otherUserName ?: "Reconnect",
                categoryBadge = "Stay in touch",
                onOpen = { actions.onPollPairOpenChat(suggestion) },
                onLongOpen = { actions.onPollPairIcebreaker(suggestion) },
            )
    }
    val reconnectPhotos =
        data.reconnectReminders.map { reminder ->
            HomePilePhotoSpec(
                id = "reconnect-${reminder.connectionId}",
                visualId = reminder.connectionId,
                title = reminder.userName ?: "Someone",
                subtitle = "${reminder.daysSinceContact} days since last chat",
                imageUrl = data.connectedUsers[reminder.userId]?.image,
                categoryBadge = "Reconnect",
                onOpen = { actions.onReconnect(reminder) },
                onLongOpen = { actions.onDismissReconnect(reminder) },
            )
        }
    val mixedStayInTouch = stayPhotos.isNotEmpty() && reconnectPhotos.isNotEmpty()
    val stayInTouchPhotos =
        (stayPhotos + reconnectPhotos)
            .take(10)
            .map { photo ->
                if (mixedStayInTouch) photo else photo.copy(categoryBadge = null)
            }
    if (stayInTouchPhotos.isNotEmpty()) {
        out +=
            HomePileClusterSpec(
                id = "stay",
                label = "Stay in touch",
                zPriority = 22f,
                photos = stayInTouchPhotos,
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
                            visualId = reminder.beaconId,
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
                    data.locationGroups.entries.take(10).map { (location, connections) ->
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
    return out.sortedByDescending { it.zPriority }
}

fun homePileRequiredClusterIds(): Set<String> =
    setOf(
        "saved",
        "explore",
        "stay",
        "recent",
    )
