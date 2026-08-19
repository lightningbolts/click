@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.CommunityHubNearbyDto // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.HomeLayoutMode // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.mostUrgentArchiveNotice // pragma: allowlist secret
import compose.project.click.click.ui.components.ActivityRecapSection // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenScaffold // pragma: allowlist secret
import compose.project.click.click.ui.components.AppShimmerScreen // pragma: allowlist secret
import compose.project.click.click.ui.components.AvailabilitySheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDialogChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionArchiveWarningBanner // pragma: allowlist secret
import compose.project.click.click.ui.components.ExploreNearbyBeaconsSection // pragma: allowlist secret
import compose.project.click.click.ui.components.FeaturedEventSection // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.HeaderChromeIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.HomeGreetingSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.components.HomePileActions // pragma: allowlist secret
import compose.project.click.click.ui.components.HomePileBoardData // pragma: allowlist secret
import compose.project.click.click.ui.components.HomeSearchPill // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeChromeAction // pragma: allowlist secret
import compose.project.click.click.ui.components.PollPairCard // pragma: allowlist secret
import compose.project.click.click.ui.components.SavedEventsSection // pragma: allowlist secret
import compose.project.click.click.ui.components.SectionHeader // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.homeGreetingTitle // pragma: allowlist secret
import compose.project.click.click.ui.components.homePhotoPileItems // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberBottomChromePadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberUnifiedToastState // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeMapBeaconLists // pragma: allowlist secret
import compose.project.click.click.viewmodel.AvailabilityViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.HomeState // pragma: allowlist secret
import compose.project.click.click.viewmodel.HomeViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock

// Spacing constants matching app's consistent 20.dp horizontal padding
private val ScreenPaddingHorizontal = 20.dp
private val CardSpacing = 24.dp

