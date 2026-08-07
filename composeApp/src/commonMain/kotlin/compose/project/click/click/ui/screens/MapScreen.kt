package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalUriHandler
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDialogChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.AnimatedClickDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackParallaxPeekRatio // pragma: allowlist secret
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveButton // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveCard // pragma: allowlist secret
import compose.project.click.click.ui.components.LiquidGlassPill // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickCircularGlassIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformMap // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapClusterPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPinKind // pragma: allowlist secret
import compose.project.click.click.ui.components.toClusterPin // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.EventPeopleDirectorySection
import compose.project.click.click.ui.components.EventPeopleDirectorySheetContent
import compose.project.click.click.ui.components.BeaconShareToChatDialog
import compose.project.click.click.ui.components.ClickFormBottomSheet
import compose.project.click.click.ui.components.EventDirectoryUserProfileSheet
import compose.project.click.click.ui.components.ProfileSheetBadge // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileSheetState // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.events.EventReminderCoordinator
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapState // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapSelection // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.withPreservedEventScheduleFrom
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.media.rememberChatAudioPlayer // pragma: allowlist secret
import compose.project.click.click.openBeaconOriginalMediaUrl // pragma: allowlist secret
import coil3.compose.AsyncImage // pragma: allowlist secret
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.zIndex
import compose.project.click.click.getPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import compose.project.click.click.ui.components.CreateHubModal
import compose.project.click.click.util.oneToOnePeerPairKey
import compose.project.click.click.utils.LocationService
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import compose.project.click.click.events.EventSchedule
import compose.project.click.click.events.buildEventShareText
import compose.project.click.click.events.buildEventShareUrl
import compose.project.click.click.events.eventCheckInCtaLabel
import compose.project.click.click.events.eventSchedule
import compose.project.click.click.events.formatEventEndDateLabel
import compose.project.click.click.events.formatEventEndTimeLabel
import compose.project.click.click.events.formatEventScheduleRange
import compose.project.click.click.events.formatEventStartDateLabel
import compose.project.click.click.events.formatEventPostedAtLabel
import compose.project.click.click.events.formatEventStartTimeLabel
import compose.project.click.click.events.isLive
import compose.project.click.click.events.openEventMapsRoute
import compose.project.click.click.platform.shareText
import compose.project.click.click.ui.utils.displayTypeTitle
import compose.project.click.click.ui.utils.displayDynamicTitle
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.PlatformBackHandler
import compose.project.click.click.ui.components.rememberFabAboveNavPadding
import compose.project.click.click.ui.components.rememberBottomChromePadding
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot
import compose.project.click.click.telemetry.TelemetryBatcher
import compose.project.click.click.ui.components.GlassmorphicOverlay
import compose.project.click.click.ui.components.UnifiedToastHost
import compose.project.click.click.ui.components.UnifiedToastTokens
import compose.project.click.click.ui.components.rememberUnifiedToastState
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.components.ClickOutlinedTextField

