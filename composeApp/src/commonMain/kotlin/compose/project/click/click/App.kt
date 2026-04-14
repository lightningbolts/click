package compose.project.click.click

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.navigation.NavigationItem
import compose.project.click.click.navigation.bottomNavItems
import compose.project.click.click.ui.components.PlatformBottomBar
import compose.project.click.click.calls.ActiveCallOverlay
import compose.project.click.click.calls.CallOverlayState
import compose.project.click.click.calls.CallPreviewOverlay
import compose.project.click.click.calls.CallSessionManager
import compose.project.click.click.calls.CallState
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.ContextTag
import compose.project.click.click.data.models.HeightCategory
import compose.project.click.click.data.models.NoiseLevelCategory
import compose.project.click.click.data.models.ONBOARDING_FLOW_VERSION_COMPLETE
import compose.project.click.click.data.models.OnboardingState
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.isPendingSync
import compose.project.click.click.ui.components.ConnectionRevealOverlay
import compose.project.click.click.ui.components.ConnectionRevealPhase
import compose.project.click.click.ui.components.ConnectionRevealUiState
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer
import compose.project.click.click.ui.components.ConnectionContextSheet
import compose.project.click.click.ui.screens.*
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.utils.rememberLocationPermissionRequester
import compose.project.click.click.ui.utils.rememberMicrophonePermissionRequester
import compose.project.click.click.viewmodel.AuthViewModel
import compose.project.click.click.viewmodel.AuthState
import compose.project.click.click.viewmodel.ChatViewModel
import compose.project.click.click.viewmodel.MapViewModel
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.proximity.rememberProximityManager
import compose.project.click.click.notifications.ChatDeepLinkManager
import compose.project.click.click.sensors.AmbientNoiseMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.captureConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.sensors.ConnectionSensorMonitorsProvider // pragma: allowlist secret
import compose.project.click.click.sensors.rememberAmbientNoiseMonitor
import compose.project.click.click.sensors.rememberBarometricHeightMonitor
import compose.project.click.click.sensors.HardwareVibeMonitor
import compose.project.click.click.data.OpenMeteoWeatherService
import compose.project.click.click.data.models.toConnectionPayloadWeatherJson
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.WindowInsets
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

