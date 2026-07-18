package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.events.HomeEventReminder
import compose.project.click.click.events.eventReminderBody
import compose.project.click.click.events.eventReminderTitle
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenScaffold // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberBottomChromePadding // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastHost
import compose.project.click.click.ui.components.rememberUnifiedToastState
import compose.project.click.click.ui.components.PollPairCard // pragma: allowlist secret
import compose.project.click.click.ui.components.AvailabilitySheet // pragma: allowlist secret
import compose.project.click.click.ui.components.AppShimmerScreen // pragma: allowlist secret
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.viewmodel.AvailabilityViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.HomeViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.HomeState // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.EventBookmarkItemDto
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionInsights // pragma: allowlist secret
import compose.project.click.click.data.models.ReconnectReminder // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.mostUrgentArchiveNotice // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionArchiveWarningBanner // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.ExploreNearbyBeaconsSection // pragma: allowlist secret
import compose.project.click.click.ui.components.FeaturedEventSection // pragma: allowlist secret
import compose.project.click.click.ui.components.HomeExploreTile // pragma: allowlist secret
import compose.project.click.click.ui.components.homeGreetingTitle // pragma: allowlist secret
import compose.project.click.click.ui.components.HomeGreetingSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.components.HomeSearchPill // pragma: allowlist secret
import compose.project.click.click.ui.components.toHomeExploreTile // pragma: allowlist secret
import compose.project.click.click.events.isActiveForDiscoveryFeed // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import androidx.compose.material.icons.filled.Groups
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

