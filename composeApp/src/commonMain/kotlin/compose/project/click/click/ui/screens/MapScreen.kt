@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.events.EventReminderCoordinator // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.telemetry.TelemetryBatcher // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.BeaconShareToChatDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDialogChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.CreateHubModal // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassmorphicOverlay // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer // pragma: allowlist secret
import compose.project.click.click.ui.components.LiquidGlassPill // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalNativeChromeActive // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformBackHandler // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformMap // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformNativeNavigationBarSwipeReveal // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileSheetBadge // pragma: allowlist secret
import compose.project.click.click.ui.components.TabbedUserProfileSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.interactiveSwipeBackUnderlay // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberBottomChromePadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberFabAboveNavPadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberInteractiveBackHostState // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberUnifiedToastState // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import compose.project.click.click.viewmodel.CreateBeaconViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapSelection // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapState // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

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
    viewModel: MapViewModel = composeViewModel { MapViewModel() },
    onNavigateToChat: ((String) -> Unit)? = null,
    /**
     * Called after the in-sheet share picker confirms destinations.
     * [openConnectionId] is the connection/chat id to navigate to when non-null.
     */
    onShareBeaconToChats: (
        (
            beacon: MapBeacon,
            chatIds: List<String>,
            openConnectionId: String?,
        ) -> Unit
    )? = null,
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
    val eventsBackHost = rememberInteractiveBackHostState()
    val eventsSwipeDragPx = eventsBackHost.dragOffsetPx
    PlatformNativeNavigationBarSwipeReveal(eventsSwipeDragPx)
    // Keep events UI composed after first open so swipe-back does not remount the list.
    var eventsOverlayMounted by remember { mutableStateOf(false) }
    var eventsCloseJob by remember { mutableStateOf<Job?>(null) }
    val eventsScope = rememberCoroutineScope()
    // 0 = off-screen below, 1 = fully shown — restores slide-up open without remounting.
    val eventsVerticalReveal = remember { Animatable(0f) }

    fun finalizeEventsClose() {
        eventsBackHost.reset()
        eventsListTransitionMode = EventsListTransitionMode.Tap
        eventsCloseJob = null
    }

    fun closeEventsList(mode: EventsListTransitionMode) {
        eventsCloseJob?.cancel()
        eventsListTransitionMode = mode
        if (mode == EventsListTransitionMode.Tap) {
            onEventsSheetExpandedChanged(false)
            eventsCloseJob =
                eventsScope.launch {
                    eventsVerticalReveal.animateTo(
                        0f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    )
                    if (!eventsSheetExpanded) finalizeEventsClose()
                }
        } else {
            onEventsSheetExpandedChanged(false)
            eventsCloseJob =
                eventsScope.launch {
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
            eventsBackHost.reset()
            if (eventsVerticalReveal.value < 0.99f) {
                eventsVerticalReveal.snapTo(0f)
                eventsVerticalReveal.animateTo(
                    1f,
                    animationSpec =
                        spring(
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
        val known =
            rawMapBeacons.any { it.id == beaconId } ||
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
    val createBeaconViewModel: CreateBeaconViewModel =
        composeViewModel(key = "create-beacon") { CreateBeaconViewModel() }

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
        selectedProfileId =
            if (sel is MapSelection.ConnectionSelected) {
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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(grayscaleModifier)
                        .background(
                            if (ghostModeEnabled) {
                                Color.DarkGray.copy(alpha = 0.3f)
                            } else {
                                GlassSheetTokens.OledBlack()
                            },
                        ).testTag("map-screen"),
            ) {
                when (val state = mapState) {
                    is MapState.Loading -> LoadingState()
                    is MapState.Error -> ErrorState(message = state.message, onRetry = { viewModel.refresh() })
                    is MapState.Success -> {
                        val feedItems =
                            remember(
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
                        val eventNearbyCount =
                            remember(feedItems) {
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
                                onPinTapped =
                                    rememberMapPinTapHandler(
                                        onConnection = { pinId -> selectedProfileId = pinId },
                                        onClearConnection = { selectedProfileId = null },
                                        onPin = { viewModel.onMapPinTapped(it) },
                                    ),
                                onClusterTapped =
                                    rememberMapClusterTapHandler {
                                        viewModel.onClusterTappedFromMap(it)
                                    },
                                onZoomChanged = rememberStableZoomHandler { viewModel.setZoomLevel(it) },
                                onVisibleBoundsChanged =
                                    rememberStableBoundsHandler { minLat, maxLat, minLon, maxLon ->
                                        viewModel.updateVisibleBounds(minLat, maxLat, minLon, maxLon)
                                    },
                                onCameraAnimationComplete =
                                    rememberStableUnitHandler {
                                        viewModel.onCameraAnimationComplete()
                                    },
                                onMapGesture =
                                    rememberStableUnitHandler {
                                        TelemetryBatcher.recordMapPan()
                                    },
                            )

                            val nearbyFullyOpen =
                                eventsSheetExpanded && !eventsBackHost.behindLayersVisible
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
                                chromeVisible = true,
                                // Stay composed under the events overlay. iOS clips host-view
                                // controls to the uncovered strip instead of toggling hidden.
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .zIndex(10f)
                                        .graphicsLayer { alpha = if (nearbyFullyOpen) 0f else 1f }
                                        .interactiveSwipeBackUnderlay(eventsBackHost),
                            )

                            EventsReopenChip(
                                count = eventNearbyCount,
                                onClick = {
                                    eventsCloseJob?.cancel()
                                    eventsCloseJob = null
                                    eventsBackHost.reset()
                                    eventsListTransitionMode = EventsListTransitionMode.Tap
                                    eventsOverlayMounted = true
                                    onEventsSheetExpandedChanged(true)
                                },
                                enabled = !eventsSheetExpanded,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .zIndex(15f)
                                        .interactiveSwipeBackUnderlay(eventsBackHost)
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
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .zIndex(20f),
                            )

                            // Persist after first open; vertical reveal restores slide-up without remounting.
                            if (eventsOverlayMounted) {
                                val eventsClosed =
                                    !eventsSheetExpanded &&
                                        !eventsBackHost.behindLayersVisible &&
                                        eventsVerticalReveal.value < 0.01f
                                BoxWithConstraints(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .zIndex(40f),
                                ) {
                                    val heightPx = constraints.maxHeight
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    // During interactive back, Y stays put — container owns X.
                                                    val swiping =
                                                        eventsBackHost.behindLayersVisible ||
                                                            eventsSwipeDragPx.floatValue > 0.5f
                                                    translationY =
                                                        if (swiping) {
                                                            0f
                                                        } else {
                                                            (1f - eventsVerticalReveal.value) * heightPx
                                                        }
                                                    alpha = if (eventsClosed) 0f else 1f
                                                }.then(
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
                                            onBehindLayersVisibleChanged = {
                                                eventsBackHost.behindLayersVisible = it
                                            },
                                            onBack = {
                                                closeEventsList(EventsListTransitionMode.Gesture)
                                            },
                                            previousContent = {},
                                            currentContent = {
                                                CompositionLocalProvider(
                                                    LocalNativeChromeActive provides
                                                        (
                                                            eventsSheetExpanded ||
                                                                eventsBackHost.behindLayersVisible
                                                        ),
                                                ) {
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
                                                }
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
        // ClickFormBottomSheet owns chrome + scroll-at-top holder. IME is handled inside
        // BeaconDropSheetContent via sheetImePadding (WindowInsets.ime is 0 in UIKit sheets).
        ClickFormBottomSheet(
            onDismissRequest = {
                showBeaconDropSheet = false
                createBeaconViewModel.reset()
            },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            expandable = true,
            // Same as search: fill viewport + sheetImePadding + rubber-band dismiss.
            useUiKitScrollHost = true,
            uiKitFillViewport = true,
        ) {
            BeaconDropSheetContent(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(bottom = 12.dp),
                errorMessage = beaconInsertError,
                onDismissError = { viewModel.clearBeaconInsertError() },
                submitLocked = beaconSubmitInFlight,
                viewModel = createBeaconViewModel,
                onResolveCurrentLocation = {
                    if (!viewModel.hasLocationPermission()) {
                        null
                    } else {
                        val loc = viewModel.resolveDropLocationForUi()
                        if (loc == null) {
                            null
                        } else {
                            val reverse =
                                compose.project.click.click.utils.GeocodingService.reverseGeocode( // pragma: allowlist secret
                                    loc.latitude,
                                    loc.longitude,
                                )
                            reverse ?: run {
                                // Never persist the literal "Current location" label.
                                // Avoid String.format — not available on Kotlin/Native.
                                val lat = (kotlin.math.round(loc.latitude * 100_000.0) / 100_000.0)
                                val lon = (kotlin.math.round(loc.longitude * 100_000.0) / 100_000.0)
                                val coords = "$lat, $lon"
                                compose.project.click.click.utils.GeocodedPlace( // pragma: allowlist secret
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    displayName = coords,
                                    shortLabel = coords,
                                )
                            }
                        }
                    }
                },
                onSubmit = {
                    kind,
                    title,
                    description,
                    soundtrackUrl,
                    ttlMs,
                    showCreatorName,
                    visibilityAudience,
                    eventSchedule,
                    eventCategories,
                    venueScale,
                    eventLocation,
                    eventListingOptions,
                    imageBytes,
                    imageMime,
                    onRejectedEarly,
                    ->
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
                        eventListingOptions = eventListingOptions,
                        imageBytes = imageBytes,
                        imageMime = imageMime,
                        onAcceptedLocally = {
                            showBeaconDropSheet = false
                            createBeaconViewModel.reset()
                        },
                        onRejectedEarly = onRejectedEarly,
                        onRemoteFinished = { },
                    )
                },
                onCreateHub = {
                    showBeaconDropSheet = false
                    createBeaconViewModel.reset()
                    showCreateHubModal = true
                },
            )
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
                modifier =
                    Modifier
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
                    modifier =
                        Modifier
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
                modifier =
                    Modifier
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
                    modifier =
                        Modifier
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
        val inboxChats by compose.project.click.click.data.AppDataManager.inboxFeedChats // pragma: allowlist secret
            .collectAsState()
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .sheetBodyScroll()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
                UnifiedToastHost(
                    state = toastState,
                    opaque = true,
                    modifier =
                        Modifier
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
        val viewerUserId =
            compose.project.click.click.data.AppDataManager // pragma: allowlist secret
                .currentUser
                .collectAsState()
                .value
                ?.id
        val peerUserId =
            connectionSelection.otherUser?.id?.takeIf { it.isNotBlank() }
                ?: connectionSelection.point.connection.user_ids.firstOrNull { id ->
                    id.isNotBlank() && id != viewerUserId
                }
        val statusBadge =
            when (connectionSelection.point.timeState) {
                TimeState.LIVE -> ProfileSheetBadge("Live now", PrimaryBlue)
                TimeState.RECENT -> ProfileSheetBadge("Recent", LightBlue)
                TimeState.ARCHIVE -> ProfileSheetBadge("Memory", Color.Gray)
            }
        TabbedUserProfileSheet(
            userId = peerUserId,
            viewerUserId = viewerUserId,
            connectionId = connectionSelection.point.connection.id,
            statusBadge = statusBadge,
            onDismiss = {
                selectedProfileId = null
                viewModel.clearSelection()
            },
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
            onOpenDisposableRoll = onOpenDisposableRoll,
        )
    }

    val mapLocationService = remember { LocationService() }
    CreateHubModal(
        visible = showCreateHubModal,
        onDismiss = { showCreateHubModal = false },
        onHubCreated = { hubId -> onJoinCommunityHub(hubId) },
        locationService = mapLocationService,
        onError = { msg ->
            toastState.show(mapScope, msg, durationMs = UnifiedToastTokens.LongDurationMs)
        },
    )

    // Above modal sheets so check-in / engagement feedback is visible on event detail.
    Box(modifier = Modifier.fillMaxSize()) {
        UnifiedToastHost(
            state = toastState,
            opaque = true,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = rememberBottomChromePadding() + 8.dp)
                    .zIndex(100f),
        )
    }
}
