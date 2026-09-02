@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSession // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSessionManager // pragma: allowlist secret
import compose.project.click.click.data.ActiveHubEntry // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.OpenMeteoWeatherService // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.hub.HubConnectionManager // pragma: allowlist secret
import compose.project.click.click.data.hub.HubVerifyResult // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.isPendingSync // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.deeplink.ConnectionDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.deeplink.EventDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.encounter.EncounterTetherManager // pragma: allowlist secret
import compose.project.click.click.navigation.NavigationItem // pragma: allowlist secret
import compose.project.click.click.notifications.ChatDeepLinkManager // pragma: allowlist secret
import compose.project.click.click.notifications.ChatNotificationDismisser // pragma: allowlist secret
import compose.project.click.click.sensors.AmbientNoiseMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.ui.components.AppShimmerScreen // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealPhase // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealUiState // pragma: allowlist secret
import compose.project.click.click.ui.components.OfflineStatusBanner // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformBackHandler // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberUnifiedToastState // pragma: allowlist secret
import compose.project.click.click.ui.screens.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.HomeViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.VerifiedCliqueProximityIntent // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppMainShell(
    reduceMotion: Boolean,
    isIOS: Boolean,
    client: HttpClient,
    tokenStorage: TokenStorage,
    appScope: CoroutineScope,
    connectionScope: CoroutineScope,
    currentUser: User,
    appDataUser: User?,
    locationService: compose.project.click.click.utils.LocationService,
    authViewModel: AuthViewModel,
    connectionViewModel: ConnectionViewModel,
    supabaseRepo: SupabaseRepository,
    ambientMonitor: AmbientNoiseMonitor,
    baroMonitor: BarometricHeightMonitor,
    openMeteoWeather: OpenMeteoWeatherService,
    showOfflineBanner: Boolean,
    isInitialLoading: Boolean,
    pendingConnectionsCount: Int,
    appError: String?,
    hasPlayedHomeEntrance: Boolean,
    showHomeRevealOverlay: Boolean,
    onboardingHandoffActive: Boolean,
    shouldStartOnboardingHandoff: Boolean,
    requestLocationPermissionIfNeeded: suspend (Boolean) -> Unit,
    resolveConnectionLocation: suspend (LocationResult?) -> LocationResult?,
    resolveHubGatekeeperLocationForChat: suspend () -> LocationResult?,
    isDarkModeState: MutableState<Boolean>,
    ambientNoiseOptInState: MutableState<Boolean>,
    lastHubGatekeeperFixState: MutableState<LocationResult?>,
    showMyQRCodeState: MutableState<Boolean>,
    showQRScannerState: MutableState<Boolean>,
    connectionRevealStateState: MutableState<ConnectionRevealUiState?>,
    revealConnectionIdState: MutableState<String?>,
    showConnectionDisposableRollState: MutableState<Boolean>,
    connectionRollConnectionIdState: MutableState<String?>,
    pendingRollSessionState: MutableState<CollaborationSession?>,
    disposableRollOpeningState: MutableState<Boolean>,
    disposableRollExitWithScaleState: MutableState<Boolean>,
) {
    var isDarkMode by isDarkModeState
    var lastHubGatekeeperFix by lastHubGatekeeperFixState
    var showMyQRCode by showMyQRCodeState
    var showQRScanner by showQRScannerState
    var connectionRevealState by connectionRevealStateState
    var revealConnectionId by revealConnectionIdState
    var showConnectionDisposableRoll by showConnectionDisposableRollState
    var connectionRollConnectionId by connectionRollConnectionIdState
    var pendingRollSession by pendingRollSessionState
    var disposableRollOpening by disposableRollOpeningState
    val homeRevealAlpha by animateFloatAsState(
        targetValue = if (showHomeRevealOverlay) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = LinearOutSlowInEasing),
        label = "home_reveal_overlay_alpha",
    )
    var homeSurfaceVisible by remember(hasPlayedHomeEntrance) { mutableStateOf(hasPlayedHomeEntrance) }
    LaunchedEffect(
        showHomeRevealOverlay,
        onboardingHandoffActive,
        shouldStartOnboardingHandoff,
        hasPlayedHomeEntrance,
    ) {
        if (hasPlayedHomeEntrance) {
            homeSurfaceVisible = true
            return@LaunchedEffect
        }
        if (showHomeRevealOverlay || onboardingHandoffActive || shouldStartOnboardingHandoff) {
            homeSurfaceVisible = false
        } else {
            delay(16)
            homeSurfaceVisible = true
        }
    }
    val homeSurfaceAlpha by animateFloatAsState(
        targetValue = if (homeSurfaceVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing),
        label = "home_surface_alpha",
    )
    val currentRouteState = remember { mutableStateOf("home") }
    var currentRoute by currentRouteState
    val previousRouteState = remember { mutableStateOf("home") }
    var previousRoute by previousRouteState
    val transitionModeState = remember { mutableStateOf(NavigationTransitionMode.Tap) }
    var transitionMode by transitionModeState
    // Route history stack for back navigation
    val routeHistory = remember { mutableStateListOf("home") }
    val showNfcScreenState = remember { mutableStateOf(false) }
    var showNfcScreen by showNfcScreenState
    val pendingChatIdState = remember { mutableStateOf<String?>(null) }
    var pendingChatId by pendingChatIdState
    val pendingTargetMessageIdState = remember { mutableStateOf<String?>(null) }
    var pendingTargetMessageId by pendingTargetMessageIdState
    val pendingHubTargetMessageIdState = remember { mutableStateOf<String?>(null) }
    var pendingHubTargetMessageId by pendingHubTargetMessageIdState
    val pendingBeaconIdState = remember { mutableStateOf<String?>(null) }
    var pendingBeaconId by pendingBeaconIdState
    val pendingMapLayerFilterState = remember { mutableStateOf<MapLayerFilter?>(null) }
    var pendingMapLayerFilter by pendingMapLayerFilterState
    val isConnectionsChatOpenState = remember { mutableStateOf(false) }
    var isConnectionsChatOpen by isConnectionsChatOpenState
    val isSettingsSubpageOpenState = remember { mutableStateOf(false) }
    var isSettingsSubpageOpen by isSettingsSubpageOpenState
    // Separate from session-open: true while chat owns the bottom edge.
    val connectionsChatSuppressesTabBarState = remember { mutableStateOf(false) }
    var connectionsChatSuppressesTabBar by connectionsChatSuppressesTabBarState
    val verifiedCliqueProximityAutofillIntentState = remember { mutableStateOf<VerifiedCliqueProximityIntent?>(null) }
    var verifiedCliqueProximityAutofillIntent by verifiedCliqueProximityAutofillIntentState

    fun navigateTo(route: String) {
        if (route != currentRoute) {
            transitionMode = NavigationTransitionMode.Tap
            previousRoute = currentRoute
            routeHistory.add(currentRoute)
            currentRoute = route
            if (route != NavigationItem.Connections.route) {
                isConnectionsChatOpen = false
                connectionsChatSuppressesTabBar = false
            }
            if (route != NavigationItem.Settings.route) {
                isSettingsSubpageOpen = false
            }
        }
    }

    // Helper: go back to previous route
    fun navigateBack(mode: NavigationTransitionMode = NavigationTransitionMode.Tap): Boolean {
        if (routeHistory.size > 1) {
            transitionMode = mode
            routeHistory.removeLastOrNull()
            val target = routeHistory.lastOrNull() ?: "home"
            previousRoute = currentRoute
            currentRoute = target
            return true
        }
        return false
    }

    fun navigatePrimaryRouteBackHome(mode: NavigationTransitionMode = NavigationTransitionMode.Tap): Boolean {
        if (!isPrimaryNavRoute(currentRoute) || currentRoute == NavigationItem.Home.route) {
            return false
        }

        transitionMode = mode
        previousRoute = currentRoute
        currentRoute = NavigationItem.Home.route
        routeHistory.clear()
        routeHistory.add(NavigationItem.Home.route)
        isConnectionsChatOpen = false
        connectionsChatSuppressesTabBar = false
        pendingChatId = null
        pendingTargetMessageId = null
        pendingHubTargetMessageId = null
        pendingBeaconId = null
        pendingMapLayerFilter = null
        return true
    }

    val focusManager = LocalFocusManager.current
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel() }
    val mapViewModel: MapViewModel = viewModel { MapViewModel() }
    val chatViewModel: ChatViewModel = viewModel { ChatViewModel() }
    val shareableMapBeacons by mapViewModel.mapBeacons.collectAsState()
    val hubChatArgsState = remember { mutableStateOf<HubChatNavArgs?>(null) }
    var hubChatArgs by hubChatArgsState
    val hubVerifyInProgressState = remember { mutableStateOf(false) }
    var hubVerifyInProgress by hubVerifyInProgressState
    val lastHubChatArgsState = remember { mutableStateOf<HubChatNavArgs?>(null) }
    var lastHubChatArgs by lastHubChatArgsState
    val hubChatTransitionModeState = remember { mutableStateOf(NavigationTransitionMode.Tap) }
    var hubChatTransitionMode by hubChatTransitionModeState
    val hubChatCloseJobState = remember { mutableStateOf<Job?>(null) }
    var hubChatCloseJob by hubChatCloseJobState

    fun closeHubChat(mode: NavigationTransitionMode) {
        hubChatCloseJob?.cancel()
        hubChatTransitionMode = mode
        pendingHubTargetMessageId = null
        if (mode == NavigationTransitionMode.GestureBack) {
            // Keep route ownership and bottom chrome stable through the gesture's final
            // frame; otherwise the revealed tab resizes while the foreground is settling.
            hubChatCloseJob =
                appScope.launch {
                    delay(64)
                    hubChatArgs = null
                    hubChatCloseJob = null
                }
        } else {
            hubChatArgs = null
        }
    }
    val showUnifiedSearchSheetState = remember { mutableStateOf(false) }
    var showUnifiedSearchSheet by showUnifiedSearchSheetState
    val eventsSheetExpandedState = remember { mutableStateOf(false) }
    var eventsSheetExpanded by eventsSheetExpandedState
    LaunchedEffect(currentRoute) {
        if (currentRoute != NavigationItem.Map.route && eventsSheetExpanded) {
            eventsSheetExpanded = false
        }
    }

    LaunchedEffect(connectionViewModel, currentUser.id) {
        if (currentUser.id.isBlank()) return@LaunchedEffect
        connectionViewModel.verifiedCliqueFromProximity.collect { intent ->
            verifiedCliqueProximityAutofillIntent = intent
            navigateTo(NavigationItem.Connections.route)
            showNfcScreen = false
            showQRScanner = false
            showMyQRCode = false
            hubChatCloseJob?.cancel()
            hubChatCloseJob = null
            hubChatArgs = null
            connectionRevealState = null
            pendingChatId = null
            pendingTargetMessageId = null
            pendingHubTargetMessageId = null
        }
    }
    val addClickOverlayKey =
        when {
            showMyQRCode -> "my_qr"
            showQRScanner -> "qr_scanner"
            showNfcScreen -> "nfc"
            else -> null
        }
    // Keep AnimatedContent on the primary tab so Add Click is not destroyed while
    // My Code / Scan / Tap overlays are open (avoids per-card remount flicker on swipe-back).
    val activeScreenKey = currentRoute
    val canSwipeBackMainRoute =
        isIOS &&
            isPrimaryNavRoute(currentRoute) &&
            currentRoute != NavigationItem.Home.route &&
            addClickOverlayKey == null &&
            !isConnectionsChatOpen &&
            !(currentRoute == NavigationItem.Settings.route && isSettingsSubpageOpen) &&
            hubChatArgs == null
    val iOSSwipeOwnsBack =
        isIOS &&
            (
                addClickOverlayKey != null ||
                    (currentRoute == NavigationItem.Connections.route && isConnectionsChatOpen) ||
                    (currentRoute == NavigationItem.Settings.route && isSettingsSubpageOpen) ||
                    hubChatArgs != null ||
                    canSwipeBackMainRoute
            )

    LaunchedEffect(currentUser.id) {
        if (currentUser.id.isNotEmpty()) {
            chatViewModel.setCurrentUser(currentUser.id)
        }
    }

    val deepLinkConnectionId by ChatDeepLinkManager.pendingConnectionId.collectAsState()
    LaunchedEffect(deepLinkConnectionId, currentUser.id) {
        val connId = deepLinkConnectionId ?: return@LaunchedEffect
        if (connId.isBlank() || currentUser.id.isBlank()) return@LaunchedEffect
        chatViewModel.setCurrentUser(currentUser.id)
        chatViewModel.leaveChatRoom(clearMessageSurface = false)
        ChatNotificationDismisser.dismissForThread(connId, connId)
        ChatDeepLinkManager.consume()
        pendingChatId = connId
        navigateTo(NavigationItem.Connections.route)
    }

    val pendingCommunityHubId by ChatDeepLinkManager.pendingCommunityHubId.collectAsState()

    val pendingConnectionUserId by ConnectionDeepLinkRouter.pendingConnectionUserId.collectAsState()
    val pendingEventDeepLinkBeaconId by EventDeepLinkRouter.pendingBeaconId.collectAsState()

    // Unified toast for connection success/error feedback
    val toastState = rememberUnifiedToastState()

    LaunchedEffect(connectionViewModel) {
        connectionViewModel.transientNotice.collect { message ->
            toastState.show(connectionScope, message)
        }
    }

    LaunchedEffect(Unit) {
        AppDataManager.transientUserMessages.collect { message ->
            toastState.show(connectionScope, message)
        }
    }

    fun launchCommunityHubJoin(
        hubId: String,
        knownCreatorId: String? = null,
    ) {
        if (hubId.isBlank() || currentUser.id.isBlank()) return
        hubChatCloseJob?.cancel()
        hubChatCloseJob = null
        // If we already have cached args for this hub, skip verification and re-enter.
        val cached = lastHubChatArgs
        if (cached != null && cached.hubId == hubId) {
            hubChatArgs =
                if (
                    cached.creatorId == null &&
                    !knownCreatorId.isNullOrBlank()
                ) {
                    cached.copy(creatorId = knownCreatorId)
                } else {
                    cached
                }
            return
        }
        connectionScope.launch {
            hubVerifyInProgress = true
            try {
                requestLocationPermissionIfNeeded(
                    !locationService.hasLocationPermission(),
                )
                if (!locationService.hasLocationPermission()) {
                    toastState.show(connectionScope, "Location permission is required to join this hub.")
                    return@launch
                }
                val loc = resolveHubGatekeeperLocationForChat()
                if (loc == null) {
                    toastState.show(connectionScope, "Could not read your location. Try again in an open area.")
                    return@launch
                }
                lastHubGatekeeperFix = loc
                val jwt =
                    EnsureFreshAccessToken
                        .get(tokenStorage)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                if (jwt.isNullOrBlank()) {
                    toastState.show(connectionScope, "Please sign in again to join the hub.")
                    return@launch
                }
                when (
                    val outcome =
                        HubConnectionManager.verifyProximity(
                            httpClient = client,
                            hubId = hubId,
                            userLat = loc.latitude,
                            userLong = loc.longitude,
                            bearerJwt = jwt,
                        )
                ) {
                    is HubVerifyResult.Success -> {
                        val creatorId = outcome.creatorId ?: knownCreatorId
                        val args =
                            HubChatNavArgs(
                                hubId = outcome.hubId,
                                realtimeChannel = outcome.channel,
                                hubTitle = outcome.name,
                                creatorId = creatorId,
                                isEventHub = false,
                            )
                        lastHubChatArgs = args
                        hubChatArgs = args
                        AppDataManager.registerActiveHub(
                            ActiveHubEntry(
                                hubId = outcome.hubId,
                                name = outcome.name,
                                realtimeChannel = outcome.channel,
                                joinedAtMs =
                                    kotlinx.datetime.Clock.System
                                        .now()
                                        .toEpochMilliseconds(),
                                creatorId = creatorId,
                            ),
                        )
                    }
                    is HubVerifyResult.Failure -> {
                        toastState.show(connectionScope, outcome.userMessage)
                    }
                }
            } finally {
                hubVerifyInProgress = false
            }
        }
    }

    fun launchEventHubJoin(
        hubId: String,
        title: String,
        knownCreatorId: String? = null,
    ) {
        if (hubId.isBlank() || currentUser.id.isBlank()) return
        hubChatCloseJob?.cancel()
        hubChatCloseJob = null
        val cached = lastHubChatArgs
        if (cached != null && cached.hubId == hubId) {
            hubChatArgs =
                cached.copy(
                    creatorId = cached.creatorId ?: knownCreatorId,
                    isEventHub = true,
                    hubCategory = if (cached.hubCategory.isBlank()) "event" else cached.hubCategory,
                    hubTitle = title.ifBlank { cached.hubTitle },
                )
            return
        }
        connectionScope.launch {
            hubVerifyInProgress = true
            try {
                val jwt =
                    EnsureFreshAccessToken
                        .get(tokenStorage)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                if (jwt.isNullOrBlank()) {
                    toastState.show(connectionScope, "Please sign in again to join the hub.")
                    return@launch
                }
                when (
                    val outcome =
                        HubConnectionManager.joinEventHub(
                            httpClient = client,
                            hubId = hubId,
                            bearerJwt = jwt,
                        )
                ) {
                    is HubVerifyResult.Success -> {
                        val creatorId = outcome.creatorId ?: knownCreatorId
                        val args =
                            HubChatNavArgs(
                                hubId = outcome.hubId,
                                realtimeChannel = outcome.channel,
                                hubTitle = outcome.name.ifBlank { title },
                                creatorId = creatorId,
                                hubCategory = "event",
                                isEventHub = true,
                            )
                        lastHubChatArgs = args
                        hubChatArgs = args
                        AppDataManager.registerActiveHub(
                            ActiveHubEntry(
                                hubId = outcome.hubId,
                                name = outcome.name.ifBlank { title },
                                realtimeChannel = outcome.channel,
                                joinedAtMs =
                                    kotlinx.datetime.Clock.System
                                        .now()
                                        .toEpochMilliseconds(),
                                creatorId = creatorId,
                                category = "event",
                                isEventHub = true,
                            ),
                        )
                    }
                    is HubVerifyResult.Failure -> {
                        toastState.show(connectionScope, outcome.userMessage)
                    }
                }
            } finally {
                hubVerifyInProgress = false
            }
        }
    }

    val pendingEventHub by ChatDeepLinkManager.pendingEventHub.collectAsState()
    LaunchedEffect(pendingEventHub, currentUser.id) {
        val pending = pendingEventHub ?: return@LaunchedEffect
        if (currentUser.id.isBlank()) return@LaunchedEffect
        ChatDeepLinkManager.consumePendingEventHub()
        launchEventHubJoin(pending.hubId, pending.title, pending.creatorId)
    }

    LaunchedEffect(pendingCommunityHubId, currentUser.id) {
        val hid = pendingCommunityHubId ?: return@LaunchedEffect
        if (currentUser.id.isBlank()) return@LaunchedEffect
        ChatDeepLinkManager.consumeCommunityHub()
        launchCommunityHubJoin(hid)
    }

    LaunchedEffect(pendingConnectionUserId, currentUser.id) {
        val scannedUserId = pendingConnectionUserId ?: return@LaunchedEffect
        if (scannedUserId.isBlank() || currentUser.id.isBlank()) return@LaunchedEffect
        if (scannedUserId == currentUser.id) {
            ConnectionDeepLinkRouter.consume()
            return@LaunchedEffect
        }
        ConnectionDeepLinkRouter.consume()
        showQRScanner = false
        showMyQRCode = false
        connectionViewModel.presentQrContextSheetFromScan(
            scannedUserId = scannedUserId,
            qrToken = null,
            venueId = null,
        )
    }

    LaunchedEffect(pendingEventDeepLinkBeaconId, currentUser.id) {
        val beaconId = pendingEventDeepLinkBeaconId ?: return@LaunchedEffect
        if (beaconId.isBlank() || currentUser.id.isBlank()) return@LaunchedEffect
        EventDeepLinkRouter.consume()
        pendingBeaconId = beaconId
        navigateTo(NavigationItem.Map.route)
    }
    val connectionState by connectionViewModel.connectionState.collectAsState()
    val collaborationSessions by CollaborationSessionManager.sessions.collectAsState()
    val openConnectionDisposableRoll: (String?) -> Unit = openConnectionDisposableRoll@{ connectionId ->
        val cid = connectionId?.trim()?.takeIf { it.isNotEmpty() } ?: return@openConnectionDisposableRoll
        connectionScope.launch {
            disposableRollOpening = true
            try {
                connectionViewModel
                    .ensureCollaborationSessionReady(cid)
                    .onSuccess { session ->
                        connectionRollConnectionId = cid
                        pendingRollSession = session
                        showConnectionDisposableRoll = true
                    }.onFailure { error ->
                        val message =
                            error.message
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                ?.take(160)
                                ?: "Couldn't open Click Drops"
                        toastState.show(connectionScope, message)
                    }
            } finally {
                disposableRollOpening = false
            }
        }
    }
    val openChatDisposableRoll: (String?) -> Unit = openChatDisposableRoll@{ chatId ->
        val cid = chatId?.trim()?.takeIf { it.isNotEmpty() } ?: return@openChatDisposableRoll
        connectionScope.launch {
            disposableRollOpening = true
            try {
                connectionViewModel
                    .ensureCollaborationSessionReadyForChat(cid)
                    .onSuccess { session ->
                        connectionRollConnectionId = cid
                        pendingRollSession = session
                        showConnectionDisposableRoll = true
                    }.onFailure { error ->
                        val message =
                            error.message
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                ?.take(160)
                                ?: "Couldn't open Click Drops"
                        toastState.show(connectionScope, message)
                    }
            } finally {
                disposableRollOpening = false
            }
        }
    }
    val suppressConnectionContextSheet =
        when (connectionRevealState?.phase) {
            ConnectionRevealPhase.Connecting,
            ConnectionRevealPhase.Success,
            -> true
            else -> false
        }
    LaunchedEffect(connectionState, showNfcScreen) {
        when (val state = connectionState) {
            is ConnectionState.Success -> {
                revealConnectionId = state.connection.id
                val peerName =
                    state.connectedUser.name
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() } ?: "user"
                val chatId = state.connection.id
                if (state.connection.isPendingSync()) {
                    connectionRevealState = null
                    toastState.show(
                        connectionScope,
                        "Connection saved offline. It will sync automatically when you're back online.",
                    )
                    navigateTo(NavigationItem.Connections.route)
                } else if (connectionRevealState != null) {
                    connectionRevealState =
                        connectionRevealState?.copy(
                            phase = ConnectionRevealPhase.Success,
                            connectedName = peerName,
                        )
                    delay(900)
                    navigateTo(NavigationItem.Connections.route)
                    connectionRevealState = null
                    toastState.show(
                        connectionScope,
                        "Connected with $peerName!",
                        durationMs = UnifiedToastTokens.LongDurationMs,
                        actionLabel = "Message",
                        onAction = { pendingChatId = chatId },
                    )
                } else {
                    navigateTo(NavigationItem.Connections.route)
                    toastState.show(
                        connectionScope,
                        "Connected with $peerName!",
                        durationMs = UnifiedToastTokens.LongDurationMs,
                        actionLabel = "Message",
                        onAction = { pendingChatId = chatId },
                    )
                }
                connectionViewModel.resetConnectionState()
            }
            is ConnectionState.Error -> {
                connectionRevealState = null
                toastState.show(connectionScope, state.message)
                connectionViewModel.resetConnectionState()
            }
            else -> {}
        }
    }

    // Platform back handler — intercepts Android back gesture/button
    compose.project.click.click.ui.components.PlatformBackHandler( // pragma: allowlist secret
        enabled =
            (
                eventsSheetExpanded ||
                    showUnifiedSearchSheet ||
                    hubChatArgs != null ||
                    showMyQRCode ||
                    showQRScanner ||
                    showNfcScreen ||
                    (connectionState is ConnectionState.TaggingContext && !showNfcScreen) ||
                    (connectionState is ConnectionState.QrAwaitingContext && !showNfcScreen) ||
                    currentRoute != "home"
            ) &&
                !iOSSwipeOwnsBack,
    ) {
        when {
            eventsSheetExpanded -> eventsSheetExpanded = false
            showUnifiedSearchSheet -> showUnifiedSearchSheet = false
            hubChatArgs != null -> closeHubChat(NavigationTransitionMode.Tap)
            showMyQRCode -> {
                transitionMode = NavigationTransitionMode.GestureBack
                showMyQRCode = false
            }
            showQRScanner -> {
                transitionMode = NavigationTransitionMode.GestureBack
                showQRScanner = false
            }
            showNfcScreen -> {
                transitionMode = NavigationTransitionMode.GestureBack
                showNfcScreen = false
            }
            connectionState is ConnectionState.TaggingContext && !showNfcScreen ->
                connectionViewModel.resetConnectionState()
            connectionState is ConnectionState.QrAwaitingContext && !showNfcScreen ->
                connectionViewModel.resetConnectionState()
            pendingChatId != null -> pendingChatId = null // close open chat first
            currentRoute == NavigationItem.Settings.route && isSettingsSubpageOpen -> {
                // SettingsScreen's own back handler pops hub; never jump to Home.
            }
            else -> navigateBack(NavigationTransitionMode.GestureBack)
        }
    }

    // On iOS the native UITabBar cannot be covered by Compose. Hiding/showing it remounts
    // Liquid Glass after chat back-swipe. Keep it visible for connections chat; chat pads
    // above it. Disposable-roll camera is a full-screen overlay — never toggle the iOS
    // tab bar for it (that resize was flashing the whole chat on send/dismiss).
    val hideMainBottomBar =
        (!isIOS && connectionsChatSuppressesTabBar) ||
            (!isIOS && hubChatArgs != null) ||
            (!isIOS && (showConnectionDisposableRoll || disposableRollOpening))

    // Wrap Scaffold in a Box to allow search overlay to be positioned at true screen bottom
    Box(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(currentUser.id) {
            EncounterTetherManager.setCurrentUserId(currentUser.id)
        }
        if (showOfflineBanner) {
            OfflineStatusBanner(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 4.dp)
                        .zIndex(10f),
            )
        }
        Scaffold(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Android: zIndex puts chat above the Compose tab bar while a thread is open.
                    // iOS: native bar stays visible for connections chat (never toggled).
                    .zIndex(if (hideMainBottomBar || (!isIOS && isConnectionsChatOpen)) 6f else 0f),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { paddingValues ->
            Box(
                modifier =
                    Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .fillMaxSize()
                        .graphicsLayer { alpha = homeSurfaceAlpha },
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppPrimaryTabsHost(
                        activeScreenKey = activeScreenKey,
                        currentRoute = currentRoute,
                        addClickOverlayKey = addClickOverlayKey,
                        isIOS = isIOS,
                        reduceMotion = reduceMotion,
                        currentUser = currentUser,
                        client = client,
                        tokenStorage = tokenStorage,
                        appScope = appScope,
                        connectionScope = connectionScope,
                        locationService = locationService,
                        authViewModel = authViewModel,
                        connectionViewModel = connectionViewModel,
                        homeViewModel = homeViewModel,
                        mapViewModel = mapViewModel,
                        chatViewModel = chatViewModel,
                        toastState = toastState,
                        shareableMapBeacons = shareableMapBeacons,
                        navigateTo = ::navigateTo,
                        navigatePrimaryRouteBackHome = ::navigatePrimaryRouteBackHome,
                        launchCommunityHubJoin = { hubId, creatorId -> launchCommunityHubJoin(hubId, creatorId) },
                        openConnectionDisposableRoll = openConnectionDisposableRoll,
                        openChatDisposableRoll = openChatDisposableRoll,
                        transitionModeState = transitionModeState,
                        isDarkModeState = isDarkModeState,
                        pendingChatIdState = pendingChatIdState,
                        pendingTargetMessageIdState = pendingTargetMessageIdState,
                        pendingBeaconIdState = pendingBeaconIdState,
                        pendingMapLayerFilterState = pendingMapLayerFilterState,
                        showUnifiedSearchSheetState = showUnifiedSearchSheetState,
                        eventsSheetExpandedState = eventsSheetExpandedState,
                        verifiedCliqueProximityAutofillIntentState = verifiedCliqueProximityAutofillIntentState,
                        isConnectionsChatOpenState = isConnectionsChatOpenState,
                        isSettingsSubpageOpenState = isSettingsSubpageOpenState,
                        connectionsChatSuppressesTabBarState = connectionsChatSuppressesTabBarState,
                        showMyQRCodeState = showMyQRCodeState,
                        showQRScannerState = showQRScannerState,
                        showNfcScreenState = showNfcScreenState,
                        hubChatArgsState = hubChatArgsState,
                        connectionRevealStateState = connectionRevealStateState,
                    )

                    AppHubChatHost(
                        isIOS = isIOS,
                        reduceMotion = reduceMotion,
                        authViewModel = authViewModel,
                        hubChatTransitionMode = hubChatTransitionMode,
                        hubVerifyInProgress = hubVerifyInProgress,
                        pendingHubTargetMessageId = pendingHubTargetMessageId,
                        closeHubChat = ::closeHubChat,
                        resolveHubGatekeeperLocationForChat = { resolveHubGatekeeperLocationForChat() },
                        hubChatArgsState = hubChatArgsState,
                        lastHubChatArgsState = lastHubChatArgsState,
                    )

                    AppSyncStatusCard(
                        isInitialLoading = isInitialLoading,
                        pendingConnectionsCount = pendingConnectionsCount,
                        appError = appError,
                    )

                    AppConnectionOverlays(
                        connectionState = connectionState,
                        showNfcScreen = showNfcScreen,
                        suppressConnectionContextSheet = suppressConnectionContextSheet,
                        currentUser = currentUser,
                        isIOS = isIOS,
                        connectionViewModel = connectionViewModel,
                        chatViewModel = chatViewModel,
                        connectionScope = connectionScope,
                        tokenStorage = tokenStorage,
                        ambientMonitor = ambientMonitor,
                        baroMonitor = baroMonitor,
                        supabaseRepo = supabaseRepo,
                        openMeteoWeather = openMeteoWeather,
                        collaborationSessions = collaborationSessions,
                        resolveConnectionLocation = { seed -> resolveConnectionLocation(seed) },
                        ambientNoiseOptInState = ambientNoiseOptInState,
                        connectionRevealStateState = connectionRevealStateState,
                        connectionRollConnectionIdState = connectionRollConnectionIdState,
                        pendingRollSessionState = pendingRollSessionState,
                        showConnectionDisposableRollState = showConnectionDisposableRollState,
                        disposableRollExitWithScaleState = disposableRollExitWithScaleState,
                    )

                    if (homeRevealAlpha > 0.01f) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = homeRevealAlpha },
                        ) {
                            AppShimmerScreen(isDarkMode = isDarkMode)
                        }
                    }
                }
            }
        }
        AppBottomChrome(
            currentRoute = currentRoute,
            hideMainBottomBar = hideMainBottomBar,
            navigateTo = ::navigateTo,
            focusManager = focusManager,
            toastState = toastState,
            authViewModel = authViewModel,
            hubChatCloseJobState = hubChatCloseJobState,
            hubChatArgsState = hubChatArgsState,
            showMyQRCodeState = showMyQRCodeState,
            showQRScannerState = showQRScannerState,
            showNfcScreenState = showNfcScreenState,
            showUnifiedSearchSheetState = showUnifiedSearchSheetState,
            pendingHubTargetMessageIdState = pendingHubTargetMessageIdState,
            pendingChatIdState = pendingChatIdState,
            pendingTargetMessageIdState = pendingTargetMessageIdState,
            pendingBeaconIdState = pendingBeaconIdState,
        )
    }
}
