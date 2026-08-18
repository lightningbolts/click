@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.heroImageUrl // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.events.formatEventScheduleRange // pragma: allowlist secret
import compose.project.click.click.events.isActiveForDiscoveryFeed // pragma: allowlist secret
import compose.project.click.click.ui.components.AppEmptyState // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenScaffold // pragma: allowlist secret
import compose.project.click.click.ui.components.CardVisualHero // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickLogoPulse // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSearchField // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.DiscoverySortSegmentBar // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.HeaderBackIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.HeaderRefreshIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeChromeAction // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberCardVisual // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberFabAboveNavPadding // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.ConnectionMapPoint // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapRenderData // pragma: allowlist secret
import compose.project.click.click.ui.utils.discoveryFeedSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import compose.project.click.click.ui.utils.haversineDistance // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import kotlinx.datetime.Clock

internal enum class DiscoverySortMode {
    Distance,
    Recent,
}

internal sealed class DiscoveryFeedItem {
    abstract val sortDistanceM: Double
    abstract val sortRecentEpochMs: Long
    abstract val key: String

    data class Hub(
        val hub: CommunityHubPin,
        val distanceM: Double,
        val ttlLabel: String,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = 0L
        override val key: String = "hub-${hub.hubId}"
    }

    data class Beacon(
        val beacon: MapBeacon,
        val distanceM: Double,
        val ttlLabel: String,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = beacon.createdAtEpochMs ?: 0L
        override val key: String = "beacon-${beacon.id}"
    }

    data class Connection(
        val point: ConnectionMapPoint,
        val distanceM: Double,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = point.connection.created
        override val key: String = "conn-${point.connection.id}"
    }
}

internal fun buildDiscoveryFeedItems(
    hubs: List<CommunityHubPin>,
    beacons: List<MapBeacon>,
    renderData: MapRenderData,
    userLat: Double?,
    userLon: Double?,
): List<DiscoveryFeedItem> {
    val now = Clock.System.now().toEpochMilliseconds()

    fun dist(
        lat: Double,
        lon: Double,
    ): Double =
        if (userLat != null && userLon != null) {
            haversineDistance(userLat, userLon, lat, lon)
        } else {
            Double.MAX_VALUE
        }

    val hubRows =
        hubs.map { hub ->
            DiscoveryFeedItem.Hub(
                hub = hub,
                distanceM = dist(hub.latitude, hub.longitude),
                ttlLabel = "Ephemeral · ${hub.activeUserCount} here",
            )
        }

    val beaconRows =
        beacons
            .filter { b -> b.isActiveForDiscoveryFeed(now) }
            .map { beacon ->
                val ttlLabel =
                    when (beacon.kind) {
                        MapBeaconKind.EVENT -> {
                            val scheduleLabel = beacon.eventSchedule()?.let { formatEventScheduleRange(it) }
                            val desc =
                                beacon.metadata.description
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                            when {
                                scheduleLabel != null -> scheduleLabel
                                desc != null -> if (desc.length > 56) desc.take(55) + "…" else desc
                                else -> "Scheduled event"
                            }
                        }
                        else -> beacon.discoveryFeedSubtitle(now)
                    }
                DiscoveryFeedItem.Beacon(
                    beacon = beacon,
                    distanceM = dist(beacon.latitude, beacon.longitude),
                    ttlLabel = ttlLabel,
                )
            }

    val connectionPoints =
        when (renderData) {
            is MapRenderData.IndividualPins -> renderData.points
            is MapRenderData.Clusters -> renderData.clusters.flatMap { it.points }
        }
    val connRows =
        connectionPoints.map { point ->
            DiscoveryFeedItem.Connection(
                point = point,
                distanceM = dist(point.latitude, point.longitude),
            )
        }

    return connRows + hubRows + beaconRows
}

internal data class DiscoveryFeedSection(
    val title: String,
    val items: List<DiscoveryFeedItem>,
)

