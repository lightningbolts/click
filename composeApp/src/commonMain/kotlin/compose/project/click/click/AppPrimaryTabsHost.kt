@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.navigation.NavigationItem // pragma: allowlist secret
import compose.project.click.click.navigation.shouldRenderHomeSwipeUnderlay // pragma: allowlist secret
import compose.project.click.click.proximity.rememberProximityManager // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealPhase // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealUiState // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalNativeChromeActive // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformNativeNavigationBarSwipeReveal // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastState // pragma: allowlist secret
import compose.project.click.click.ui.components.interactiveSwipeBackUnderlay // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberInteractiveBackHostState // pragma: allowlist secret
import compose.project.click.click.ui.screens.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthState // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.HomeViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.VerifiedCliqueProximityIntent // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPrimaryTabsHost(
    activeScreenKey: String,
    currentRoute: String,
    addClickOverlayKey: String?,
    isIOS: Boolean,
    reduceMotion: Boolean,
    currentUser: User,
    client: HttpClient,
    tokenStorage: TokenStorage,
    appScope: CoroutineScope,
    connectionScope: CoroutineScope,
    locationService: compose.project.click.click.utils.LocationService,
    authViewModel: AuthViewModel,
    connectionViewModel: ConnectionViewModel,
    homeViewModel: HomeViewModel,
    mapViewModel: MapViewModel,
    chatViewModel: ChatViewModel,
    toastState: UnifiedToastState,
    shareableMapBeacons: List<compose.project.click.click.data.models.MapBeacon>,
    navigateTo: (String) -> Unit,
    navigatePrimaryRouteBackHome: (NavigationTransitionMode) -> Boolean,
    launchCommunityHubJoin: (String, String?) -> Unit,
    openConnectionDisposableRoll: (String?) -> Unit,
    openChatDisposableRoll: (String?) -> Unit,
    transitionModeState: MutableState<NavigationTransitionMode>,
    isDarkModeState: MutableState<Boolean>,
    pendingChatIdState: MutableState<String?>,
    pendingTargetMessageIdState: MutableState<String?>,
    pendingBeaconIdState: MutableState<String?>,
    pendingMapLayerFilterState: MutableState<MapLayerFilter?>,
    showUnifiedSearchSheetState: MutableState<Boolean>,
    eventsSheetExpandedState: MutableState<Boolean>,
    verifiedCliqueProximityAutofillIntentState: MutableState<VerifiedCliqueProximityIntent?>,
    isConnectionsChatOpenState: MutableState<Boolean>,
    isSettingsSubpageOpenState: MutableState<Boolean>,
    connectionsChatSuppressesTabBarState: MutableState<Boolean>,
    showMyQRCodeState: MutableState<Boolean>,
    showQRScannerState: MutableState<Boolean>,
    showNfcScreenState: MutableState<Boolean>,
    hubChatArgsState: MutableState<HubChatNavArgs?>,
    connectionRevealStateState: MutableState<ConnectionRevealUiState?>,
) {
    var transitionMode by transitionModeState
    var isDarkMode by isDarkModeState
    var pendingChatId by pendingChatIdState
    var pendingTargetMessageId by pendingTargetMessageIdState
    var pendingBeaconId by pendingBeaconIdState
    var pendingMapLayerFilter by pendingMapLayerFilterState
    var showUnifiedSearchSheet by showUnifiedSearchSheetState
    var eventsSheetExpanded by eventsSheetExpandedState
    var verifiedCliqueProximityAutofillIntent by verifiedCliqueProximityAutofillIntentState
    var isConnectionsChatOpen by isConnectionsChatOpenState
    var isSettingsSubpageOpen by isSettingsSubpageOpenState
    var connectionsChatSuppressesTabBar by connectionsChatSuppressesTabBarState
    var showMyQRCode by showMyQRCodeState
    var showQRScanner by showQRScannerState
    var showNfcScreen by showNfcScreenState
    var hubChatArgs by hubChatArgsState
    var connectionRevealState by connectionRevealStateState
    val screenKey = activeScreenKey
    val addClickBackHost = rememberInteractiveBackHostState()
    val addClickSwipeDragPx = addClickBackHost.dragOffsetPx
    PlatformNativeNavigationBarSwipeReveal(addClickSwipeDragPx)
    var lastAddClickOverlayKey by remember { mutableStateOf<String?>(null) }
    var addClickOverlayTransitionMode by remember {
        mutableStateOf(NavigationTransitionMode.Tap)
    }
    LaunchedEffect(addClickOverlayKey) {
        if (addClickOverlayKey != null) {
            lastAddClickOverlayKey = addClickOverlayKey
        } else {
            addClickBackHost.reset()
        }
    }

    LaunchedEffect(addClickOverlayKey, addClickOverlayTransitionMode) {
        if (addClickOverlayKey == null &&
            addClickOverlayTransitionMode == NavigationTransitionMode.GestureBack
        ) {
            delay(80)
            addClickOverlayTransitionMode = NavigationTransitionMode.Tap
        }
    }

    val primaryTabStateHolder = rememberSaveableStateHolder()
    val latestHomeContent =
        rememberUpdatedState<@Composable () -> Unit> {
            HomeScreen(
                homeViewModel = homeViewModel,
                mapViewModel = mapViewModel,
                onNavigateToChat = { connectionId ->
                    pendingChatId = connectionId
                    navigateTo(NavigationItem.Connections.route)
                },
                onOpenSearch = { showUnifiedSearchSheet = true },
                onNavigateToMap = { beaconId ->
                    pendingBeaconId = beaconId
                    navigateTo(NavigationItem.Map.route)
                },
                onNavigateToMapLayer = { filter ->
                    pendingMapLayerFilter = filter
                    navigateTo(NavigationItem.Map.route)
                },
                onNavigateToAddClick = {
                    navigateTo(NavigationItem.AddClick.route)
                },
                onShareBeaconToChats = { beacon, chatIds, openConnectionId ->
                    chatIds.forEach { chatId ->
                        chatViewModel.sendBeaconMessageToChat(chatId, beacon)
                    }
                    if (openConnectionId != null) {
                        pendingChatId = openConnectionId
                        navigateTo(NavigationItem.Connections.route)
                    }
                },
            )
        }
    val movableHomeContent =
        remember {
            movableContentOf {
                primaryTabStateHolder.SaveableStateProvider(NavigationItem.Home.route) {
                    latestHomeContent.value()
                }
            }
        }

    @Composable
    fun renderScreen(
        animatedScreen: String,
        allowInteractiveSwipeBack: Boolean = true,
    ) {
        @Composable
        fun renderPrimaryScreen(route: String) {
            if (route == NavigationItem.Home.route) {
                movableHomeContent()
                return
            }

            primaryTabStateHolder.SaveableStateProvider(route) {
                when (route) {
                    NavigationItem.Home.route -> Unit

                    NavigationItem.AddClick.route ->
                        AddClickScreen(
                            currentUserId = currentUser.id,
                            currentUsername = currentUser.name,
                            locationService = locationService,
                            onNavigateToNfc = { showNfcScreen = true },
                            onShowMyQRCode = { showMyQRCode = true },
                            onScanQRCode = { showQRScanner = true },
                            onJoinCommunityHub = { hubId ->
                                launchCommunityHubJoin(hubId, null)
                            },
                            onCommunityHubCreated = { hubId ->
                                launchCommunityHubJoin(hubId, currentUser.id)
                            },
                            onHubCreateError = { msg ->
                                toastState.show(connectionScope, msg)
                            },
                            onStartChatting = { navigateTo(NavigationItem.Connections.route) },
                        )

                    NavigationItem.Connections.route -> {
                        val userId =
                            when (val state = authViewModel.authState) {
                                is AuthState.Success -> state.userId
                                else -> ""
                            }
                        if (userId.isNotEmpty()) {
                            ConnectionsScreen(
                                userId = userId,
                                searchQuery = "",
                                onOpenSearch = { showUnifiedSearchSheet = true },
                                initialChatId = pendingChatId,
                                initialTargetMessageId = pendingTargetMessageId,
                                onChatDismissed = {
                                    pendingChatId = null
                                    pendingTargetMessageId = null
                                },
                                onChatOpenStateChanged = { isOpen ->
                                    isConnectionsChatOpen = isOpen
                                    if (!isOpen) {
                                        pendingChatId = null
                                        pendingTargetMessageId = null
                                        connectionsChatSuppressesTabBar = false
                                    }
                                },
                                onChatSuppressesTabBarChanged = { suppresses ->
                                    connectionsChatSuppressesTabBar = suppresses
                                },
                                onNavigateToLocationSettings = {
                                    navigateTo(
                                        NavigationItem.Settings.route,
                                    )
                                },
                                onHubSelected = { hub ->
                                    hubChatArgs =
                                        HubChatNavArgs(
                                            hubId = hub.hubId,
                                            realtimeChannel = hub.realtimeChannel,
                                            hubTitle = hub.name,
                                            creatorId = hub.creatorId,
                                            hubCategory = hub.category,
                                        )
                                },
                                viewModel = chatViewModel,
                                verifiedCliqueProximityAutofill = verifiedCliqueProximityAutofillIntent,
                                onVerifiedCliqueProximityAutofillConsumed = {
                                    verifiedCliqueProximityAutofillIntent = null
                                },
                                onOpenDisposableRoll = { cid ->
                                    openConnectionDisposableRoll(cid)
                                },
                                onOpenDisposableRollForChat = { chatId ->
                                    openChatDisposableRoll(chatId)
                                },
                                shareableBeacons = shareableMapBeacons,
                                mapViewModel = mapViewModel,
                                onShareBeaconToChats = { beacon, chatIds, openConnectionId ->
                                    chatIds.forEach { chatId ->
                                        chatViewModel.sendBeaconMessageToChat(chatId, beacon)
                                    }
                                    if (openConnectionId != null) {
                                        pendingChatId = openConnectionId
                                    }
                                },
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Please log in to view connections")
                            }
                        }
                    }

                    NavigationItem.Map.route ->
                        MapScreen(
                            viewModel = mapViewModel,
                            onNavigateToChat = { connectionId ->
                                pendingChatId = connectionId
                                navigateTo(NavigationItem.Connections.route)
                            },
                            onShareBeaconToChats = { beacon, chatIds, openConnectionId ->
                                chatIds.forEach { chatId ->
                                    chatViewModel.sendBeaconMessageToChat(chatId, beacon)
                                }
                                if (openConnectionId != null) {
                                    pendingChatId = openConnectionId
                                    navigateTo(NavigationItem.Connections.route)
                                }
                            },
                            initialBeaconId = pendingBeaconId,
                            onBeaconFocusConsumed = { pendingBeaconId = null },
                            initialLayerFilter = pendingMapLayerFilter,
                            onLayerFilterConsumed = { pendingMapLayerFilter = null },
                            onJoinCommunityHub = { hubId ->
                                launchCommunityHubJoin(hubId, null)
                            },
                            eventsSheetExpanded = eventsSheetExpanded,
                            onEventsSheetExpandedChanged = { eventsSheetExpanded = it },
                            onOpenSearch = { showUnifiedSearchSheet = true },
                            onOpenDisposableRoll = { cid ->
                                openConnectionDisposableRoll(cid)
                            },
                        )

                    NavigationItem.Settings.route ->
                        SettingsScreen(
                            isDarkMode = isDarkMode,
                            onOpenSearch = { showUnifiedSearchSheet = true },
                            onSubpageOpenChanged = { isSettingsSubpageOpen = it },
                            onToggleDarkMode = {
                                val next = !isDarkMode
                                isDarkMode = next
                                appScope.launch {
                                    tokenStorage.saveDarkModeEnabled(next)
                                }
                            },
                            onSignOut = { authViewModel.signOut() },
                            mapViewModel = mapViewModel,
                            onShareBeaconToChats = { beacon, chatIds, openConnectionId ->
                                chatIds.forEach { chatId ->
                                    chatViewModel.sendBeaconMessageToChat(chatId, beacon)
                                }
                                if (openConnectionId != null) {
                                    pendingChatId = openConnectionId
                                    navigateTo(NavigationItem.Connections.route)
                                }
                            },
                        )
                }
            }
        }

        val previousKey = NavigationItem.Home.route
        val interactivePrimary =
            allowInteractiveSwipeBack &&
                isIOS &&
                isPrimaryNavRoute(animatedScreen) &&
                animatedScreen != NavigationItem.Connections.route &&
                previousKey != animatedScreen &&
                !(animatedScreen == NavigationItem.Connections.route && isConnectionsChatOpen)

        if (interactivePrimary) {
            InteractiveSwipeBackContainer(
                enabled =
                    !(
                        animatedScreen == NavigationItem.Settings.route &&
                            isSettingsSubpageOpen
                    ),
                edgeSwipeWidth = 44.dp,
                onBack = { navigatePrimaryRouteBackHome(NavigationTransitionMode.GestureBack) },
                previousContent = {
                    // Exactly one slot owns movable Home. On commit currentRoute
                    // flips before AnimatedContent disposes this outgoing route,
                    // so relinquish the underlay in that same recomposition.
                    // Never bind the shared tab UINavigationBar from this underlay —
                    // Map has no tab header, and a stale Add Click title would paint
                    // over the map the moment the back gesture starts.
                    CompositionLocalProvider(
                        LocalNativeChromeActive provides false,
                    ) {
                        if (shouldRenderHomeSwipeUnderlay(currentRoute)) {
                            renderPrimaryScreen(previousKey)
                        }
                    }
                },
                currentContent = { renderPrimaryScreen(animatedScreen) },
            )
        } else {
            renderPrimaryScreen(animatedScreen)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .interactiveSwipeBackUnderlay(addClickBackHost),
        ) {
            AnimatedContent(
                // Primary tabs (Home/AddClick/Connections/Map/Settings) all go through
                // this AnimatedContent with the 280ms crossfade. Do not reintroduce a
                // Home-underlay + Map overlay shell — it flashed Home on Map open and
                // broke Connections tab motion.
                targetState = screenKey,
                transitionSpec = {
                    if (transitionMode == NavigationTransitionMode.GestureBack) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (reduceMotion) {
                        fadeIn(animationSpec = tween(120))
                            .togetherWith(fadeOut(animationSpec = tween(90)))
                    } else {
                        val routeOrder =
                            listOf(
                                NavigationItem.Home.route,
                                NavigationItem.AddClick.route,
                                NavigationItem.Connections.route,
                                NavigationItem.Map.route,
                                NavigationItem.Settings.route,
                            )

                        val initialIndex =
                            routeOrder.indexOf(initialState).let {
                                if (it >=
                                    0
                                ) {
                                    it
                                } else {
                                    0
                                }
                            }
                        val targetIndex =
                            routeOrder.indexOf(targetState).let {
                                if (it >=
                                    0
                                ) {
                                    it
                                } else {
                                    0
                                }
                            }
                        val movingForward = targetIndex >= initialIndex

                        val primaryTabs =
                            setOf(
                                NavigationItem.Home.route,
                                NavigationItem.AddClick.route,
                                NavigationItem.Connections.route,
                                NavigationItem.Map.route,
                                NavigationItem.Settings.route,
                            )
                        val isPrimaryTabCrossfade =
                            initialState in primaryTabs && targetState in primaryTabs

                        val slideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                        val fadeSpec = tween<Float>(320, easing = LinearOutSlowInEasing)
                        val crossfadeSpec = tween<Float>(280, easing = FastOutSlowInEasing)

                        if (isPrimaryTabCrossfade) {
                            fadeIn(animationSpec = crossfadeSpec)
                                .togetherWith(fadeOut(animationSpec = crossfadeSpec))
                        } else if (movingForward) {
                            (
                                slideInHorizontally(
                                    animationSpec = slideSpec,
                                    initialOffsetX = { it },
                                ) +
                                    fadeIn(animationSpec = fadeSpec)
                            ).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = slideSpec,
                                    targetOffsetX = { -it },
                                ) +
                                    fadeOut(animationSpec = fadeSpec),
                            ).using(SizeTransform(clip = true))
                        } else {
                            (
                                slideInHorizontally(
                                    animationSpec = slideSpec,
                                    initialOffsetX = { -it },
                                ) +
                                    fadeIn(animationSpec = fadeSpec)
                            ).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = slideSpec,
                                    targetOffsetX = { it },
                                ) +
                                    fadeOut(animationSpec = fadeSpec),
                            ).using(SizeTransform(clip = true))
                        }
                    }
                },
                label = "app_screen_transition",
            ) { animatedScreen ->
                CompositionLocalProvider(
                    LocalNativeChromeActive provides
                        (animatedScreen == screenKey),
                ) {
                    renderScreen(animatedScreen)
                }
            }
        }

        if (addClickOverlayKey != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }

        val addClickSlideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
        val addClickFadeSpec = tween<Float>(220, easing = LinearOutSlowInEasing)
        AnimatedVisibility(
            visible = addClickOverlayKey != null,
            modifier = Modifier.fillMaxSize(),
            enter =
                if (reduceMotion) {
                    fadeIn(animationSpec = tween(120))
                } else {
                    slideInHorizontally(animationSpec = addClickSlideSpec, initialOffsetX = { it }) +
                        fadeIn(animationSpec = addClickFadeSpec)
                },
            exit =
                if (addClickOverlayTransitionMode == NavigationTransitionMode.GestureBack) {
                    ExitTransition.None
                } else if (reduceMotion) {
                    fadeOut(animationSpec = tween(90))
                } else {
                    slideOutHorizontally(animationSpec = addClickSlideSpec, targetOffsetX = { it }) +
                        fadeOut(animationSpec = addClickFadeSpec)
                },
            label = "add_click_overlay",
        ) {
            val overlayKey = lastAddClickOverlayKey
            if (overlayKey != null) {
                fun dismissAddClickOverlay(mode: NavigationTransitionMode) {
                    addClickOverlayTransitionMode = mode
                    transitionMode = mode
                    when (overlayKey) {
                        "my_qr" -> showMyQRCode = false
                        "qr_scanner" -> showQRScanner = false
                        "nfc" -> showNfcScreen = false
                    }
                }
                InteractiveSwipeBackContainer(
                    enabled = isIOS,
                    edgeSwipeWidth = 44.dp,
                    onBack = { dismissAddClickOverlay(NavigationTransitionMode.GestureBack) },
                    opaquePreviousBackground = false,
                    externalDragOffsetPx = addClickSwipeDragPx,
                    onBehindLayersVisibleChanged = {
                        addClickBackHost.behindLayersVisible = it
                    },
                    previousContent = {},
                    currentContent = {
                        when (overlayKey) {
                            "my_qr" ->
                                MyQRCodeScreen(
                                    userId = currentUser.id,
                                    username = currentUser.name,
                                    locationService = locationService,
                                    onNavigateBack = {
                                        dismissAddClickOverlay(NavigationTransitionMode.Tap)
                                    },
                                )
                            "qr_scanner" ->
                                QRScannerScreen(
                                    onQRCodeScanned = { userId ->
                                        showQRScanner = false
                                        if (userId.isNotEmpty() && currentUser.id.isNotEmpty()) {
                                            connectionViewModel.presentQrContextSheetFromScan(
                                                scannedUserId = userId,
                                                qrToken = null,
                                                venueId = null,
                                            )
                                        }
                                    },
                                    onQRCodeScannedWithToken = { userId, qrToken, venueId ->
                                        showQRScanner = false
                                        if (userId.isNotEmpty() && currentUser.id.isNotEmpty()) {
                                            connectionViewModel.presentQrContextSheetFromScan(
                                                scannedUserId = userId,
                                                qrToken = qrToken,
                                                venueId = venueId?.takeIf { it.isNotBlank() },
                                            )
                                        }
                                    },
                                    onCommunityHubScanned = { hubId ->
                                        showQRScanner = false
                                        launchCommunityHubJoin(hubId, null)
                                    },
                                    onNavigateBack = {
                                        dismissAddClickOverlay(NavigationTransitionMode.Tap)
                                    },
                                )
                            "nfc" -> {
                                val userId =
                                    when (val state = authViewModel.authState) {
                                        is AuthState.Success -> state.userId
                                        else -> ""
                                    }
                                val authToken by produceState(initialValue = "") {
                                    value = tokenStorage.getJwt() ?: ""
                                }
                                val proximityManager = rememberProximityManager()
                                NfcScreen(
                                    userId = userId,
                                    authToken = authToken,
                                    httpClient = client,
                                    proximityManager = proximityManager,
                                    connectionViewModel = connectionViewModel,
                                    onConnectionCreated = {
                                        connectionViewModel.resetConnectionState()
                                        showNfcScreen = false
                                        navigateTo(NavigationItem.Connections.route)
                                    },
                                    onBackPressed = {
                                        dismissAddClickOverlay(NavigationTransitionMode.Tap)
                                    },
                                    onProximityFinalizeStart = {
                                        connectionRevealState =
                                            ConnectionRevealUiState(
                                                methodLabel = "Tap",
                                                phase = ConnectionRevealPhase.Connecting,
                                            )
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
