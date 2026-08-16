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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.calendar.AvailabilityOverlapGap // pragma: allowlist secret
import compose.project.click.click.calendar.lockAvailabilityIntentForGap // pragma: allowlist secret
import compose.project.click.click.calls.ActiveCallOverlay // pragma: allowlist secret
import compose.project.click.click.calls.CallOverlayState // pragma: allowlist secret
import compose.project.click.click.calls.CallOverlayTransitionPolicy // pragma: allowlist secret
import compose.project.click.click.calls.CallPreviewOverlay // pragma: allowlist secret
import compose.project.click.click.calls.CallSessionManager // pragma: allowlist secret
import compose.project.click.click.calls.CallState // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSession // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSessionManager // pragma: allowlist secret
import compose.project.click.click.data.ActiveHubEntry // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.OpenMeteoWeatherService // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.hub.HubConnectionManager // pragma: allowlist secret
import compose.project.click.click.data.hub.HubVerifyResult // pragma: allowlist secret
import compose.project.click.click.data.models.ContextTag // pragma: allowlist secret
import compose.project.click.click.data.models.HeightCategory // pragma: allowlist secret
import compose.project.click.click.data.models.NoiseLevelCategory // pragma: allowlist secret
import compose.project.click.click.data.models.ONBOARDING_FLOW_VERSION_COMPLETE // pragma: allowlist secret
import compose.project.click.click.data.models.OnboardingState // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.isPendingSync // pragma: allowlist secret
import compose.project.click.click.data.models.isPublicUserProfileIncomplete // pragma: allowlist secret
import compose.project.click.click.data.models.resolveOnboardingAfterRemoteResolution // pragma: allowlist secret
import compose.project.click.click.data.models.resolveOnboardingInitialState // pragma: allowlist secret
import compose.project.click.click.data.models.toConnectionPayloadWeatherJson // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.deeplink.ConnectionDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.deeplink.EventDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.encounter.EncounterTetherManager // pragma: allowlist secret
import compose.project.click.click.navigation.NavigationItem // pragma: allowlist secret
import compose.project.click.click.navigation.bottomNavItems // pragma: allowlist secret
import compose.project.click.click.navigation.shouldRenderHomeSwipeUnderlay // pragma: allowlist secret
import compose.project.click.click.notifications.ChatDeepLinkManager // pragma: allowlist secret
import compose.project.click.click.notifications.ChatNotificationDismisser // pragma: allowlist secret
import compose.project.click.click.platform.rememberReduceMotionEnabled // pragma: allowlist secret
import compose.project.click.click.proximity.rememberProximityManager // pragma: allowlist secret
import compose.project.click.click.sensors.AmbientNoiseMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.CalibratedBarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.ConnectionSensorMonitorsProvider // pragma: allowlist secret
import compose.project.click.click.sensors.HardwareVibeMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.captureConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.sensors.rememberAmbientNoiseMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.rememberBarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.ui.camera.DisposableCameraView // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.AppShimmerScreen // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionContextPresentation // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionContextSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealOverlay // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealPhase // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealUiState // pragma: allowlist secret
import compose.project.click.click.ui.components.GlobalTetherOverlay // pragma: allowlist secret
import compose.project.click.click.ui.components.INTEREST_ONBOARDING_MIN_TAGS // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalNativeChromeActive // pragma: allowlist secret
import compose.project.click.click.ui.components.OfflineStatusBanner // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformBackHandler // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformBottomBar // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformNativeNavigationBarSwipeReveal // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.interactiveSwipeBackUnderlay // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberBottomChromePadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberInteractiveBackHostState // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberUnifiedToastState // pragma: allowlist secret
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
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectivityViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.HomeViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.OnboardingViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.VerifiedCliqueProximityIntent // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    var isDarkMode by remember { mutableStateOf(false) }
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

    var lastHubGatekeeperFix by remember {
        mutableStateOf<LocationResult?>(null)
    }

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
    var showMyQRCode by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    var connectionRevealState by remember { mutableStateOf<ConnectionRevealUiState?>(null) }
    var revealConnectionId by remember { mutableStateOf<String?>(null) }
    var showConnectionDisposableRoll by remember { mutableStateOf(false) }
    var connectionRollConnectionId by remember { mutableStateOf<String?>(null) }
    var pendingRollSession by remember { mutableStateOf<CollaborationSession?>(null) }
    var disposableRollOpening by remember { mutableStateOf(false) }

    /** Scale exit matches open; fade-only after send so the underlying chat does not flash. */
    var disposableRollExitWithScale by remember { mutableStateOf(true) }

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

    var showSignUp by remember { mutableStateOf(false) }
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
                    val authSurfaceAlpha by animateFloatAsState(
                        targetValue = if (authSurfaceVisible) 1f else 0f,
                        animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing),
                        label = "auth_surface_alpha",
                    )
                    val authSurfaceScale by animateFloatAsState(
                        targetValue = if (authSurfaceVisible) 1f else 1.01f,
                        animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing),
                        label = "auth_surface_scale",
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = authSurfaceAlpha
                                    scaleX = authSurfaceScale
                                    scaleY = authSurfaceScale
                                },
                    ) {
                        AnimatedContent(
                            targetState = showSignUp,
                            transitionSpec = {
                                val towardsSignUp = targetState
                                val slideSpec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                                val fadeSpec = tween<Float>(180, easing = LinearOutSlowInEasing)
                                val enterX = if (towardsSignUp) { width: Int -> width } else { width: Int -> -width }
                                val exitX = if (towardsSignUp) { width: Int -> -width } else { width: Int -> width }
                                (
                                    slideInHorizontally(animationSpec = slideSpec, initialOffsetX = enterX) +
                                        fadeIn(animationSpec = fadeSpec)
                                ).togetherWith(
                                    slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = exitX) +
                                        fadeOut(animationSpec = fadeSpec),
                                ).using(SizeTransform(clip = true))
                            },
                            label = "auth_login_signup",
                        ) { signUp ->
                            if (signUp) {
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
                                    errorMessage =
                                        if (authViewModel.authState is AuthState.Error) {
                                            (authViewModel.authState as AuthState.Error).message
                                        } else {
                                            null
                                        },
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
                                    onGoogleSignIn = { authViewModel.signInWithGoogle() },
                                    onAppleSignIn = { authViewModel.signInWithApple() },
                                    isLoading = authViewModel.authState is AuthState.Loading,
                                    errorMessage =
                                        if (authViewModel.authState is AuthState.Error) {
                                            (authViewModel.authState as AuthState.Error).message
                                        } else {
                                            null
                                        },
                                )
                            }
                        }
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
                    var suppressEndedPreviewAfterActiveCall by remember { mutableStateOf(false) }
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
                        val onboardingToastState = rememberUnifiedToastState()
                        LaunchedEffect(Unit) {
                            AppDataManager.transientUserMessages.collect {
                                onboardingToastState.show(onboardingScope, it)
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            PlatformBackHandler(enabled = onboardingVm.canGoBack()) {
                                onboardingVm.goBack()
                            }
                            AnimatedContent(
                                targetState = onboardingStep,
                                transitionSpec = {
                                    val slideSpec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                                    val fadeSpec = tween<Float>(180, easing = LinearOutSlowInEasing)
                                    (
                                        slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) +
                                            fadeIn(animationSpec = fadeSpec)
                                    ).togetherWith(
                                        slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { -it }) +
                                            fadeOut(animationSpec = fadeSpec),
                                    ).using(SizeTransform(clip = true))
                                },
                                label = "onboarding_transition",
                            ) { step ->
                                when (step) {
                                    "welcome" -> {
                                        WelcomeScreen(
                                            firstName = appDataUser?.firstName,
                                            onContinue = { onboardingVm.onWelcomeAcknowledged() },
                                        )
                                    }

                                    "avatar" -> {
                                        AvatarScreen(
                                            existingAvatarUrl = appDataUser?.image,
                                            onUploadBytes = { bytes, mimeType ->
                                                avatarAuthRepo.uploadProfilePicture(bytes, mimeType)
                                            },
                                            onUploaded = { _ ->
                                                onboardingScope.launch {
                                                    AppDataManager.refresh(force = true)
                                                    onboardingVm.onAvatarSetOrSkipped()
                                                }
                                            },
                                            onSkip = { onboardingVm.onAvatarSetOrSkipped() },
                                        )
                                    }

                                    "prior_connections" -> {
                                        PriorConnectionsScreen(
                                            onSkip = { onboardingVm.onPriorConnectionsSetOrSkipped() },
                                        )
                                    }

                                    "personality" -> {
                                        PersonalityTaggingScreen(
                                            initialTags = appDataUser?.personalityTags.orEmpty(),
                                            onTagsSelected = { tags ->
                                                onboardingScope.launch {
                                                    val saveResult =
                                                        ApiClient().patchUserProfile(
                                                            currentUser.id,
                                                            personalityTags = tags,
                                                        )
                                                    if (saveResult.isSuccess) {
                                                        AppDataManager.applyPersonalityTags(tags)
                                                        onboardingVm.onPersonalitySaved()
                                                        val base = onboardingState ?: OnboardingState()
                                                        persistOnboardingState(
                                                            base.copy(
                                                                welcomeSeen = true,
                                                                interestsCompleted = true,
                                                                personalityCompleted = true,
                                                            ),
                                                        )
                                                    } else {
                                                        val msg =
                                                            saveResult
                                                                .exceptionOrNull()
                                                                ?.message
                                                                ?.trim()
                                                                .orEmpty()
                                                                .ifBlank {
                                                                    "Couldn't save personality. Check your connection and try again."
                                                                }
                                                        AppDataManager.postTransientUserMessage(msg)
                                                    }
                                                }
                                            },
                                        )
                                    }

                                    else -> {
                                        if (!interestsRemoteResolved) {
                                            AppShimmerScreen(isDarkMode = isDarkMode)
                                        } else {
                                            InterestTaggingScreen(
                                                onTagsSelected = { tags ->
                                                    onboardingScope.launch {
                                                        val saveResult = supabaseRepo.updateUserInterests(currentUser.id, tags)
                                                        if (saveResult.isSuccess) {
                                                            tokenStorage.saveTagsInitialized(true)
                                                            // Advance the in-memory step immediately, then persist
                                                            // flowVersion so cold start recognizes Phase 2 completion.
                                                            onboardingVm.onInterestsSaved()
                                                            val base = onboardingState ?: OnboardingState()
                                                            persistOnboardingState(
                                                                base.copy(
                                                                    interestsCompleted = true,
                                                                    flowVersion = ONBOARDING_FLOW_VERSION_COMPLETE,
                                                                ),
                                                            )
                                                            AppDataManager.refresh(force = true)
                                                        } else {
                                                            val msg =
                                                                saveResult
                                                                    .exceptionOrNull()
                                                                    ?.message
                                                                    ?.trim()
                                                                    .orEmpty()
                                                                    .ifBlank {
                                                                        "Couldn't save interests. Check your connection and try again."
                                                                    }
                                                            AppDataManager.postTransientUserMessage(msg)
                                                        }
                                                    }
                                                },
                                                canSkip = false,
                                            )
                                        }
                                    }
                                }
                            }
                            OnboardingShellChrome(
                                stepIndex = onboardingVm.visibleStepIndex(),
                                stepCount = onboardingVm.visibleStepCount(),
                                canGoBack = onboardingVm.canGoBack(),
                                onBack = { onboardingVm.goBack() },
                            )
                            UnifiedToastHost(
                                state = onboardingToastState,
                                opaque = true,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 24.dp),
                            )
                        }
                    } else if (onboardingHandoffActive || shouldStartOnboardingHandoff) {
                        AppShimmerScreen(isDarkMode = isDarkMode)
                    } else {
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
                        var currentRoute by remember { mutableStateOf("home") }
                        var previousRoute by remember { mutableStateOf("home") }
                        var transitionMode by remember { mutableStateOf(NavigationTransitionMode.Tap) }
                        // Route history stack for back navigation
                        val routeHistory = remember { mutableStateListOf("home") }
                        var showNfcScreen by remember { mutableStateOf(false) }
                        var pendingChatId by remember { mutableStateOf<String?>(null) }
                        var pendingBeaconId by remember { mutableStateOf<String?>(null) }
                        var pendingMapLayerFilter by remember { mutableStateOf<MapLayerFilter?>(null) }
                        var isConnectionsChatOpen by remember { mutableStateOf(false) }
                        var isSettingsSubpageOpen by remember { mutableStateOf(false) }
                        // Separate from session-open: true while chat owns the bottom edge.
                        var connectionsChatSuppressesTabBar by remember { mutableStateOf(false) }
                        var verifiedCliqueProximityAutofillIntent by remember { mutableStateOf<VerifiedCliqueProximityIntent?>(null) }

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
                            pendingBeaconId = null
                            pendingMapLayerFilter = null
                            return true
                        }

                        val focusManager = LocalFocusManager.current
                        val homeViewModel: HomeViewModel = viewModel { HomeViewModel() }
                        val mapViewModel: MapViewModel = viewModel { MapViewModel() }
                        val chatViewModel: ChatViewModel = viewModel { ChatViewModel() }
                        val shareableMapBeacons by mapViewModel.mapBeacons.collectAsState()
                        var hubChatArgs by remember { mutableStateOf<HubChatNavArgs?>(null) }
                        var hubVerifyInProgress by remember { mutableStateOf(false) }
                        var lastHubChatArgs by remember { mutableStateOf<HubChatNavArgs?>(null) }
                        var hubChatTransitionMode by remember { mutableStateOf(NavigationTransitionMode.Tap) }
                        var hubChatCloseJob by remember { mutableStateOf<Job?>(null) }

                        fun closeHubChat(mode: NavigationTransitionMode) {
                            hubChatCloseJob?.cancel()
                            hubChatTransitionMode = mode
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
                        var showUnifiedSearchSheet by remember { mutableStateOf(false) }
                        var eventsSheetExpanded by remember { mutableStateOf(false) }
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
                                        shouldRequest = !locationService.hasLocationPermission(),
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
                                hubChatArgs != null ||
                                (!isIOS && (showConnectionDisposableRoll || disposableRollOpening)) ||
                                callOwnsNativeChrome

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
                                                                    launchCommunityHubJoin(hubId)
                                                                },
                                                                onCommunityHubCreated = { hubId ->
                                                                    launchCommunityHubJoin(hubId, knownCreatorId = currentUser.id)
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
                                                                    onChatDismissed = { pendingChatId = null },
                                                                    onChatOpenStateChanged = { isOpen ->
                                                                        isConnectionsChatOpen = isOpen
                                                                        if (!isOpen) {
                                                                            pendingChatId = null
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
                                                                    launchCommunityHubJoin(hubId)
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
                                                        if (shouldRenderHomeSwipeUnderlay(currentRoute)) {
                                                            renderPrimaryScreen(previousKey)
                                                        }
                                                    },
                                                    currentContent = { renderPrimaryScreen(animatedScreen) },
                                                )
                                            } else {
                                                renderPrimaryScreen(animatedScreen)
                                            }
                                        }

                                        var hubChatRightToLeftPeek by remember {
                                            mutableStateOf<InteractiveSwipeBackRightToLeftPeek?>(null)
                                        }
                                        val hubSwipeDragPx = remember { mutableFloatStateOf(0f) }
                                        PlatformNativeNavigationBarSwipeReveal(hubSwipeDragPx)
                                        LaunchedEffect(hubChatArgs) {
                                            if (hubChatArgs != null) {
                                                lastHubChatArgs = hubChatArgs
                                            } else {
                                                hubChatRightToLeftPeek = null
                                                hubSwipeDragPx.floatValue = 0f
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
                                                                            launchCommunityHubJoin(hubId)
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

                                        // ── Hub Chat Overlay (mirrors ConnectionsScreen iOS chat overlay) ──
                                        val hubSlideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                                        val hubFadeSpec = tween<Float>(220, easing = LinearOutSlowInEasing)
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = hubChatArgs != null,
                                            modifier = Modifier.fillMaxSize(),
                                            enter =
                                                if (reduceMotion) {
                                                    fadeIn(animationSpec = tween(120))
                                                } else {
                                                    slideInHorizontally(animationSpec = hubSlideSpec, initialOffsetX = { it }) +
                                                        fadeIn(animationSpec = hubFadeSpec)
                                                },
                                            exit =
                                                if (hubChatTransitionMode == NavigationTransitionMode.GestureBack) {
                                                    ExitTransition.None
                                                } else if (reduceMotion) {
                                                    fadeOut(animationSpec = tween(90))
                                                } else {
                                                    slideOutHorizontally(animationSpec = hubSlideSpec, targetOffsetX = { it }) +
                                                        fadeOut(animationSpec = hubFadeSpec)
                                                },
                                            label = "hub_chat_overlay",
                                        ) {
                                            val activeHubArgs = lastHubChatArgs
                                            val hubUserId =
                                                when (val state = authViewModel.authState) {
                                                    is AuthState.Success -> state.userId
                                                    else -> ""
                                                }
                                            if (activeHubArgs != null && hubUserId.isNotEmpty()) {
                                                val hubKeyboardController = LocalSoftwareKeyboardController.current
                                                val hubFocusManager = LocalFocusManager.current
                                                InteractiveSwipeBackContainer(
                                                    enabled = true,
                                                    opaquePreviousBackground = false,
                                                    externalDragOffsetPx = hubSwipeDragPx,
                                                    onBehindLayersVisibleChanged = {},
                                                    onBack = {
                                                        hubFocusManager.clearFocus()
                                                        if (!isIOS) {
                                                            hubKeyboardController?.hide()
                                                        }
                                                        closeHubChat(NavigationTransitionMode.GestureBack)
                                                    },
                                                    rightToLeftPeek = hubChatRightToLeftPeek,
                                                    previousContent = {},
                                                    currentContent = {
                                                        HubChatScreen(
                                                            args = activeHubArgs,
                                                            currentUserId = hubUserId,
                                                            onNavigateBack = {
                                                                closeHubChat(NavigationTransitionMode.Tap)
                                                            },
                                                            resolveHubGatekeeperLocation = { resolveHubGatekeeperLocationForChat() },
                                                            integrateTimestampPeekWithSwipeBackContainer = true,
                                                            onRegisterSwipeBackRightToLeftPeek = {
                                                                hubChatRightToLeftPeek = it
                                                            },
                                                            parentInteractiveBackSwipePx = hubSwipeDragPx,
                                                        )
                                                    },
                                                )
                                            }
                                        }

                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = hubVerifyInProgress,
                                            enter =
                                                androidx.compose.animation.fadeIn(
                                                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                ),
                                            exit =
                                                androidx.compose.animation.fadeOut(
                                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                ),
                                        ) {
                                            val hubLoadTransition = rememberInfiniteTransition(label = "hub_verify_pulse")
                                            val hubPulseAlpha by hubLoadTransition.animateFloat(
                                                initialValue = 0.6f,
                                                targetValue = 1f,
                                                animationSpec =
                                                    infiniteRepeatable(
                                                        animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                                                        repeatMode = RepeatMode.Reverse,
                                                    ),
                                                label = "hub_verify_alpha",
                                            )
                                            val hubPulseMix by hubLoadTransition.animateFloat(
                                                initialValue = 0f,
                                                targetValue = 1f,
                                                animationSpec =
                                                    infiniteRepeatable(
                                                        animation = tween(durationMillis = 1400, easing = LinearOutSlowInEasing),
                                                        repeatMode = RepeatMode.Reverse,
                                                    ),
                                                label = "hub_verify_mix",
                                            )
                                            val hubAccentColor =
                                                androidx.compose.ui.graphics
                                                    .lerp(PrimaryBlue, LightBlue, hubPulseMix)
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                ChatAmbientMeshBackground(
                                                    connection = null,
                                                    isHubNeutral = true,
                                                    animateMesh = true,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                                    ) {
                                                        Box(
                                                            modifier =
                                                                Modifier
                                                                    .size(48.dp)
                                                                    .graphicsLayer { alpha = hubPulseAlpha },
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            AdaptiveCircularProgressIndicator(
                                                                modifier = Modifier.size(36.dp),
                                                                strokeWidth = 2.5.dp,
                                                                color = hubAccentColor,
                                                            )
                                                        }
                                                        Text(
                                                            text = "Joining hub…",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (!isInitialLoading && (pendingConnectionsCount > 0 || appError != null)) {
                                            Card(
                                                modifier =
                                                    Modifier
                                                        .align(Alignment.TopCenter)
                                                        .windowInsetsPadding(WindowInsets.statusBars)
                                                        .padding(
                                                            top = 8.dp,
                                                            start = 16.dp,
                                                            end = 16.dp,
                                                        ).fillMaxWidth(),
                                                colors =
                                                    CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                                    ),
                                                shape = RoundedCornerShape(18.dp),
                                            ) {
                                                Column(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    if (pendingConnectionsCount > 0) {
                                                        Text(
                                                            text = "$pendingConnectionsCount connection${if (pendingConnectionsCount == 1) "" else "s"} queued for sync.",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                    if (!appError.isNullOrBlank()) {
                                                        Text(
                                                            text = appError ?: "",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Multi-peer PendingConfirmation → TaggingContext(requiresSelection) when NFC UI is not showing.
                                        LaunchedEffect(connectionState, showNfcScreen) {
                                            if (showNfcScreen) return@LaunchedEffect
                                            val pending = connectionState as? ConnectionState.PendingConfirmation ?: return@LaunchedEffect
                                            if (pending.users.size >= 2) {
                                                connectionViewModel.promotePendingConfirmationToHostSelection(currentUser.id)
                                            }
                                        }

                                        if (connectionState is ConnectionState.TaggingContext &&
                                            !showNfcScreen &&
                                            !suppressConnectionContextSheet
                                        ) {
                                            val tagging = connectionState as ConnectionState.TaggingContext
                                            var calendarLockInProgress by remember { mutableStateOf(false) }
                                            val reconnectConnectionId = tagging.newConnections.firstOrNull()?.id
                                            val reconnectPeerId =
                                                tagging.targetUsers.firstOrNull { it.id != currentUser.id }?.id
                                                    ?: tagging.targetUsers.firstOrNull()?.id
                                            val sheetSelectableUsers =
                                                when {
                                                    tagging.selectableUsers.size >= 2 -> tagging.selectableUsers
                                                    tagging.targetUsers.size >= 2 -> tagging.targetUsers
                                                    else -> emptyList()
                                                }
                                            val sheetInitialSelectedIds =
                                                tagging.selectedPeerIds
                                                    .ifEmpty { sheetSelectableUsers.map { it.id }.toSet() }
                                            // requiresSelection must use Connect → confirmHostProximitySelection (not Save Encounter).
                                            val presentation =
                                                if (!tagging.isNewConnection && !tagging.requiresSelection) {
                                                    ConnectionContextPresentation.ReconnectEncounter
                                                } else {
                                                    ConnectionContextPresentation.NewSpark
                                                }

                                            fun taggingForSelectedPeers(selectedIds: Set<String>): ConnectionState.TaggingContext {
                                                if (selectedIds.isEmpty() || sheetSelectableUsers.size < 2) return tagging
                                                val filteredTargets =
                                                    tagging.targetUsers
                                                        .filter { it.id in selectedIds }
                                                        .ifEmpty { tagging.targetUsers }
                                                val filteredConnections =
                                                    tagging.newConnections
                                                        .filter { conn ->
                                                            conn.user_ids.any { uid -> uid in selectedIds && uid != currentUser.id }
                                                        }.ifEmpty { tagging.newConnections }
                                                return tagging.copy(
                                                    targetUsers = filteredTargets,
                                                    newConnections = filteredConnections,
                                                    memberUserIds =
                                                        tagging.memberUserIds
                                                            .filter { it in selectedIds || it == currentUser.id }
                                                            .ifEmpty { tagging.memberUserIds },
                                                    selectedPeerIds = selectedIds,
                                                )
                                            }
                                            val finishWithoutTags: () -> Unit = {
                                                connectionScope.launch {
                                                    if (tagging.requiresSelection) {
                                                        connectionViewModel.resetConnectionState()
                                                        return@launch
                                                    }
                                                    val noiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
                                                    val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                                                    val sensors =
                                                        captureConnectionSensorContext(
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
                                                connectedUsers = tagging.selectableUsers.ifEmpty { tagging.targetUsers },
                                                locationName = null,
                                                initialNoiseOptIn = ambientNoiseOptIn,
                                                noisePermissionGranted = ambientMonitor.hasPermission,
                                                onDismiss = finishWithoutTags,
                                                onSkip = finishWithoutTags,
                                                presentation = presentation,
                                                encounterSaveInProgress = tagging.encounterSubmitting,
                                                selectableUsers = sheetSelectableUsers,
                                                initialSelectedUserIds = sheetInitialSelectedIds,
                                                onSaveEncounter = { selectedIds ->
                                                    connectionScope.launch {
                                                        connectionViewModel.saveReconnectEncounter(
                                                            tagging = taggingForSelectedPeers(selectedIds),
                                                            currentUserId = currentUser.id,
                                                            ambientNoiseMonitor = ambientMonitor,
                                                            barometricHeightMonitor = baroMonitor,
                                                            ambientNoiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true,
                                                            barometricContextOptIn = tokenStorage.getBarometricContextOptIn() ?: true,
                                                        )
                                                    }
                                                },
                                                connectionId = reconnectConnectionId,
                                                peerUserId = reconnectPeerId,
                                                currentUserId = currentUser.id,
                                                lockIntentInProgress = calendarLockInProgress,
                                                onLockIntent = { gap: AvailabilityOverlapGap ->
                                                    connectionScope.launch {
                                                        calendarLockInProgress = true
                                                        val ok =
                                                            withContext(Dispatchers.Default) {
                                                                lockAvailabilityIntentForGap(
                                                                    repository = supabaseRepo,
                                                                    userId = currentUser.id,
                                                                    gap = gap,
                                                                )
                                                            }
                                                        calendarLockInProgress = false
                                                        if (ok) {
                                                            PlatformHapticsPolicy.successNotification()
                                                        }
                                                    }
                                                },
                                                onConfirm = { contextTag, noiseOptIn, selectedIds ->
                                                    if (tagging.isNewConnection) {
                                                        PlatformHapticsPolicy.successNotification()
                                                        connectionRevealState =
                                                            ConnectionRevealUiState(
                                                                methodLabel = "Tap",
                                                                phase = ConnectionRevealPhase.Connecting,
                                                            )
                                                    }
                                                    connectionScope.launch {
                                                        ambientNoiseOptIn = noiseOptIn
                                                        tokenStorage.saveAmbientNoiseOptIn(noiseOptIn)
                                                        val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                                                        val sensors =
                                                            captureConnectionSensorContext(
                                                                ambientNoiseMonitor = ambientMonitor,
                                                                barometricHeightMonitor = baroMonitor,
                                                                ambientNoiseOptIn = noiseOptIn,
                                                                barometricContextOptIn = baroOptIn,
                                                            )
                                                        if (tagging.requiresSelection) {
                                                            connectionViewModel.confirmHostProximitySelection(
                                                                selectedPeerIds = selectedIds.toList(),
                                                                currentUserId = currentUser.id,
                                                                contextTag = contextTag,
                                                                sensorContext = sensors,
                                                            )
                                                        } else {
                                                            connectionViewModel.saveContextTags(
                                                                tagging = taggingForSelectedPeers(selectedIds),
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
                                                    }
                                                },
                                            )
                                        }

                                        if (connectionState is ConnectionState.QrAwaitingContext &&
                                            !showNfcScreen &&
                                            !suppressConnectionContextSheet
                                        ) {
                                            val awaiting = connectionState as ConnectionState.QrAwaitingContext
                                            val cancelQr: () -> Unit = { connectionViewModel.resetConnectionState() }
                                            ConnectionContextSheet(
                                                connectedUsers = awaiting.targetUsers,
                                                locationName = null,
                                                initialNoiseOptIn = ambientNoiseOptIn,
                                                noisePermissionGranted = ambientMonitor.hasPermission,
                                                onDismiss = cancelQr,
                                                onSkip = cancelQr,
                                                presentation = ConnectionContextPresentation.QrFlow,
                                                onConfirm = { contextTag, noiseOptIn, _ ->
                                                    PlatformHapticsPolicy.successNotification()
                                                    connectionRevealState =
                                                        ConnectionRevealUiState(
                                                            methodLabel = "QR",
                                                            phase = ConnectionRevealPhase.Connecting,
                                                        )
                                                    connectionScope.launch {
                                                        ambientNoiseOptIn = noiseOptIn
                                                        tokenStorage.saveAmbientNoiseOptIn(noiseOptIn)
                                                        val venue = awaiting.venueId
                                                        val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                                                        coroutineScope {
                                                            val vibeDeferred =
                                                                async(Dispatchers.Default) {
                                                                    runCatching { HardwareVibeMonitor().takeSnapshot() }.getOrNull()
                                                                }
                                                            val locationDeferred =
                                                                async {
                                                                    when {
                                                                        !venue.isNullOrBlank() -> null
                                                                        AppDataManager.shouldCaptureLocationAtTap() ->
                                                                            resolveConnectionLocation(null)
                                                                        else -> null
                                                                    }
                                                                }
                                                            val sensorsDeferred =
                                                                async {
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
                                                            val weatherDeferred =
                                                                async(Dispatchers.Default) {
                                                                    if (
                                                                        la != null &&
                                                                        lo != null &&
                                                                        la.isFinite() &&
                                                                        lo.isFinite() &&
                                                                        !(la == 0.0 && lo == 0.0)
                                                                    ) {
                                                                        openMeteoWeather
                                                                            .fetchWeather(
                                                                                la,
                                                                                lo,
                                                                            )?.toConnectionPayloadWeatherJson()
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
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize()
                                                        .zIndex(10_000f),
                                            )
                                        }

                                        val rollConnectionId = connectionRollConnectionId
                                        val activeRollSession =
                                            pendingRollSession
                                                ?: rollConnectionId?.let { collaborationSessions[it] }
                                        AnimatedVisibility(
                                            visible = showConnectionDisposableRoll && rollConnectionId != null && activeRollSession != null,
                                            enter =
                                                fadeIn(animationSpec = tween(120, easing = FastOutSlowInEasing)) +
                                                    scaleIn(
                                                        initialScale = 0.08f,
                                                        animationSpec =
                                                            spring(
                                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                                stiffness = Spring.StiffnessMediumLow,
                                                            ),
                                                    ),
                                            exit =
                                                if (disposableRollExitWithScale) {
                                                    fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing)) +
                                                        scaleOut(
                                                            targetScale = 0.08f,
                                                            animationSpec =
                                                                spring(
                                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                                    stiffness = Spring.StiffnessMediumLow,
                                                                ),
                                                        )
                                                } else {
                                                    // After send: fade only — scale-out over an open thread flashed the chat.
                                                    fadeOut(animationSpec = tween(90, easing = FastOutSlowInEasing))
                                                },
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .zIndex(10_500f),
                                        ) {
                                            val cameraSession = activeRollSession
                                            val cameraConnectionId = rollConnectionId
                                            DisposableCameraView(
                                                onPhotoConfirmed = { bytes ->
                                                    if (cameraSession != null && !cameraConnectionId.isNullOrBlank()) {
                                                        connectionScope.launch {
                                                            chatViewModel.setCurrentUser(currentUser.id)
                                                            // Do not reload the open thread — that forced a full
                                                            // timeline refresh and briefly flickered the chat.
                                                            chatViewModel.sendDisposableRollPhoto(
                                                                bytes = bytes,
                                                                encounterId = cameraSession.encounterId,
                                                                collaborationTtlIso = cameraSession.collaborationTtlIso,
                                                            )
                                                        }
                                                    }
                                                    disposableRollExitWithScale = false
                                                    showConnectionDisposableRoll = false
                                                    pendingRollSession = null
                                                },
                                                onDismiss = {
                                                    disposableRollExitWithScale = true
                                                    showConnectionDisposableRoll = false
                                                    pendingRollSession = null
                                                },
                                                extraBottomPadding =
                                                    if (isIOS) {
                                                        // Native UITabBar stays visible under this overlay on iOS.
                                                        AppScreenDefaults.IosTabBarContentHeight
                                                    } else {
                                                        0.dp
                                                    },
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize(),
                                            )
                                        }

                                        val overlayState = globalCallOverlayState
                                        LaunchedEffect(overlayState, globalCallState) {
                                            if (
                                                overlayState is CallOverlayState.Ended &&
                                                (
                                                    lastActiveCallPresentedState.value is CallState.Connected ||
                                                        lastActiveCallPresentedState.value is CallState.Ended
                                                )
                                            ) {
                                                suppressEndedPreviewAfterActiveCall = true
                                            } else if (overlayState is CallOverlayState.Idle && globalCallState is CallState.Idle) {
                                                suppressEndedPreviewAfterActiveCall = false
                                            }
                                        }
                                        val suppressEndedForPresentation =
                                            suppressEndedPreviewAfterActiveCall ||
                                                (
                                                    overlayState is CallOverlayState.Ended &&
                                                        (
                                                            lastActiveCallPresentedState.value is CallState.Connected ||
                                                                lastActiveCallPresentedState.value is CallState.Ended
                                                        )
                                                )
                                        val callPresentation =
                                            CallOverlayTransitionPolicy.presentationFor(
                                                overlayState = overlayState,
                                                callState = globalCallState,
                                                suppressEndedPreviewAfterActiveCall = suppressEndedForPresentation,
                                            )
                                        val activeCallVisible =
                                            callPresentation == CallOverlayTransitionPolicy.Presentation.Active
                                        val callPreviewVisible =
                                            callPresentation == CallOverlayTransitionPolicy.Presentation.Preview
                                        val previewOverlayUiState =
                                            if (overlayState !is CallOverlayState.Idle) {
                                                overlayState
                                            } else {
                                                lastPreviewOverlayPresentedState.value
                                            }
                                        val callPreviewAlpha by animateFloatAsState(
                                            targetValue = if (callPreviewVisible) 1f else 0f,
                                            animationSpec =
                                                if (reduceMotion) {
                                                    tween(100)
                                                } else if (callPreviewVisible) {
                                                    MotionTokens.softEnterSpec()
                                                } else {
                                                    MotionTokens.softExitSpec()
                                                },
                                            label = "callPreviewOverlayAlpha",
                                        )
                                        val activeCallAlpha by animateFloatAsState(
                                            targetValue = if (activeCallVisible) 1f else 0f,
                                            animationSpec =
                                                if (reduceMotion) {
                                                    tween(100)
                                                } else if (activeCallVisible) {
                                                    MotionTokens.softEnterSpec()
                                                } else {
                                                    MotionTokens.softExitSpec()
                                                },
                                            label = "activeCallOverlayAlpha",
                                        )
                                        LaunchedEffect(
                                            overlayState,
                                            activeCallVisible,
                                            activeCallAlpha,
                                            suppressEndedPreviewAfterActiveCall,
                                        ) {
                                            if (
                                                suppressEndedPreviewAfterActiveCall &&
                                                overlayState is CallOverlayState.Ended &&
                                                !activeCallVisible &&
                                                activeCallAlpha <= 0.01f
                                            ) {
                                                suppressEndedPreviewAfterActiveCall = false
                                                CallSessionManager.dismissEndedCall()
                                            }
                                        }
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .zIndex(11_000f),
                                        ) {
                                            if (
                                                (callPreviewVisible || callPreviewAlpha > 0.01f) &&
                                                previewOverlayUiState !is CallOverlayState.Idle
                                            ) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxSize()
                                                            .graphicsLayer {
                                                                alpha = callPreviewAlpha
                                                            },
                                                ) {
                                                    CallPreviewOverlay(
                                                        overlayState = previewOverlayUiState,
                                                        currentUserId = appDataUser?.id,
                                                        onAccept = { CallSessionManager.acceptIncomingCall() },
                                                        onDecline = { CallSessionManager.declineIncomingCall() },
                                                        onCancel = { CallSessionManager.cancelCurrentCall() },
                                                        onDismissEnded = { CallSessionManager.dismissEndedCall() },
                                                    )
                                                }
                                            }

                                            if (activeCallVisible || activeCallAlpha > 0.01f) {
                                                // Do not put LiveKit TextureViewRenderer under graphicsLayer alpha —
                                                // Android TextureView composites black through alpha layers while audio still works.
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                ) {
                                                    ActiveCallOverlay(
                                                        callManager = CallSessionManager.callManager,
                                                        otherUserName = activeInvite?.counterpartName(appDataUser?.id) ?: "Connection",
                                                        state = activeCallUiState,
                                                        onEndCall = { CallSessionManager.endActiveCall() },
                                                        chromeAlpha = activeCallAlpha,
                                                        connectedAtMs = CallSessionManager.connectedAtMs,
                                                    )
                                                }
                                            }
                                        }

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
                            // Overlay (not Scaffold bottomBar) so tab content scrolls under a translucent bar.
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .zIndex(5f),
                            ) {
                                PlatformBottomBar(
                                    items = bottomNavItems,
                                    currentRoute = currentRoute,
                                    visible = !hideMainBottomBar,
                                    onItemSelected = { item ->
                                        navigateTo(item.route)
                                        hubChatCloseJob?.cancel()
                                        hubChatCloseJob = null
                                        hubChatArgs = null
                                        showMyQRCode = false
                                        showQRScanner = false
                                        showNfcScreen = false
                                        focusManager.clearFocus()
                                    },
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
                                        .padding(bottom = rememberBottomChromePadding() + 8.dp)
                                        .zIndex(6f),
                            )
                            if (showUnifiedSearchSheet) {
                                val searchUserId =
                                    when (val state = authViewModel.authState) {
                                        is AuthState.Success -> state.userId
                                        else -> ""
                                    }
                                if (searchUserId.isNotEmpty()) {
                                    UnifiedSearchSheet(
                                        onDismissRequest = { showUnifiedSearchSheet = false },
                                        userId = searchUserId,
                                        onNavigateToChat = { connectionId ->
                                            showUnifiedSearchSheet = false
                                            pendingChatId = connectionId
                                            navigateTo(NavigationItem.Connections.route)
                                        },
                                        onNavigateToMap = {
                                            showUnifiedSearchSheet = false
                                            navigateTo(NavigationItem.Map.route)
                                        },
                                        onNavigateToBeacon = { beaconId ->
                                            showUnifiedSearchSheet = false
                                            pendingBeaconId = beaconId
                                            navigateTo(NavigationItem.Map.route)
                                        },
                                        onNavigateToSettings = {
                                            showUnifiedSearchSheet = false
                                            navigateTo(NavigationItem.Settings.route)
                                        },
                                    )
                                }
                            }
                            GlobalTetherOverlay(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .zIndex(70f),
                            )
                        } // End of Scaffold wrapper Box
                    } // End of onboarding gate
                }
            } // End of Global Background Box
        } // End of ConnectionSensorMonitorsProvider
    } // End of PlatformThemeProvider
}

private enum class NavigationTransitionMode {
    Tap,
    GestureBack,
}

private fun isPrimaryNavRoute(route: String): Boolean =
    route == NavigationItem.Home.route ||
        route == NavigationItem.AddClick.route ||
        route == NavigationItem.Connections.route ||
        route == NavigationItem.Map.route ||
        route == NavigationItem.Settings.route