/**
 * Map screen — Phase 2 refactor (B1, C10, C11):
 *
 *  * Full-bleed [PlatformMap] that reaches every edge — no header, no rounded corners, no
 *    side padding. The map itself _is_ the screen.
 *  * [LiquidGlassPill] top-left overlay that surfaces the memories / live count in Material 3
 *    "Liquid Glass" styling. Replaces the old [PageHeader] + top-right stats chip.
 *  * GhostMode FAB is gone — the toggle now lives in Settings (per directive Q5). Ghost mode
 *    state itself still flows from the view model so tinting/snackbars remain correct.
 *  * The memories list was extracted into [MemoriesListSection] and is consumed from the
 *    connections-nav tab + profile sheet Timeline subtab instead of cluttering the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel { MapViewModel() },
    onNavigateToChat: ((String) -> Unit)? = null,
    /**
     * Called after the in-sheet share picker confirms destinations.
     * [openConnectionId] is the connection/chat id to navigate to when non-null.
     */
    onShareBeaconToChats: ((
        beacon: MapBeacon,
        chatIds: List<String>,
        openConnectionId: String?,
    ) -> Unit)? = null,
    /** When set, focuses the matching beacon pin once map beacons have loaded. */
    initialBeaconId: String? = null,
    onBeaconFocusConsumed: () -> Unit = {},
    /** Home explore: apply a single map layer preset once when navigating from Home. */
    initialLayerFilter: MapLayerFilter? = null,
    onLayerFilterConsumed: () -> Unit = {},
    /** Proximity verify + hop into hub chat (matches Add Click hub join). */
    onJoinCommunityHub: (hubId: String) -> Unit = {},
    eventsSheetExpanded: Boolean = false,
    onEventsSheetExpandedChanged: (Boolean) -> Unit = {},
    onOpenSearch: (() -> Unit)? = null,
    onOpenDisposableRoll: ((String) -> Unit)? = null,
) {
    val mapState by viewModel.mapState.collectAsState()
    val mapBindingZoom by viewModel.mapBindingZoom.collectAsState()
    val renderData by viewModel.renderData.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val ghostModeEnabled by viewModel.ghostModeEnabled.collectAsState()
    val mapFabAboveNav = rememberFabAboveNavPadding()
    val cameraTarget by viewModel.cameraTarget.collectAsState()
    val layerFilters by viewModel.selectedLayerFilters.collectAsState()
    val beaconInsertError by viewModel.beaconInsertError.collectAsState()
    val beaconDropFailureToast by viewModel.beaconDropFailureToast.collectAsState()
    val beaconSubmitInFlight by viewModel.beaconSubmitInFlight.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val communityHubs by viewModel.discoveryFeedHubs.collectAsState()
    val mapBeacons by viewModel.discoveryFeedBeacons.collectAsState()
    val discoveryFeedPending by viewModel.discoveryFeedPending.collectAsState()
    val discoveryFeedLoading by viewModel.discoveryFeedLoading.collectAsState()
    val locationService = remember { LocationService() }
    val cachedDeviceLocation by AppDataManager.lastKnownDeviceLocation.collectAsState()
    var userLat by remember {
        mutableStateOf(AppDataManager.lastKnownDeviceLocation.value?.first)
    }
    var userLon by remember {
        mutableStateOf(AppDataManager.lastKnownDeviceLocation.value?.second)
    }

    val effectiveUserLat = userLat ?: cachedDeviceLocation?.first
    val effectiveUserLon = userLon ?: cachedDeviceLocation?.second

    val frictionUi by TelemetryBatcher.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onMapScreenEntered()
        TelemetryBatcher.beginMapSession()
        AppDataManager.lastKnownDeviceLocation.value?.let { (lat, lon) ->
            userLat = lat
            userLon = lon
        }
        val loc = locationService.getCurrentLocation()
        userLat = loc?.latitude ?: userLat
        userLon = loc?.longitude ?: userLon
        loc?.let { viewModel.updateMapDeviceLocation(it.latitude, it.longitude) }
        TelemetryBatcher.updateHexbinFromCoordinates(userLat, userLon)
    }

    DisposableEffect(Unit) {
        onDispose { TelemetryBatcher.endMapSession() }
    }

    LaunchedEffect(userLat, userLon) {
        val lat = userLat ?: return@LaunchedEffect
        val lon = userLon ?: return@LaunchedEffect
        viewModel.updateMapDeviceLocation(lat, lon)
        TelemetryBatcher.updateHexbinFromCoordinates(lat, lon)
    }

    LaunchedEffect(cachedDeviceLocation) {
        cachedDeviceLocation?.let { (lat, lon) ->
            if (userLat == null) userLat = lat
            if (userLon == null) userLon = lon
            viewModel.updateMapDeviceLocation(lat, lon)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            TelemetryBatcher.refreshUiClock()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000L)
            val loc = locationService.getCurrentLocation()
            if (loc != null) {
                userLat = loc.latitude
                userLon = loc.longitude
            }
        }
    }

    var eventsListTransitionMode by remember { mutableStateOf(EventsListTransitionMode.Tap) }
    val eventsSwipeDragPx = remember { mutableFloatStateOf(0f) }
    var eventsSwipeBehindLayers by remember { mutableStateOf(false) }
    // Keep events UI composed after first open so swipe-back does not remount the list.
    var eventsOverlayMounted by remember { mutableStateOf(false) }
    var eventsCloseJob by remember { mutableStateOf<Job?>(null) }
    val eventsScope = rememberCoroutineScope()
    // 0 = off-screen below, 1 = fully shown — restores slide-up open without remounting.
    val eventsVerticalReveal = remember { Animatable(0f) }

    fun finalizeEventsClose() {
        eventsSwipeDragPx.floatValue = 0f
        eventsSwipeBehindLayers = false
        eventsListTransitionMode = EventsListTransitionMode.Tap
        eventsCloseJob = null
    }

    fun closeEventsList(mode: EventsListTransitionMode) {
        eventsCloseJob?.cancel()
        eventsListTransitionMode = mode
        if (mode == EventsListTransitionMode.Tap) {
            onEventsSheetExpandedChanged(false)
            eventsCloseJob = eventsScope.launch {
                eventsVerticalReveal.animateTo(
                    0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
                if (!eventsSheetExpanded) finalizeEventsClose()
            }
        } else {
            onEventsSheetExpandedChanged(false)
            eventsCloseJob = eventsScope.launch {
                delay(64L)
                eventsVerticalReveal.snapTo(0f)
                if (!eventsSheetExpanded) finalizeEventsClose()
            }
        }
    }

    LaunchedEffect(eventsSheetExpanded) {
        if (eventsSheetExpanded) {
            eventsCloseJob?.cancel()
            eventsCloseJob = null
            eventsOverlayMounted = true
            eventsSwipeDragPx.floatValue = 0f
            eventsSwipeBehindLayers = false
            if (eventsVerticalReveal.value < 0.99f) {
                eventsVerticalReveal.snapTo(0f)
                eventsVerticalReveal.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            } else {
                eventsVerticalReveal.snapTo(1f)
            }
            viewModel.refreshDiscoveryFromMapInteraction()
        }
    }

    val rawMapBeacons by viewModel.mapBeacons.collectAsState()
    LaunchedEffect(initialBeaconId, rawMapBeacons) {
        val beaconId = initialBeaconId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val known = rawMapBeacons.any { it.id == beaconId } ||
            EventReminderCoordinator.beaconById(beaconId) != null
        if (!known) return@LaunchedEffect
        viewModel.focusBeaconOnMap(beaconId)
        onBeaconFocusConsumed()
    }

    LaunchedEffect(initialLayerFilter) {
        val filter = initialLayerFilter ?: return@LaunchedEffect
        viewModel.applyHomeLayerPreset(filter)
        onLayerFilterConsumed()
    }

    DisposableEffect(Unit) {
        onDispose { onEventsSheetExpandedChanged(false) }
    }

    PlatformBackHandler(enabled = eventsSheetExpanded) {
        closeEventsList(EventsListTransitionMode.Tap)
    }

    val toastState = rememberUnifiedToastState()
    val mapScope = rememberCoroutineScope()

    LaunchedEffect(ghostModeEnabled) {
        if (ghostModeEnabled) {
            toastState.show(mapScope, "You are off the grid")
        }
    }

    val nudgeResult by viewModel.nudgeResult.collectAsState()
    LaunchedEffect(nudgeResult) {
        nudgeResult?.let { msg ->
            toastState.show(mapScope, msg)
            viewModel.clearNudgeResult()
        }
    }

    LaunchedEffect(beaconDropFailureToast) {
        beaconDropFailureToast?.let { msg ->
            toastState.show(mapScope, msg, durationMs = UnifiedToastTokens.LongDurationMs)
            viewModel.clearBeaconDropFailureToast()
        }
    }

    val engagementSnackbar by viewModel.engagementSnackbar.collectAsState()
    LaunchedEffect(engagementSnackbar) {
        engagementSnackbar?.let { msg ->
            toastState.show(mapScope, msg)
            viewModel.clearEngagementSnackbar()
        }
    }

    LaunchedEffect(beaconInsertError) {
        val err = beaconInsertError?.trim().orEmpty()
        if (err.isNotEmpty()) {
            toastState.show(mapScope, err, durationMs = UnifiedToastTokens.LongDurationMs)
            viewModel.clearBeaconInsertError()
        }
    }

    // C12 directive: explicit state variable that drives the new ProfileBottomSheet.
    // Pin taps update this directly (in addition to the view-model selection state) so
    // sheet visibility is decoupled from any race in the selection StateFlow.
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var showBeaconDropSheet by remember { mutableStateOf(false) }
    var showCreateHubModal by remember { mutableStateOf(false) }
    var pendingHubName by remember { mutableStateOf("") }
    var pendingHubCategory by remember { mutableStateOf("general") }

    LaunchedEffect(showBeaconDropSheet) {
        if (showBeaconDropSheet) {
            viewModel.clearBeaconInsertError()
        }
    }
    val showBottomSheet = selectedProfileId != null && selection is MapSelection.ConnectionSelected
    val showBeaconDetailSheet = selection is MapSelection.BeaconSelected
    val showCommunityHubSheet = selection is MapSelection.HubSelected
    val showOverlappingPinsSheet = selection is MapSelection.OverlappingPinsSelected

    LaunchedEffect(selection) {
        val sel = selection
        selectedProfileId = if (sel is MapSelection.ConnectionSelected) {
            sel.point.connection.id
        } else {
            null
        }
    }

    // Parallax removed — the upward shift was perceived as a layout bug when sheets opened.

    // Match App.kt: content is full-bleed under the tab bar; bottom inset is applied only on controls.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        val grayscaleModifier = if (ghostModeEnabled) Modifier.alpha(0.7f) else Modifier

        AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(grayscaleModifier)
                .background(
                    if (ghostModeEnabled) Color.DarkGray.copy(alpha = 0.3f)
                    else GlassSheetTokens.OledBlack(),
                ),
        ) {
            when (val state = mapState) {
                is MapState.Loading -> LoadingState()
                is MapState.Error -> ErrorState(message = state.message, onRetry = { viewModel.refresh() })
                is MapState.Success -> {
                    val feedItems = remember(
                        communityHubs,
                        mapBeacons,
                        renderData,
                        effectiveUserLat,
                        effectiveUserLon,
                    ) {
                        buildDiscoveryFeedItems(
                            hubs = communityHubs,
                            beacons = mapBeacons,
                            renderData = renderData,
                            userLat = effectiveUserLat,
                            userLon = effectiveUserLon,
                        )
                    }
                    val eventNearbyCount = remember(feedItems) {
                        feedItems.count { it is DiscoveryFeedItem.Beacon || it is DiscoveryFeedItem.Hub }
                    }
                    val fabBottomPadding = mapFabAboveNav + EventsReopenChipClearance

                    // Map layer is isolated from eventsSheetExpanded so open/close cannot
                    // invalidate PlatformMap (gestures stay on; overlay eats touches).
                    Box(modifier = Modifier.fillMaxSize()) {
                        MapContent(
                            modifier = Modifier.fillMaxSize(),
                            renderData = renderData,
                            communityHubs = communityHubs,
                            zoom = cameraTarget?.zoom ?: mapBindingZoom,
                            ghostMode = ghostModeEnabled,
                            mapGesturesEnabled = true,
                            showCompass = true,
                            cameraTarget = cameraTarget,
                            userLat = effectiveUserLat,
                            userLon = effectiveUserLon,
                            currentUserId = currentUser?.id,
                            onPinTapped = rememberMapPinTapHandler(
                                onConnection = { pinId -> selectedProfileId = pinId },
                                onClearConnection = { selectedProfileId = null },
                                onPin = { viewModel.onMapPinTapped(it) },
                            ),
                            onClusterTapped = rememberMapClusterTapHandler {
                                viewModel.onClusterTappedFromMap(it)
                            },
                            onZoomChanged = rememberStableZoomHandler { viewModel.setZoomLevel(it) },
                            onVisibleBoundsChanged = rememberStableBoundsHandler { minLat, maxLat, minLon, maxLon ->
                                viewModel.updateVisibleBounds(minLat, maxLat, minLon, maxLon)
                            },
                            onCameraAnimationComplete = rememberStableUnitHandler {
                                viewModel.onCameraAnimationComplete()
                            },
                            onMapGesture = rememberStableUnitHandler {
                                TelemetryBatcher.recordMapPan()
                            },
                        )

                        MapAlwaysOnChrome(
                            dockBottomPadding = fabBottomPadding,
                            layerFilters = layerFilters,
                            onToggleLayerFilter = { viewModel.toggleLayerFilter(it) },
                            onDropBeacon = {
                                TelemetryBatcher.recordActionTaken()
                                showBeaconDropSheet = true
                            },
                            onZoomIn = { viewModel.zoomIn() },
                            onZoomOut = { viewModel.zoomOut() },
                            // Stay composed under the events overlay (covered, not alpha-hidden) so
                            // swipe-back reveals controls that never remounted.
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(10f)
                                .graphicsLayer {
                                    if (!eventsSwipeBehindLayers) {
                                        translationX = 0f
                                        return@graphicsLayer
                                    }
                                    val w = size.width.coerceAtLeast(1f)
                                    val o = eventsSwipeDragPx.floatValue.coerceIn(0f, w)
                                    val progress = (o / w).coerceIn(0f, 1f)
                                    translationX =
                                        -(size.width * InteractiveSwipeBackParallaxPeekRatio) * (1f - progress)
                                },
                        )

                        EventsReopenChip(
                            count = eventNearbyCount,
                            onClick = {
                                eventsCloseJob?.cancel()
                                eventsCloseJob = null
                                eventsSwipeDragPx.floatValue = 0f
                                eventsSwipeBehindLayers = false
                                eventsListTransitionMode = EventsListTransitionMode.Tap
                                eventsOverlayMounted = true
                                onEventsSheetExpandedChanged(true)
                            },
                            enabled = !eventsSheetExpanded,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(15f)
                                .graphicsLayer {
                                    if (!eventsSwipeBehindLayers) {
                                        translationX = 0f
                                        return@graphicsLayer
                                    }
                                    val w = size.width.coerceAtLeast(1f)
                                    val o = eventsSwipeDragPx.floatValue.coerceIn(0f, w)
                                    val progress = (o / w).coerceIn(0f, 1f)
                                    translationX =
                                        -(size.width * InteractiveSwipeBackParallaxPeekRatio) * (1f - progress)
                                }
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = mapFabAboveNav,
                                ),
                        )

                        GlassmorphicOverlay(
                            visible = frictionUi.showGrassNudge && !eventsSheetExpanded,
                            message = "Looking for the right vibe? Try dropping a 'Looking for Coffee' intent and let the map come to you. Put your phone in your pocket and we'll vibrate when a match is nearby.",
                            onDismiss = { TelemetryBatcher.dismissGrassNudge() },
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(20f),
                        )

                        // Persist after first open; vertical reveal restores slide-up without remounting.
                        if (eventsOverlayMounted) {
                            val eventsClosed =
                                !eventsSheetExpanded &&
                                    !eventsSwipeBehindLayers &&
                                    eventsVerticalReveal.value < 0.01f
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(40f),
                            ) {
                                val heightPx = constraints.maxHeight
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            // During interactive back, Y stays put — container owns X.
                                            val swiping = eventsSwipeBehindLayers ||
                                                eventsSwipeDragPx.floatValue > 0.5f
                                            translationY = if (swiping) {
                                                0f
                                            } else {
                                                (1f - eventsVerticalReveal.value) * heightPx
                                            }
                                            alpha = if (eventsClosed) 0f else 1f
                                        }
                                        .then(
                                            if (eventsClosed) {
                                                Modifier.offset { IntOffset(constraints.maxWidth, 0) }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                ) {
                                    InteractiveSwipeBackContainer(
                                        enabled = eventsSheetExpanded,
                                        opaquePreviousBackground = false,
                                        externalDragOffsetPx = eventsSwipeDragPx,
                                        onBehindLayersVisibleChanged = { eventsSwipeBehindLayers = it },
                                        onBack = {
                                            closeEventsList(EventsListTransitionMode.Gesture)
                                        },
                                        previousContent = {},
                                        currentContent = {
                                            EventsDiscoveryFullScreen(
                                                feedItems = feedItems,
                                                discoveryFeedPending = discoveryFeedPending,
                                                discoveryFeedRefreshing = discoveryFeedLoading,
                                                onRefreshDiscovery = { viewModel.refreshDiscoveryFeed() },
                                                layerFilters = layerFilters,
                                                onToggleLayerFilter = { viewModel.toggleLayerFilter(it) },
                                                viewModel = viewModel,
                                                onBack = {
                                                    closeEventsList(EventsListTransitionMode.Tap)
                                                },
                                                onBeaconClick = { beacon, distanceM ->
                                                    TelemetryBatcher.recordActionTaken()
                                                    viewModel.onBeaconPinTapped(
                                                        beacon.id,
                                                        seedDistanceMeters = distanceM,
                                                    )
                                                },
                                                interactiveBackSwipeOffsetPx = eventsSwipeDragPx,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
        }
    }

    if (showBeaconDropSheet) {
        val dropSheetColor = MaterialTheme.colorScheme.surface
        val onDropSheet = MaterialTheme.colorScheme.onSurface
        MapBeaconSheetRoot(
            visible = true,
            onDismissRequest = { showBeaconDropSheet = false },
            containerColor = dropSheetColor,
            contentColor = onDropSheet,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            appColorScheme = MaterialTheme.colorScheme,
            appTypography = MaterialTheme.typography,
            modifier = Modifier,
        ) {
            ClickSheetDialogChrome(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                sheetColor = dropSheetColor,
                onSurface = onDropSheet,
                alignSemanticColorsToSheet = true,
            ) {
                BeaconDropSheetContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    errorMessage = beaconInsertError,
                    onDismissError = { viewModel.clearBeaconInsertError() },
                    submitLocked = beaconSubmitInFlight,
                    onResolveCurrentLocation = {
                        if (!viewModel.hasLocationPermission()) {
                            null
                        } else {
                            val loc = viewModel.resolveDropLocationForUi()
                            if (loc == null) {
                                null
                            } else {
                                val reverse = compose.project.click.click.utils.GeocodingService.reverseGeocode(
                                    loc.latitude,
                                    loc.longitude,
                                )
                                reverse ?: run {
                                    // Never persist the literal "Current location" label.
                                    // Avoid String.format — not available on Kotlin/Native.
                                    val lat = (kotlin.math.round(loc.latitude * 100_000.0) / 100_000.0)
                                    val lon = (kotlin.math.round(loc.longitude * 100_000.0) / 100_000.0)
                                    val coords = "$lat, $lon"
                                    compose.project.click.click.utils.GeocodedPlace(
                                        latitude = loc.latitude,
                                        longitude = loc.longitude,
                                        displayName = coords,
                                        shortLabel = coords,
                                    )
                                }
                            }
                        }
                    },
                    onSubmit = { kind, title, description, soundtrackUrl, ttlMs, showCreatorName, visibilityAudience, eventSchedule, eventCategories, venueScale, eventLocation, onRejectedEarly ->
                        viewModel.submitBeaconDrop(
                            kind = kind,
                            title = title,
                            description = description,
                            soundtrackUrl = soundtrackUrl,
                            ttlMs = ttlMs,
                            showCreatorName = showCreatorName,
                            visibilityAudience = visibilityAudience,
                            eventSchedule = eventSchedule,
                            eventCategories = eventCategories,
                            venueScale = venueScale,
                            eventLocation = eventLocation,
                            onAcceptedLocally = { showBeaconDropSheet = false },
                            onRejectedEarly = onRejectedEarly,
                            onRemoteFinished = { },
                        )
                    },
                    onCreateHub = { name, hubCat ->
                        showBeaconDropSheet = false
                        showCreateHubModal = true
                        pendingHubName = name
                        pendingHubCategory = hubCat
                    },
                )
            }
        }
    }

    if (showOverlappingPinsSheet && selection is MapSelection.OverlappingPinsSelected) {
        val stack = selection as MapSelection.OverlappingPinsSelected
        val sheetBg = MaterialTheme.colorScheme.surface
        val onSheet = MaterialTheme.colorScheme.onSurface
        MapBeaconSheetRoot(
            visible = true,
            onDismissRequest = { viewModel.clearSelection() },
            containerColor = sheetBg,
            contentColor = onSheet,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            appColorScheme = MaterialTheme.colorScheme,
            appTypography = MaterialTheme.typography,
            modifier = Modifier,
        ) {
            ClickSheetDialogChrome(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                sheetColor = sheetBg,
                onSurface = onSheet,
                alignSemanticColorsToSheet = true,
            ) {
                OverlappingMapPinsChooser(
                    pins = stack.pins,
                    onChoose = { viewModel.onOverlappingPinChosen(it) },
                    onDismiss = { viewModel.clearSelection() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .sheetBodyScroll()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }

    if (showCommunityHubSheet && selection is MapSelection.HubSelected) {
        val hubSel = selection as MapSelection.HubSelected
        val hubSheetBg = MaterialTheme.colorScheme.surface
        val onHubSheet = MaterialTheme.colorScheme.onSurface
        MapBeaconSheetRoot(
            visible = true,
            onDismissRequest = { viewModel.clearSelection() },
            containerColor = hubSheetBg,
            contentColor = onHubSheet,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            appColorScheme = MaterialTheme.colorScheme,
            appTypography = MaterialTheme.typography,
            modifier = Modifier,
        ) {
            ClickSheetDialogChrome(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                sheetColor = hubSheetBg,
                onSurface = onHubSheet,
                alignSemanticColorsToSheet = true,
            ) {
                CommunityHubBottomSheet(
                    hub = hubSel.hub,
                    distanceMeters = hubSel.distanceMeters,
                    canJoinGeofence = hubSel.canJoinGeofence,
                    onJoin = {
                        onJoinCommunityHub(hubSel.hub.hubId)
                        viewModel.clearSelection()
                    },
                    onDismiss = { viewModel.clearSelection() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .sheetBodyScroll()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }

    if (showBeaconDetailSheet && selection is MapSelection.BeaconSelected) {
        val beaconSel = selection as MapSelection.BeaconSelected
        val detailSurface = MaterialTheme.colorScheme.surface
        val onDetailSurface = MaterialTheme.colorScheme.onSurface
        var shareBeaconToChat by remember(beaconSel.beacon.id) {
            mutableStateOf<MapBeacon?>(null)
        }
        val inboxChats by compose.project.click.click.data.AppDataManager.inboxFeedChats.collectAsState()
        MapBeaconSheetRoot(
            visible = true,
            onDismissRequest = {
                shareBeaconToChat = null
                viewModel.clearSelection()
            },
            containerColor = detailSurface,
            contentColor = onDetailSurface,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            appColorScheme = MaterialTheme.colorScheme,
            appTypography = MaterialTheme.typography,
            modifier = Modifier,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ClickSheetDialogChrome(
                    modifier = Modifier.fillMaxWidth(),
                    sheetColor = detailSurface,
                    onSurface = onDetailSurface,
                    alignSemanticColorsToSheet = true,
                ) {
                    BeaconDetailSheetContent(
                        beacon = beaconSel.beacon,
                        distanceMeters = beaconSel.distanceMeters,
                        currentUserId = currentUser?.id,
                        viewModel = viewModel,
                        onShareBeaconToChat = { beacon ->
                            shareBeaconToChat = beacon
                        },
                        onNavigateToChat = onNavigateToChat,
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
                // Hosted inside the sheet window so it stacks above the sheet on iOS/Android.
                shareBeaconToChat?.let { beaconToShare ->
                    BeaconShareToChatDialog(
                        beacon = beaconToShare,
                        chats = inboxChats,
                        onDismissRequest = { shareBeaconToChat = null },
                        onShare = { selectedChatIds, openChatConnectionId ->
                            onShareBeaconToChats?.invoke(
                                beaconToShare,
                                selectedChatIds,
                                openChatConnectionId,
                            )
                            shareBeaconToChat = null
                        },
                    )
                }
            }
        }
    }

    if (showBottomSheet && selection is MapSelection.ConnectionSelected) {
        val connectionSelection = selection as MapSelection.ConnectionSelected
        val viewerUserId = compose.project.click.click.data.AppDataManager
            .currentUser.collectAsState().value?.id
        val sheetData = remember(connectionSelection, viewerUserId) {
            buildProfileSheetState(connectionSelection, viewerUserId)
        }
        val profileSheetColor = MaterialTheme.colorScheme.surface
        val onProfileSheet = MaterialTheme.colorScheme.onSurface
            MapBeaconSheetRoot(
            visible = true,
            onDismissRequest = {
                selectedProfileId = null
                viewModel.clearSelection()
            },
            containerColor = profileSheetColor,
            contentColor = onProfileSheet,
            scrimColor = Color.Black.copy(alpha = ClickSheetDefaults.ScrimAlpha),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            appColorScheme = MaterialTheme.colorScheme,
            appTypography = MaterialTheme.typography,
            // LazyColumn tabs — Compose scroll + whole-sheet drag dismiss.
            useUiKitScrollHost = false,
        ) {
            ClickSheetDialogChrome(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                sheetColor = profileSheetColor,
                onSurface = onProfileSheet,
                alignSemanticColorsToSheet = true,
            ) {
                ProfileBottomSheet(
                    state = sheetData,
                    onMessage = {
                        selectedProfileId = null
                        viewModel.clearSelection()
                        onNavigateToChat?.invoke(connectionSelection.point.connection.id)
                    },
                    onNudge = {
                        viewModel.sendNudge(
                            connectionId = connectionSelection.point.connection.id,
                            otherUserName = connectionSelection.otherUser?.name ?: "Someone",
                        )
                        selectedProfileId = null
                        viewModel.clearSelection()
                    },
                    onOpenDisposableRoll = onOpenDisposableRoll?.let { open ->
                        {
                            val connectionId = connectionSelection.point.connection.id
                            selectedProfileId = null
                            viewModel.clearSelection()
                            open(connectionId)
                        }
                    },
                )
            }
        }
    }

    val mapLocationService = remember { LocationService() }
    CreateHubModal(
        visible = showCreateHubModal,
        onDismiss = { showCreateHubModal = false },
        onHubCreated = { hubId -> onJoinCommunityHub(hubId) },
        locationService = mapLocationService,
        initialName = pendingHubName,
        initialCategory = pendingHubCategory,
        onError = { msg ->
            toastState.show(mapScope, msg, durationMs = UnifiedToastTokens.LongDurationMs)
        },
    )

    // Above modal sheets so check-in / engagement feedback is visible on event detail.
    Box(modifier = Modifier.fillMaxSize()) {
        UnifiedToastHost(
            state = toastState,
            opaque = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = rememberBottomChromePadding() + 8.dp)
                .zIndex(100f),
        )
    }
}

/**
 * Shapes a [MapSelection.ConnectionSelected] into the data the shared
 * [ProfileBottomSheet] renders. Media / Links / Files tabs are seeded empty — C15
 * (not in this phase) plumbs the message-history query that populates them. The
 * Timeline tab always has at least one row: the connection event itself.
 */
private fun buildProfileSheetState(
    sel: MapSelection.ConnectionSelected,
    viewerUserId: String?,
): ProfileSheetState {
    val otherUser = sel.otherUser
    val point = sel.point
    val displayName = otherUser?.name?.takeIf { it.isNotBlank() }
        ?: "Connection"
    val status = when (point.timeState) {
        TimeState.LIVE -> ProfileSheetBadge("Live now", PrimaryBlue)
        TimeState.RECENT -> ProfileSheetBadge("Recent", LightBlue)
        TimeState.ARCHIVE -> ProfileSheetBadge("Memory", Color.Gray)
    }
    return ProfileSheetState(
        displayName = displayName,
        subtitle = otherUser?.email?.takeIf { it.isNotBlank() },
        avatarUrl = otherUser?.image,
        statusBadge = status,
        canNudge = point.connection.id.isNotBlank() && (
            point.connection.has_begun ||
                point.connection.normalizedConnectionStatus() in setOf("active", "kept", "pending") ||
                point.timeState == TimeState.LIVE ||
                point.timeState == TimeState.RECENT
            ),
        timeline = emptyList(),
        media = emptyList(),
        links = emptyList(),
        files = emptyList(),
        userId = otherUser?.id,
        email = otherUser?.email?.takeIf { it.isNotBlank() },
        viewerUserId = viewerUserId,
        // Drives the BFF-owned Media / Files hydration inside [ProfileBottomSheet]
        // via `ConnectionRepository.fetchConnectionTabs`.
        connectionId = point.connection.id,
    )
}

@Composable
private fun MapAlwaysOnChrome(
    dockBottomPadding: Dp,
    layerFilters: Set<MapLayerFilter>,
    onToggleLayerFilter: (MapLayerFilter) -> Unit,
    onDropBeacon: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalPlatformStyle.current
    val glassStrength = if (style.isIOS) 0.64f else 0.4f
    val topSafe = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(40f)
                .windowInsetsPadding(topSafe)
                .padding(top = 8.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapLayerFilterDropdown(
                selected = layerFilters,
                onToggle = onToggleLayerFilter,
                opensDownward = true,
                modifier = Modifier.widthIn(max = 132.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .zIndex(10f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, bottom = dockBottomPadding),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClickCircularGlassIconButton(
                icon = Icons.Filled.AddLocationAlt,
                contentDescription = "Drop beacon",
                onClick = onDropBeacon,
                glassStrength = glassStrength,
                size = 56.dp,
            )
            Spacer(modifier = Modifier.weight(1f))
            MapZoomGlassControls(
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                glassStrength = glassStrength,
            )
        }
    }
}

@Composable
private fun MapZoomGlassControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    glassStrength: Float,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClickCircularGlassIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Zoom in",
            onClick = onZoomIn,
            glassStrength = glassStrength,
            size = 48.dp,
        )
        ClickCircularGlassIconButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Zoom out",
            onClick = onZoomOut,
            glassStrength = glassStrength,
            size = 48.dp,
        )
    }
}

/** One-line label for the compact map layer control. */
private fun mapLayerFilterShortLabel(selected: Set<MapLayerFilter>): String {
    if (MapLayerFilter.ALL in selected) return "All"
    val withoutAll = selected - MapLayerFilter.ALL
    if (withoutAll.isEmpty()) return "—"
    if (withoutAll.size == 1) {
        return when (val f = withoutAll.first()) {
            MapLayerFilter.MY_CONNECTIONS -> "Conn"
            MapLayerFilter.SOUNDTRACKS -> "Audio"
            MapLayerFilter.ALERTS_UTILITIES -> "Alerts"
            MapLayerFilter.SOCIAL_VIBES -> "Social"
            MapLayerFilter.COMMUNITY_HUBS -> "Hubs"
            else -> f.label.take(6)
        }
    }
    return "${withoutAll.size} on"
}

/**
 * Native [DropdownMenu] from a liquid-glass style pill (iOS) / solid surface (Android).
 * Menu opens **upward** (negative offset) so it stays on-screen over the bottom bar, with a
 * fully opaque container for readable text.
 */
@Composable
private fun MapLayerFilterDropdown(
    selected: Set<MapLayerFilter>,
    onToggle: (MapLayerFilter) -> Unit,
    modifier: Modifier = Modifier,
    opensDownward: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    val style = LocalPlatformStyle.current
    val menuSurface = MaterialTheme.colorScheme.surface
    val onMenuSurface = MaterialTheme.colorScheme.onSurface
    val menuOutline = clickBorderColor()
    val itemCount = MapLayerFilter.entries.size
    val menuUpOffset = if (opensDownward) {
        8.dp
    } else {
        -(itemCount * 48 + 24).dp
    }
    val menuWidth = 240.dp
    val triggerWidth = 132.dp
    val glassStrength = if (style.isIOS) 0.64f else 0.4f

    val triggerShape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .widthIn(max = triggerWidth)
            .wrapContentWidth(Alignment.End),
    ) {
        LiquidGlassPill(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 48.dp)
                .clip(triggerShape)
                .clickable { expanded = true },
            cornerRadiusDp = 20,
            backgroundStrength = glassStrength,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = mapLayerFilterShortLabel(selected),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Same horizontal origin as the pill; full-opacity surface for legibility.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(menuWidth)
                .wrapContentWidth(Alignment.Start)
                .zIndex(20f),
            offset = DpOffset(0.dp, -menuUpOffset),
            shape = RoundedCornerShape(if (isIOS) 14.dp else 12.dp),
            containerColor = menuSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(2.dp, menuOutline),
        ) {
            MapLayerFilter.entries.forEach { filter ->
                val isSelected = when (filter) {
                    MapLayerFilter.ALL -> MapLayerFilter.ALL in selected
                    else -> filter in selected
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            filter.label,
                            color = onMenuSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onToggle(filter)
                        expanded = false
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = onMenuSurface,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun BeaconDetailSheetContent(
    beacon: MapBeacon,
    distanceMeters: Double?,
    currentUserId: String?,
    viewModel: MapViewModel,
    onShareBeaconToChat: ((MapBeacon) -> Unit)? = null,
    onNavigateToChat: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isCreator = !currentUserId.isNullOrBlank() && beacon.createdByUserId == currentUserId
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editDraft by remember(beacon.id) {
        mutableStateOf(beacon.metadata.description?.trim().orEmpty())
    }
    val openEdit: () -> Unit = {
        editDraft = beacon.metadata.description?.trim().orEmpty()
        showEditDialog = true
    }
    val openDelete: () -> Unit = { showDeleteConfirm = true }

    Column(modifier = modifier) {
        when (beacon.kind) {
            MapBeaconKind.SOUNDTRACK -> SoundtrackBeaconDetail(
                beacon = beacon,
                distanceMeters = distanceMeters,
                viewModel = viewModel,
                isCreator = isCreator,
                onEdit = openEdit,
                onDelete = openDelete,
                onShareToChat = onShareBeaconToChat?.let { cb -> { cb(beacon) } },
                modifier = Modifier.fillMaxWidth(),
            )
            MapBeaconKind.EVENT -> EventBeaconDetail(
                beacon = beacon,
                distanceMeters = distanceMeters,
                viewModel = viewModel,
                isCreator = isCreator,
                onEdit = openEdit,
                onDelete = openDelete,
                onShareToChat = onShareBeaconToChat?.let { cb -> { cb(beacon) } },
                onNavigateToChat = onNavigateToChat,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> {
                CommunityBeaconDetail(
                    beacon = beacon,
                    distanceMeters = distanceMeters,
                    viewModel = viewModel,
                    isCreator = isCreator,
                    onEdit = openEdit,
                    onDelete = openDelete,
                    onShareToChat = onShareBeaconToChat?.let { cb -> { cb(beacon) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Animated (scale + fade) confirmation, matching the create/join hub popup motion.
    AnimatedClickDialog(
        visible = showDeleteConfirm,
        onDismissRequest = { showDeleteConfirm = false },
        title = "Delete beacon?",
        confirmLabel = "Delete",
        onConfirm = {
            showDeleteConfirm = false
            viewModel.deleteOwnedBeacon(beacon.id)
        },
    ) {
        Text(
            text = "This removes the pin from the map for everyone nearby.",
            color = GlassSheetTokens.OnOledMuted(),
        )
    }

    AnimatedClickDialog(
        visible = showEditDialog,
        onDismissRequest = { showEditDialog = false },
        title = "Edit beacon",
        confirmLabel = "Save",
        onConfirm = {
            showEditDialog = false
            viewModel.updateOwnedBeaconDescription(beacon.id, editDraft)
        },
    ) {
        ClickOutlinedTextField(
            value = editDraft,
            onValueChange = { if (it.length <= 140) editDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") },
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GlassSheetTokens.OnOled(),
                unfocusedTextColor = GlassSheetTokens.OnOled(),
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = GlassSheetTokens.GlassBorder(),
                cursorColor = PrimaryBlue,
                focusedLabelColor = GlassSheetTokens.OnOledMuted(),
                unfocusedLabelColor = GlassSheetTokens.OnOledMuted(),
            ),
        )
    }
}

@Composable
private fun BeaconOwnerOverflowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val border = clickBorderColor()
    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        EventHeroIconButton(
            selected = menuExpanded,
            border = border,
            onClick = { menuExpanded = true },
            contentDescription = "More actions",
            icon = Icons.Filled.MoreVert,
        )
        BeaconOwnerDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
}

/** Functional Clarity overflow: opaque surface, 2dp hard border, zero elevation. */
@Composable
private fun BeaconOwnerDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val border = clickBorderColor()
    val menuSurface = MaterialTheme.colorScheme.surface
    val onMenu = MaterialTheme.colorScheme.onSurface
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 180.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = menuSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(2.dp, border),
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    "Edit",
                    color = onMenu,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            onClick = {
                onDismissRequest()
                onEdit()
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = onMenu,
                )
            },
            colors = MenuDefaults.itemColors(
                textColor = onMenu,
                leadingIconColor = onMenu,
            ),
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            thickness = 1.dp,
            color = border.copy(alpha = 0.45f),
        )
        DropdownMenuItem(
            text = {
                Text(
                    "Delete",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            onClick = {
                onDismissRequest()
                onDelete()
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            colors = MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.error,
                leadingIconColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EventBeaconDetail(
    beacon: MapBeacon,
    distanceMeters: Double?,
    viewModel: MapViewModel,
    isCreator: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareToChat: (() -> Unit)? = null,
    onNavigateToChat: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val rsvpCache by viewModel.beaconRsvpById.collectAsState()
    val rsvpLoadingIds by viewModel.beaconRsvpLoadingIds.collectAsState()
    val rsvpPendingIds by viewModel.beaconRsvpPendingIds.collectAsState()
    val entry = rsvpCache[beacon.id]
    val attendees = entry?.attendees.orEmpty()
    val rsvpCacheSignedUp = entry?.currentUserSignedUp == true
    val rsvpLoading = entry == null && beacon.id in rsvpLoadingIds
    val rsvpPending = beacon.id in rsvpPendingIds
    var rsvpError by remember(beacon.id) { mutableStateOf<String?>(null) }
    val engagementCache by viewModel.beaconEngagementById.collectAsState()
    val engagementPendingIds by viewModel.beaconEngagementPendingIds.collectAsState()
    val engagement = engagementCache[beacon.id]
    val bookmarked = engagement?.bookmarked == true
    val engagementCheckedIn = engagement?.checkedIn == true || engagement?.localEarlyCheckIn == true
    val checkInPending = beacon.id in engagementPendingIds
    val uriHandler = LocalUriHandler.current
    val currentUser by AppDataManager.currentUser.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val mapBeacons by viewModel.mapBeacons.collectAsState()
    val displayBeacon = remember(beacon, mapBeacons) {
        val fromMap = mapBeacons.firstOrNull { it.id == beacon.id }
        when {
            fromMap == null -> beacon
            else -> fromMap.withPreservedEventScheduleFrom(beacon)
                .let { merged ->
                    // Prefer whichever side still has schedule if merge left it null.
                    when {
                        merged.eventSchedule() != null -> merged
                        beacon.eventSchedule() != null ->
                            beacon.copy(
                                latitude = merged.latitude,
                                longitude = merged.longitude,
                            )
                        else -> merged
                    }
                }
        }
    }
    val schedule = displayBeacon.eventSchedule()
    val live = schedule?.isLive() == true
    val distanceLabel = distanceMeters?.let { formatBeaconDistance(it) }
    val scheduleRange = schedule?.let { formatEventScheduleRange(it) }
    val categories = displayBeacon.metadata.eventCategories
    val border = clickBorderColor()
    val cardSurface = clickCardSurface()
    val hostUserId = displayBeacon.createdByUserId?.takeIf { it.isNotBlank() }
    val hostUser = hostUserId?.let { id ->
        if (id == currentUser?.id) currentUser else connectedUsers[id]
    }
    val hostDisplayName = displayBeacon.creatorDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: hostUser?.name?.trim()?.takeIf { it.isNotEmpty() }
    val hostAvatarUrl = hostUser?.image?.trim()?.takeIf { it.isNotEmpty() }

    val directoryCache by viewModel.beaconDirectoryById.collectAsState()
    val directoryLoadingIds by viewModel.beaconDirectoryLoadingIds.collectAsState()
    val directoryEntry = directoryCache[beacon.id]
    val directoryAttendees = directoryEntry?.attendees.orEmpty()
    val directoryLoading = beacon.id in directoryLoadingIds
    // The enriched directory independently returns the viewer's RSVP/check-in state.
    // Prefer a positive answer from either cache so a stale engagement response cannot
    // hide Cancel RSVP / Check out controls.
    val currentUserSignedUp =
        rsvpCacheSignedUp || directoryEntry?.currentUserSignedUp == true
    val checkedIn =
        engagementCheckedIn || directoryEntry?.currentUserCheckedIn == true
    // Mutuals are server-authorized enrichment. Local/early check-in only changes the CTA;
    // it must not expose an old or unavailable mutual directory payload.
    val mutualsUnlocked = directoryEntry?.mutualsSectionUnlocked == true
    var showPeopleDirectory by remember(beacon.id) { mutableStateOf(false) }
    var directoryProfileUserId by remember(beacon.id) { mutableStateOf<String?>(null) }
    var pendingDirectoryProfileUserId by remember(beacon.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(displayBeacon.id) {
        viewModel.loadBeaconRsvp(displayBeacon.id, forceRefresh = true)
        viewModel.loadBeaconEngagement(displayBeacon.id, forceRefresh = true)
        viewModel.recordEventImpression(displayBeacon.id)
        // Always hydrate missing Posted / Host / creator / schedule — bookmark & proximity rows
        // often already have schedule, so the old schedule-only gate skipped host+posted forever.
        viewModel.ensureEventBeaconDetail(displayBeacon.id, seed = displayBeacon)
    }

    LaunchedEffect(displayBeacon.id, currentUserSignedUp, checkedIn) {
        viewModel.loadBeaconAttendeeDirectory(displayBeacon.id, forceRefresh = false)
    }

    LaunchedEffect(showPeopleDirectory, displayBeacon.id) {
        if (showPeopleDirectory) {
            // Prefer cache; only refresh if we never enriched this beacon.
            viewModel.loadBeaconAttendeeDirectory(
                displayBeacon.id,
                forceRefresh = directoryEntry == null,
            )
        }
    }
    // UIKit cannot present the profile sheet while the directory's dismiss animation is active.
    // Queue the presentation until the first page sheet has fully left the screen.
    LaunchedEffect(showPeopleDirectory, pendingDirectoryProfileUserId) {
        val pendingId = pendingDirectoryProfileUserId
        if (!showPeopleDirectory && pendingId != null) {
            delay(450)
            directoryProfileUserId = pendingId
            pendingDirectoryProfileUserId = null
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (live) {
                    EventLiveBadge()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = displayBeacon.displayDynamicTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                distanceLabel?.let { d ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = d,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            EventHeroActions(
                bookmarked = bookmarked,
                isCreator = isCreator ||
                    (!currentUser?.id.isNullOrBlank() && displayBeacon.createdByUserId == currentUser?.id),
                onShare = {
                    val shareUrl = buildEventShareUrl(displayBeacon.id)
                    viewModel.recordEventShare(displayBeacon.id, shareUrl = shareUrl)
                    shareText(
                        text = buildEventShareText(displayBeacon, scheduleRange, distanceLabel),
                        subject = displayBeacon.displayDynamicTitle(),
                    )
                },
                onShareToChat = onShareToChat,
                onToggleBookmark = { viewModel.toggleBeaconBookmark(displayBeacon.id) },
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }

        schedule?.let { EventScheduleBento(schedule = it, border = border, cardSurface = cardSurface) }

        if (categories.isNotEmpty()) {
            EventCategoryChips(categories = categories, border = border, cardSurface = cardSurface)
        }

        val rawLocationLabel = displayBeacon.metadata.formattedAddress?.trim()?.takeIf { it.isNotEmpty() }
            ?: displayBeacon.metadata.locationName?.trim()?.takeIf { it.isNotEmpty() }
        // Legacy drops stored the literal "Current location" — never show that to viewers.
        var resolvedLocationLabel by remember(displayBeacon.id, rawLocationLabel) {
            mutableStateOf(
                rawLocationLabel?.takeUnless { it.equals("Current location", ignoreCase = true) },
            )
        }
        LaunchedEffect(displayBeacon.id, rawLocationLabel, displayBeacon.latitude, displayBeacon.longitude) {
            if (resolvedLocationLabel != null) return@LaunchedEffect
            if (
                rawLocationLabel == null ||
                rawLocationLabel.equals("Current location", ignoreCase = true)
            ) {
                val reverse = withContext(Dispatchers.Default) {
                    compose.project.click.click.utils.GeocodingService.reverseGeocode(
                        displayBeacon.latitude,
                        displayBeacon.longitude,
                    )
                }
                resolvedLocationLabel = reverse?.shortLabel?.takeIf { it.isNotBlank() }
                    ?: reverse?.displayName?.takeIf { it.isNotBlank() }
            }
        }
        resolvedLocationLabel?.let { locationLabel ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, border, RoundedCornerShape(12.dp))
                    .background(cardSurface, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = locationLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (displayBeacon.showCreatorName && !hostDisplayName.isNullOrBlank()) {
            EventHostCard(
                displayName = hostDisplayName,
                userId = hostUserId ?: "host:$hostDisplayName",
                avatarUrl = hostAvatarUrl,
                border = border,
                cardSurface = cardSurface,
            )
        }

        Text(
            text = displayBeacon.metadata.description?.trim().orEmpty().ifBlank { "No description" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        displayBeacon.createdAtEpochMs?.let { createdMs ->
            Text(
                text = "Posted ${formatEventPostedAtLabel(createdMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EventPeopleDirectorySection(
            attendees = directoryAttendees.ifEmpty {
                attendees.map {
                    compose.project.click.click.events.DirectoryAttendee(
                        userId = it.userId,
                        name = it.name,
                        avatarUrl = it.avatarUrl,
                    )
                }
            },
            loading = directoryLoading || rsvpLoading,
            mutualsSectionUnlocked = mutualsUnlocked,
            directoryEnriched = directoryEntry != null,
            onOpenDirectory = { showPeopleDirectory = true },
        )

        if (showPeopleDirectory) {
            ClickFormBottomSheet(onDismissRequest = { showPeopleDirectory = false }) {
                EventPeopleDirectorySheetContent(
                    attendees = directoryAttendees.ifEmpty {
                        attendees.map {
                            compose.project.click.click.events.DirectoryAttendee(
                                userId = it.userId,
                                name = it.name,
                                avatarUrl = it.avatarUrl,
                            )
                        }
                    },
                    loading = directoryLoading,
                    mutualsSectionUnlocked = mutualsUnlocked,
                    directoryEnriched = directoryEntry != null,
                    onAttendeeClick = { attendee ->
                        pendingDirectoryProfileUserId = attendee.userId
                        showPeopleDirectory = false
                    },
                )
            }
        }

        directoryProfileUserId?.let { profileId ->
            val attendee = directoryAttendees.firstOrNull { it.userId == profileId }
                ?: attendees.map {
                    compose.project.click.click.events.DirectoryAttendee(
                        userId = it.userId,
                        name = it.name,
                        avatarUrl = it.avatarUrl,
                    )
                }.firstOrNull { it.userId == profileId }
            if (attendee != null) {
                val viewerId = currentUser?.id
                val canMessage = compose.project.click.click.events.allowsDirectoryConnectActions(
                    attendee.relationship,
                )
                EventDirectoryUserProfileSheet(
                    attendee = attendee,
                    viewerUserId = viewerId,
                    onDismiss = { directoryProfileUserId = null },
                    onMessage = if (canMessage) {
                        {
                            val conn = compose.project.click.click.data.AppDataManager.connections.value
                                .firstOrNull { c ->
                                    attendee.userId in c.user_ids &&
                                        (viewerId.isNullOrBlank() || viewerId in c.user_ids)
                                }
                            conn?.id?.let { onNavigateToChat?.invoke(it) }
                        }
                    } else {
                        null
                    },
                )
            }
        }

        val actionShape = RoundedCornerShape(12.dp)
        val checkInLabel = eventCheckInCtaLabel(checkedIn = checkedIn, pending = checkInPending)
        Button(
            onClick = {
                if (checkInPending) return@Button
                viewModel.toggleBeaconCheckIn(displayBeacon.id)
            },
            enabled = !checkInPending,
            modifier = Modifier.fillMaxWidth(),
            shape = actionShape,
            border = BorderStroke(2.dp, border),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (checkedIn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (checkedIn) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            ),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            if (checkInPending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = if (checkedIn) Icons.Filled.CheckCircle else Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(checkInLabel, fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = {
                openEventMapsRoute(
                    openUri = { uriHandler.openUri(it) },
                    latitude = displayBeacon.latitude,
                    longitude = displayBeacon.longitude,
                    label = displayBeacon.displayDynamicTitle(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = actionShape,
            border = BorderStroke(2.dp, border),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Directions,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Join Event Route", fontWeight = FontWeight.SemiBold)
        }

        if (currentUserSignedUp) {
            Button(
                onClick = {
                    if (rsvpPending) return@Button
                    rsvpError = null
                    viewModel.cancelRsvpToBeacon(displayBeacon.id) { ok ->
                        if (!ok) rsvpError = "Could not update RSVP. Please try again."
                    }
                },
                enabled = !rsvpPending,
                modifier = Modifier.fillMaxWidth(),
                shape = actionShape,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(
                    text = if (rsvpPending) "Updating…" else "Cancel RSVP",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            OutlinedButton(
                onClick = {
                    if (rsvpPending) return@OutlinedButton
                    rsvpError = null
                    viewModel.rsvpToBeacon(displayBeacon.id) { ok ->
                        if (!ok) rsvpError = "Could not update RSVP. Please try again."
                    }
                },
                enabled = !rsvpPending,
                modifier = Modifier.fillMaxWidth(),
                shape = actionShape,
                border = BorderStroke(2.dp, border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(
                    text = if (rsvpPending) "Updating…" else "RSVP / Sign Up",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (rsvpPending) {
            Text(
                text = "Saving in the background...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rsvpError?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EventLiveBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFDC2626))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun BeaconShareMenuButton(
    onShare: () -> Unit,
    onShareToChat: (() -> Unit)?,
    border: androidx.compose.ui.graphics.Color,
    contentDescription: String = "Share",
) {
    var shareMenuExpanded by remember { mutableStateOf(false) }
    Box {
        EventHeroIconButton(
            selected = shareMenuExpanded,
            border = border,
            onClick = {
                if (onShareToChat != null) {
                    shareMenuExpanded = true
                } else {
                    onShare()
                }
            },
            contentDescription = contentDescription,
            icon = Icons.Filled.Share,
        )
        DropdownMenu(
            expanded = shareMenuExpanded,
            onDismissRequest = { shareMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Share link") },
                onClick = {
                    shareMenuExpanded = false
                    onShare()
                },
            )
            if (onShareToChat != null) {
                DropdownMenuItem(
                    text = { Text("Share to chat") },
                    onClick = {
                        shareMenuExpanded = false
                        onShareToChat()
                    },
                )
            }
        }
    }
}

@Composable
private fun EventHeroActions(
    bookmarked: Boolean,
    isCreator: Boolean,
    onShare: () -> Unit,
    onShareToChat: (() -> Unit)? = null,
    onToggleBookmark: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val border = clickBorderColor()
    var menuExpanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BeaconShareMenuButton(
            onShare = onShare,
            onShareToChat = onShareToChat,
            border = border,
            contentDescription = "Share event",
        )
        EventHeroIconButton(
            selected = bookmarked,
            border = border,
            onClick = onToggleBookmark,
            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark event",
            icon = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
        )
        if (isCreator) {
            Box {
                EventHeroIconButton(
                    selected = menuExpanded,
                    border = border,
                    onClick = { menuExpanded = true },
                    contentDescription = "More actions",
                    icon = Icons.Filled.MoreVert,
                )
                BeaconOwnerDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun EventHeroIconButton(
    selected: Boolean,
    border: Color,
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .border(2.dp, border, CircleShape)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent,
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EventScheduleBento(
    schedule: EventSchedule,
    border: Color,
    cardSurface: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EventBentoCell(
            modifier = Modifier.weight(1f),
            label = "Start Time",
            date = formatEventStartDateLabel(schedule),
            time = formatEventStartTimeLabel(schedule),
            icon = Icons.Filled.Schedule,
            border = border,
            cardSurface = cardSurface,
        )
        EventBentoCell(
            modifier = Modifier.weight(1f),
            label = "End Time",
            date = formatEventEndDateLabel(schedule),
            time = formatEventEndTimeLabel(schedule),
            icon = Icons.Filled.EventBusy,
            border = border,
            cardSurface = cardSurface,
        )
    }
}

@Composable
private fun EventBentoCell(
    modifier: Modifier,
    label: String,
    date: String,
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    border: Color,
    cardSurface: Color,
) {
    Column(
        modifier = modifier
            .border(2.dp, border, RoundedCornerShape(12.dp))
            .background(cardSurface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = date,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = time,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCategoryChips(
    categories: List<String>,
    border: Color,
    cardSurface: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "CATEGORIES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .border(2.dp, border, RoundedCornerShape(999.dp))
                        .background(cardSurface, RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun EventHostCard(
    displayName: String,
    userId: String,
    avatarUrl: String?,
    border: Color,
    cardSurface: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, border, RoundedCornerShape(12.dp))
            .background(cardSurface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConnectionListUserAvatarFace(
            displayName = displayName,
            email = null,
            avatarUrl = avatarUrl,
            userId = userId,
            modifier = Modifier
                .size(56.dp)
                .border(2.dp, border, CircleShape)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Host",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EventAttendeeStack(
    attendees: List<compose.project.click.click.data.api.BeaconAttendeeDto>,
    loading: Boolean,
    border: Color,
    cardSurface: Color,
) {
    val visible = attendees.take(4)
    val overflow = (attendees.size - visible.size).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "ACTIVE CLICKS (${attendees.size})",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            loading -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            attendees.isEmpty() -> Text(
                text = "Be the first to RSVP.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    visible.forEachIndexed { index, attendee ->
                        ConnectionListUserAvatarFace(
                            displayName = attendee.name,
                            email = null,
                            avatarUrl = attendee.avatarUrl,
                            userId = attendee.userId,
                            modifier = Modifier
                                .offset(x = (-10 * index).dp)
                                .zIndex((visible.size - index).toFloat())
                                .size(48.dp)
                                .border(2.dp, border, CircleShape)
                                .clip(CircleShape)
                                .background(cardSurface),
                        )
                    }
                    if (overflow > 0) {
                        Box(
                            modifier = Modifier
                                .offset(x = (-10 * visible.size).dp)
                                .zIndex(0f)
                                .size(48.dp)
                                .border(2.dp, border, CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+$overflow",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommunityBeaconDetail(
    beacon: MapBeacon,
    distanceMeters: Double?,
    viewModel: MapViewModel,
    isCreator: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareToChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val border = clickBorderColor()
    val cardSurface = clickCardSurface()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val kindLabel = when (beacon.kind) {
        MapBeaconKind.HAZARD -> "Hazard"
        MapBeaconKind.SOS -> "SOS"
        MapBeaconKind.UTILITY -> "Utility"
        MapBeaconKind.STUDY -> "Study"
        MapBeaconKind.SOCIAL_VIBE -> "Social"
        MapBeaconKind.OTHER -> "Beacon"
        else -> "Beacon"
    }
    val kindIcon = when (beacon.kind) {
        MapBeaconKind.HAZARD -> Icons.Filled.Warning
        MapBeaconKind.SOS -> Icons.Filled.NotificationsActive
        MapBeaconKind.UTILITY -> Icons.Filled.Build
        MapBeaconKind.STUDY -> Icons.Filled.MenuBook
        MapBeaconKind.SOCIAL_VIBE -> Icons.Filled.Groups
        else -> Icons.Filled.Place
    }
    val hostUserId = beacon.createdByUserId?.takeIf { it.isNotBlank() }
    val hostUser = hostUserId?.let { id ->
        if (id == currentUser?.id) currentUser else connectedUsers[id]
    }
    val hostDisplayName = beacon.creatorDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: hostUser?.name?.trim()?.takeIf { it.isNotEmpty() }
    val hostAvatarUrl = hostUser?.image?.trim()?.takeIf { it.isNotEmpty() }
    val distanceLabel = distanceMeters?.let { formatBeaconDistance(it) }
    val createdLabel = formatBeaconInstant(beacon.createdAtEpochMs)
    val expiresLabel = formatBeaconInstant(beacon.expiresAtEpochMs)
    val createdParts = createdLabel.split(" · ").let { parts ->
        if (parts.size >= 2) parts[0] to parts.drop(1).joinToString(" · ")
        else createdLabel to ""
    }
    val expiresParts = expiresLabel.split(" · ").let { parts ->
        if (parts.size >= 2) parts[0] to parts.drop(1).joinToString(" · ")
        else expiresLabel to ""
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = kindIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = kindLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = beacon.displayDynamicTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                distanceLabel?.let { d ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = d,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BeaconShareMenuButton(
                    onShare = {
                        val shareUrl = buildEventShareUrl(beacon.id)
                        viewModel.recordEventShare(beacon.id, shareUrl = shareUrl)
                        shareText(
                            text = buildEventShareText(beacon, scheduleLabel = null, distanceLabel = distanceLabel),
                            subject = beacon.displayDynamicTitle(),
                        )
                    },
                    onShareToChat = onShareToChat,
                    border = border,
                    contentDescription = "Share beacon",
                )
                if (isCreator ||
                    (!currentUser?.id.isNullOrBlank() && beacon.createdByUserId == currentUser?.id)
                ) {
                    BeaconOwnerOverflowMenu(
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EventBentoCell(
                modifier = Modifier.weight(1f),
                label = "Posted",
                date = createdParts.first,
                time = createdParts.second.ifBlank { "—" },
                icon = Icons.Filled.Schedule,
                border = border,
                cardSurface = cardSurface,
            )
            EventBentoCell(
                modifier = Modifier.weight(1f),
                label = "Expires",
                date = expiresParts.first,
                time = expiresParts.second.ifBlank { "—" },
                icon = Icons.Filled.EventBusy,
                border = border,
                cardSurface = cardSurface,
            )
        }

        if (beacon.showCreatorName && !hostDisplayName.isNullOrBlank()) {
            EventHostCard(
                displayName = hostDisplayName,
                userId = hostUserId ?: "host:$hostDisplayName",
                avatarUrl = hostAvatarUrl,
                border = border,
                cardSurface = cardSurface,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, border, RoundedCornerShape(12.dp))
                .background(cardSurface, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = beacon.metadata.description?.trim().orEmpty().ifBlank { "No description" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun SoundtrackBeaconDetail(
    beacon: MapBeacon,
    distanceMeters: Double?,
    viewModel: MapViewModel,
    isCreator: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareToChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mapBeacons by viewModel.mapBeacons.collectAsState()
    val displayBeacon = remember(beacon, mapBeacons) {
        mapBeacons.firstOrNull { it.id == beacon.id } ?: beacon
    }
    val trackTitle = displayBeacon.metadata.trackName?.takeIf { it.isNotBlank() }
        ?: displayBeacon.metadata.title?.takeIf { it.isNotBlank() }
        ?: displayBeacon.displayDynamicTitle()
    val artistLine = displayBeacon.metadata.artistName?.takeIf { it.isNotBlank() }
        ?: displayBeacon.metadata.artist?.takeIf { it.isNotBlank() }
    val art = displayBeacon.metadata.albumArtUrl?.takeIf { it.isNotBlank() }
    val preview = displayBeacon.metadata.previewUrl?.takeIf { it.isNotBlank() }
    val original = (displayBeacon.metadata.originalUrl ?: displayBeacon.metadata.musicUrl)
        ?.takeIf { it.isNotBlank() }
    val distanceLabel = distanceMeters?.let { formatBeaconDistance(it) }
    val border = clickBorderColor()
    val cardSurface = clickCardSurface()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val hostUserId = displayBeacon.createdByUserId?.takeIf { it.isNotBlank() }
    val hostUser = hostUserId?.let { id ->
        if (id == currentUser?.id) currentUser else connectedUsers[id]
    }
    val hostDisplayName = displayBeacon.creatorDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: hostUser?.name?.trim()?.takeIf { it.isNotEmpty() }
    val hostAvatarUrl = hostUser?.image?.trim()?.takeIf { it.isNotEmpty() }

    LaunchedEffect(displayBeacon.id) {
        viewModel.ensureSoundtrackBeaconDetail(displayBeacon.id, seed = displayBeacon)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!artistLine.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artistLine,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                distanceLabel?.let { d ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = d,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BeaconShareMenuButton(
                    onShare = {
                        val shareUrl = buildEventShareUrl(displayBeacon.id)
                        viewModel.recordEventShare(displayBeacon.id, shareUrl = shareUrl)
                        shareText(
                            text = buildEventShareText(displayBeacon, scheduleLabel = null, distanceLabel = distanceLabel),
                            subject = trackTitle,
                        )
                    },
                    onShareToChat = onShareToChat,
                    border = border,
                    contentDescription = "Share soundtrack",
                )
                if (isCreator ||
                    (!currentUser?.id.isNullOrBlank() && displayBeacon.createdByUserId == currentUser?.id)
                ) {
                    BeaconOwnerOverflowMenu(
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardSurface)
                .border(2.dp, border, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, border, RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(2.dp, border, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Soundtrack",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!artistLine.isNullOrBlank()) {
                        Text(
                            text = artistLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (preview != null) {
            val player = rememberChatAudioPlayer(mediaUrl = preview, durationHintMs = 30_000L)
            var tick by remember(preview) { mutableIntStateOf(0) }
            var isDragging by remember(preview) { mutableStateOf(false) }
            var sliderPosition by remember(preview) { mutableFloatStateOf(0f) }
            var wasPlayingBeforeDrag by remember(preview) { mutableStateOf(false) }
            LaunchedEffect(player.isPlaying) {
                while (player.isPlaying) {
                    delay(220)
                    tick++
                }
            }
            val pos = player.positionMs
            val dur = player.durationMs.takeIf { it > 0 } ?: 30_000L
            val progressed = remember(tick, pos, dur, isDragging) {
                (pos.toFloat() / dur.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            val sliderValue = if (isDragging) sliderPosition else progressed

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardSurface)
                    .border(2.dp, border, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledIconButton(
                        onClick = { player.togglePlayPause() },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (player.isPlaying) "Pause preview" else "Play preview",
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = sliderValue,
                            onValueChange = { newVal ->
                                if (!isDragging) {
                                    wasPlayingBeforeDrag = player.isPlaying
                                    if (player.isPlaying) {
                                        player.togglePlayPause()
                                    }
                                    isDragging = true
                                }
                                sliderPosition = newVal.coerceIn(0f, 1f)
                                tick++
                            },
                            onValueChangeFinished = {
                                val seekMs = (sliderPosition.coerceIn(0f, 1f) * dur.toFloat()).toLong()
                                player.seekTo(seekMs)
                                if (wasPlayingBeforeDrag) {
                                    player.togglePlayPause()
                                }
                                isDragging = false
                                tick++
                            },
                            modifier = Modifier.fillMaxWidth(),
                            valueRange = 0f..1f,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatBeaconPreviewClock(
                                    if (isDragging) (sliderPosition * dur).toLong() else pos,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatBeaconPreviewClock(dur),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            var waitedForPreview by remember(displayBeacon.id) { mutableStateOf(false) }
            LaunchedEffect(displayBeacon.id, preview) {
                if (preview == null) {
                    delay(2_500)
                    waitedForPreview = true
                } else {
                    waitedForPreview = false
                }
            }
            Text(
                text = if (!waitedForPreview) {
                    "Loading preview…"
                } else {
                    "No audio preview available for this track."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (original != null) {
            Button(
                onClick = { openBeaconOriginalMediaUrl(original) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open in music app")
            }
        }

        if (displayBeacon.showCreatorName && !hostDisplayName.isNullOrBlank()) {
            EventHostCard(
                displayName = hostDisplayName,
                userId = hostUserId ?: "host:$hostDisplayName",
                avatarUrl = hostAvatarUrl,
                border = border,
                cardSurface = cardSurface,
            )
        }

        displayBeacon.metadata.description?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Created · ${formatBeaconInstant(displayBeacon.createdAtEpochMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Expires · ${formatBeaconInstant(displayBeacon.expiresAtEpochMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatBeaconInstant(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "Unknown"
    return runCatching {
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
        val hour24 = dt.hour
        val h12 = ((hour24 + 11) % 12) + 1
        val amPm = if (hour24 < 12) "AM" else "PM"
        val mon = dt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        "$mon ${dt.dayOfMonth}, ${dt.year} · $h12:${dt.minute.toString().padStart(2, '0')} $amPm"
    }.getOrElse { "Unknown" }
}

private fun formatBeaconPreviewClock(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun formatBeaconDistance(meters: Double): String {
    if (!meters.isFinite() || meters < 0) return ""
    return if (meters < 1000) {
        "${meters.toInt()} m away"
    } else {
        val km = meters / 1000.0
        val tenths = ((km * 10.0) + 0.5).toInt().coerceAtLeast(1)
        val whole = tenths / 10
        val frac = tenths % 10
        "$whole.$frac km away"
    }
}

@Composable
private fun MemoriesPillContent(
    memories: Int,
    liveCount: Int,
    ghostMode: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ghostMode) Icons.Filled.LocationOff else Icons.Filled.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (ghostMode) Color.Gray else PrimaryBlue,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (ghostMode) {
                "Ghost Mode"
            } else {
                "$memories ${if (memories == 1) "memory" else "memories"}"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!ghostMode && liveCount > 0) {
            Spacer(modifier = Modifier.width(10.dp))
            LiveIndicator(count = liveCount)
        }
    }
}

@Composable
private fun LiveIndicator(count: Int) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = alpha)),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "$count Live",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryBlue,
        )
    }
}

@Composable
private fun CommunityHubBottomSheet(
    hub: CommunityHubPin,
    distanceMeters: Double?,
    canJoinGeofence: Boolean?,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = hub.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = GlassSheetTokens.OnOled(),
        )
        Text(
            text = "${hub.activeUserCount} active nearby",
            style = MaterialTheme.typography.bodyMedium,
            color = GlassSheetTokens.OnOledMuted(),
        )
        val distLabel = distanceMeters?.let { d ->
            if (d >= 1000) {
                val kmTenths = (d / 100.0).toInt()
                "${kmTenths / 10.0} km away"
            } else {
                "${d.toInt()} m away"
            }
        } ?: if (canJoinGeofence == null) "Checking location…" else "Distance unavailable"
        Text(
            text = distLabel,
            style = MaterialTheme.typography.bodySmall,
            color = GlassSheetTokens.OnOledMuted(),
        )
        when (canJoinGeofence) {
            true -> {
                Button(
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Join Hub")
                }
            }
            false -> {
                Text(
                    text = "Move closer to join this hub.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassSheetTokens.OnOledMuted(),
                )
            }
            null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Verifying your location…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassSheetTokens.OnOledMuted(),
                    )
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}

@Composable
private fun MapContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    renderData: MapRenderData,
    communityHubs: List<CommunityHubPin>,
    zoom: Double,
    ghostMode: Boolean,
    mapGesturesEnabled: Boolean = true,
    showCompass: Boolean = true,
    cameraTarget: compose.project.click.click.viewmodel.CameraTarget?,
    userLat: Double? = null,
    userLon: Double? = null,
    currentUserId: String? = null,
    onPinTapped: (MapPin) -> Unit,
    onClusterTapped: (MapClusterPin) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit,
    onCameraAnimationComplete: () -> Unit,
    onMapGesture: () -> Unit = {},
) {
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val hubPins = remember(communityHubs) {
        communityHubs.map { MapPin.fromCommunityHub(it) }
    }
    val pins = remember(renderData, connectedUsers, currentUserId, hubPins) {
        when (renderData) {
            is MapRenderData.IndividualPins -> {
                val conn = renderData.points
                    .distinctBy { oneToOnePeerPairKey(it.connection.user_ids) ?: it.connection.id }
                    .map { point ->
                    val peerId = point.connection.user_ids.firstOrNull { it != currentUserId }
                    val peer = peerId?.let { connectedUsers[it] }
                    MapPin.fromConnectionPoint(
                        point,
                        imageUrl = peer?.image,
                        avatarSeed = peerId ?: point.connection.id,
                    )
                }
                val bc = renderData.beacons.map { MapPin.fromBeacon(it) }
                (conn + bc + hubPins).sortedByDescending { it.zIndex }
            }
            is MapRenderData.Clusters -> {
                val standalone = renderData.standaloneBeacons.map { MapPin.fromBeacon(it) }
                (standalone + hubPins).sortedByDescending { it.zIndex }
            }
        }
    }

    val clusters = remember(renderData) {
        when (renderData) {
            is MapRenderData.Clusters -> renderData.clusters.map { it.toClusterPin() }
            is MapRenderData.IndividualPins -> emptyList()
        }
    }

    // Drive the native map only while a programmatic CameraTarget is active.
    // After onCameraAnimationComplete clears it, pass null centers so PlatformMap
    // keeps the settled viewport (do not snap back to GPS / default user zoom).
    val mapCenterLat = cameraTarget?.latitude
    val mapCenterLon = cameraTarget?.longitude
    val mapZoom = zoom

    PlatformMap(
        modifier = modifier,
        pins = pins,
        clusters = clusters,
        zoom = mapZoom,
        centerLat = mapCenterLat,
        centerLon = mapCenterLon,
        ghostMode = ghostMode,
        mapGesturesEnabled = mapGesturesEnabled,
        showCompass = showCompass,
        onPinTapped = onPinTapped,
        onClusterTapped = onClusterTapped,
        onZoomChanged = onZoomChanged,
        onVisibleBoundsChanged = onVisibleBoundsChanged,
        onCameraAnimationComplete = onCameraAnimationComplete,
        onMapGesture = onMapGesture,
    )
}

/** Stable callback identity so [MapContent] / [PlatformMap] can skip when Events opens. */
@Composable
private fun rememberMapPinTapHandler(
    onConnection: (pinId: String) -> Unit,
    onClearConnection: () -> Unit,
    onPin: (MapPin) -> Unit,
): (MapPin) -> Unit {
    val onConnectionState = rememberUpdatedState(onConnection)
    val onClearConnectionState = rememberUpdatedState(onClearConnection)
    val onPinState = rememberUpdatedState(onPin)
    return remember {
        { pin: MapPin ->
            TelemetryBatcher.recordActionTaken()
            if (pin.kind == MapPinKind.CONNECTION) {
                onConnectionState.value(pin.id)
            } else {
                onClearConnectionState.value()
            }
            onPinState.value(pin)
        }
    }
}

@Composable
private fun rememberMapClusterTapHandler(
    onCluster: (clusterId: String) -> Unit,
): (MapClusterPin) -> Unit {
    val onClusterState = rememberUpdatedState(onCluster)
    return remember {
        { clusterPin: MapClusterPin ->
            TelemetryBatcher.recordActionTaken()
            onClusterState.value(clusterPin.id)
        }
    }
}

@Composable
private fun rememberStableZoomHandler(onZoom: (Double) -> Unit): (Double) -> Unit {
    val state = rememberUpdatedState(onZoom)
    return remember { { z: Double -> state.value(z) } }
}

@Composable
private fun rememberStableBoundsHandler(
    onBounds: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit,
): (Double, Double, Double, Double) -> Unit {
    val state = rememberUpdatedState(onBounds)
    return remember {
        { minLat: Double, maxLat: Double, minLon: Double, maxLon: Double ->
            state.value(minLat, maxLat, minLon, maxLon)
        }
    }
}

@Composable
private fun rememberStableUnitHandler(onInvoke: () -> Unit): () -> Unit {
    val state = rememberUpdatedState(onInvoke)
    return remember { { state.value() } }
}

@Composable
private fun OverlappingMapPinsChooser(
    pins: List<MapPin>,
    onChoose: (MapPin) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Which pin?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "A few pins are stacked here — pick the one you meant.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pins.forEach { pin ->
            val kindLabel = when (pin.kind) {
                MapPinKind.CONNECTION -> "Connection"
                MapPinKind.COMMUNITY_HUB -> "Hub"
                MapPinKind.BEACON_SOUNDTRACK -> "Soundtrack"
                MapPinKind.BEACON_ALERT -> "Alert"
                MapPinKind.BEACON_SOCIAL -> "Event"
                MapPinKind.BEACON_OTHER -> "Beacon"
            }
            val shape = RoundedCornerShape(16.dp)
            val rowSurface = clickCardSurface()
            val rowBorder = clickBorderColor()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(rowSurface)
                    .border(2.dp, rowBorder, shape)
                    .clickable { onChoose(pin) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectionListUserAvatarFace(
                    displayName = pin.title,
                    email = null,
                    avatarUrl = pin.imageUrl,
                    userId = pin.avatarUserId ?: pin.id,
                    modifier = Modifier
                        .size(48.dp)
                        .border(2.dp, rowBorder, CircleShape)
                        .clip(CircleShape),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pin.title.ifBlank { kindLabel },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = kindLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ZoomControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    MapZoomGlassControls(
        modifier = modifier,
        onZoomIn = onZoomIn,
        onZoomOut = onZoomOut,
        glassStrength = if (LocalPlatformStyle.current.isIOS) 0.78f else 0.4f,
    )
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AdaptiveCircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Error loading map",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Connection Marker Bottom Sheet — shown when a pin is tapped.
 */
@Composable
fun ConnectionMarkerSheet(
    point: ConnectionMapPoint,
    otherUser: User?,
    onMessage: (String) -> Unit,
    onNudge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetBg = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(sheetBg)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    when (point.timeState) {
                        TimeState.LIVE -> PrimaryBlue
                        TimeState.RECENT -> MaterialTheme.colorScheme.primaryContainer
                        TimeState.ARCHIVE -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .border(2.dp, clickBorderColor(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (point.shouldPulse) {
                PulsingRing()
            }
            Text(
                otherUser?.name?.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = when (point.timeState) {
                    TimeState.LIVE -> Color.White
                    TimeState.RECENT -> MaterialTheme.colorScheme.onPrimaryContainer
                    TimeState.ARCHIVE -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            otherUser?.name ?: "Connection",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Met at ${point.locationLabel}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            point.formattedDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        MarkerSheetTimeStateBadge(timeState = point.timeState)

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isActive = point.connection.should_continue.contains(true)
            val hasChat = point.connection.has_begun

            if (hasChat || isActive) {
                Button(
                    onClick = { onMessage(point.connection.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                ) {
                    Icon(Icons.Filled.Message, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }
            }

            if (point.timeState == TimeState.LIVE || point.timeState == TimeState.RECENT) {
                OutlinedButton(onClick = onNudge, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nudge")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Spacer(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun MarkerSheetTimeStateBadge(timeState: TimeState) {
    val (color, label, icon) = when (timeState) {
        TimeState.LIVE -> Triple(PrimaryBlue, "Live Now", Icons.Filled.Bolt)
        TimeState.RECENT -> Triple(LightBlue, "Recent", Icons.Filled.AccessTime)
        TimeState.ARCHIVE -> Triple(Color.Gray, "Memory", Icons.Filled.History)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(2.dp, color),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}

@Composable
private fun PulsingRing() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Box(
        modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .border(3.dp, PrimaryBlue.copy(alpha = alpha), CircleShape),
    )
}

private enum class EventsListTransitionMode {
    Tap,
    Gesture,
}