// Spacing constants matching app's consistent 20.dp horizontal padding
private val ScreenPaddingHorizontal = 20.dp
private val CardSpacing = 24.dp
/** Visible gap under the floating greeting before the search pill (cancels spacedBy after inset). */
private val HeaderToSearchGap = 8.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel { HomeViewModel() },
    onNavigateToChat: (String) -> Unit = {},
    onOpenSearch: (() -> Unit)? = null,
    onNavigateToMap: (beaconId: String?) -> Unit = {},
    onNavigateToMapLayer: (MapLayerFilter) -> Unit = {},
) {
    val homeState by viewModel.homeState.collectAsState()
    val reconnectReminders by viewModel.reconnectReminders.collectAsState()
    val homeEventReminders by viewModel.homeEventReminders.collectAsState()
    val savedEventBookmarks by viewModel.savedEventBookmarks.collectAsState()
    val connectionInsights by viewModel.connectionInsights.collectAsState()
    val showInsightsPanel by viewModel.showInsightsPanel.collectAsState()
    val locationGroupedConnections by viewModel.locationGroupedConnections.collectAsState()
    val expandedLocations by viewModel.expandedLocations.collectAsState()
    val connectedUsers by viewModel.connectedUsers.collectAsState()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val pollPairSuggestion by viewModel.pollPairSuggestion.collectAsState()
    val icebreakerSendCooldownSec by viewModel.icebreakerSendCooldownRemainingSec.collectAsState()
    val homeAvailabilityIntents by viewModel.homeAvailabilityIntents.collectAsState()
    val homeAvailabilityOverlapMessages by viewModel.homeAvailabilityOverlapMessages.collectAsState()
    val archivedForHome by AppDataManager.archivedConnectionIds.collectAsState()
    val hiddenForHome by AppDataManager.hiddenConnectionIds.collectAsState()
    val connectionsForArchiveBanner by AppDataManager.connections.collectAsState()
    val prefetchedBeacons by AppDataManager.prefetchedMapBeacons.collectAsState()
    val prefetchedHubs by AppDataManager.prefetchedCommunityHubs.collectAsState()
    var archiveBannerNow by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(60_000)
            archiveBannerNow = Clock.System.now().toEpochMilliseconds()
        }
    }

    val archiveBannerNotice = remember(
        homeState,
        connectionsForArchiveBanner,
        archiveBannerNow,
        connectedUsers,
        archivedForHome,
        hiddenForHome,
    ) {
        val success = homeState as? HomeState.Success ?: return@remember null
        connectionsForArchiveBanner
            .filter { it.isActiveForUser(archivedForHome, hiddenForHome) }
            .mostUrgentArchiveNotice(archiveBannerNow) { conn ->
            val otherId = conn.user_ids.firstOrNull { it != success.user.id }
            otherId?.let { connectedUsers[it]?.name?.trim() }?.takeIf { it.isNotBlank() }
                ?: conn.displayLocationLabel?.trim()?.takeIf { it.isNotBlank() }
                ?: "this connection"
        }
    }

    val exploreTiles = remember(prefetchedBeacons, prefetchedHubs) {
        buildHomeExploreTiles(prefetchedBeacons, prefetchedHubs)
    }

    val featuredEvent = homeEventReminders.firstOrNull()
    val remainingEventReminders = remember(homeEventReminders, featuredEvent) {
        val featuredId = featuredEvent?.beaconId
        if (featuredId == null) homeEventReminders
        else homeEventReminders.filterNot { it.beaconId == featuredId }
    }

    val toastState = rememberUnifiedToastState()
    val scope = rememberCoroutineScope()

    val availabilityViewModel: AvailabilityViewModel = viewModel { AvailabilityViewModel() }
    var showAvailabilityIntentSheet by remember { mutableStateOf(false) }

    LaunchedEffect(nudgeResult) {
        val result = nudgeResult
        if (result != null) {
            toastState.show(scope, result)
            viewModel.clearNudgeResult()
        }
    }

    LaunchedEffect(showAvailabilityIntentSheet) {
        if (!showAvailabilityIntentSheet) {
            viewModel.refreshHomeAvailabilityIntents()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = homeState) {
            is HomeState.Loading -> {
                AppShimmerScreen(
                    isDarkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                )
            }
            is HomeState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ScreenPaddingHorizontal),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Error loading home data",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Retry")
                    }
                }
            }
            is HomeState.Success -> {
                val firstName = state.user.name
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "there"
                AppScreenScaffold(
                    title = homeGreetingTitle(firstName),
                    subtitle = HomeGreetingSubtitle,
                    showFloatingHeader = true,
                    // spacedBy also applies after the header inset item — compensate so search
                    // sits HeaderToSearchGap under the greeting, not CardSpacing×2.
                    belowHeaderSpacing = HeaderToSearchGap - CardSpacing,
                    verticalArrangement = Arrangement.spacedBy(CardSpacing),
                ) {
                        if (onOpenSearch != null) {
                            item(key = "home_search_pill") {
                                HomeSearchPill(onClick = onOpenSearch)
                            }
                        }

                        featuredEvent?.let { reminder ->
                            item(key = "featured_event") {
                                FeaturedEventSection(
                                    reminder = reminder,
                                    onViewMap = { onNavigateToMap(reminder.beaconId) },
                                )
                            }
                        }

                        if (savedEventBookmarks.isNotEmpty()) {
                            item(key = "saved_events_header") {
                                SectionHeader(text = "Saved events")
                            }
                            items(savedEventBookmarks, key = { "saved-${it.beaconId}" }) { bookmark ->
                                SavedEventBookmarkCard(
                                    bookmark = bookmark,
                                    onViewMap = { onNavigateToMap(bookmark.beaconId) },
                                )
                            }
                        }

                        item(key = "availability_intents_strip") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HomeAvailabilityIntentsRow(
                                    intents = homeAvailabilityIntents,
                                    onOpenSheet = {
                                        availabilityViewModel.resetAvailabilityIntentSheet()
                                        showAvailabilityIntentSheet = true
                                    },
                                )
                                if (homeAvailabilityOverlapMessages.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        homeAvailabilityOverlapMessages.forEach { line ->
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (exploreTiles.isNotEmpty()) {
                            item(key = "explore_nearby") {
                                ExploreNearbyBeaconsSection(
                                    tiles = exploreTiles,
                                    onTileClick = { tile -> onNavigateToMapLayer(tile.layerFilter) },
                                )
                            }
                        }

                        archiveBannerNotice?.let { notice ->
                            item(key = "archive_banner") {
                                ConnectionArchiveWarningBanner(
                                    notice = notice,
                                    onOpenChat = { onNavigateToChat(notice.connectionId) },
                                    onSendIcebreaker = { viewModel.sendArchiveBannerIcebreaker(notice) },
                                    modifier = Modifier.fillMaxWidth(),
                                    icebreakerSendEnabled = icebreakerSendCooldownSec <= 0,
                                    icebreakerCooldownSec = icebreakerSendCooldownSec,
                                )
                            }
                        }
                        pollPairSuggestion?.let { suggestion ->
                            item(key = "poll_pair_card") {
                                PollPairCard(
                                    suggestion = suggestion,
                                    onOpenChat = { onNavigateToChat(suggestion.connectionId) },
                                    onSendIcebreaker = { viewModel.sendPollPairIcebreaker(suggestion) },
                                    icebreakerSendEnabled = icebreakerSendCooldownSec <= 0,
                                    icebreakerCooldownSec = icebreakerSendCooldownSec,
                                )
                            }
                        }

                        if (reconnectReminders.isNotEmpty()) {
                            item(key = "reconnect_header") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SectionHeader(text = "Reconnect")
                                    Text(
                                        "Connections you haven't talked to in a while",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            items(reconnectReminders, key = { it.connectionId }) { reminder ->
                                val peer = connectedUsers[reminder.userId]
                                ReconnectReminderCard(
                                    reminder = reminder,
                                    onReconnect = { onNavigateToChat(reminder.connectionId) },
                                    onDismiss = { viewModel.dismissReminder(reminder.connectionId) },
                                    avatarUrl = peer?.image,
                                    email = peer?.email,
                                )
                            }
                        }

                        if (remainingEventReminders.isNotEmpty()) {
                            item(key = "event_reminders_header") {
                                SectionHeader(text = "Event reminders")
                            }
                            items(
                                remainingEventReminders,
                                key = { "${it.beaconId}:${it.kind.name}" },
                            ) { reminder ->
                                HomeEventReminderCard(
                                    reminder = reminder,
                                    onDismiss = {
                                        viewModel.dismissEventReminder(reminder.beaconId, reminder.kind)
                                    },
                                    onViewMap = { onNavigateToMap(reminder.beaconId) },
                                )
                            }
                        }

                        if (locationGroupedConnections.isNotEmpty()) {
                            item(key = "recent_connections_header") {
                                SectionHeader(text = "Recent Connections")
                            }
                            items(locationGroupedConnections.entries.toList(), key = { it.key }) { (location, connections) ->
                                val isExpanded = location in expandedLocations
                                LocationGroupCard(
                                    location = location,
                                    connections = connections,
                                    isExpanded = isExpanded,
                                    connectedUsers = connectedUsers,
                                    currentUserId = state.user.id,
                                    onToggleExpand = { viewModel.toggleLocationExpanded(location) },
                                    onNavigateToChat = onNavigateToChat,
                                    onNudge = { connectionId, otherUserName ->
                                        viewModel.sendNudgeByConnectionId(connectionId, otherUserName)
                                    }
                                )
                            }
                        } else {
                            item(key = "empty_connections") {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.TouchApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "No Connections Yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Start making connections by tapping Add Click",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (connectionInsights != null && state.stats.totalConnections > 0) {
                            item(key = "connection_insights") {
                                ConnectionInsightsCard(
                                    insights = connectionInsights!!,
                                    expanded = showInsightsPanel,
                                    onToggle = { viewModel.toggleInsightsPanel() }
                                )
                            }
                        }

                        item(key = "stats_header") {
                            SectionHeader(text = "Your Stats")
                        }

                        item(key = "stats_row") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HomeStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Check,
                                    value = state.stats.totalConnections.toString(),
                                    label = "Total Clicks"
                                )
                                HomeStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.LocationOn,
                                    value = state.stats.uniqueLocations.toString(),
                                    label = "Locations"
                                )
                            }
                        }
                }
            }
        }

        if (showAvailabilityIntentSheet) {
            AvailabilitySheet(
                viewModel = availabilityViewModel,
                onDismiss = {
                    showAvailabilityIntentSheet = false
                    availabilityViewModel.resetAvailabilityIntentSheet()
                },
            )
        }

        UnifiedToastHost(
            state = toastState,
            opaque = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = rememberBottomChromePadding() + 8.dp),
        )
    }
}

