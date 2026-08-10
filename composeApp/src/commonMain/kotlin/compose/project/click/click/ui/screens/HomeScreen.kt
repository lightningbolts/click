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
import androidx.compose.ui.zIndex
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
import compose.project.click.click.data.api.CommunityHubNearbyDto
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionInsights // pragma: allowlist secret
import compose.project.click.click.data.models.ReconnectReminder // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.mostUrgentArchiveNotice // pragma: allowlist secret
import compose.project.click.click.ui.components.BeaconShareToChatDialog
import compose.project.click.click.ui.components.ConnectionArchiveWarningBanner // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDialogChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.components.ExploreNearbyBeaconsSection // pragma: allowlist secret
import compose.project.click.click.ui.components.FeaturedEventSection // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.HomeExploreTile // pragma: allowlist secret
import compose.project.click.click.ui.components.SavedEventsSection // pragma: allowlist secret
import compose.project.click.click.ui.components.homeGreetingTitle // pragma: allowlist secret
import compose.project.click.click.ui.components.HomeGreetingSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.components.HomeSearchPill // pragma: allowlist secret
import compose.project.click.click.ui.components.toHomeExploreTile // pragma: allowlist secret
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot // pragma: allowlist secret
import compose.project.click.click.events.isActiveForDiscoveryFeed // pragma: allowlist secret
import compose.project.click.click.events.EventReminderCoordinator
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import compose.project.click.click.data.models.withPreservedEventScheduleFrom
import compose.project.click.click.ui.utils.haversineDistance
import compose.project.click.click.ui.utils.hasUsableMapCoordinates
import compose.project.click.click.ui.utils.mergeMapBeaconLists
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.foundation.verticalScroll
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
    homeViewModel: HomeViewModel = viewModel { HomeViewModel() },
    mapViewModel: MapViewModel,
    onNavigateToChat: (String) -> Unit = {},
    onOpenSearch: (() -> Unit)? = null,
    onNavigateToMap: (beaconId: String?) -> Unit = {},
    onNavigateToMapLayer: (MapLayerFilter) -> Unit = {},
    onShareBeaconToChats: ((
        MapBeacon,
        List<String>,
        String?,
    ) -> Unit)? = null,
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
    val connectionInsights by homeViewModel.connectionInsights.collectAsState()
    val showInsightsPanel by homeViewModel.showInsightsPanel.collectAsState()
    val locationGroupedConnections by homeViewModel.locationGroupedConnections.collectAsState()
    val expandedLocations by homeViewModel.expandedLocations.collectAsState()
    val connectedUsers by homeViewModel.connectedUsers.collectAsState()
    val nudgeResult by homeViewModel.nudgeResult.collectAsState()
    val pollPairSuggestion by homeViewModel.pollPairSuggestion.collectAsState()
    val icebreakerSendCooldownSec by homeViewModel.icebreakerSendCooldownRemainingSec.collectAsState()
    val availabilityViewModel: AvailabilityViewModel = viewModel { AvailabilityViewModel() }
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
    var selectedSavedEventBeacon by remember { mutableStateOf<MapBeacon?>(null) }
    var shareSavedBeaconToChat by remember { mutableStateOf<MapBeacon?>(null) }
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

    val displayedSavedBookmarks = remember(savedEventBookmarks, cachedEventBookmarks) {
        if (savedEventBookmarks.isNotEmpty()) savedEventBookmarks else cachedEventBookmarks
    }

    val exploreTiles = remember(prefetchedBeacons, prefetchedHubs, mapBeacons, mapCommunityHubs) {
        val mergedBeacons = mergeMapBeaconLists(prefetchedBeacons, mapBeacons)
        val hubDtos = buildList {
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
    val remainingEventReminders = remember(homeEventReminders, featuredEvent) {
        val featuredId = featuredEvent?.beaconId
        if (featuredId == null) homeEventReminders
        else homeEventReminders.filterNot { it.beaconId == featuredId }
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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = renderedHomeState) {
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
                        onClick = { homeViewModel.refresh() },
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

                        if (displayedSavedBookmarks.isNotEmpty()) {
                            item(key = "saved_events") {
                                SavedEventsSection(
                                    bookmarks = displayedSavedBookmarks,
                                    onBookmarkClick = { bookmark ->
                                        selectedSavedEventBeacon = resolveSavedEventBeacon(
                                            bookmark = bookmark,
                                            mapBeacons = mapBeacons,
                                            prefetchedBeacons = prefetchedBeacons,
                                        )
                                    },
                                )
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
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SectionHeader(text = "Reconnect")
                                    Text(
                                        "Connections you haven't talked to in a while",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                    onToggle = { homeViewModel.toggleInsightsPanel() }
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
                seedIntent = seedAvailabilityIntent,
                onDismiss = {
                    showAvailabilityIntentSheet = false
                    seedAvailabilityIntent = null
                    availabilityViewModel.resetAvailabilityIntentSheet()
                },
            )
        }

        val detailBeaconSeed = selectedSavedEventBeacon
        if (detailBeaconSeed != null) {
            // Keep sheet seed in sync with map/prefetch hydration so Host/Posted update live.
            val detailBeacon = remember(detailBeaconSeed, mapBeacons, prefetchedBeacons) {
                val live = mapBeacons.firstOrNull { it.id == detailBeaconSeed.id }
                    ?: prefetchedBeacons.firstOrNull { it.id == detailBeaconSeed.id }
                live?.withPreservedEventScheduleFrom(detailBeaconSeed) ?: detailBeaconSeed
            }
            LaunchedEffect(detailBeaconSeed.id) {
                mapViewModel.ensureEventBeaconDetail(detailBeaconSeed.id, seed = detailBeaconSeed)
            }
            val detailSurface = GlassSheetTokens.OledBlack()
            val onDetailSurface = GlassSheetTokens.OnOled()
            val distanceMeters = AppDataManager.lastKnownDeviceLocation.value?.let { (lat, lon) ->
                haversineDistance(lat, lon, detailBeacon.latitude, detailBeacon.longitude)
            }
            val isCreator = !currentUser?.id.isNullOrBlank() &&
                detailBeacon.createdByUserId == currentUser?.id
            val inboxChats by AppDataManager.inboxFeedChats.collectAsState()
            MapBeaconSheetRoot(
                visible = true,
                onDismissRequest = {
                    shareSavedBeaconToChat = null
                    selectedSavedEventBeacon = null
                },
                containerColor = detailSurface,
                contentColor = onDetailSurface,
                scrimColor = Color.Black.copy(alpha = ClickSheetDefaults.ScrimAlpha),
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
                appColorScheme = MaterialTheme.colorScheme,
                appTypography = MaterialTheme.typography,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ClickSheetDialogChrome(
                        modifier = Modifier.fillMaxWidth(),
                        sheetColor = detailSurface,
                        onSurface = onDetailSurface,
                        alignSemanticColorsToSheet = true,
                    ) {
                        EventBeaconDetail(
                            beacon = detailBeacon,
                            distanceMeters = distanceMeters,
                            viewModel = mapViewModel,
                            isCreator = isCreator,
                            onEdit = { selectedSavedEventBeacon = null },
                            onDelete = { selectedSavedEventBeacon = null },
                            onShareToChat = {
                                shareSavedBeaconToChat = detailBeacon
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .sheetBodyScroll()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                    UnifiedToastHost(
                        state = toastState,
                        opaque = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp)
                            .zIndex(100f),
                    )
                }
                shareSavedBeaconToChat?.let { beaconToShare ->
                    BeaconShareToChatDialog(
                        beacon = beaconToShare,
                        chats = inboxChats,
                        onDismissRequest = { shareSavedBeaconToChat = null },
                        onShare = { selectedChatIds, openChatConnectionId ->
                            onShareBeaconToChats?.invoke(
                                beaconToShare,
                                selectedChatIds,
                                openChatConnectionId,
                            )
                            shareSavedBeaconToChat = null
                        },
                    )
                }
            }
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
 * Resolve a bookmarked event into a [MapBeacon] for the Home detail sheet (no Map tab jump).
 * Bookmark `event_start_at` / `event_end_at` win over any stale map cache so Start Time matches Home.
 * Bookmark lat/lng and venue labels fill gaps when the map cache is sparse/null-island.
 */
private fun resolveSavedEventBeacon(
    bookmark: EventBookmarkItemDto,
    mapBeacons: List<MapBeacon>,
    prefetchedBeacons: List<MapBeacon>,
): MapBeacon {
    val id = bookmark.beaconId.trim()
    val base = mapBeacons.firstOrNull { it.id == id }
        ?: EventReminderCoordinator.beaconById(id)
        ?: prefetchedBeacons.firstOrNull { it.id == id }
        ?: return bookmark.toSyntheticMapBeacon()
    return base.withBookmarkScheduleOverlay(bookmark)
}

private fun MapBeacon.withBookmarkScheduleOverlay(bookmark: EventBookmarkItemDto): MapBeacon {
    val startIso = bookmark.eventStartAt?.trim()?.takeIf { it.isNotEmpty() }
    val endIso = bookmark.eventEndAt?.trim()?.takeIf { it.isNotEmpty() }
    val locationName = bookmark.locationName?.trim()?.takeIf { it.isNotEmpty() }
    val formattedAddress = bookmark.formattedAddress?.trim()?.takeIf { it.isNotEmpty() }
    val categories = bookmark.eventCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val bookmarkLat = bookmark.latitude
    val bookmarkLon = bookmark.longitude
    val adoptBookmarkCoords =
        !hasUsableMapCoordinates() &&
            bookmarkLat != null &&
            bookmarkLon != null &&
            bookmarkLat.isFinite() &&
            bookmarkLon.isFinite() &&
            !(bookmarkLat == 0.0 && bookmarkLon == 0.0)

    val existing = metadata.raw
    val merged = buildJsonObject {
        existing?.forEach { (k, v) -> put(k, v) }
        startIso?.let { put("event_start_at", JsonPrimitive(it)) }
        endIso?.let { put("event_end_at", JsonPrimitive(it)) }
        bookmark.title?.trim()?.takeIf { it.isNotEmpty() }?.let { title ->
            if (existing?.get("title") == null) put("title", JsonPrimitive(title))
        }
        if (metadata.locationName.isNullOrBlank() && locationName != null) {
            put("location_name", JsonPrimitive(locationName))
        }
        if (metadata.formattedAddress.isNullOrBlank() && formattedAddress != null) {
            put("formatted_address", JsonPrimitive(formattedAddress))
        }
        if (metadata.eventCategories.isEmpty() && categories.isNotEmpty()) {
            put("event_categories", JsonArray(categories.map { JsonPrimitive(it) }))
        }
    }
    return copy(
        latitude = if (adoptBookmarkCoords) bookmarkLat!! else latitude,
        longitude = if (adoptBookmarkCoords) bookmarkLon!! else longitude,
        metadata = metadata.copy(
            title = metadata.title ?: bookmark.title?.trim()?.takeIf { it.isNotEmpty() },
            locationName = metadata.locationName?.takeIf { it.isNotBlank() } ?: locationName,
            formattedAddress = metadata.formattedAddress?.takeIf { it.isNotBlank() } ?: formattedAddress,
            eventCategories = metadata.eventCategories.ifEmpty { categories },
            raw = merged,
        ),
    )
}

private fun EventBookmarkItemDto.toSyntheticMapBeacon(): MapBeacon {
    val lat = latitude ?: 0.0
    val lon = longitude ?: 0.0
    val title = this.title?.takeIf { it.isNotBlank() }
    val locationName = this.locationName?.takeIf { it.isNotBlank() }
    val formattedAddress = this.formattedAddress?.takeIf { it.isNotBlank() }
    val categories = eventCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val raw = buildJsonObject {
        title?.let { put("title", JsonPrimitive(it)) }
        title?.let { put("description", JsonPrimitive(it)) }
        eventStartAt?.takeIf { it.isNotBlank() }?.let { put("event_start_at", JsonPrimitive(it)) }
        eventEndAt?.takeIf { it.isNotBlank() }?.let { put("event_end_at", JsonPrimitive(it)) }
        locationName?.let { put("location_name", JsonPrimitive(it)) }
        formattedAddress?.let { put("formatted_address", JsonPrimitive(it)) }
        if (categories.isNotEmpty()) {
            put("event_categories", JsonArray(categories.map { JsonPrimitive(it) }))
        }
    }
    val expiresMs = expiresAt
        ?.let { compose.project.click.click.data.models.parseEpochMs(it) }
    return MapBeacon(
        id = beaconId,
        kind = MapBeaconKind.EVENT,
        latitude = lat,
        longitude = lon,
        metadata = MapBeaconMetadata(
            title = title,
            description = title,
            locationName = locationName,
            formattedAddress = formattedAddress,
            eventCategories = categories,
            raw = raw,
        ),
        createdAtEpochMs = null,
        expiresAtEpochMs = expiresMs,
        sourceBeaconType = "event",
    )
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
    onCreateIntent: () -> Unit,
    onEditIntent: (AvailabilityIntentRow) -> Unit,
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
                    onClick = { onEditIntent(row) },
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
                onClick = onCreateIntent,
                label = {
                    Text(
                        if (intents.isEmpty()) "Set what you're down for" else "Add intent",
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