import compose.project.click.click.viewmodel.ConnectionViewModel
import compose.project.click.click.viewmodel.ConnectionState
import compose.project.click.click.viewmodel.VerifiedCliqueProximityIntent
import compose.project.click.click.data.hub.HubConnectionManager
import compose.project.click.click.data.hub.HubVerifyResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    // Default to dark until persisted preference is loaded.
    var isDarkMode by remember { mutableStateOf(true) }

    // Ktor client
    val client = remember {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    // Auth ViewModel with TokenStorage
    val tokenStorage = remember { createTokenStorage() }
    val ambientNoiseMonitor = rememberAmbientNoiseMonitor()
    val barometricHeightMonitor = rememberBarometricHeightMonitor()
    val appScope = rememberCoroutineScope()
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(tokenStorage = tokenStorage) }
    val connectionViewModel: ConnectionViewModel = viewModel { ConnectionViewModel() }
    val openMeteoWeather = remember { OpenMeteoWeatherService() }

    // Location service for capturing GPS during QR scans
    val locationService = remember { compose.project.click.click.utils.LocationService() }
    val requestLocationPermissionThen = rememberLocationPermissionRequester()
    val requestMicrophonePermissionThen = rememberMicrophonePermissionRequester()
    val onboardingJson = remember { Json { ignoreUnknownKeys = true } }

    val currentUser = when (val state = authViewModel.authState) {
        is AuthState.Success -> User(id = state.userId, name = state.name ?: state.email, createdAt = 0L)
        else -> User(id = "", name = "", createdAt = 0L)
    }

    LaunchedEffect(connectionViewModel, currentUser.id) {
        if (currentUser.id.isBlank()) return@LaunchedEffect
        AppDataManager.proximityHandshakeRecovered.collect { payload ->
            connectionViewModel.onProximityHandshakeRecoveredFromBackground(payload, currentUser.id)
        }
    }

    var ambientNoiseOptIn by remember { mutableStateOf(true) }
    var barometricContextOptIn by remember { mutableStateOf(true) }
    var onboardingState by remember { mutableStateOf<OnboardingState?>(null) }
    var hasCompletedOnboarding by remember { mutableStateOf<Boolean?>(null) }
    /** False until `user_interests` has been checked for this session (fresh install / login). */
    var interestsRemoteResolved by remember { mutableStateOf(false) }
    var isCompletingPermissions by remember { mutableStateOf(false) }

    val notificationPreferences by AppDataManager.notificationPreferences.collectAsState()
    val locationPreferences by AppDataManager.locationPreferences.collectAsState()
    val pendingConnectionsCount by AppDataManager.pendingConnectionsCount.collectAsState()
    val isInitialLoading by AppDataManager.isLoading.collectAsState()
    val appError by AppDataManager.error.collectAsState()

    LaunchedEffect(Unit) {
        val persisted = tokenStorage.getDarkModeEnabled()
        if (persisted != null) {
            isDarkMode = persisted
        }
        ambientNoiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
        barometricContextOptIn = tokenStorage.getBarometricContextOptIn() ?: true
    }

    suspend fun persistOnboardingState(state: OnboardingState) {
        onboardingState = state
        tokenStorage.saveOnboardingState(onboardingJson.encodeToString(state))
    }

    suspend fun requestLocationPermissionIfNeeded(shouldRequest: Boolean) {
        if (!shouldRequest) return
        suspendCancellableCoroutine<Unit> { continuation ->
            requestLocationPermissionThen {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    LaunchedEffect(authViewModel.isAuthenticated, currentUser.id) {
        if (!authViewModel.isAuthenticated || currentUser.id.isBlank()) {
            onboardingState = null
            hasCompletedOnboarding = null
            interestsRemoteResolved = true
            return@LaunchedEffect
        }

        interestsRemoteResolved = false

        val persistedHasCompletedOnboarding = tokenStorage.getHasCompletedOnboarding()

        val savedState = tokenStorage.getOnboardingState()
            ?.let { serialized ->
                runCatching { onboardingJson.decodeFromString<OnboardingState>(serialized) }.getOrNull()
            }

        val effectiveHasCompletedOnboarding =
            persistedHasCompletedOnboarding ?: savedState?.permissionsCompleted ?: false
        hasCompletedOnboarding = effectiveHasCompletedOnboarding
        if (persistedHasCompletedOnboarding == null && savedState?.permissionsCompleted == true) {
            tokenStorage.saveHasCompletedOnboarding(true)
        }

        val normalizedSavedState = savedState?.let { state ->
            if (effectiveHasCompletedOnboarding && !state.permissionsCompleted) {
                state.copy(permissionsCompleted = true)
            } else {
                state
            }
        }

        when {
            normalizedSavedState != null && normalizedSavedState.isComplete -> {
                onboardingState = normalizedSavedState
            }
            normalizedSavedState != null -> {
                onboardingState = normalizedSavedState
            }
            else -> {
                val shell = OnboardingState(flowVersion = 0)
                onboardingState = shell
                tokenStorage.saveOnboardingState(onboardingJson.encodeToString(shell))
            }
        }

        val supabaseRepo = compose.project.click.click.data.repository.SupabaseRepository()
        supabaseRepo.fetchUserInterests(currentUser.id).fold(
            onSuccess = { row ->
                if (row != null) {
                    val base = (onboardingState ?: OnboardingState()).let { state ->
                        if (hasCompletedOnboarding == true && !state.permissionsCompleted) {
                            state.copy(permissionsCompleted = true)
                        } else {
                            state
                        }
                    }
                    val permissionsCompleted = hasCompletedOnboarding == true || base.permissionsCompleted
                    val merged = base.copy(
                        interestsCompleted = true,
                        flowVersion = if (permissionsCompleted) ONBOARDING_FLOW_VERSION_COMPLETE else base.flowVersion,
                        completedAt = base.completedAt ?: if (permissionsCompleted) {
                            kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        } else {
                            null
                        },
                    )
                    onboardingState = merged
                    tokenStorage.saveTagsInitialized(true)
                    tokenStorage.saveOnboardingState(onboardingJson.encodeToString(merged))
                }
            },
            onFailure = { err ->
                println("App: user_interests fetch failed, using local onboarding only: ${err.message}")
            },
        )

        interestsRemoteResolved = true
    }

    // Coroutine scope for location-aware connection
    val connectionScope = rememberCoroutineScope()

    fun hasUsableLocation(location: compose.project.click.click.utils.LocationResult?): Boolean {
        return location != null &&
            location.latitude.isFinite() &&
            location.longitude.isFinite() &&
            !(location.latitude == 0.0 && location.longitude == 0.0)
    }

    suspend fun resolveConnectionLocation(
        initialLocation: compose.project.click.click.utils.LocationResult? = null,
    ): compose.project.click.click.utils.LocationResult? {
        if (hasUsableLocation(initialLocation)) return initialLocation

        return try {
            val refreshed = locationService.getHighAccuracyLocation(4000L)
            if (hasUsableLocation(refreshed)) refreshed else initialLocation.takeIf(::hasUsableLocation)
        } catch (e: Exception) {
            println("App: Failed to get high-accuracy location: ${e.message}")
            initialLocation.takeIf(::hasUsableLocation)
        }
    }

    fun connectWithUser(
        userId: String,
        qrToken: String? = null,
        tokenAgeMs: Long? = null,
        venueId: String? = null,
        contextTagObject: ContextTag? = null,
        capturedLocation: compose.project.click.click.utils.LocationResult? = null,
        heightCategory: HeightCategory? = null,
        exactBarometricElevationMeters: Double? = null,
        exactBarometricPressureHpa: Double? = null,
        noiseLevelCategory: NoiseLevelCategory? = null,
        exactNoiseLevelDb: Double? = null,
        hardwareVibeOverride: compose.project.click.click.sensors.HardwareVibeSnapshot? = null,
        weatherSnapshotLabel: String? = null,
    ) {
        if (currentUser.id.isNotEmpty()) {
            connectionScope.launch {
                // Venue-bound QR: never use device GPS; backend maps the venue.
                val location = if (!venueId.isNullOrBlank()) {
                    null
                } else if (AppDataManager.shouldCaptureLocationAtTap()) {
                    resolveConnectionLocation(capturedLocation)
                } else {
                    null
                }
                connectionViewModel.connectWithUser(
                    scannedUserId = userId,
                    currentUserId = currentUser.id,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    venueId = venueId?.takeIf { it.isNotBlank() },
                    altitudeMeters = location?.altitudeMeters,
                    heightCategory = heightCategory,
                    exactBarometricElevationMeters = exactBarometricElevationMeters,
                    exactBarometricPressureHpa = exactBarometricPressureHpa,
                    contextTagObject = contextTagObject,
                    connectionMethod = "qr",
                    tokenAgeMs = tokenAgeMs,
                    qrToken = qrToken,
                    noiseLevelCategory = noiseLevelCategory,
                    exactNoiseLevelDb = exactNoiseLevelDb,
                    hardwareVibeOverride = hardwareVibeOverride,
                    weatherSnapshotLabel = weatherSnapshotLabel,
                )
            }
        }
    }

    // Navigation / connection flow state
    var showMyQRCode by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    var connectionRevealState by remember { mutableStateOf<ConnectionRevealUiState?>(null) }

    LaunchedEffect(Unit) {
        launch {
            connectionViewModel.transientNotice.collect { message ->
                if (message == ConnectionViewModel.RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE) {
                    connectionRevealState = null
                }
            }
        }
    }

    val isIOS = remember {
        getPlatform().name.contains("iOS", ignoreCase = true)
    }



    var showSignUp by remember { mutableStateOf(false) }

    val scheme = if (isDarkMode) {
        darkColorScheme(
            primary = PrimaryBlue,
            secondary = AccentBlue,
            background = BackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            primaryContainer = DeepBlue,
            onPrimaryContainer = NeonPurple,
            surfaceVariant = Color(0xFF2C2C2C)
        )
    } else {
        lightColorScheme(
            primary = PrimaryBlue,
            secondary = AccentBlue,
            background = BackgroundLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            primaryContainer = SoftBlue,
            onPrimaryContainer = DeepBlue,
            surfaceVariant = Color(0xFFE0E0E0)
        )
    }

    MaterialTheme(colorScheme = scheme) {
        compose.project.click.click.ui.theme.PlatformThemeProvider {
        ConnectionSensorMonitorsProvider(
            ambientNoiseMonitor = ambientNoiseMonitor,
            barometricHeightMonitor = barometricHeightMonitor,
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) BackgroundDark else BackgroundLight)
                .let { modifier ->
                    if (isDarkMode) {
                        modifier.background(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    PrimaryBlue.copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(0f, 0f),
                                radius = 1000f
                            )
                        )
                    } else modifier
                }
        ) {
        // Show login/signup screens when not authenticated
        if (authViewModel.authState is AuthState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AdaptiveCircularProgressIndicator(color = PrimaryBlue)
            }
        } else if (!authViewModel.isAuthenticated) {
            if (showSignUp) {
                SignUpScreen(
                    onSignUpSuccess = {
                        // Success is handled by state change in viewModel
                    },
                    onLoginClick = {
                        showSignUp = false
                        authViewModel.resetAuthState()
                    },
                    onEmailSignUp = { firstName, lastName, birthdayIso, email, password, avatarBytes, avatarMime ->
                        authViewModel.signUpWithEmail(
                            firstName,
                            lastName,
                            birthdayIso,
                            email,
                            password,
                            avatarBytes,
                            avatarMime,
                        )
                    },
                    isLoading = authViewModel.authState is AuthState.Loading,
                    errorMessage = if (authViewModel.authState is AuthState.Error) {
                        (authViewModel.authState as AuthState.Error).message
                    } else null
                )
            } else {
                LoginScreen(
                    onLoginSuccess = {
                        // Success is handled by state change in viewModel
                    },
                    onSignUpClick = {
                        showSignUp = true
                        authViewModel.resetAuthState()
                    },
                    onEmailSignIn = { email, password ->
                        authViewModel.signInWithEmail(email, password)
                    },
                    isLoading = authViewModel.authState is AuthState.Loading,
                    errorMessage = if (authViewModel.authState is AuthState.Error) {
                        (authViewModel.authState as AuthState.Error).message
                    } else null
                )
            }
        } else {
            val ambientMonitor = AmbientNoiseMonitorProvider.current
            val baroMonitor = BarometricHeightMonitorProvider.current
            // Main app content when authenticated
            // Initialize app data once when authenticated
            LaunchedEffect(Unit) {
                AppDataManager.initializeData()
            }
            val appDataUser by AppDataManager.currentUser.collectAsState()
            val globalCallOverlayState by CallSessionManager.overlayState.collectAsState()
            val globalCallState by CallSessionManager.callState.collectAsState()
            val activeInvite by CallSessionManager.activeInvite.collectAsState()

            LaunchedEffect(appDataUser?.id, appDataUser?.name) {
                CallSessionManager.bindUser(appDataUser?.id, appDataUser?.name)
            }

            val supabaseRepo = remember { compose.project.click.click.data.repository.SupabaseRepository() }
            val onboardingScope = rememberCoroutineScope()
            val onboardingStep = when {
                onboardingState == null || appDataUser == null || !interestsRemoteResolved || hasCompletedOnboarding == null -> "loading"
                hasCompletedOnboarding != true -> "permissions"
                onboardingState?.interestsCompleted != true -> "interests"
                else -> "complete"
            }

            if (onboardingStep == "loading") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AdaptiveCircularProgressIndicator(color = PrimaryBlue)
                }
            } else if (onboardingStep != "complete") {
                val onboardingSnackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    AppDataManager.transientUserMessages.collect { onboardingSnackbarHostState.showSnackbar(it) }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = onboardingStep,
                    transitionSpec = {
                        val slideSpec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                        val fadeSpec = tween<Float>(180, easing = LinearOutSlowInEasing)
                        (slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) +
                            fadeIn(animationSpec = fadeSpec))
                            .togetherWith(
                                slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { -it }) +
                                    fadeOut(animationSpec = fadeSpec)
                            )
                            .using(SizeTransform(clip = true))
                    },
                    label = "onboarding_transition"
                ) { step ->
                    when (step) {
                        "permissions" -> {
                            PermissionsOnboardingScreen(
                                initialConnectionSnapEnabled = locationPreferences.connectionSnapEnabled,
                                initialShowOnMapEnabled = locationPreferences.showOnMapEnabled,
                                initialIncludeInInsightsEnabled = locationPreferences.includeInInsightsEnabled,
                                initialNotificationsEnabled = notificationPreferences.messagePushEnabled || notificationPreferences.callPushEnabled,
                                initialAmbientNoiseEnabled = ambientNoiseOptIn,
                                initialBarometricContextEnabled = barometricContextOptIn,
                                locationService = locationService,
                                ambientNoiseMonitor = ambientNoiseMonitor,
                                requestLocationPermissionThen = requestLocationPermissionThen,
                                requestMicrophonePermissionThen = requestMicrophonePermissionThen,
                                isLoading = isCompletingPermissions,
                                onContinue = { selection ->
                                    onboardingScope.launch {
                                        isCompletingPermissions = true
                                        try {
                                            ambientNoiseOptIn = selection.ambientNoiseEnabled
                                            barometricContextOptIn = selection.barometricContextEnabled
                                            tokenStorage.saveAmbientNoiseOptIn(selection.ambientNoiseEnabled)
                                            tokenStorage.saveBarometricContextOptIn(selection.barometricContextEnabled)
                                            tokenStorage.saveLocationExplainerSeen(true)

                                            AppDataManager.updateLocationPreferences(
                                                locationPreferences.copy(
                                                    connectionSnapEnabled = selection.connectionSnapEnabled,
                                                    showOnMapEnabled = selection.showOnMapEnabled,
                                                    includeInInsightsEnabled = selection.includeInInsightsEnabled
                                                )
                                            )
                                            AppDataManager.setMessageNotificationsEnabled(selection.notificationsEnabled)
                                            AppDataManager.setCallNotificationsEnabled(selection.notificationsEnabled)
                                            tokenStorage.saveHasCompletedOnboarding(true)
                                            hasCompletedOnboarding = true

                                            val updatedState = (onboardingState ?: OnboardingState()).copy(
                                                permissionsCompleted = true,
                                                locationPermissionRequested = selection.connectionSnapEnabled,
                                                notificationPermissionRequested = selection.notificationsEnabled,
                                                microphonePermissionRequested = selection.ambientNoiseEnabled,
                                                barometricContextPermissionReviewed = selection.barometricContextEnabled,
                                                completedAt = if (onboardingState?.interestsCompleted == true) {
                                                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                                } else {
                                                    null
                                                }
                                            )
                                            persistOnboardingState(updatedState)
                                        } finally {
                                            isCompletingPermissions = false
                                        }
                                    }
                                }
                            )
                        }

                        else -> {
                            InterestTaggingScreen(
                                onTagsSelected = { tags ->
                                    onboardingScope.launch {
                                        val saveResult = supabaseRepo.updateUserInterests(currentUser.id, tags)
                                        if (saveResult.isSuccess) {
                                            tokenStorage.saveTagsInitialized(true)
                                            val base = onboardingState ?: OnboardingState()
                                            persistOnboardingState(
                                                base.copy(
                                                    interestsCompleted = true,
                                                    flowVersion = if (base.permissionsCompleted) {
                                                        ONBOARDING_FLOW_VERSION_COMPLETE
                                                    } else {
                                                        base.flowVersion
                                                    },
                                                    completedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                                ),
                                            )
                                            AppDataManager.refresh(force = true)
                                        } else {
                                            val msg = saveResult.exceptionOrNull()?.message?.trim().orEmpty()
                                                .ifBlank { "Couldn't save interests. Check your connection and try again." }
                                            AppDataManager.postTransientUserMessage(msg)
                                        }
                                    }
                                },
                                canSkip = false
                            )
                        }
                    }
                }
                SnackbarHost(
                    hostState = onboardingSnackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                )
                }
            } else {
            var currentRoute by remember { mutableStateOf("home") }
            var previousRoute by remember { mutableStateOf("home") }
            var transitionMode by remember { mutableStateOf(NavigationTransitionMode.Tap) }
            // Route history stack for back navigation
            val routeHistory = remember { mutableStateListOf("home") }
            var showNfcScreen by remember { mutableStateOf(false) }
            var pendingChatId by remember { mutableStateOf<String?>(null) }
            var isConnectionsChatOpen by remember { mutableStateOf(false) }
            var verifiedCliqueProximityAutofillIntent by remember { mutableStateOf<VerifiedCliqueProximityIntent?>(null) }

            // Helper: navigate to a route, pushing onto history stack
            fun navigateTo(route: String) {
                if (route != currentRoute) {
                    transitionMode = NavigationTransitionMode.Tap
                    previousRoute = currentRoute
                    routeHistory.add(currentRoute)
                    currentRoute = route
                    if (route != NavigationItem.Connections.route) {
                        isConnectionsChatOpen = false
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
                pendingChatId = null
                return true
            }

            val focusManager = LocalFocusManager.current
            val mapViewModel: MapViewModel = viewModel { MapViewModel() }
            val chatViewModel: ChatViewModel = viewModel { ChatViewModel() }
            var hubChatArgs by remember { mutableStateOf<HubChatNavArgs?>(null) }
            var hubVerifyInProgress by remember { mutableStateOf(false) }

            LaunchedEffect(connectionViewModel, currentUser.id) {
                if (currentUser.id.isBlank()) return@LaunchedEffect
                connectionViewModel.verifiedCliqueFromProximity.collect { intent ->
                    verifiedCliqueProximityAutofillIntent = intent
                    navigateTo(NavigationItem.Connections.route)
                    showNfcScreen = false
                    showQRScanner = false
                    showMyQRCode = false
                    hubChatArgs = null
                    connectionRevealState = null
                    pendingChatId = null
                }
            }
            val activeScreenKey = when {
                hubChatArgs != null -> "hub_chat"
                showMyQRCode -> "my_qr"
                showQRScanner -> "qr_scanner"
                showNfcScreen -> "nfc"
                currentRoute == "search" -> "search"
                else -> currentRoute
            }
            val canSwipeBackMainRoute = isIOS &&
                isPrimaryNavRoute(currentRoute) &&
                currentRoute != NavigationItem.Home.route &&
                hubChatArgs == null &&
                !showMyQRCode &&
                !showQRScanner &&
                !showNfcScreen &&
                !isConnectionsChatOpen
            val iOSSwipeOwnsBack = isIOS && (
                isSwipeBackScreen(activeScreenKey) ||
                    (currentRoute == NavigationItem.Connections.route && isConnectionsChatOpen) ||
                    canSwipeBackMainRoute
                )

            LaunchedEffect(currentUser.id) {
                if (currentUser.id.isNotEmpty()) {
                    chatViewModel.setCurrentUser(currentUser.id)
                }
            }

            val deepLinkConnectionId by ChatDeepLinkManager.pendingConnectionId.collectAsState()
            LaunchedEffect(deepLinkConnectionId) {
                val connId = deepLinkConnectionId
                if (!connId.isNullOrBlank()) {
                    ChatDeepLinkManager.consume()
                    pendingChatId = connId
                    navigateTo(NavigationItem.Connections.route)
                }
            }

            val pendingCommunityHubId by ChatDeepLinkManager.pendingCommunityHubId.collectAsState()

            // Snackbar for connection success/error feedback
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(connectionViewModel) {
                connectionViewModel.transientNotice.collect { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }

            LaunchedEffect(Unit) {
                AppDataManager.transientUserMessages.collect { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }

            fun launchCommunityHubJoin(hubId: String) {
                if (hubId.isBlank() || currentUser.id.isBlank()) return
                connectionScope.launch {
                    hubVerifyInProgress = true
                    try {
                        requestLocationPermissionIfNeeded(
                            shouldRequest = !locationService.hasLocationPermission()
                        )
                        if (!locationService.hasLocationPermission()) {
                            snackbarHostState.showSnackbar(
                                "Location permission is required to join this hub."
                            )
                            return@launch
                        }
                        val loc = resolveConnectionLocation()
                        if (loc == null) {
                            snackbarHostState.showSnackbar(
                                "Could not read your location. Try again in an open area."
                            )
                            return@launch
                        }
                        val jwt = tokenStorage.getJwt()
                        if (jwt.isNullOrBlank()) {
                            snackbarHostState.showSnackbar(
                                "Please sign in again to join the hub."
                            )
                            return@launch
                        }
                        when (
                            val outcome = HubConnectionManager.verifyProximity(
                                httpClient = client,
                                hubId = hubId,
                                userLat = loc.latitude,
                                userLong = loc.longitude,
                                bearerJwt = jwt,
                            )
                        ) {
                            is HubVerifyResult.Success -> {
                                hubChatArgs = HubChatNavArgs(
                                    hubId = outcome.hubId,
                                    realtimeChannel = outcome.channel,
                                    hubTitle = outcome.name,
                                )
                            }
                            is HubVerifyResult.Failure -> {
                                snackbarHostState.showSnackbar(outcome.userMessage)
                            }
                        }
                    } finally {
                        hubVerifyInProgress = false
                    }
                }
            }

            LaunchedEffect(pendingCommunityHubId, currentUser.id) {
                val hid = pendingCommunityHubId ?: return@LaunchedEffect
                if (currentUser.id.isBlank()) return@LaunchedEffect
                ChatDeepLinkManager.consumeCommunityHub()
                launchCommunityHubJoin(hid)
            }
            val connectionState by connectionViewModel.connectionState.collectAsState()
            val suppressConnectionContextSheet =
                when (connectionRevealState?.phase) {
                    ConnectionRevealPhase.Connecting,
                    ConnectionRevealPhase.Success,
                    -> true
                    else -> false
                }
            LaunchedEffect(connectionState, showNfcScreen) {
                when (val state = connectionState) {
                    is ConnectionState.Success ->  {
                        if (state.connection.isPendingSync()) {
                            connectionRevealState = null
                            snackbarHostState.showSnackbar("Connection saved offline. It will sync automatically when you're back online.")
                            navigateTo(NavigationItem.Connections.route)
                        } else if (connectionRevealState != null) {
                            connectionRevealState = connectionRevealState?.copy(
                                phase = ConnectionRevealPhase.Success,
                                connectedName = state.connectedUser.name
                            )
                            delay(900)
                            navigateTo(NavigationItem.Connections.route)
                            connectionRevealState = null
                        } else {
                            snackbarHostState.showSnackbar("Connected with ${state.connectedUser.name ?: "user"}!")
                        }
                        connectionViewModel.resetConnectionState()
                    }
                    is ConnectionState.Error -> {
                        connectionRevealState = null
                        snackbarHostState.showSnackbar(state.message)
                        connectionViewModel.resetConnectionState()
                    }
                    else -> {}
                }
            }

            // Platform back handler — intercepts Android back gesture/button
            compose.project.click.click.ui.components.PlatformBackHandler(
                enabled = (
                    hubChatArgs != null ||
                        showMyQRCode ||
                        showQRScanner ||
                        showNfcScreen ||
                        (connectionState is ConnectionState.TaggingContext && !showNfcScreen) ||
                        (connectionState is ConnectionState.QrAwaitingContext && !showNfcScreen) ||
                        currentRoute != "home"
                    ) && !iOSSwipeOwnsBack
            ) {
                when {
                    hubChatArgs != null -> hubChatArgs = null
                    showMyQRCode -> showMyQRCode = false
                    showQRScanner -> showQRScanner = false
                    showNfcScreen -> showNfcScreen = false
                    connectionState is ConnectionState.TaggingContext && !showNfcScreen ->
                        connectionViewModel.resetConnectionState()
                    connectionState is ConnectionState.QrAwaitingContext && !showNfcScreen ->
                        connectionViewModel.resetConnectionState()
                    pendingChatId != null -> pendingChatId = null // close open chat first
                    else -> navigateBack()
                }
            }

            // Wrap Scaffold in a Box to allow search overlay to be positioned at true screen bottom
            Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    PlatformBottomBar(
                        items = bottomNavItems + NavigationItem.Search,
                        currentRoute = currentRoute,
                        onItemSelected = { item ->
                            navigateTo(item.route)
                            hubChatArgs = null
                            showMyQRCode = false
                            showQRScanner = false
                            showNfcScreen = false
                            focusManager.clearFocus()
                        }
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val screenKey = activeScreenKey
                        val swipeBackEnabled = isIOS && isSwipeBackScreen(screenKey)

                        LaunchedEffect(screenKey) {
                            if (transitionMode == NavigationTransitionMode.GestureBack) {
                                transitionMode = NavigationTransitionMode.Tap
                            }
                        }

                        @Composable
                        fun renderScreen(animatedScreen: String, allowInteractiveSwipeBack: Boolean = true) {
                            @Composable
                            fun renderPrimaryScreen(route: String) {
                                when (route) {
                                    NavigationItem.Home.route -> HomeScreen(
                                        onNavigateToChat = { connectionId ->
                                            pendingChatId = connectionId
                                            navigateTo(NavigationItem.Connections.route)
                                        }
                                    )

                                    NavigationItem.AddClick.route -> AddClickScreen(
                                        currentUserId = currentUser.id,
                                        currentUsername = currentUser.name,
                                        onNavigateToNfc = { showNfcScreen = true },
                                        onShowMyQRCode = { showMyQRCode = true },
                                        onScanQRCode = { showQRScanner = true },
                                        onJoinCommunityHub = { hubId ->
                                            launchCommunityHubJoin(hubId)
                                        },
                                        onStartChatting = { navigateTo(NavigationItem.Connections.route) }
                                    )

                                    NavigationItem.Connections.route -> {
                                        val userId = when (val state = authViewModel.authState) {
                                            is AuthState.Success -> state.userId
                                            else -> ""
                                        }
                                        if (userId.isNotEmpty()) {
                                            ConnectionsScreen(
                                                userId = userId,
                                                searchQuery = "",
                                                initialChatId = pendingChatId,
                                                onChatDismissed = { pendingChatId = null },
                                                onChatOpenStateChanged = { isOpen ->
                                                    isConnectionsChatOpen = isOpen
                                                    if (!isOpen) {
                                                        pendingChatId = null
                                                    }
                                                },
                                                onNavigateToLocationSettings = { navigateTo(NavigationItem.Settings.route) },
                                                viewModel = chatViewModel,
                                                verifiedCliqueProximityAutofill = verifiedCliqueProximityAutofillIntent,
                                                onVerifiedCliqueProximityAutofillConsumed = {
                                                    verifiedCliqueProximityAutofillIntent = null
                                                },
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Please log in to view connections")
                                            }
                                        }
                                    }

                                    NavigationItem.Map.route -> MapScreen(
                                        viewModel = mapViewModel,
                                        onNavigateToChat = { connectionId ->
                                            pendingChatId = connectionId
                                            navigateTo(NavigationItem.Connections.route)
                                        }
                                    )

                                    NavigationItem.Settings.route -> SettingsScreen(
                                        isDarkMode = isDarkMode,
                                        onToggleDarkMode = {
                                            val next = !isDarkMode
                                            isDarkMode = next
                                            appScope.launch {
                                                tokenStorage.saveDarkModeEnabled(next)
                                            }
                                        },
                                        onSignOut = { authViewModel.signOut() }
                                    )
                                }
                            }

                            when (animatedScreen) {
                                "my_qr" -> {
                                    val previousKey = currentRoute
                                    val interactive = allowInteractiveSwipeBack &&
                                        swipeBackEnabled &&
                                        previousKey != animatedScreen

                                    val content: @Composable () -> Unit = {
                                        MyQRCodeScreen(
                                            userId = currentUser.id,
                                            username = currentUser.name,
                                            locationService = locationService,
                                            onNavigateBack = {
                                                transitionMode = NavigationTransitionMode.Tap
                                                showMyQRCode = false
                                            }
                                        )
                                    }

                                    if (interactive) {
                                        InteractiveSwipeBackContainer(
                                            enabled = true,
                                            edgeSwipeWidth = 44.dp,
                                            onBack = {
                                                transitionMode = NavigationTransitionMode.GestureBack
                                                showMyQRCode = false
                                            },
                                            previousContent = { renderScreen(previousKey, false) },
                                            currentContent = content
                                        )
                                    } else {
                                        content()
                                    }
                                }

                                "qr_scanner" -> {
                                    val previousKey = currentRoute
                                    val interactive = allowInteractiveSwipeBack &&
                                        swipeBackEnabled &&
                                        previousKey != animatedScreen

                                    val content: @Composable () -> Unit = {
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
                                                launchCommunityHubJoin(hubId)
                                            },
                                            onNavigateBack = {
                                                transitionMode = NavigationTransitionMode.Tap
                                                showQRScanner = false
                                            }
                                        )
                                    }

                                    if (interactive) {
                                        InteractiveSwipeBackContainer(
                                            enabled = true,
                                            edgeSwipeWidth = 44.dp,
                                            useLayoutOffsetForSwipeReveal = true,
                                            onBack = {
                                                transitionMode = NavigationTransitionMode.GestureBack
                                                showQRScanner = false
                                            },
                                            previousContent = { renderScreen(previousKey, false) },
                                            currentContent = content
                                        )
                                    } else {
                                        content()
                                    }
                                }

                                "nfc" -> {
                                    val previousKey = currentRoute
                                    val interactive = allowInteractiveSwipeBack &&
                                        swipeBackEnabled &&
                                        previousKey != animatedScreen

                                    val content: @Composable () -> Unit = {
                                        val userId = when (val state = authViewModel.authState) {
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
                                                transitionMode = NavigationTransitionMode.Tap
                                                showNfcScreen = false
                                            },
                                            onProximityFinalizeStart = {
                                                connectionRevealState = ConnectionRevealUiState(
                                                    methodLabel = "Tap",
                                                    phase = ConnectionRevealPhase.Connecting,
                                                )
                                            },
                                        )
                                    }

                                    if (interactive) {
                                        InteractiveSwipeBackContainer(
                                            enabled = true,
                                            edgeSwipeWidth = 44.dp,
                                            onBack = {
                                                transitionMode = NavigationTransitionMode.GestureBack
                                                showNfcScreen = false
                                            },
                                            previousContent = { renderScreen(previousKey, false) },
                                            currentContent = content
                                        )
                                    } else {
                                        content()
                                    }
                                }

                                "hub_chat" -> {
                                    val previousKey = currentRoute
                                    val interactive = allowInteractiveSwipeBack &&
                                        swipeBackEnabled &&
                                        previousKey != animatedScreen
                                    val hubArgs = hubChatArgs
                                    val hubUserId = when (val state = authViewModel.authState) {
                                        is AuthState.Success -> state.userId
                                        else -> ""
                                    }

                                    val content: @Composable () -> Unit = {
                                        if (hubArgs != null && hubUserId.isNotEmpty()) {
                                            HubChatScreen(
                                                args = hubArgs,
                                                currentUserId = hubUserId,
                                                onNavigateBack = {
                                                    transitionMode = NavigationTransitionMode.Tap
                                                    hubChatArgs = null
                                                },
                                                resolveHubGatekeeperLocation = { resolveConnectionLocation() },
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("Unable to open hub chat.")
                                            }
                                        }
                                    }

                                    if (interactive) {
                                        InteractiveSwipeBackContainer(
                                            enabled = true,
                                            edgeSwipeWidth = 44.dp,
                                            onBack = {
                                                transitionMode = NavigationTransitionMode.GestureBack
                                                hubChatArgs = null
                                            },
                                            previousContent = { renderScreen(previousKey, false) },
                                            currentContent = content,
                                        )
                                    } else {
                                        content()
                                    }
                                }

                                "search" -> {
                                    val previousKey = routeHistory.lastOrNull()
                                    val interactive = allowInteractiveSwipeBack &&
                                        swipeBackEnabled &&
                                        previousKey != null &&
                                        previousKey != animatedScreen

                                    val content: @Composable () -> Unit = {
                                        val userId = when (val state = authViewModel.authState) {
                                            is AuthState.Success -> state.userId
                                            else -> ""
                                        }
                                        GlobalSearchScreen(
                                            userId = userId,
                                            onNavigateToChat = { connectionId ->
                                                pendingChatId = connectionId
                                                navigateTo(NavigationItem.Connections.route)
                                            },
                                            onNavigateToMap = {
                                                navigateTo(NavigationItem.Map.route)
                                            },
                                            onBack = { navigateBack() }
                                        )
                                    }

                                    if (interactive) {
                                        InteractiveSwipeBackContainer(
                                            enabled = true,
                                            edgeSwipeWidth = 44.dp,
                                            onBack = { navigateBack(NavigationTransitionMode.GestureBack) },
                                            previousContent = { renderScreen(previousKey, false) },
                                            currentContent = content
                                        )
                                    } else {
                                        content()
                                    }
                                }

                                else -> {
                                    val previousKey = NavigationItem.Home.route
                                    val interactivePrimary = allowInteractiveSwipeBack &&
                                        isIOS &&
                                        isPrimaryNavRoute(animatedScreen) &&
                                        animatedScreen != NavigationItem.Connections.route &&
                                        previousKey != animatedScreen &&
                                        !(animatedScreen == NavigationItem.Connections.route && isConnectionsChatOpen)

                                    if (interactivePrimary) {
                                        InteractiveSwipeBackContainer(
                                            enabled = true,
                                            edgeSwipeWidth = 44.dp,
                                            useLayoutOffsetForSwipeReveal = animatedScreen == NavigationItem.Map.route,
                                            onBack = { navigatePrimaryRouteBackHome(NavigationTransitionMode.GestureBack) },
                                            previousContent = { renderScreen(previousKey, false) },
                                            currentContent = { renderPrimaryScreen(animatedScreen) }
                                        )
                                    } else {
                                        renderPrimaryScreen(animatedScreen)
                                    }
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = screenKey,
                            transitionSpec = {
                                if (transitionMode == NavigationTransitionMode.GestureBack) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                val routeOrder = listOf(
                                    NavigationItem.Home.route,
                                    NavigationItem.AddClick.route,
                                    NavigationItem.Connections.route,
                                    NavigationItem.Map.route,
                                    NavigationItem.Settings.route,
                                    "search",
                                    "my_qr",
                                    "qr_scanner",
                                    "nfc",
                                    "hub_chat",
                                )

                                val initialIndex = routeOrder.indexOf(initialState).let { if (it >= 0) it else 0 }
                                val targetIndex = routeOrder.indexOf(targetState).let { if (it >= 0) it else 0 }
                                val movingForward = targetIndex >= initialIndex

                                val slideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                                val fadeSpec  = tween<Float>(220, easing = LinearOutSlowInEasing)

                                if (movingForward) {
                                    (slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) +
                                        fadeIn(animationSpec = fadeSpec))
                                        .togetherWith(
                                            slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { -it }) +
                                                fadeOut(animationSpec = fadeSpec)
                                        ).using(SizeTransform(clip = true))
                                } else {
                                    (slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { -it }) +
                                        fadeIn(animationSpec = fadeSpec))
                                        .togetherWith(
                                            slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { it }) +
                                                fadeOut(animationSpec = fadeSpec)
                                        ).using(SizeTransform(clip = true))
                                }
                                }
                            },
                            label = "app_screen_transition"
                        ) { animatedScreen ->
                            renderScreen(animatedScreen)
                        }

                        if (hubVerifyInProgress) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.38f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AdaptiveCircularProgressIndicator(color = PrimaryBlue)
                                    Text(
                                        text = "Verifying you're at the hub…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        if (!isInitialLoading && (pendingConnectionsCount > 0 || appError != null)) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                                ),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (pendingConnectionsCount > 0) {
                                        Text(
                                            text = "$pendingConnectionsCount connection${if (pendingConnectionsCount == 1) "" else "s"} queued for sync.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!appError.isNullOrBlank()) {
                                        Text(
                                            text = appError ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (connectionState is ConnectionState.TaggingContext && !showNfcScreen && !suppressConnectionContextSheet) {
                            val tagging = connectionState as ConnectionState.TaggingContext
                            val finishWithoutTags: () -> Unit = {
                                connectionScope.launch {
                                    val noiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
                                    val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                                    val sensors = captureConnectionSensorContext(
                                        ambientNoiseMonitor = ambientMonitor,
                                        barometricHeightMonitor = baroMonitor,
                                        ambientNoiseOptIn = noiseOptIn,
                                        barometricContextOptIn = baroOptIn,
                                    )
                                    connectionViewModel.saveContextTags(
                                        tagging = tagging,
                                        contextTag = null,
                                        noiseLevelCategory = sensors.noiseLevelCategory,
                                        exactNoiseLevelDb = sensors.exactNoiseLevelDb,
                                        heightCategory = sensors.heightCategory,
                                        exactBarometricElevationMeters = sensors.exactBarometricElevationMeters,
                                        ambientNoiseMonitor = ambientMonitor,
                                        barometricHeightMonitor = baroMonitor,
                                        ambientNoiseOptIn = noiseOptIn,
                                        barometricContextOptIn = baroOptIn,
                                    )
                                }
                            }
                            ConnectionContextSheet(
                                connectedUsers = tagging.targetUsers,
                                locationName = null,
                                initialNoiseOptIn = ambientNoiseOptIn,
                                noisePermissionGranted = ambientMonitor.hasPermission,
                                onDismiss = finishWithoutTags,
                                onSkip = finishWithoutTags,
                                onConfirm = { contextTag, noiseOptIn ->
                                    connectionRevealState = ConnectionRevealUiState(
                                        methodLabel = "QR",
                                        phase = ConnectionRevealPhase.Connecting,
                                    )
                                    connectionScope.launch {
                                        ambientNoiseOptIn = noiseOptIn
                                        tokenStorage.saveAmbientNoiseOptIn(noiseOptIn)
                                        val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                                        val sensors = captureConnectionSensorContext(
                                            ambientNoiseMonitor = ambientMonitor,
                                            barometricHeightMonitor = baroMonitor,
                                            ambientNoiseOptIn = noiseOptIn,
                                            barometricContextOptIn = baroOptIn,
                                        )
                                        connectionViewModel.saveContextTags(
                                            tagging = tagging,
                                            contextTag = contextTag,
                                            noiseLevelCategory = sensors.noiseLevelCategory,
                                            exactNoiseLevelDb = sensors.exactNoiseLevelDb,
                                            heightCategory = sensors.heightCategory,
                                            exactBarometricElevationMeters = sensors.exactBarometricElevationMeters,
                                            ambientNoiseMonitor = ambientMonitor,
                                            barometricHeightMonitor = baroMonitor,
                                            ambientNoiseOptIn = noiseOptIn,
                                            barometricContextOptIn = baroOptIn,
                                        )
                                    }
                                },
                            )
                        }

                        if (connectionState is ConnectionState.QrAwaitingContext && !showNfcScreen && !suppressConnectionContextSheet) {
                            val awaiting = connectionState as ConnectionState.QrAwaitingContext
                            val cancelQr: () -> Unit = { connectionViewModel.resetConnectionState() }
                            ConnectionContextSheet(
                                connectedUsers = awaiting.targetUsers,
                                locationName = null,
                                initialNoiseOptIn = ambientNoiseOptIn,
                                noisePermissionGranted = ambientMonitor.hasPermission,
                                onDismiss = cancelQr,
                                onSkip = cancelQr,
                                onConfirm = { contextTag, noiseOptIn ->
                                    connectionRevealState = ConnectionRevealUiState(
                                        methodLabel = "QR",
                                        phase = ConnectionRevealPhase.Connecting,
                                    )
                                    connectionScope.launch {
                                        ambientNoiseOptIn = noiseOptIn
                                        tokenStorage.saveAmbientNoiseOptIn(noiseOptIn)
                                        val venue = awaiting.venueId
                                        val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                                        coroutineScope {
                                            val vibeDeferred = async(Dispatchers.Default) {
                                                runCatching { HardwareVibeMonitor().takeSnapshot() }.getOrNull()
                                            }
                                            val locationDeferred = async {
                                                when {
                                                    !venue.isNullOrBlank() -> null
                                                    AppDataManager.shouldCaptureLocationAtTap() ->
                                                        resolveConnectionLocation(null)
                                                    else -> null
                                                }
                                            }
                                            val sensorsDeferred = async {
                                                captureConnectionSensorContext(
                                                    ambientNoiseMonitor = ambientMonitor,
                                                    barometricHeightMonitor = baroMonitor,
                                                    ambientNoiseOptIn = noiseOptIn,
                                                    barometricContextOptIn = baroOptIn,
                                                )
                                            }

                                            val locationCaptured = locationDeferred.await()
                                            val la = locationCaptured?.latitude
                                            val lo = locationCaptured?.longitude
                                            val weatherDeferred = async(Dispatchers.Default) {
                                                if (
                                                    la != null && lo != null &&
                                                    la.isFinite() && lo.isFinite() &&
                                                    !(la == 0.0 && lo == 0.0)
                                                ) {
                                                    openMeteoWeather.fetchWeather(la, lo)?.toConnectionPayloadWeatherJson()
                                                } else {
                                                    null
                                                }
                                            }

                                            val vibe = vibeDeferred.await()
                                            val sensors = sensorsDeferred.await()
                                            val weatherLabel = weatherDeferred.await()

                                            connectionViewModel.connectWithUser(
                                                scannedUserId = awaiting.scannedUserId,
                                                currentUserId = currentUser.id,
                                                latitude = locationCaptured?.latitude,
                                                longitude = locationCaptured?.longitude,
                                                venueId = venue,
                                                altitudeMeters = locationCaptured?.altitudeMeters,
                                                heightCategory = sensors.heightCategory,
                                                exactBarometricElevationMeters = sensors.exactBarometricElevationMeters,
                                                exactBarometricPressureHpa = sensors.exactBarometricPressureHpa,
                                                contextTagObject = contextTag,
                                                connectionMethod = "qr",
                                                qrToken = awaiting.qrToken,
                                                noiseLevelCategory = sensors.noiseLevelCategory,
                                                exactNoiseLevelDb = sensors.exactNoiseLevelDb,
                                                hardwareVibeOverride = vibe,
                                                weatherSnapshotLabel = weatherLabel,
                                            )
                                        }
                                    }
                                },
                            )
                        }

                        connectionRevealState?.let { revealState ->
                            ConnectionRevealOverlay(
                                state = revealState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(10_000f),
                            )
                        }

                        when (val overlayState = globalCallOverlayState) {
                            is CallOverlayState.Outgoing,
                            is CallOverlayState.Incoming,
                            is CallOverlayState.Connecting,
                            is CallOverlayState.Ended,
                            -> {
                                CallPreviewOverlay(
                                    overlayState = overlayState,
                                    currentUserId = appDataUser?.id,
                                    onAccept = { CallSessionManager.acceptIncomingCall() },
                                    onDecline = { CallSessionManager.declineIncomingCall() },
                                    onCancel = { CallSessionManager.cancelCurrentCall() },
                                    onDismissEnded = { CallSessionManager.dismissEndedCall() },
                                )
                            }

                            CallOverlayState.Idle -> {
                                if (globalCallState !is CallState.Idle) {
                                    ActiveCallOverlay(
                                        callManager = CallSessionManager.callManager,
                                        otherUserName = activeInvite?.counterpartName(appDataUser?.id) ?: "Connection",
                                        state = globalCallState,
                                        onEndCall = { CallSessionManager.endActiveCall() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            } // End of Scaffold wrapper Box

            } // End of onboarding gate
        }
        } // End of Global Background Box
        } // End of ConnectionSensorMonitorsProvider
        } // End of PlatformThemeProvider
    }
}

private enum class NavigationTransitionMode {
    Tap,
    GestureBack
}

private fun isSwipeBackScreen(screenKey: String): Boolean {
    return screenKey == "search" ||
        screenKey == "my_qr" ||
        screenKey == "qr_scanner" ||
        screenKey == "nfc" ||
        screenKey == "hub_chat"
}

private fun isPrimaryNavRoute(route: String): Boolean {
    return route == NavigationItem.Home.route ||
        route == NavigationItem.AddClick.route ||
        route == NavigationItem.Connections.route ||
        route == NavigationItem.Map.route ||
        route == NavigationItem.Settings.route
}