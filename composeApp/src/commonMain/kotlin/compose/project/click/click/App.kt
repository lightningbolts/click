@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.calls.CallOverlayState // pragma: allowlist secret
import compose.project.click.click.calls.CallSessionManager // pragma: allowlist secret
import compose.project.click.click.calls.CallState // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSession // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.OpenMeteoWeatherService // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.models.ContextTag // pragma: allowlist secret
import compose.project.click.click.data.models.HeightCategory // pragma: allowlist secret
import compose.project.click.click.data.models.NoiseLevelCategory // pragma: allowlist secret
import compose.project.click.click.data.models.OnboardingState // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.isPublicUserProfileIncomplete // pragma: allowlist secret
import compose.project.click.click.data.models.resolveOnboardingAfterRemoteResolution // pragma: allowlist secret
import compose.project.click.click.data.models.resolveOnboardingInitialState // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.navigation.NavigationItem // pragma: allowlist secret
import compose.project.click.click.platform.rememberReduceMotionEnabled // pragma: allowlist secret
import compose.project.click.click.sensors.AmbientNoiseMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.CalibratedBarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.ConnectionSensorMonitorsProvider // pragma: allowlist secret
import compose.project.click.click.sensors.rememberAmbientNoiseMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.rememberBarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.ui.components.AppShimmerScreen // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealUiState // pragma: allowlist secret
import compose.project.click.click.ui.components.INTEREST_ONBOARDING_MIN_TAGS // pragma: allowlist secret
import compose.project.click.click.ui.screens.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.PermissionCoordinatorHost // pragma: allowlist secret
import compose.project.click.click.ui.utils.rememberLocationPermissionRequester // pragma: allowlist secret
import compose.project.click.click.ui.utils.rememberMicrophonePermissionRequester // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.utils.HUB_GATEKEEPER_HIGH_ACCURACY_TIMEOUT_MS // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.utils.hasUsableHubLocation // pragma: allowlist secret
import compose.project.click.click.utils.resolveHubGatekeeperLocation // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthState // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectivityViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.OnboardingViewModel // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    // Functional Clarity: light-first until persisted preference is loaded.
    val isDarkModeState = remember { mutableStateOf(false) }
    var isDarkMode by isDarkModeState
    val reduceMotion = rememberReduceMotionEnabled()

    // Ktor client
    val client =
        remember {
            HttpClient {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        }

    // Auth ViewModel with TokenStorage
    val tokenStorage = remember { createTokenStorage() }
    val ambientNoiseMonitor = rememberAmbientNoiseMonitor()
    val platformBarometricHeightMonitor = rememberBarometricHeightMonitor()
    val openMeteoWeather = remember { OpenMeteoWeatherService() }
    val barometricHeightMonitor =
        remember(platformBarometricHeightMonitor, openMeteoWeather) {
            CalibratedBarometricHeightMonitor(platformBarometricHeightMonitor, openMeteoWeather)
        }
    val appScope = rememberCoroutineScope()
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(tokenStorage = tokenStorage) }
    val connectivityViewModel: ConnectivityViewModel = viewModel { ConnectivityViewModel() }
    val connectionViewModel: ConnectionViewModel = viewModel { ConnectionViewModel() }
    val isOnline by connectivityViewModel.isOnline.collectAsState()
    val showOfflineBanner by connectivityViewModel.showOfflineBanner.collectAsState()

    // Location service for capturing GPS during QR scans
    val locationService =
        remember {
            compose.project.click.click.utils // pragma: allowlist secret
                .LocationService()
        }
    val requestLocationPermissionThen = rememberLocationPermissionRequester()
    val requestMicrophonePermissionThen = rememberMicrophonePermissionRequester()
    val onboardingJson = remember { Json { ignoreUnknownKeys = true } }

    val currentUser =
        when (val state = authViewModel.authState) {
            is AuthState.Success -> User(id = state.userId, name = state.name ?: state.email, createdAt = 0L)
            else -> User(id = "", name = "", createdAt = 0L)
        }

    LaunchedEffect(connectionViewModel, currentUser.id) {
        if (currentUser.id.isBlank()) return@LaunchedEffect
        AppDataManager.proximityHandshakeRecovered.collect { payload ->
            connectionViewModel.onProximityHandshakeRecoveredFromBackground(payload, currentUser.id)
        }
    }

    val ambientNoiseOptInState = remember { mutableStateOf(true) }

    var ambientNoiseOptIn by ambientNoiseOptInState
    var barometricContextOptIn by remember { mutableStateOf(true) }
    val onboardingStateState = remember { mutableStateOf<OnboardingState?>(null) }
    var onboardingState by onboardingStateState
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

        val persistedHasCompletedOnboarding = tokenStorage.getHasCompletedOnboarding()

        val savedState =
            tokenStorage
                .getOnboardingState()
                ?.let { serialized ->
                    runCatching { onboardingJson.decodeFromString<OnboardingState>(serialized) }.getOrNull()
                }

        val effectiveHasCompletedOnboarding =
            persistedHasCompletedOnboarding ?: savedState?.permissionsCompleted ?: false
        hasCompletedOnboarding = effectiveHasCompletedOnboarding
        if (persistedHasCompletedOnboarding == null && savedState?.permissionsCompleted == true) {
            tokenStorage.saveHasCompletedOnboarding(true)
        }

        val normalizedSavedState =
            savedState?.let { state ->
                if (effectiveHasCompletedOnboarding && !state.permissionsCompleted) {
                    state.copy(permissionsCompleted = true)
                } else {
                    state
                }
            }

        val initialState =
            resolveOnboardingInitialState(
                savedState = normalizedSavedState,
                hasCompletedOnboarding = effectiveHasCompletedOnboarding,
            )
        if (initialState != null) {
            onboardingState = initialState
            if (initialState != normalizedSavedState) {
                tokenStorage.saveOnboardingState(onboardingJson.encodeToString(initialState))
            }
        } else {
            onboardingState = null
        }

        val supabaseRepo =
            compose.project.click.click.data.repository // pragma: allowlist secret
                .SupabaseRepository()
        // Already-onboarded sessions must not wait on network — blocking here left the home
        // reveal half-finished (black content + nav only) when the fetch raced the entrance.
        val localOnboardingReady =
            normalizedSavedState?.interestsCompleted == true ||
                normalizedSavedState?.isComplete == true ||
                effectiveHasCompletedOnboarding ||
                initialState?.isComplete == true
        interestsRemoteResolved = localOnboardingReady
        launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(2_500L) {
                    supabaseRepo.fetchUserInterests(currentUser.id).fold(
                        onSuccess = { row ->
                            val tagCount = row?.tags?.size ?: 0
                            val resolved =
                                resolveOnboardingAfterRemoteResolution(
                                    currentState = onboardingState,
                                    tagCount = tagCount,
                                    userFirstName = AppDataManager.currentUser.value?.firstName,
                                    userBirthday = AppDataManager.currentUser.value?.birthday,
                                    userAvatarUrl = AppDataManager.currentUser.value?.image,
                                    minInterestTags = INTEREST_ONBOARDING_MIN_TAGS,
                                )
                            onboardingState = resolved
                            if (tagCount >= INTEREST_ONBOARDING_MIN_TAGS) {
                                tokenStorage.saveTagsInitialized(true)
                            }
                            tokenStorage.saveOnboardingState(onboardingJson.encodeToString(resolved))
                        },
                        onFailure = { err ->
                            println("App: user_interests fetch failed, using local onboarding only: ${err.message}")
                            if (onboardingState == null) {
                                val fallback =
                                    resolveOnboardingAfterRemoteResolution(
                                        currentState = null,
                                        tagCount = 0,
                                        userFirstName = AppDataManager.currentUser.value?.firstName,
                                        userBirthday = AppDataManager.currentUser.value?.birthday,
                                        userAvatarUrl = AppDataManager.currentUser.value?.image,
                                        minInterestTags = INTEREST_ONBOARDING_MIN_TAGS,
                                    )
                                onboardingState = fallback
                                tokenStorage.saveOnboardingState(onboardingJson.encodeToString(fallback))
                            }
                        },
                    )
                }
            } finally {
                if (!isActive) return@launch
                if (onboardingState == null) {
                    val fallback =
                        resolveOnboardingAfterRemoteResolution(
                            currentState = null,
                            tagCount = 0,
                            userFirstName = AppDataManager.currentUser.value?.firstName,
                            userBirthday = AppDataManager.currentUser.value?.birthday,
                            userAvatarUrl = AppDataManager.currentUser.value?.image,
                            minInterestTags = INTEREST_ONBOARDING_MIN_TAGS,
                        )
                    onboardingState = fallback
                    tokenStorage.saveOnboardingState(onboardingJson.encodeToString(fallback))
                }
                interestsRemoteResolved = true
            }
        }
    }

    // Coroutine scope for location-aware connection
    val connectionScope = rememberCoroutineScope()

    fun hasUsableLocation(location: LocationResult?): Boolean =
        location != null &&
            location.latitude.isFinite() &&
            location.longitude.isFinite() &&
            !(location.latitude == 0.0 && location.longitude == 0.0)

    suspend fun resolveConnectionLocation(initialLocation: LocationResult? = null): LocationResult? {
        if (hasUsableLocation(initialLocation)) return initialLocation

        return try {
            if (!locationService.hasLocationPermission()) {
                requestLocationPermissionIfNeeded(shouldRequest = true)
                delay(500L)
            }
            if (!locationService.hasLocationPermission()) {
                return AppDataManager.lastKnownDeviceLocation.value
                    ?.let { (lat, lon) ->
                        compose.project.click.click.utils // pragma: allowlist secret
                            .LocationResult(latitude = lat, longitude = lon)
                    }?.takeIf(::hasUsableLocation)
            }

            val refreshed = locationService.getHighAccuracyLocation(6_500L)
            if (hasUsableLocation(refreshed)) return refreshed

            val current = locationService.getCurrentLocation()
            if (hasUsableLocation(current)) return current

            AppDataManager.lastKnownDeviceLocation.value
                ?.let { (lat, lon) ->
                    compose.project.click.click.utils // pragma: allowlist secret
                        .LocationResult(latitude = lat, longitude = lon)
                }?.takeIf(::hasUsableLocation)
        } catch (e: Exception) {
            println("App: Failed to get high-accuracy location: ${e.redactedRestMessage()}")
            initialLocation.takeIf(::hasUsableLocation)
                ?: AppDataManager.lastKnownDeviceLocation.value
                    ?.let { (lat, lon) ->
                        compose.project.click.click.utils // pragma: allowlist secret
                            .LocationResult(latitude = lat, longitude = lon)
                    }?.takeIf(::hasUsableLocation)
        }
    }

    val lastHubGatekeeperFixState =
        remember {
            mutableStateOf<LocationResult?>(null)
        }
    var lastHubGatekeeperFix by lastHubGatekeeperFixState

    suspend fun resolveHubGatekeeperLocationForChat(seed: LocationResult? = null): LocationResult? {
        lastHubGatekeeperFix?.takeIf(::hasUsableHubLocation)?.let { return it }
        seed?.takeIf(::hasUsableHubLocation)?.let { return it }

        if (!locationService.hasLocationPermission()) {
            requestLocationPermissionIfNeeded(shouldRequest = true)
            delay(250L)
        }

        val resolved =
            resolveHubGatekeeperLocation(
                locationService = locationService,
                lastKnownLatLon = AppDataManager.lastKnownDeviceLocation.value,
                seed = seed,
                highAccuracyTimeoutMs = HUB_GATEKEEPER_HIGH_ACCURACY_TIMEOUT_MS,
            )
        if (resolved != null) {
            lastHubGatekeeperFix = resolved
            AppDataManager.noteDeviceLocation(resolved.latitude, resolved.longitude)
        }
        return resolved
    }

    fun connectWithUser(
        userId: String,
        qrToken: String? = null,
        tokenAgeMs: Long? = null,
        venueId: String? = null,
        contextTagObject: ContextTag? = null,
        capturedLocation: LocationResult? = null,
        heightCategory: HeightCategory? = null,
        exactBarometricElevationMeters: Double? = null,
        exactBarometricPressureHpa: Double? = null,
        noiseLevelCategory: NoiseLevelCategory? = null,
        exactNoiseLevelDb: Double? = null,
        hardwareVibeOverride: compose.project.click.click.sensors.HardwareVibeSnapshot? = null, // pragma: allowlist secret
        weatherSnapshotLabel: String? = null,
    ) {
        if (currentUser.id.isNotEmpty()) {
            connectionScope.launch {
                // Venue-bound QR: never use device GPS; backend maps the venue.
                val location =
                    if (!venueId.isNullOrBlank()) {
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
    val showMyQRCodeState = remember { mutableStateOf(false) }
    var showMyQRCode by showMyQRCodeState
    val showQRScannerState = remember { mutableStateOf(false) }
    var showQRScanner by showQRScannerState
    val connectionRevealStateState = remember { mutableStateOf<ConnectionRevealUiState?>(null) }
    var connectionRevealState by connectionRevealStateState
    val revealConnectionIdState = remember { mutableStateOf<String?>(null) }
    var revealConnectionId by revealConnectionIdState
    val showConnectionDisposableRollState = remember { mutableStateOf(false) }
    var showConnectionDisposableRoll by showConnectionDisposableRollState
    val connectionRollConnectionIdState = remember { mutableStateOf<String?>(null) }
    var connectionRollConnectionId by connectionRollConnectionIdState
    val pendingRollSessionState = remember { mutableStateOf<CollaborationSession?>(null) }
    var pendingRollSession by pendingRollSessionState
    val disposableRollOpeningState = remember { mutableStateOf(false) }
    var disposableRollOpening by disposableRollOpeningState

    /** Scale exit matches open; fade-only after send so the underlying chat does not flash. */
    val disposableRollExitWithScaleState = remember { mutableStateOf(true) }
    var disposableRollExitWithScale by disposableRollExitWithScaleState
    LaunchedEffect(Unit) {
        launch {
            connectionViewModel.transientNotice.collect { message ->
                if (message == ConnectionViewModel.RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE) {
                    connectionRevealState = null
                }
            }
        }
    }

    val isIOS =
        remember {
            getPlatform().name.contains("iOS", ignoreCase = true)
        }

    val showSignUpState = remember { mutableStateOf(false) }

    var showSignUp by showSignUpState
    var authShimmerVisible by remember { mutableStateOf(false) }
    var authSurfaceVisible by remember { mutableStateOf(false) }

    LaunchedEffect(authViewModel.authState) {
        if (authViewModel.authState is AuthState.Loading) {
            authShimmerVisible = true
        } else if (authShimmerVisible) {
            delay(340)
            authShimmerVisible = false
        }
    }

    LaunchedEffect(authViewModel.isAuthenticated, authViewModel.authState, authShimmerVisible) {
        if (!authViewModel.isAuthenticated && authViewModel.authState !is AuthState.Loading && !authShimmerVisible) {
            authSurfaceVisible = false
            delay(16)
            authSurfaceVisible = true
        } else {
            authSurfaceVisible = false
        }
    }

    PlatformThemeProvider(isDarkMode = isDarkMode) {
        BindPlatformHapticsToViewHierarchy()
        ConnectionSensorMonitorsProvider(
            ambientNoiseMonitor = ambientNoiseMonitor,
            barometricHeightMonitor = barometricHeightMonitor,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(if (isDarkMode) BackgroundDark else BackgroundLight),
            ) {
                PermissionCoordinatorHost()
                // Show login/signup screens when not authenticated
                if (authViewModel.authState is AuthState.Loading || authShimmerVisible) {
                    AppShimmerScreen(isDarkMode = isDarkMode)
                } else if (!authViewModel.isAuthenticated) {
                    AppAuthGate(
                        authViewModel = authViewModel,
                        authSurfaceVisible = authSurfaceVisible,
                        showSignUpState = showSignUpState,
                    )
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
                    var callOwnsNativeChrome by remember { mutableStateOf(false) }
                    LaunchedEffect(globalCallOverlayState, globalCallState) {
                        if (
                            globalCallOverlayState !is CallOverlayState.Idle ||
                            globalCallState !is CallState.Idle
                        ) {
                            callOwnsNativeChrome = true
                        } else {
                            // The native iOS tab bar sits outside Compose z-order. Keep it hidden until
                            // the final call-card fade has cleared, then restore the preserved tab.
                            delay(220)
                            callOwnsNativeChrome = false
                        }
                    }
                    // While [AnimatedVisibility] exits, [globalCallState] may already be [CallState.Idle]; keep the
                    // last in-room state so [ActiveCallOverlay] does not snap to an empty Idle layout mid-fade.
                    val lastActiveCallPresentedState = remember { mutableStateOf<CallState>(CallState.Idle) }
                    val lastPreviewOverlayPresentedState = remember { mutableStateOf<CallOverlayState>(CallOverlayState.Idle) }
                    val suppressEndedPreviewAfterActiveCallState = remember { mutableStateOf(false) }
                    var suppressEndedPreviewAfterActiveCall by suppressEndedPreviewAfterActiveCallState
                    SideEffect {
                        if (globalCallState !is CallState.Idle) {
                            lastActiveCallPresentedState.value = globalCallState
                        }
                        if (globalCallOverlayState !is CallOverlayState.Idle) {
                            lastPreviewOverlayPresentedState.value = globalCallOverlayState
                        }
                    }
                    val activeCallUiState =
                        if (globalCallState !is CallState.Idle) globalCallState else lastActiveCallPresentedState.value
                    val profileApi = remember { ApiClient() }
                    var remoteAvatarPresent by remember { mutableStateOf<Boolean?>(null) }
                    var profileGateCheckReady by remember { mutableStateOf(false) }

                    LaunchedEffect(
                        authViewModel.isAuthenticated,
                        currentUser.id,
                        appDataUser?.id,
                    ) {
                        if (!authViewModel.isAuthenticated || currentUser.id.isBlank()) {
                            remoteAvatarPresent = null
                            profileGateCheckReady = false
                            return@LaunchedEffect
                        }

                        val localUser =
                            appDataUser
                                ?: run {
                                    profileGateCheckReady = false
                                    return@LaunchedEffect
                                }

                        if (!localUser.image.isNullOrBlank()) {
                            remoteAvatarPresent = true
                            profileGateCheckReady = true
                        } else {
                            remoteAvatarPresent = null
                            profileGateCheckReady = false
                        }

                        launch(Dispatchers.IO) {
                            val remoteUser =
                                withTimeoutOrNull(2_500L) {
                                    profileApi.getUserProfile(currentUser.id).getOrNull()?.user
                                }
                            withContext(Dispatchers.Main) {
                                if (remoteUser != null) {
                                    val remoteHasAvatar = !remoteUser.image.isNullOrBlank()
                                    if (remoteHasAvatar) {
                                        remoteAvatarPresent = true
                                    } else if (remoteAvatarPresent == null) {
                                        remoteAvatarPresent = false
                                    }
                                    if (
                                        isPublicUserProfileIncomplete(localUser) &&
                                        !isPublicUserProfileIncomplete(remoteUser)
                                    ) {
                                        AppDataManager.refresh(force = true)
                                    }
                                } else if (remoteAvatarPresent == null) {
                                    remoteAvatarPresent = false
                                }
                                profileGateCheckReady = true
                            }
                        }
                    }

                    LaunchedEffect(appDataUser?.id, appDataUser?.name) {
                        CallSessionManager.bindUser(appDataUser?.id, appDataUser?.name)
                    }

                    val supabaseRepo =
                        remember {
                            compose.project.click.click.data.repository // pragma: allowlist secret
                                .SupabaseRepository()
                        }
                    val onboardingScope = rememberCoroutineScope()

                    // Phase 2 (C8): drive onboarding through OnboardingViewModel rather than the legacy
                    // permissions-first gate. Permissions now live in the Settings Permissions Hub (C9)
                    // and are requested contextually; the gate is Loading → Welcome → Interests → Personality
                    // → Avatar → PriorConnections → Complete. Existing users skip Personality via legacyComplete.
                    // We rebuild the VM whenever the persisted state changes so step() stays
                    // in sync without having to hoist the whole thing into AppDataManager.
                    val onboardingStateSnapshot = onboardingState
                    val userHasAvatar = remoteAvatarPresent
                    val onboardingPersistScope = rememberCoroutineScope()
                    val onboardingVm =
                        remember(onboardingStateSnapshot, userHasAvatar) {
                            OnboardingViewModel(
                                initialState = onboardingStateSnapshot ?: OnboardingState(),
                                userHasAvatar = { userHasAvatar },
                                onPersist = { next ->
                                    onboardingPersistScope.launch {
                                        persistOnboardingState(next)
                                    }
                                },
                                clockMillis = {
                                    kotlinx.datetime.Clock.System
                                        .now()
                                        .toEpochMilliseconds()
                                },
                            )
                        }
                    val vmStep by onboardingVm.step.collectAsState()
                    val isDataReady =
                        onboardingStateSnapshot != null &&
                            appDataUser != null &&
                            hasCompletedOnboarding != null
                    LaunchedEffect(isDataReady) {
                        if (isDataReady) onboardingVm.onDataLoaded()
                    }
                    val onboardingStep =
                        when {
                            !isDataReady || vmStep == OnboardingViewModel.Step.Loading -> "loading"
                            vmStep == OnboardingViewModel.Step.Welcome -> "welcome"
                            vmStep == OnboardingViewModel.Step.Interests -> "interests"
                            vmStep == OnboardingViewModel.Step.Personality -> "personality"
                            vmStep == OnboardingViewModel.Step.Avatar -> "avatar"
                            vmStep == OnboardingViewModel.Step.PriorConnections -> "prior_connections"
                            else -> "complete"
                        }

                    var previousOnboardingStep by remember { mutableStateOf<String?>(null) }
                    var onboardingHandoffActive by remember { mutableStateOf(false) }
                    var showHomeRevealOverlay by remember { mutableStateOf(false) }
                    var hasPlayedHomeEntrance by remember(currentUser.id) { mutableStateOf(false) }

                    val avatarAuthRepo = remember(tokenStorage) { AuthRepository(tokenStorage = tokenStorage) }

                    val profileGatePending =
                        currentUser.id.isNotBlank() &&
                            appDataUser != null &&
                            !profileGateCheckReady

                    val profileGateActive =
                        currentUser.id.isNotBlank() &&
                            appDataUser != null &&
                            profileGateCheckReady &&
                            isPublicUserProfileIncomplete(appDataUser!!)

                    val shouldStartOnboardingHandoff =
                        !hasPlayedHomeEntrance &&
                            previousOnboardingStep != null &&
                            previousOnboardingStep != "complete" &&
                            previousOnboardingStep != "loading" &&
                            onboardingStep == "complete" &&
                            !profileGateActive &&
                            !profileGatePending

                    val shouldStartInitialHomeReveal =
                        !hasPlayedHomeEntrance &&
                            (previousOnboardingStep == null || previousOnboardingStep == "loading") &&
                            onboardingStep == "complete" &&
                            !profileGateActive &&
                            !profileGatePending

                    LaunchedEffect(shouldStartOnboardingHandoff) {
                        if (shouldStartOnboardingHandoff) {
                            onboardingHandoffActive = true
                            try {
                                delay(600)
                                showHomeRevealOverlay = true
                                delay(380)
                            } finally {
                                // Cancellation mid-handoff previously left the reveal overlay stuck and
                                // content at alpha 0 — nav bar only on a black screen.
                                onboardingHandoffActive = false
                                showHomeRevealOverlay = false
                                hasPlayedHomeEntrance = true
                            }
                        }
                    }

                    LaunchedEffect(shouldStartInitialHomeReveal) {
                        if (shouldStartInitialHomeReveal) {
                            try {
                                showHomeRevealOverlay = true
                                delay(180)
                            } finally {
                                showHomeRevealOverlay = false
                                hasPlayedHomeEntrance = true
                            }
                        }
                    }

                    // Fail-safe: never leave the main shell invisible after onboarding is complete.
                    LaunchedEffect(onboardingStep, profileGateActive, profileGatePending) {
                        if (
                            onboardingStep == "complete" &&
                            !profileGateActive &&
                            !profileGatePending &&
                            !hasPlayedHomeEntrance
                        ) {
                            delay(900)
                            if (!hasPlayedHomeEntrance) {
                                showHomeRevealOverlay = false
                                onboardingHandoffActive = false
                                hasPlayedHomeEntrance = true
                            }
                        }
                    }

                    SideEffect {
                        previousOnboardingStep = onboardingStep
                    }

                    if (profileGatePending) {
                        AppShimmerScreen(isDarkMode = isDarkMode)
                    } else if (profileGateActive) {
                        ProfileBasicsGateScreen(
                            userId = currentUser.id,
                            initialFirstName = appDataUser!!.firstName.orEmpty(),
                            initialLastName = appDataUser!!.lastName.orEmpty(),
                            initialBirthdayIso = appDataUser!!.birthday.orEmpty(),
                            requireBirthday = appDataUser!!.birthday.isNullOrBlank(),
                            onCompleted = {
                                profileGateCheckReady = true
                                appScope.launch { AppDataManager.refresh(force = true) }
                            },
                        )
                    } else if (onboardingStep == "loading") {
                        AppShimmerScreen(isDarkMode = isDarkMode)
                    } else if (onboardingStep != "complete") {
                        AppOnboardingFlowHost(
                            onboardingVm = onboardingVm,
                            onboardingScope = onboardingScope,
                            onboardingStep = onboardingStep,
                            appDataUser = appDataUser,
                            currentUser = currentUser,
                            avatarAuthRepo = avatarAuthRepo,
                            supabaseRepo = supabaseRepo,
                            tokenStorage = tokenStorage,
                            interestsRemoteResolved = interestsRemoteResolved,
                            isDarkMode = isDarkMode,
                            onboardingStateState = onboardingStateState,
                            persistOnboardingState = { next -> persistOnboardingState(next) },
                        )
                    } else if (onboardingHandoffActive || shouldStartOnboardingHandoff) {
                        AppShimmerScreen(isDarkMode = isDarkMode)
                    } else {
                        AppMainShell(
                            reduceMotion = reduceMotion,
                            isIOS = isIOS,
                            client = client,
                            tokenStorage = tokenStorage,
                            appScope = appScope,
                            connectionScope = connectionScope,
                            currentUser = currentUser,
                            appDataUser = appDataUser,
                            locationService = locationService,
                            authViewModel = authViewModel,
                            connectionViewModel = connectionViewModel,
                            supabaseRepo = supabaseRepo,
                            ambientMonitor = ambientMonitor,
                            baroMonitor = baroMonitor,
                            openMeteoWeather = openMeteoWeather,
                            showOfflineBanner = showOfflineBanner,
                            isInitialLoading = isInitialLoading,
                            pendingConnectionsCount = pendingConnectionsCount,
                            appError = appError,
                            callOwnsNativeChrome = callOwnsNativeChrome,
                            globalCallOverlayState = globalCallOverlayState,
                            globalCallState = globalCallState,
                            activeCallUiState = activeCallUiState,
                            activeInvite = activeInvite,
                            hasPlayedHomeEntrance = hasPlayedHomeEntrance,
                            showHomeRevealOverlay = showHomeRevealOverlay,
                            onboardingHandoffActive = onboardingHandoffActive,
                            shouldStartOnboardingHandoff = shouldStartOnboardingHandoff,
                            requestLocationPermissionIfNeeded = { shouldRequest -> requestLocationPermissionIfNeeded(shouldRequest) },
                            resolveConnectionLocation = { seed -> resolveConnectionLocation(seed) },
                            resolveHubGatekeeperLocationForChat = { resolveHubGatekeeperLocationForChat() },
                            isDarkModeState = isDarkModeState,
                            ambientNoiseOptInState = ambientNoiseOptInState,
                            lastHubGatekeeperFixState = lastHubGatekeeperFixState,
                            showMyQRCodeState = showMyQRCodeState,
                            showQRScannerState = showQRScannerState,
                            connectionRevealStateState = connectionRevealStateState,
                            revealConnectionIdState = revealConnectionIdState,
                            showConnectionDisposableRollState = showConnectionDisposableRollState,
                            connectionRollConnectionIdState = connectionRollConnectionIdState,
                            pendingRollSessionState = pendingRollSessionState,
                            disposableRollOpeningState = disposableRollOpeningState,
                            disposableRollExitWithScaleState = disposableRollExitWithScaleState,
                            lastActiveCallPresentedState = lastActiveCallPresentedState,
                            lastPreviewOverlayPresentedState = lastPreviewOverlayPresentedState,
                            suppressEndedPreviewAfterActiveCallState = suppressEndedPreviewAfterActiveCallState,
                        )
                    } // End of onboarding gate
                }
            } // End of Global Background Box
        } // End of ConnectionSensorMonitorsProvider
    } // End of PlatformThemeProvider
}

internal enum class NavigationTransitionMode {
    Tap,
    GestureBack,
}

internal fun isPrimaryNavRoute(route: String): Boolean =
    route == NavigationItem.Home.route ||
        route == NavigationItem.AddClick.route ||
        route == NavigationItem.Connections.route ||
        route == NavigationItem.Map.route ||
        route == NavigationItem.Settings.route
