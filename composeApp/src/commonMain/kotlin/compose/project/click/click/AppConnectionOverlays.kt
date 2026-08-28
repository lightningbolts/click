@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.calendar.AvailabilityOverlapGap // pragma: allowlist secret
import compose.project.click.click.calendar.lockAvailabilityIntentForGap // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSession // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.OpenMeteoWeatherService // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.toConnectionPayloadWeatherJson // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.sensors.AmbientNoiseMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.HardwareVibeMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.captureConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.ui.camera.DisposableCameraView // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.BindPlatformNativeNavigationBar // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionContextPresentation // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionContextSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealOverlay // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealPhase // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionRevealUiState // pragma: allowlist secret
import compose.project.click.click.ui.components.CoverPlatformOverlayNavigationBar // pragma: allowlist secret
import compose.project.click.click.ui.screens.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionViewModel // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppConnectionOverlays(
    connectionState: ConnectionState,
    showNfcScreen: Boolean,
    suppressConnectionContextSheet: Boolean,
    currentUser: User,
    isIOS: Boolean,
    connectionViewModel: ConnectionViewModel,
    chatViewModel: ChatViewModel,
    connectionScope: CoroutineScope,
    tokenStorage: TokenStorage,
    ambientMonitor: AmbientNoiseMonitor,
    baroMonitor: BarometricHeightMonitor,
    supabaseRepo: SupabaseRepository,
    openMeteoWeather: OpenMeteoWeatherService,
    collaborationSessions: Map<String, CollaborationSession>,
    resolveConnectionLocation: suspend (LocationResult?) -> LocationResult?,
    ambientNoiseOptInState: MutableState<Boolean>,
    connectionRevealStateState: MutableState<ConnectionRevealUiState?>,
    connectionRollConnectionIdState: MutableState<String?>,
    pendingRollSessionState: MutableState<CollaborationSession?>,
    showConnectionDisposableRollState: MutableState<Boolean>,
    disposableRollExitWithScaleState: MutableState<Boolean>,
) {
    var ambientNoiseOptIn by ambientNoiseOptInState
    var connectionRevealState by connectionRevealStateState
    val connectionRollConnectionId by connectionRollConnectionIdState
    var pendingRollSession by pendingRollSessionState
    var showConnectionDisposableRoll by showConnectionDisposableRollState
    var disposableRollExitWithScale by disposableRollExitWithScaleState
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
        if (isIOS) {
            CoverPlatformOverlayNavigationBar()
            BindPlatformNativeNavigationBar(
                title = "Click Drops",
                onNavigateBack = {
                    disposableRollExitWithScale = true
                    showConnectionDisposableRoll = false
                    pendingRollSession = null
                },
                leadingClose = true,
            )
        }
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
}