internal fun groupDiscoveryFeedIntoSections(items: List<DiscoveryFeedItem>): List<DiscoveryFeedSection> {
    if (items.isEmpty()) return emptyList()
    val sections = mutableListOf<DiscoveryFeedSection>()
    val hubs = items.filterIsInstance<DiscoveryFeedItem.Hub>()
    if (hubs.isNotEmpty()) {
        sections += DiscoveryFeedSection(title = "Community hubs", items = hubs)
    }
    val beacons = items.filterIsInstance<DiscoveryFeedItem.Beacon>()
    if (beacons.isNotEmpty()) {
        beacons
            .groupBy { it.beacon.kind }
            .entries
            .sortedBy { (kind, _) -> kind.ordinal }
            .forEach { (kind, rows) ->
                val pluralTitle =
                    when (kind) {
                        MapBeaconKind.SOUNDTRACK -> "Soundtracks"
                        MapBeaconKind.SOS -> "SOS beacons"
                        MapBeaconKind.HAZARD -> "Hazards"
                        MapBeaconKind.UTILITY -> "Utilities"
                        MapBeaconKind.STUDY -> "Study spots"
                        MapBeaconKind.SOCIAL_VIBE -> "Social vibes"
                        MapBeaconKind.EVENT -> "Events"
                        MapBeaconKind.OTHER -> "Beacons"
                    }
                sections +=
                    DiscoveryFeedSection(
                        title = pluralTitle,
                        items = rows,
                    )
            }
    }
    return sections
}

internal fun sortDiscoveryFeedItems(
    items: List<DiscoveryFeedItem>,
    mode: DiscoverySortMode,
): List<DiscoveryFeedItem> =
    when (mode) {
        DiscoverySortMode.Distance -> items.sortedBy { it.sortDistanceM }
        DiscoverySortMode.Recent -> items.sortedByDescending { it.sortRecentEpochMs }
    }

/** Approximate bottom inset so FABs sit clearly above the reopen chip (chip ~72dp + gap). */
internal val EventsReopenChipClearance: Dp = 120.dp

@Composable
private fun DiscoveryFeedLoadingPulse(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        ClickLogoPulse(logoSize = 72.dp)
    }
}

/**
 * Map-first canvas: full interactive map + chrome + peek reopen chip.
 * Full-screen events list is [EventsDiscoveryFullScreen] mounted by MapScreen.
 */
@Composable
internal fun MapDiscoveryScreen(
    eventsListVisible: Boolean,
    onOpenEventsList: () -> Unit,
    eventNearbyCount: Int,
    mapContent: @Composable (Modifier) -> Unit,
    mapChrome: @Composable () -> Unit,
) {
    val fabAboveNav = rememberFabAboveNavPadding()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlassSheetTokens.OledBlack()),
    ) {
        // Map stays mounted for the lifetime of this screen — peek/events overlay must not
        // remount PlatformMap (Android SurfaceView + marker flash).
        mapContent(Modifier.fillMaxSize())

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(10f),
        ) {
            // Keep chrome composed under the events overlay (covered, not disposed).
            mapChrome()
        }

        // Peek stays composed; hide when list is open so we do not leave/enter composition.
        EventsReopenChip(
            count = eventNearbyCount,
            onClick = onOpenEventsList,
            enabled = !eventsListVisible,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(15f)
                    .graphicsLayer { alpha = if (eventsListVisible) 0f else 1f }
                    .padding(start = 16.dp, end = 16.dp, bottom = fabAboveNav),
        )
    }
}