/** Visible gap under the native greeting before the sticky search pill. */
private val HeaderToSearchGap = 12.dp

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel { HomeViewModel() },
    mapViewModel: MapViewModel,
    onNavigateToChat: (String) -> Unit = {},
    onOpenSearch: (() -> Unit)? = null,
    onNavigateToMap: (beaconId: String?) -> Unit = {},
    onNavigateToMapLayer: (MapLayerFilter) -> Unit = {},
    onNavigateToAddClick: () -> Unit = {},
    onShareBeaconToChats: (
        (
            MapBeacon,
            List<String>,
            String?,
        ) -> Unit
    )? = null,
) {
    val homeState by homeViewModel.homeState.collectAsState()
    var lastSuccessfulHomeState by remember { mutableStateOf<HomeState.Success?>(null) }
    if (homeState is HomeState.Success) {
        SideEffect { lastSuccessfulHomeState = homeState as HomeState.Success }
    }
    val renderedHomeState: HomeState =
        if (homeState is HomeState.Loading) lastSuccessfulHomeState ?: homeState else homeState
    val reconnectReminders by homeViewModel.reconnectReminders.collectAsState()
    val homeEventReminders by homeViewModel.homeEventReminders.collectAsState()
    val savedEventBookmarks by homeViewModel.savedEventBookmarks.collectAsState()
    val activityRecap by homeViewModel.activityRecap.collectAsState()
    val recapWindow by homeViewModel.recapWindow.collectAsState()
    val connectionInsights by homeViewModel.connectionInsights.collectAsState()
    val showInsightsPanel by homeViewModel.showInsightsPanel.collectAsState()
    val locationGroupedConnections by homeViewModel.locationGroupedConnections.collectAsState()
    val expandedLocations by homeViewModel.expandedLocations.collectAsState()
    val connectedUsers by homeViewModel.connectedUsers.collectAsState()
    val nudgeResult by homeViewModel.nudgeResult.collectAsState()
    val pollPairSuggestion by homeViewModel.pollPairSuggestion.collectAsState()
    val icebreakerSendCooldownSec by homeViewModel.icebreakerSendCooldownRemainingSec.collectAsState()
    val availabilityViewModel: AvailabilityViewModel =
        viewModel(key = "home-availability") { AvailabilityViewModel() }
    var showAvailabilityIntentSheet by remember { mutableStateOf(false) }
    var seedAvailabilityIntent by remember { mutableStateOf<AvailabilityIntentRow?>(null) }
    val homeAvailabilityIntents by availabilityViewModel.activeAvailabilityIntents.collectAsState()
    val homeAvailabilityOverlapMessages by homeViewModel.homeAvailabilityOverlapMessages.collectAsState()
    val archivedForHome by AppDataManager.archivedConnectionIds.collectAsState()
    val hiddenForHome by AppDataManager.hiddenConnectionIds.collectAsState()
    val connectionsForArchiveBanner by AppDataManager.connections.collectAsState()
    val prefetchedBeacons by AppDataManager.prefetchedMapBeacons.collectAsState()
    val prefetchedHubs by AppDataManager.prefetchedCommunityHubs.collectAsState()
    val mapBeacons by mapViewModel.mapBeacons.collectAsState()
    val mapCommunityHubs by mapViewModel.communityHubs.collectAsState()
    val cachedEventBookmarks by AppDataManager.cachedEventBookmarks.collectAsState()
    val discoveryPrefetchComplete by AppDataManager.discoveryMapPrefetchComplete.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val homeLayoutMode by AppDataManager.homeLayoutMode.collectAsState()
    var selectedSavedEventBeacon by remember { mutableStateOf<MapBeacon?>(null) }
    var selectedPileLocation by remember { mutableStateOf<String?>(null) }
    var archiveBannerNow by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }

    // Use the same auth-aware refresh path as Settings so intents aren't empty after fast boot.
    LaunchedEffect(currentUser?.id) {
        if (currentUser?.id != null) {
            availabilityViewModel.refreshActiveAvailabilityIntents()
            homeViewModel.refreshHomeAvailabilityIntents()
            homeViewModel.retrySavedEventBookmarksIfNeeded()
            // Nearby tiles used to appear only after Map tab opened — prefetch from Home too.
            AppDataManager.requestMapDiscoveryPrefetch()
            mapViewModel.warmDiscoveryFeed()
        }
    }
    LaunchedEffect(discoveryPrefetchComplete) {
        if (discoveryPrefetchComplete) {
            homeViewModel.retrySavedEventBookmarksIfNeeded()
            AppDataManager.requestMapDiscoveryPrefetch()
        }
    }
    LaunchedEffect(Unit) {
        homeViewModel.retrySavedEventBookmarksIfNeeded()
        AppDataManager.requestMapDiscoveryPrefetch()
        while (isActive) {
            delay(60_000)
            archiveBannerNow = Clock.System.now().toEpochMilliseconds()
        }
    }

    val archiveBannerNotice =
        remember(
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

    val displayedSavedBookmarks =
        remember(savedEventBookmarks, cachedEventBookmarks) {
            if (savedEventBookmarks.isNotEmpty()) savedEventBookmarks else cachedEventBookmarks
        }

    val exploreTiles =
        remember(prefetchedBeacons, prefetchedHubs, mapBeacons, mapCommunityHubs) {
            val mergedBeacons = mergeMapBeaconLists(prefetchedBeacons, mapBeacons)
            val hubDtos =
                buildList {
                    addAll(prefetchedHubs)
                    val seen = prefetchedHubs.map { it.hubId }.toSet()
                    mapCommunityHubs.forEach { pin ->
                        if (pin.hubId !in seen) {
                            add(
                                CommunityHubNearbyDto(
                                    hubId = pin.hubId,
                                    name = pin.name,
                                    latitude = pin.latitude,
                                    longitude = pin.longitude,
                                    radiusMeters = pin.radiusMeters,
                                    activeUserCount = pin.activeUserCount,
                                    distanceMeters = pin.reportedDistanceMeters ?: 0.0,
                                ),
                            )
                        }
                    }
                }
            buildHomeExploreTiles(mergedBeacons, hubDtos)
        }

    val featuredEvent = homeEventReminders.firstOrNull()
    val remainingEventReminders =
        remember(homeEventReminders, featuredEvent) {
            val featuredId = featuredEvent?.beaconId
            if (featuredId == null) {
                homeEventReminders
            } else {
                homeEventReminders.filterNot { it.beaconId == featuredId }
            }
        }

    val toastState = rememberUnifiedToastState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(nudgeResult) {
        val result = nudgeResult
        if (result != null) {
            toastState.show(scope, result)
            homeViewModel.clearNudgeResult()
        }
    }

    val engagementSnackbar by mapViewModel.engagementSnackbar.collectAsState()
    LaunchedEffect(engagementSnackbar) {
        engagementSnackbar?.let { msg ->
            toastState.show(scope, msg)
            mapViewModel.clearEngagementSnackbar()
        }
    }

    LaunchedEffect(showAvailabilityIntentSheet) {
        if (!showAvailabilityIntentSheet) {
            availabilityViewModel.refreshActiveAvailabilityIntents()
            homeViewModel.refreshHomeAvailabilityIntents()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = renderedHomeState) {
            is HomeState.Loading -> {
                AppShimmerScreen(
                    isDarkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                )
            }
            is HomeState.Error -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(ScreenPaddingHorizontal),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Error loading home data",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { homeViewModel.refresh() },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Text("Retry")
                    }
                }
            }
            is HomeState.Success -> {
                val firstName =
                    state.user.name
                        ?.trim()
                        ?.split(Regex("\\s+"))
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "there"
                AppScreenScaffold(
                    title = homeGreetingTitle(firstName),
                    subtitle = HomeGreetingSubtitle,
                    showFloatingHeader = true,
                    belowHeaderSpacing = HeaderToSearchGap,
                    collapseSearchIntoBar = true,
                    onOpenSearch = onOpenSearch,
                    verticalArrangement = Arrangement.spacedBy(CardSpacing),
                    nativeTrailingActions =
                        listOf(
                            NativeChromeAction(
                                sfSymbol =
                                    if (homeLayoutMode == HomeLayoutMode.PILE) {
                                        "list.bullet"
                                    } else {
                                        "square.stack"
                                    },
                                contentDescription =
                                    if (homeLayoutMode == HomeLayoutMode.PILE) {
                                        "Switch to list view"
                                    } else {
                                        "Switch to photo pile"
                                    },
                                onClick = {
                                    AppDataManager.setHomeLayoutMode(
                                        if (homeLayoutMode == HomeLayoutMode.PILE) {
                                            HomeLayoutMode.LINEAR
                                        } else {
                                            HomeLayoutMode.PILE
                                        },
                                    )
                                },
                            ),
                        ),
                    actions = {
                        HeaderChromeIconButton(
                            icon =
                                if (homeLayoutMode == HomeLayoutMode.PILE) {
                                    Icons.Filled.Menu
                                } else {
                                    Icons.Filled.Star
                                },
                            contentDescription =
                                if (homeLayoutMode == HomeLayoutMode.PILE) {
                                    "Switch to list view"
                                } else {
                                    "Switch to photo pile"
                                },
                            onClick = {
                                AppDataManager.setHomeLayoutMode(
                                    if (homeLayoutMode == HomeLayoutMode.PILE) {
                                        HomeLayoutMode.LINEAR
                                    } else {
                                        HomeLayoutMode.PILE
                                    },
                                )
                            },
                        )
                    },
                ) {
                    if (onOpenSearch != null) {
                        item(key = "home_search_pill") {
                            HomeSearchPill(onClick = onOpenSearch)
                        }
                    }
                    if (homeLayoutMode == HomeLayoutMode.PILE) {
                        item(key = "availability_intents_strip") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HomeAvailabilityIntentsRow(
                                    intents = homeAvailabilityIntents,
                                    onCreateIntent = {
                                        availabilityViewModel.resetAvailabilityIntentSheet()
                                        seedAvailabilityIntent = null
                                        showAvailabilityIntentSheet = true
                                    },
                                    onEditIntent = { row ->
                                        availabilityViewModel.beginEditAvailabilityIntent(row)
                                        seedAvailabilityIntent = row
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
                        homePhotoPileItems(
                            data =
                                HomePileBoardData(
                                    intents = homeAvailabilityIntents,
                                    featuredEvent = featuredEvent,
                                    recap = activityRecap,
                                    savedBookmarks = displayedSavedBookmarks,
                                    exploreTiles = exploreTiles,
                                    archiveNotice = archiveBannerNotice,
                                    pollPair = pollPairSuggestion,
                                    reconnectReminders = reconnectReminders,
                                    eventReminders = remainingEventReminders,
                                    locationGroups = locationGroupedConnections,
                                    insights =
                                        if (connectionInsights != null && state.stats.totalConnections > 0) {
                                            connectionInsights
                                        } else {
                                            null
                                        },
                                    stats = state.stats,
                                    connectedUsers = connectedUsers,
                                    currentUserId = state.user.id,
                                ),
                            actions =
                                HomePileActions(
                                    onCreateIntent = {
                                        availabilityViewModel.resetAvailabilityIntentSheet()
                                        seedAvailabilityIntent = null
                                        showAvailabilityIntentSheet = true
                                    },
                                    onEditIntent = { row ->
                                        availabilityViewModel.beginEditAvailabilityIntent(row)
                                        seedAvailabilityIntent = row
                                        showAvailabilityIntentSheet = true
                                    },
                                    onFeaturedMap = { reminder -> onNavigateToMap(reminder.beaconId) },
                                    onSavedEventClick = { bookmark ->
                                        selectedSavedEventBeacon =
                                            resolveSavedEventBeacon(
                                                bookmark = bookmark,
                                                mapBeacons = mapBeacons,
                                                prefetchedBeacons = prefetchedBeacons,
                                            )
                                    },
                                    onExploreClick = { tile -> onNavigateToMapLayer(tile.layerFilter) },
                                    onArchiveOpenChat = { notice -> onNavigateToChat(notice.connectionId) },
                                    onArchiveIcebreaker = { notice ->
                                        homeViewModel.sendArchiveBannerIcebreaker(notice)
                                    },
                                    onPollPairOpenChat = { suggestion ->
                                        onNavigateToChat(suggestion.connectionId)
                                    },
                                    onPollPairIcebreaker = { suggestion ->
                                        homeViewModel.sendPollPairIcebreaker(suggestion)
                                    },
                                    onReconnect = { reminder -> onNavigateToChat(reminder.connectionId) },
                                    onDismissReconnect = { reminder ->
                                        homeViewModel.dismissReminder(reminder.connectionId)
                                    },
                                    onEventReminderMap = { reminder -> onNavigateToMap(reminder.beaconId) },
                                    onLocationClick = { location ->
                                        selectedPileLocation = location
                                    },
                                ),
                        )
                        activityRecap?.let { recap ->
                            item(key = "activity_recap") {
                                ActivityRecapSection(
                                    recap = recap,
                                    window = recapWindow,
                                    onWindowChange = { homeViewModel.setRecapWindow(it) },
                                )
                            }
                        }
                        if (connectionInsights != null && state.stats.totalConnections > 0) {
                            item(key = "connection_insights") {
                                ConnectionInsightsCard(
                                    insights = connectionInsights!!,
                                    expanded = showInsightsPanel,
                                    onToggle = { homeViewModel.toggleInsightsPanel() },
                                )
                            }
                        }
                        item(key = "stats_header") {
                            SectionHeader(text = "Your Stats")
                        }
                        item(key = "stats_row") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HomeStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Check,
                                    value = state.stats.totalConnections.toString(),
                                    label = "Total Clicks",
                                )
                                HomeStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.LocationOn,
                                    value = state.stats.uniqueLocations.toString(),
                                    label = "Locations",
                                    iconTint = accentColor(AccentRole.Emphasis),
                                )
                            }
                        }
                    } else {
                        featuredEvent?.let { reminder ->
                            item(key = "featured_event") {
                                FeaturedEventSection(
                                    reminder = reminder,
                                    onViewMap = { onNavigateToMap(reminder.beaconId) },
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
                                    onCreateIntent = {
                                        availabilityViewModel.resetAvailabilityIntentSheet()
                                        seedAvailabilityIntent = null
                                        showAvailabilityIntentSheet = true
                                    },
                                    onEditIntent = { row ->
                                        availabilityViewModel.beginEditAvailabilityIntent(row)
                                        seedAvailabilityIntent = row
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

                        activityRecap?.let { recap ->
                            item(key = "activity_recap") {
                                ActivityRecapSection( // pragma: allowlist secret
                                    recap = recap,
                                    window = recapWindow,
                                    onWindowChange = { homeViewModel.setRecapWindow(it) },
                                    onMakeFirstClick = onNavigateToAddClick,
                                )
                            }
                        }

                        item(key = "saved_events") {
                            SavedEventsSection(
                                bookmarks = displayedSavedBookmarks,
                                onBookmarkClick = { bookmark ->
                                    selectedSavedEventBeacon =
                                        resolveSavedEventBeacon(
                                            bookmark = bookmark,
                                            mapBeacons = mapBeacons,
                                            prefetchedBeacons = prefetchedBeacons,
                                        )
                                },
                                onExploreMap = { onNavigateToMap(null) },
                            )
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
                                    onSendIcebreaker = { homeViewModel.sendArchiveBannerIcebreaker(notice) },
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
                                    onSendIcebreaker = { homeViewModel.sendPollPairIcebreaker(suggestion) },
                                    icebreakerSendEnabled = icebreakerSendCooldownSec <= 0,
                                    icebreakerCooldownSec = icebreakerSendCooldownSec,
                                )
                            }
                        }

                        if (reconnectReminders.isNotEmpty()) {
                            item(key = "reconnect_header") {
                                SectionHeader(
                                    text = "Reconnect",
                                    caption = "Connections you haven't talked to in a while",
                                )
                            }

                            items(
                                reconnectReminders,
                                key = { it.connectionId },
                                contentType = { "reconnect_reminder" },
                            ) { reminder ->
                                val peer = connectedUsers[reminder.userId]
                                ReconnectReminderCard(
                                    reminder = reminder,
                                    onReconnect = { onNavigateToChat(reminder.connectionId) },
                                    onDismiss = { homeViewModel.dismissReminder(reminder.connectionId) },
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
                                contentType = { "event_reminder" },
                            ) { reminder ->
                                HomeEventReminderCard(
                                    reminder = reminder,
                                    onDismiss = {
                                        homeViewModel.dismissEventReminder(reminder.beaconId, reminder.kind)
                                    },
                                    onViewMap = { onNavigateToMap(reminder.beaconId) },
                                )
                            }
                        }

                        if (locationGroupedConnections.isNotEmpty()) {
                            item(key = "recent_connections_header") {
                                SectionHeader(text = "Recent Connections")
                            }
                            items(
                                locationGroupedConnections.entries.toList(),
                                key = { it.key },
                                contentType = { "location_group" },
                            ) { (location, connections) ->
                                val isExpanded = location in expandedLocations
                                LocationGroupCard(
                                    location = location,
                                    connections = connections,
                                    isExpanded = isExpanded,
                                    connectedUsers = connectedUsers,
                                    currentUserId = state.user.id,
                                    onToggleExpand = { homeViewModel.toggleLocationExpanded(location) },
                                    onNavigateToChat = onNavigateToChat,
                                    onNudge = { connectionId, otherUserName ->
                                        homeViewModel.sendNudgeByConnectionId(connectionId, otherUserName)
                                    },
                                )
                            }
                        } else {
                            item(key = "empty_connections") {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Icon(
                                            Icons.Filled.TouchApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "No Connections Yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Start making connections by tapping Add Click",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    onToggle = { homeViewModel.toggleInsightsPanel() },
                                )
                            }
                        }

                        item(key = "stats_header") {
                            SectionHeader(text = "Your Stats")
                        }

                        item(key = "stats_row") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HomeStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Check,
                                    value = state.stats.totalConnections.toString(),
                                    label = "Total Clicks",
                                )
                                HomeStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.LocationOn,
                                    value = state.stats.uniqueLocations.toString(),
                                    label = "Locations",
                                    iconTint = accentColor(AccentRole.Emphasis),
                                )
                            }
                        }
                    }
                }
            }
        }

        val pileLocation = selectedPileLocation
        if (pileLocation != null) {
            val successUserId =
                (homeState as? HomeState.Success)?.user?.id
                    ?: lastSuccessfulHomeState?.user?.id
                    ?: ""
            val pileConnections = locationGroupedConnections[pileLocation].orEmpty()
            MapBeaconSheetRoot(
                visible = true,
                onDismissRequest = { selectedPileLocation = null },
                containerColor = GlassSheetTokens.OledBlack(),
                contentColor = GlassSheetTokens.OnOled(),
                scrimColor = Color.Black.copy(alpha = ClickSheetDefaults.ScrimAlpha),
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
                appColorScheme = MaterialTheme.colorScheme,
                appTypography = MaterialTheme.typography,
            ) {
                ClickSheetDialogChrome(
                    modifier = Modifier.fillMaxWidth(),
                    sheetColor = GlassSheetTokens.OledBlack(),
                    onSurface = GlassSheetTokens.OnOled(),
                    alignSemanticColorsToSheet = true,
                ) {
                    LocationGroupCard(
                        location = pileLocation,
                        connections = pileConnections,
                        isExpanded = true,
                        connectedUsers = connectedUsers,
                        currentUserId = successUserId,
                        onToggleExpand = { selectedPileLocation = null },
                        onNavigateToChat = { connectionId ->
                            selectedPileLocation = null
                            onNavigateToChat(connectionId)
                        },
                        onNudge = { connectionId, otherUserName ->
                            homeViewModel.sendNudgeByConnectionId(connectionId, otherUserName)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .sheetBodyScroll()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }

        if (showAvailabilityIntentSheet) {
            AvailabilitySheet(
                viewModel = availabilityViewModel,
                seedIntent = seedAvailabilityIntent,
                onDismiss = {
                    showAvailabilityIntentSheet = false
                    seedAvailabilityIntent = null
                    availabilityViewModel.resetAvailabilityIntentSheet()
                },
            )
        }

        val detailBeaconSeed = selectedSavedEventBeacon
        SavedEventDetailSheet(
            beacon = detailBeaconSeed,
            mapViewModel = mapViewModel,
            currentUserId = currentUser?.id,
            onDismiss = { selectedSavedEventBeacon = null },
            onShareBeaconToChats = onShareBeaconToChats,
        )

        UnifiedToastHost(
            state = toastState,
            opaque = true,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = rememberBottomChromePadding() + 8.dp),
        )
    }
}