private fun buildHomeExploreTiles(
    beacons: List<compose.project.click.click.data.models.MapBeacon>,
    hubs: List<compose.project.click.click.data.api.CommunityHubNearbyDto>,
): List<HomeExploreTile> {
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val activeBeacons = beacons.filter { it.isActiveForDiscoveryFeed(nowMs) }
    val kindTiles = activeBeacons
        .groupBy { it.kind }
        .entries
        .sortedBy { it.key.ordinal }
        .map { (kind, group) -> kind.toHomeExploreTile(group.size) }
    val hubCount = hubs.size
    val hubTile = if (hubCount > 0) {
        HomeExploreTile(
            id = "hubs",
            label = "Hub",
            count = hubCount,
            layerFilter = MapLayerFilter.COMMUNITY_HUBS,
            icon = Icons.Filled.Groups,
        )
    } else {
        null
    }
    return buildList {
        addAll(kindTiles)
        hubTile?.let { add(it) }
    }
}

/**
 * Section header with solid Neo-Brutalist typography.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Stat card — Functional Clarity bordered surface.
 */
@Composable
private fun HomeStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    GlassCard(
        modifier = modifier,
        usePrimaryBorder = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * An expandable card grouping all connections made at a single semantic location.
 * Tapping the header toggles the list of individual connections open/closed.
 */
@Composable
private fun LocationGroupCard(
    location: String,
    connections: List<Connection>,
    isExpanded: Boolean,
    connectedUsers: Map<String, User>,
    currentUserId: String,
    onToggleExpand: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNudge: (connectionId: String, otherUserName: String) -> Unit
) {
    val chevronAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "chevron"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggleExpand,
        usePrimaryBorder = isExpanded
    ) {
        Column {
            // Group header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        location,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${connections.size} connection${if (connections.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Count badge
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .border(2.dp, clickBorderColor(), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        connections.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(chevronAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded individual connections
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        color = clickBorderColor(),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    connections.forEach { connection ->
                        val otherUserId = connection.user_ids.firstOrNull { it != currentUserId }
                        val otherUser = otherUserId?.let { connectedUsers[it] }
                        ConnectionRowItem(
                            connection = connection,
                            otherUser = otherUser,
                            currentUserId = currentUserId,
                            onNavigate = { onNavigateToChat(connection.id) },
                            onNudge = {
                                onNudge(connection.id, otherUser?.name ?: "them")
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual connection row rendered inside an expanded LocationGroupCard.
 */
@Composable
private fun ConnectionRowItem(
    connection: Connection,
    otherUser: User?,
    currentUserId: String,
    onNavigate: () -> Unit,
    onNudge: () -> Unit
) {
    val duration = (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - connection.created).milliseconds
    val timeAgo = when {
        duration.inWholeMinutes < 1 -> "Just now"
        duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
        duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
        duration.inWholeDays < 7 -> "${duration.inWholeDays}d ago"
        else -> {
            val dt = Instant.fromEpochMilliseconds(connection.created)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            "${dt.month.name.take(3)} ${dt.dayOfMonth}"
        }
    }
    val displayName = otherUser?.name ?: "Connection"

    val rowStyle = LocalPlatformStyle.current
    val rowShape = RoundedCornerShape(if (rowStyle.isIOS) 14.dp else 12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable { onNavigate() }
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, clickBorderColor(), rowShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, clickBorderColor(), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                displayName.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                timeAgo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Nudge button
        IconButton(
            onClick = onNudge,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = "Nudge",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Chat button
        IconButton(
            onClick = onNavigate,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Chat,
                contentDescription = "Open chat",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectionCard(connection: Connection, currentUserId: String) {
    val otherUserId = connection.user_ids.firstOrNull { it != currentUserId }

    val instant = Instant.fromEpochMilliseconds(connection.created)
    val now = kotlinx.datetime.Clock.System.now()
    val duration = (now.toEpochMilliseconds() - connection.created).milliseconds

    val timeAgo = when {
        duration.inWholeMinutes < 1 -> "Just now"
        duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
        duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
        duration.inWholeDays < 7 -> "${duration.inWholeDays}d ago"
        else -> {
            val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${dateTime.month.name.take(3)} ${dateTime.dayOfMonth}"
        }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { /* Navigate to connection details */ }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, clickBorderColor(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    connection.semanticLocation ?: "Connection",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        timeAgo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Bookmarked event row on Home — private saves from Map event detail.
 */
@Composable
fun SavedEventBookmarkCard(
    bookmark: EventBookmarkItemDto,
    onViewMap: () -> Unit,
) {
    val title = bookmark.title?.takeIf { it.isNotBlank() } ?: "Saved event"
    val timeBadge = bookmark.eventStartAt
        ?.let { iso -> runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull() }
        ?.let { formatSavedEventTimeBadge(it) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        usePrimaryBorder = true,
        contentPadding = 14.dp,
        onClick = onViewMap,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (timeBadge != null) {
                    Text(
                        text = timeBadge,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            TextButton(
                onClick = onViewMap,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("View on Map", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatSavedEventTimeBadge(startEpochMs: Long): String {
    val now = Clock.System.now()
    val start = Instant.fromEpochMilliseconds(startEpochMs)
    val local = start.toLocalDateTime(TimeZone.currentSystemDefault())
    val nowLocal = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = local.hour
    val minute = local.minute
    val amPm = if (hour < 12) "AM" else "PM"
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val timeStr = if (minute == 0) {
        "$hour12 $amPm"
    } else {
        "$hour12:${minute.toString().padStart(2, '0')} $amPm"
    }
    return when {
        local.date == nowLocal.date -> "Today $timeStr"
        else -> "${local.month.name.take(3)} ${local.dayOfMonth} · $timeStr"
    }
}

/**
 * In-app event reminder surfaced on Home (day-of + one hour before start).
 */
@Composable
fun HomeEventReminderCard(
    reminder: HomeEventReminder,
    onDismiss: () -> Unit,
    onViewMap: (() -> Unit)? = null,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        usePrimaryBorder = true,
        contentPadding = 14.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = eventReminderTitle(reminder.kind, reminder.description),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = eventReminderBody(reminder.kind, reminder.description, reminder.title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                if (onViewMap != null) {
                    TextButton(onClick = onViewMap) {
                        Text("View on Map", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Card for displaying a reconnect reminder.
 */
@Composable
fun ReconnectReminderCard(
    reminder: ReconnectReminder,
    onReconnect: () -> Unit,
    onDismiss: () -> Unit,
    avatarUrl: String? = null,
    email: String? = null,
) {
    val actionShape = RoundedCornerShape(8.dp)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        usePrimaryBorder = true,
        contentPadding = 14.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionListUserAvatarFace(
                    displayName = reminder.userName,
                    email = email,
                    avatarUrl = avatarUrl,
                    userId = reminder.userId,
                    modifier = Modifier
                        .size(44.dp)
                        .border(2.dp, clickBorderColor(), CircleShape),
                    useCompactTypography = true,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reminder.userName ?: "Someone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${reminder.daysSinceContact} days since last chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = actionShape,
                    border = BorderStroke(2.dp, clickBorderColor()),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onReconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = actionShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Message",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Expandable card for displaying connection insights - Glass styled
 */
@Composable
fun ConnectionInsightsCard(
    insights: ConnectionInsights,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        usePrimaryBorder = true
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Connection Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Quick stats row (always visible)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InsightStat(
                    value = "${insights.keepRate.toInt()}%",
                    label = "Keep Rate"
                )
                InsightStat(
                    value = insights.activeConnections.toString(),
                    label = "Active"
                )
                InsightStat(
                    value = insights.dormantConnections.toString(),
                    label = "Need Attention"
                )
            }
            
            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = clickBorderColor())
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InsightRow(
                        icon = Icons.Filled.Group,
                        label = "Total Connections",
                        value = insights.totalConnections.toString()
                    )
                    
                    InsightRow(
                        icon = Icons.Filled.Favorite,
                        label = "Connections Kept",
                        value = insights.keptConnections.toString()
                    )
                    
                    if (insights.longestConnectionDays > 0) {
                        val longestName = insights.longestConnectionName?.trim()?.takeIf { it.isNotEmpty() }
                        InsightRow(
                            icon = Icons.Filled.AccessTime,
                            label = "Longest Connection",
                            value = if (longestName != null) {
                                "${insights.longestConnectionDays} days\n($longestName)"
                            } else {
                                "${insights.longestConnectionDays} days"
                            },
                        )
                    }
                    
                    InsightRow(
                        icon = Icons.Filled.CalendarToday,
                        label = "New This Week",
                        value = insights.connectionsThisWeek.toString()
                    )
                    
                    InsightRow(
                        icon = Icons.Filled.DateRange,
                        label = "New This Month",
                        value = insights.connectionsThisMonth.toString()
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightStat(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InsightRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeAvailabilityIntentsRow(
    intents: List<AvailabilityIntentRow>,
    onOpenSheet: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = "I'm down for…")
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            intents.forEach { row ->
                val label = row.intentTag?.trim().orEmpty().ifEmpty { "Intent" }
                val sub = row.activeUntilLabel()
                AssistChip(
                    onClick = onOpenSheet,
                    label = {
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (sub.isNotBlank()) {
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(2.dp, clickBorderColor()),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
            AssistChip(
                onClick = onOpenSheet,
                label = {
                    Text(
                        if (intents.isEmpty()) "Set what you're down for" else "Edit intents",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(2.dp, clickBorderColor()),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