@Composable
internal fun EventsReopenChip(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .background(clickCardSurface())
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Nearby",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Full-screen Nearby discovery (not a modal sheet). Back returns to map + peek chip.
 * Shows layer-filtered beacons (events, soundtracks, alerts, vibes) and hubs — not Events-only.
 * Uses the same floating Liquid Glass header / scaffold chrome as Home & Connections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventsDiscoveryFullScreen(
    feedItems: List<DiscoveryFeedItem>,
    discoveryFeedPending: Boolean,
    discoveryFeedRefreshing: Boolean,
    onRefreshDiscovery: () -> Unit,
    layerFilters: Set<MapLayerFilter>,
    onToggleLayerFilter: (MapLayerFilter) -> Unit,
    viewModel: MapViewModel,
    onBack: () -> Unit,
    onBeaconClick: (MapBeacon, distanceMeters: Double?) -> Unit,
    interactiveBackSwipeOffsetPx: androidx.compose.runtime.MutableFloatState? = null,
) {
    var sortMode by remember { mutableIntStateOf(0) }
    val discoverySortMode = if (sortMode == 0) DiscoverySortMode.Distance else DiscoverySortMode.Recent
    var eventsQuery by remember { mutableStateOf("") }

    val discoverySections =
        remember(feedItems, discoverySortMode, eventsQuery) {
            val sorted = sortDiscoveryFeedItems(feedItems, discoverySortMode)
            val q = eventsQuery.trim()
            val filtered =
                if (q.isEmpty()) {
                    sorted
                } else {
                    sorted.filter { item ->
                        when (item) {
                            is DiscoveryFeedItem.Beacon ->
                                item.beacon.displayDynamicTitle().contains(q, ignoreCase = true) ||
                                    item.ttlLabel.contains(q, ignoreCase = true) ||
                                    item.beacon.metadata.description
                                        .orEmpty()
                                        .contains(q, ignoreCase = true) ||
                                    item.beacon.metadata.trackName
                                        .orEmpty()
                                        .contains(q, ignoreCase = true) ||
                                    item.beacon.metadata.artistName
                                        .orEmpty()
                                        .contains(q, ignoreCase = true) ||
                                    item.beacon.creatorDisplayName
                                        .orEmpty()
                                        .contains(q, ignoreCase = true)
                            is DiscoveryFeedItem.Hub ->
                                item.hub.name.contains(q, ignoreCase = true) ||
                                    item.ttlLabel.contains(q, ignoreCase = true)
                            is DiscoveryFeedItem.Connection ->
                                item.point.displayName.contains(q, ignoreCase = true)
                        }
                    }
                }
            groupDiscoveryFeedIntoSections(filtered)
        }
    val discoveryItemCount =
        remember(discoverySections) {
            discoverySections.sumOf { it.items.size }
        }
    val pullRefreshState = rememberPullToRefreshState()
    val pullFraction = pullRefreshState.distanceFraction
    var crossedRefreshThreshold by remember { mutableStateOf(false) }
    LaunchedEffect(pullFraction, discoveryFeedRefreshing) {
        val atThreshold = pullFraction >= 1f
        if (atThreshold && !crossedRefreshThreshold && !discoveryFeedRefreshing) {
            PlatformHapticsPolicy.lightImpact()
        }
        crossedRefreshThreshold = atThreshold
    }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            isRefreshing = discoveryFeedRefreshing,
            onRefresh = {
                PlatformHapticsPolicy.successNotification()
                onRefreshDiscovery()
            },
            modifier = Modifier.fillMaxSize(),
            state = pullRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = discoveryFeedRefreshing,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            // Clear Dynamic Island / status bar so the spinner is fully visible.
                            .padding(top = statusBarTop + 8.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            AppScreenScaffold(
                title = "Nearby",
                subtitle = null,
                onNavigateBack = onBack,
                nativeTrailingActions =
                    listOf(
                        NativeChromeAction(
                            sfSymbol = "arrow.clockwise",
                            contentDescription = "Refresh feed",
                            onClick = onRefreshDiscovery,
                        ),
                    ),
                navigationIcon = {
                    HeaderBackIconButton(
                        onClick = onBack,
                        contentDescription = "Back to map",
                    )
                },
                actions = {
                    HeaderRefreshIconButton(
                        onClick = onRefreshDiscovery,
                        enabled = !discoveryFeedRefreshing,
                    )
                },
                belowHeaderSpacing = 12.dp,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "events_search") {
                    EventsSheetSearchField(
                        query = eventsQuery,
                        onQueryChange = { eventsQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "events_sort") {
                    DiscoverySortSegmentBar(
                        selectedTabIndex = sortMode,
                        onTabSelected = { sortMode = it },
                    )
                }
                item(key = "events_layers") {
                    EventsSheetLayerChips(
                        layerFilters = layerFilters,
                        onToggleLayerFilter = onToggleLayerFilter,
                    )
                }
                when {
                    discoveryItemCount == 0 && discoveryFeedPending -> {
                        item(key = "events_loading") {
                            DiscoveryFeedLoadingPulse(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                            )
                        }
                    }
                    discoveryItemCount == 0 -> {
                        item(key = "events_empty") {
                            AppEmptyState(
                                icon = Icons.Default.Place,
                                title = "Nothing nearby",
                                body = "Drop a soundtrack or event, enable more layers, or set a simulator location / grant location access so we can load what’s around you.",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    else -> {
                        discoverySections.forEach { section ->
                            item(key = "section-${section.title}") {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 2.dp),
                                )
                            }
                            items(
                                items = section.items,
                                key = { it.key },
                                contentType = { item ->
                                    when (item) {
                                        is DiscoveryFeedItem.Beacon -> "beacon-${item.beacon.kind}"
                                        is DiscoveryFeedItem.Hub -> "hub"
                                        is DiscoveryFeedItem.Connection -> "conn"
                                    }
                                },
                            ) { item ->
                                when (item) {
                                    is DiscoveryFeedItem.Beacon -> {
                                        DiscoveryEventCard(
                                            item = item,
                                            viewModel = viewModel,
                                            onOpen = {
                                                onBeaconClick(
                                                    item.beacon,
                                                    item.distanceM.takeIf {
                                                        it.isFinite() && it < Double.MAX_VALUE
                                                    },
                                                )
                                            },
                                        )
                                    }
                                    is DiscoveryFeedItem.Hub -> {
                                        DiscoveryHubCard(item = item)
                                    }
                                    is DiscoveryFeedItem.Connection -> Unit
                                }
                            }
                        }
                        if (discoveryItemCount == 1) {
                            item(key = "events_only_one") {
                                AppEmptyState(
                                    icon = Icons.Default.Place,
                                    title = "Only 1 beacon nearby",
                                    body = "Drop your own!",
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsSheetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickSearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = "Search nearby…",
        modifier = modifier,
    )
}

@Composable
private fun EventsSheetLayerChips(
    layerFilters: Set<MapLayerFilter>,
    onToggleLayerFilter: (MapLayerFilter) -> Unit,
) {
    val scroll = rememberScrollState()
    val chips =
        listOf(
            MapLayerFilter.EVENTS,
            MapLayerFilter.COMMUNITY_HUBS,
            MapLayerFilter.SOCIAL_VIBES,
            MapLayerFilter.SOUNDTRACKS,
            MapLayerFilter.ALERTS_UTILITIES,
            MapLayerFilter.MY_CONNECTIONS,
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { filter ->
            val selected = MapLayerFilter.ALL in layerFilters || filter in layerFilters
            FilterChipPill(
                label = filter.label,
                selected = selected,
                onClick = { onToggleLayerFilter(filter) },
            )
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        }
    val fg =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.primary
        }
    val outline = MaterialTheme.colorScheme.primary
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = fg,
        maxLines = 1,
        modifier =
            Modifier
                .clip(shape)
                .border(clickBorderWidth(), outline, shape)
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun DiscoveryHubCard(item: DiscoveryFeedItem.Hub) {
    val hub = item.hub
    val shape = RoundedCornerShape(16.dp)
    val distanceText =
        if (item.sortDistanceM < Double.MAX_VALUE / 2) {
            formatDiscoveryDistance(item.sortDistanceM)
        } else {
            null
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(clickCardSurface())
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = hub.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = item.ttlLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (distanceText != null) {
            Text(
                text = distanceText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscoveryEventCard(
    item: DiscoveryFeedItem.Beacon,
    viewModel: MapViewModel,
    onOpen: () -> Unit,
) {
    val beacon = item.beacon
    val isEvent = beacon.kind == MapBeaconKind.EVENT
    val rsvpCache by viewModel.beaconRsvpById.collectAsState()
    val rsvpLoadingIds by viewModel.beaconRsvpLoadingIds.collectAsState()
    val rsvpPendingIds by viewModel.beaconRsvpPendingIds.collectAsState()
    val entry = rsvpCache[beacon.id]
    val attendees = entry?.attendees.orEmpty()
    val currentUserSignedUp = entry?.currentUserSignedUp == true
    val rsvpLoading = isEvent && entry == null && beacon.id in rsvpLoadingIds
    val rsvpPending = beacon.id in rsvpPendingIds

    LaunchedEffect(beacon.id, isEvent) {
        if (isEvent) {
            viewModel.loadBeaconRsvp(beacon.id, forceRefresh = false)
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val title = beacon.displayDynamicTitle()
    val schedule = beacon.eventSchedule()?.let { formatEventScheduleRange(it) }
    val rawDescription =
        beacon.metadata.description
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(beacon.metadata.artistName, beacon.metadata.trackName)
                .joinToString(" · ")
                .takeIf { it.isNotBlank() && it != title }
    // Avoid duplicating description when it was promoted to the title.
    val description = rawDescription?.takeIf { it != title }
    val host =
        beacon.creatorDisplayName?.trim()?.takeIf {
            beacon.showCreatorName && it.isNotEmpty()
        } ?: beacon.createdByUserId?.takeIf { beacon.showCreatorName }?.let { creatorId ->
            AppDataManager
                .getConnectedUser(creatorId)
                ?.name
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    val distanceText =
        if (item.sortDistanceM < Double.MAX_VALUE / 2) {
            formatDiscoveryDistance(item.sortDistanceM)
        } else {
            null
        }
    val kindLabel =
        when (beacon.kind) {
            MapBeaconKind.SOUNDTRACK -> "Soundtrack"
            MapBeaconKind.SOS -> "SOS"
            MapBeaconKind.HAZARD -> "Hazard"
            MapBeaconKind.UTILITY -> "Utility"
            MapBeaconKind.STUDY -> "Study"
            MapBeaconKind.SOCIAL_VIBE -> "Vibe"
            MapBeaconKind.EVENT -> "Event"
            MapBeaconKind.OTHER -> "Beacon"
        }
    val kindIcon =
        when (beacon.kind) {
            MapBeaconKind.SOUNDTRACK -> Icons.Filled.MusicNote
            MapBeaconKind.SOS -> Icons.Filled.Campaign
            MapBeaconKind.HAZARD -> Icons.Filled.Warning
            MapBeaconKind.UTILITY -> Icons.Filled.Build
            MapBeaconKind.STUDY -> Icons.Filled.School
            MapBeaconKind.SOCIAL_VIBE -> Icons.Filled.Celebration
            MapBeaconKind.EVENT -> Icons.Filled.Event
            MapBeaconKind.OTHER -> Icons.Filled.Place
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(clickCardSurface())
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .clickable(onClick = onOpen),
    ) {
        val soundtrackArt = beacon.metadata.heroImageUrl()
        val visual = rememberCardVisual(beacon.id, beacon.kind, beacon.sourceBeaconType)
        CardVisualHero(
            id = beacon.id,
            visual = visual,
            imageUrl = soundtrackArt,
            chipLabel = kindLabel,
            scrim = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(168.dp),
        ) {
            if (soundtrackArt == null) {
                Icon(
                    kindIcon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = visual.onContent,
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f),
                                    ),
                            ),
                        ).padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (host != null) {
                        Text(
                            text = "Hosted by $host",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (distanceText != null) {
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (schedule != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Event,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = schedule,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isEvent) {
                when {
                    rsvpLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    attendees.isNotEmpty() -> {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            attendees.take(6).forEach { attendee ->
                                ConnectionListUserAvatarFace(
                                    displayName = attendee.name,
                                    email = null,
                                    avatarUrl = attendee.avatarUrl,
                                    userId = attendee.userId,
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape),
                                    useCompactTypography = true,
                                )
                            }
                            if (attendees.size > 6) {
                                Text(
                                    text = "+${attendees.size - 6}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
                if (currentUserSignedUp) {
                    OutlinedButton(
                        onClick = { viewModel.cancelRsvpToBeacon(beacon.id) {} },
                        enabled = !rsvpPending && !rsvpLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(clickBorderWidth(), clickBorderColor()),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        Text(
                            text = if (rsvpPending) "Updating…" else "Cancel RSVP",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.rsvpToBeacon(beacon.id) {} },
                        enabled = !rsvpPending && !rsvpLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        Text(
                            text = if (rsvpPending) "Updating…" else "RSVP",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun formatDiscoveryDistance(meters: Double): String =
    when {
        meters < 1_000 -> "${meters.toInt()} m away"
        else -> {
            val km = (meters / 100.0).toInt() / 10.0
            "$km km away"
        }
    }
