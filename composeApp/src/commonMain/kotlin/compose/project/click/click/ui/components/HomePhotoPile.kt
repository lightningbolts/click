@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.lazy.LazyListScope
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
 * Emits a single unified photo pile into the Home [LazyColumn]. Category marker cards are
 * interleaved in the queue. Availability pills and telemetry stay outside this item.
 */
fun LazyListScope.homePhotoPileItems(
    data: HomePileBoardData,
    actions: HomePileActions,
    @Suppress("UNUSED_PARAMETER") expandedClusterId: String? = null,
    @Suppress("UNUSED_PARAMETER") onExpandedClusterChange: (String?) -> Unit = {},
) {
    val photos = buildHomePileQueue(data, actions)
    if (photos.isEmpty()) return
    item(key = "pile_unified", contentType = "photo_pile") {
        PhotoPileStack(
            photos = photos,
            label = "Home photo pile",
        )
    }
}

internal data class HomePilePhotoSpec(
    /** Unique within the queue, used as an animation / list identity. */
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

private fun HomePilePhotoSpec.toPilePhoto(): PilePhoto =
    PilePhoto(
        id = id,
        visualId = visualId,
        title = title,
        subtitle = subtitle,
        imageUrl = imageUrl,
        categoryBadge = categoryBadge,
        onClick = onOpen,
        onLongClick = onLongOpen,
    )

private fun categoryMarker(
    id: String,
    label: String,
): HomePilePhotoSpec =
    HomePilePhotoSpec(
        id = "marker-$id",
        visualId = "marker-$id",
        title = label,
        subtitle = "Swipe to explore",
        categoryBadge = label,
        onOpen = {},
    )

/**
 * Single linear queue:
 * 1. Recently saved events (max 5), featured first when present
 * 2. Category marker: Explore Nearby + items (max 10)
 * 3. Category marker: Stay in Touch + reconnect/archive/poll (max 10)
 * 4. Category marker: Recent Connections + location groups (max 10)
 */
internal fun buildHomePileQueue(
    data: HomePileBoardData,
    actions: HomePileActions,
): List<PilePhoto> = buildHomePileQueueSpecs(data, actions).map { it.toPilePhoto() }

internal fun buildHomePileQueueSpecs(
    data: HomePileBoardData,
    actions: HomePileActions,
): List<HomePilePhotoSpec> {
    val out = mutableListOf<HomePilePhotoSpec>()

    val saved = mutableListOf<HomePilePhotoSpec>()
    data.featuredEvent?.let { featured ->
        saved +=
            HomePilePhotoSpec(
                id = "featured-${featured.beaconId}",
                visualId = featured.beaconId,
                title =
                    featured.title
                        ?.trim()
                        .orEmpty()
                        .ifEmpty { featured.description },
                subtitle = "View on map",
                categoryBadge = "Saved",
                onOpen = { actions.onFeaturedMap(featured) },
            )
    }
    data.savedBookmarks.forEach { bookmark ->
        if (saved.any { it.visualId == bookmark.beaconId }) return@forEach
        saved +=
            HomePilePhotoSpec(
                id = "saved-${bookmark.beaconId}",
                visualId = bookmark.beaconId,
                title =
                    bookmark.title
                        ?.trim()
                        .orEmpty()
                        .ifEmpty { "Saved event" },
                subtitle = bookmark.locationName,
                categoryBadge = "Saved",
                onOpen = { actions.onSavedEventClick(bookmark) },
            )
    }
    out += saved.take(5)

    val explore =
        data.exploreTiles.take(10).map { tile ->
            HomePilePhotoSpec(
                id = tile.id,
                title = tile.label,
                subtitle = "${tile.count} nearby",
                categoryBadge = "Explore Nearby",
                onOpen = { actions.onExploreClick(tile) },
            )
        }
    if (explore.isNotEmpty()) {
        out += categoryMarker("explore", "Explore Nearby")
        out += explore
    }

    val stayPhotos = mutableListOf<HomePilePhotoSpec>()
    data.archiveNotice?.let { notice ->
        stayPhotos +=
            HomePilePhotoSpec(
                id = "stay-${notice.connectionId}",
                visualId = notice.connectionId,
                title = notice.headline,
                subtitle = notice.body,
                categoryBadge = "Stay in Touch",
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
                categoryBadge = "Stay in Touch",
                onOpen = { actions.onPollPairOpenChat(suggestion) },
                onLongOpen = { actions.onPollPairIcebreaker(suggestion) },
            )
    }
    data.reconnectReminders.forEach { reminder ->
        stayPhotos +=
            HomePilePhotoSpec(
                id = "reconnect-${reminder.connectionId}",
                visualId = reminder.connectionId,
                title = reminder.userName ?: "Someone",
                subtitle = "${reminder.daysSinceContact} days since last chat",
                imageUrl = data.connectedUsers[reminder.userId]?.image,
                categoryBadge = "Stay in Touch",
                onOpen = { actions.onReconnect(reminder) },
                onLongOpen = { actions.onDismissReconnect(reminder) },
            )
    }
    val stay = stayPhotos.take(10)
    if (stay.isNotEmpty()) {
        out += categoryMarker("stay", "Stay in Touch")
        out += stay
    }

    val recent =
        if (data.locationGroups.isNotEmpty()) {
            data.locationGroups.entries.take(10).map { (location, connections) ->
                HomePilePhotoSpec(
                    id = "loc-$location",
                    title = location.ifBlank { "Somewhere" },
                    subtitle = "${connections.size} connections",
                    categoryBadge = "Recent Connections",
                    onOpen = { actions.onLocationClick(location) },
                )
            }
        } else {
            listOf(
                HomePilePhotoSpec(
                    id = "recent-empty",
                    title = "No Connections Yet",
                    subtitle = "Start making connections by tapping Add Click",
                    categoryBadge = "Recent Connections",
                    onOpen = {},
                ),
            )
        }
    out += categoryMarker("recent", "Recent Connections")
    out += recent
    return out
}

/** @deprecated Clusters collapsed into [buildHomePileQueueSpecs]. Kept for test compatibility. */
internal fun buildHomePileClusters(
    data: HomePileBoardData,
    actions: HomePileActions,
): List<HomePileClusterSpec> {
    val specs = buildHomePileQueueSpecs(data, actions)

    fun section(
        id: String,
        label: String,
        zPriority: Float,
        photos: List<HomePilePhotoSpec>,
    ): HomePileClusterSpec? {
        if (photos.isEmpty()) return null
        return HomePileClusterSpec(id = id, label = label, zPriority = zPriority, photos = photos)
    }
    val saved = specs.filter { it.id.startsWith("saved-") || it.id.startsWith("featured-") }
    val explore = specs.filter { it.categoryBadge == "Explore Nearby" && !it.id.startsWith("marker-") }
    val stay = specs.filter { it.categoryBadge == "Stay in Touch" && !it.id.startsWith("marker-") }
    val recent = specs.filter { it.categoryBadge == "Recent Connections" && !it.id.startsWith("marker-") }
    return listOfNotNull(
        section("saved", "Saved events", 16f, saved),
        section("explore", "Explore nearby", 15f, explore),
        section("stay", "Stay in touch", 22f, stay),
        section("recent", "Recent Connections", 10f, recent),
    )
}

fun homePileRequiredClusterIds(): Set<String> =
    setOf(
        "saved",
        "explore",
        "stay",
        "recent",
    )
