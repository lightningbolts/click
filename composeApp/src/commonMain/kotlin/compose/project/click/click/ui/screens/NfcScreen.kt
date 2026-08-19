@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.calendar.AvailabilityOverlapGap // pragma: allowlist secret
import compose.project.click.click.calendar.lockAvailabilityIntentForGap // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.OpenMeteoWeatherService // pragma: allowlist secret
import compose.project.click.click.data.models.toConnectionPayloadWeatherJson // pragma: allowlist secret
import compose.project.click.click.data.models.toUserProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.proximity.MockProximityManager // pragma: allowlist secret
import compose.project.click.click.proximity.ProximityManager // pragma: allowlist secret
import compose.project.click.click.proximity.isSimulatorOrEmulatorRuntime // pragma: allowlist secret
import compose.project.click.click.sensors.AmbientNoiseMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.HardwareVibeMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.captureConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionContextPresentation // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionContextSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.HeaderBackIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.HeaderChromeIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeChromeAction // pragma: allowlist secret
import compose.project.click.click.ui.components.PageHeader // pragma: allowlist secret
import compose.project.click.click.ui.components.bottomChromePadding // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.openApplicationSystemSettings // pragma: allowlist secret
import compose.project.click.click.ui.utils.rememberLocationPermissionRequester // pragma: allowlist secret
import compose.project.click.click.ui.utils.rememberProximityHardwarePermissionRequester // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ConnectionViewModel // pragma: allowlist secret
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NfcScreen(
    userId: String,
    authToken: String,
    httpClient: HttpClient,
    proximityManager: ProximityManager,
    connectionViewModel: ConnectionViewModel,
    onConnectionCreated: (String) -> Unit,
    onBackPressed: () -> Unit,
    onProximityFinalizeStart: () -> Unit = {},
) {
    val connectionState by connectionViewModel.connectionState.collectAsState()
    val supportsTap = remember(proximityManager) { proximityManager.supportsTapExchange() }
    val showHowItWorksCard =
        remember(proximityManager) {
            proximityManager is MockProximityManager || isSimulatorOrEmulatorRuntime()
        }
    val capabilityNote = remember(proximityManager) { proximityManager.capabilityNote() }
    val ambientNoiseMonitor = AmbientNoiseMonitorProvider.current
    val barometricHeightMonitor = BarometricHeightMonitorProvider.current
    val tokenStorage = remember { createTokenStorage() }
    val openMeteoWeather = remember { OpenMeteoWeatherService() }
    val scope = rememberCoroutineScope()
    var ambientNoiseOptIn by remember { mutableStateOf(false) }
    val locationService =
        remember {
            LocationService()
        }
    val requestLocationPermissionThen = rememberLocationPermissionRequester()
    val requestProximityHardwarePermissions = rememberProximityHardwarePermissionRequester()

    LaunchedEffect(Unit) {
        ambientNoiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
    }

    LaunchedEffect(authToken) {
        connectionViewModel.prewarmBindProximityEdgeFunction(httpClient, authToken)
    }

    // GPS warm-up when the NFC sheet is visible (non-blocking).
    LaunchedEffect(Unit) {
        launch(Dispatchers.Default) {
            runCatching { locationService.getHighAccuracyLocation(4000L) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            proximityManager.stopAll()
        }
    }

    fun startTapProximityHandshake(skipLocation: Boolean) {
        connectionViewModel.startTapProximityHandshake(
            httpClient = httpClient,
            proximityManager = proximityManager,
            jwt = authToken,
            currentUserId = userId,
            locationService = locationService,
            skipLocation = skipLocation,
            ambientNoiseMonitor = ambientNoiseMonitor,
            barometricHeightMonitor = barometricHeightMonitor,
        )
    }

    fun startTapAfterHardwarePermissionGate() {
        if (proximityManager is MockProximityManager || isSimulatorOrEmulatorRuntime()) {
            startTapProximityHandshake(skipLocation = true)
            return
        }
        requestProximityHardwarePermissions { granted ->
            if (!granted) {
                connectionViewModel.showHardwarePermissionsMissing()
            } else if (!AppDataManager.shouldCaptureLocationAtTap()) {
                startTapProximityHandshake(skipLocation = true)
            } else if (!locationService.hasLocationPermission()) {
                requestLocationPermissionThen {
                    startTapProximityHandshake(skipLocation = !locationService.hasLocationPermission())
                }
            } else {
                startTapProximityHandshake(skipLocation = false)
            }
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .bottomChromePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header - consistent with MyQRCodeScreen and QRScannerScreen
                Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                    PageHeader(
                        title = "Tap to Connect",
                        subtitle = "BLE + ultrasonic handshake",
                        onNavigateBack = onBackPressed,
                        nativeTrailingActions =
                            listOf(
                                NativeChromeAction(
                                    sfSymbol = "gearshape",
                                    contentDescription = "Bluetooth and audio settings",
                                    onClick = { proximityManager.openRadiosSettings() },
                                ),
                            ),
                        navigationIcon = {
                            HeaderBackIconButton(onClick = onBackPressed)
                        },
                        actions = {
                            HeaderChromeIconButton(
                                icon = Icons.Default.Settings,
                                contentDescription = "Bluetooth and audio settings",
                                onClick = { proximityManager.openRadiosSettings() },
                            )
                        },
                    )
                }

                // Main content area
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = connectionState,
                        transitionSpec = {
                            (
                                fadeIn(spring(dampingRatio = 0.82f, stiffness = 420f)) +
                                    scaleIn(
                                        initialScale = 0.96f,
                                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f),
                                    )
                            ).togetherWith(
                                fadeOut(spring(dampingRatio = 0.9f, stiffness = 520f)) +
                                    scaleOut(
                                        targetScale = 0.98f,
                                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
                                    ),
                            ).using(SizeTransform(clip = false))
                        },
                        label = "tap_connect_state",
                    ) { state ->
                        val pulseHandshake =
                            state is ConnectionState.ProximityFetchingLocation ||
                                state is ConnectionState.ProximityHandshaking ||
                                state is ConnectionState.ProximityResolving ||
                                state is ConnectionState.Loading ||
                                state is ConnectionState.SecuringConnection
                        when (state) {
                            is ConnectionState.Idle -> {
                                NfcIdleContent(
                                    onOpenAppSettings = { openApplicationSystemSettings() },
                                    onStartScanning = {
                                        startTapAfterHardwarePermissionGate()
                                    },
                                    supportsTap = supportsTap,
                                    capabilityNote = capabilityNote,
                                    showHowItWorksCard = showHowItWorksCard,
                                    onOpenSettings = { proximityManager.openRadiosSettings() },
                                )
                            }
                            is ConnectionState.ProximityFetchingLocation -> {
                                NfcFetchingLocationContent(pulseActive = pulseHandshake)
                            }
                            is ConnectionState.ProximityHandshaking -> {
                                NfcScanningContent(pulseActive = pulseHandshake)
                            }
                            is ConnectionState.ProximityResolving -> {
                                NfcMatchingPeersContent(pulseActive = pulseHandshake)
                            }
                            is ConnectionState.PendingConfirmation -> {
                                // Single-peer keeps inline confirm; multi-peer is promoted to TaggingContext.
                                if (state.users.size <= 1) {
                                    ProximityConfirmConnectionsContent(
                                        users = state.users,
                                        onConfirmAll = {
                                            onProximityFinalizeStart()
                                            scope.launch {
                                                val vibe =
                                                    withContext(Dispatchers.Default) {
                                                        runCatching { HardwareVibeMonitor().takeSnapshot() }.getOrNull()
                                                    }
                                                val (la, lo) = connectionViewModel.lastProximityCoordinates()
                                                val weatherLabel =
                                                    withContext(Dispatchers.Default) {
                                                        if (
                                                            la != null &&
                                                            lo != null &&
                                                            la.isFinite() &&
                                                            lo.isFinite() &&
                                                            !(la == 0.0 && lo == 0.0)
                                                        ) {
                                                            openMeteoWeather.fetchWeather(la, lo)?.toConnectionPayloadWeatherJson()
                                                        } else {
                                                            null
                                                        }
                                                    }
                                                val noiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
                                                val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                                                val sensors =
                                                    captureConnectionSensorContext(
                                                        ambientNoiseMonitor = ambientNoiseMonitor,
                                                        barometricHeightMonitor = barometricHeightMonitor,
                                                        ambientNoiseOptIn = noiseOptIn,
                                                        barometricContextOptIn = baroOptIn,
                                                    )
                                                connectionViewModel.confirmProximityConnection(
                                                    peerUsers = state.users,
                                                    currentUserId = userId,
                                                    hardwareVibe = vibe,
                                                    weatherSnapshotLabel = weatherLabel,
                                                    sensorContext = sensors,
                                                )
                                            }
                                        },
                                        onCancel = { connectionViewModel.resetConnectionState() },
                                    )
                                } else {
                                    ProximityAwaitingContextContent(
                                        targetUsers = state.users.map { it.toUserProfile() },
                                    )
                                }
                            }
                            is ConnectionState.TaggingContext -> {
                                ProximityAwaitingContextContent(targetUsers = state.targetUsers)
                            }
                            is ConnectionState.Loading -> {
                                NfcCreatingConnectionContent(pulseActive = pulseHandshake)
                            }
                            is ConnectionState.SecuringConnection -> {
                                NfcCreatingConnectionContent(
                                    title = "Securing Connection...",
                                    pulseActive = pulseHandshake,
                                )
                            }
                            is ConnectionState.ProximityCapturedOfflineSyncing -> {
                                ProximityOfflineCapturedContent(
                                    message = state.message,
                                    onTryNow = {
                                        connectionViewModel.tryFlushPendingProximityHandshakes(
                                            jwt = authToken,
                                            currentUserId = userId,
                                        )
                                    },
                                    onDismiss = { connectionViewModel.resetConnectionState() },
                                )
                            }
                            is ConnectionState.ProximityHandshakePendingMatch -> {
                                ProximityPendingMatchContent(
                                    message = state.message,
                                    onDismiss = { connectionViewModel.resetConnectionState() },
                                )
                            }
                            is ConnectionState.Success -> {
                                NfcSuccessContent(
                                    connection = state.connection,
                                    connectedUser = state.connectedUser,
                                    onViewConnection = {
                                        onConnectionCreated(state.connection.id)
                                    },
                                    onCreateAnother = {
                                        connectionViewModel.resetConnectionState()
                                    },
                                )
                            }
                            is ConnectionState.QrAwaitingContext -> {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                            is ConnectionState.Error -> {
                                NfcErrorContent(
                                    message = state.message,
                                    onRetry = {
                                        startTapAfterHardwarePermissionGate()
                                    },
                                    onDismiss = { connectionViewModel.resetConnectionState() },
                                )
                            }
                        }
                    }
                }

                // Multi-peer PendingConfirmation → TaggingContext(requiresSelection) people+tags sheet.
                LaunchedEffect(connectionState) {
                    val pending = connectionState as? ConnectionState.PendingConfirmation ?: return@LaunchedEffect
                    if (pending.users.size >= 2) {
                        connectionViewModel.promotePendingConfirmationToHostSelection(userId)
                    }
                }

                if (connectionState is ConnectionState.TaggingContext) {
                    val tagging = connectionState as ConnectionState.TaggingContext
                    var calendarLockInProgress by remember { mutableStateOf(false) }
                    val supabaseRepo = remember { SupabaseRepository() }
                    val reconnectConnectionId = tagging.newConnections.firstOrNull()?.id
                    val reconnectPeerId =
                        tagging.targetUsers.firstOrNull { it.id != userId }?.id
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
                                    conn.user_ids.any { uid -> uid in selectedIds && uid != userId }
                                }.ifEmpty { tagging.newConnections }
                        return tagging.copy(
                            targetUsers = filteredTargets,
                            newConnections = filteredConnections,
                            memberUserIds =
                                tagging.memberUserIds
                                    .filter { it in selectedIds || it == userId }
                                    .ifEmpty { tagging.memberUserIds },
                            selectedPeerIds = selectedIds,
                        )
                    }
                    val finishWithoutTags: () -> Unit = {
                        scope.launch {
                            if (tagging.requiresSelection) {
                                connectionViewModel.resetConnectionState()
                                return@launch
                            }
                            val noiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
                            val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                            val sensors =
                                captureConnectionSensorContext(
                                    ambientNoiseMonitor = ambientNoiseMonitor,
                                    barometricHeightMonitor = barometricHeightMonitor,
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
                                ambientNoiseMonitor = ambientNoiseMonitor,
                                barometricHeightMonitor = barometricHeightMonitor,
                                ambientNoiseOptIn = noiseOptIn,
                                barometricContextOptIn = baroOptIn,
                            )
                        }
                    }
                    ConnectionContextSheet(
                        connectedUsers = tagging.selectableUsers.ifEmpty { tagging.targetUsers },
                        locationName = null,
                        initialNoiseOptIn = ambientNoiseOptIn,
                        noisePermissionGranted = ambientNoiseMonitor.hasPermission,
                        onSkip = finishWithoutTags,
                        onDismiss = finishWithoutTags,
                        presentation = presentation,
                        encounterSaveInProgress = tagging.encounterSubmitting,
                        selectableUsers = sheetSelectableUsers,
                        initialSelectedUserIds = sheetInitialSelectedIds,
                        onSaveEncounter = { selectedIds ->
                            scope.launch {
                                connectionViewModel.saveReconnectEncounter(
                                    tagging = taggingForSelectedPeers(selectedIds),
                                    currentUserId = userId,
                                    ambientNoiseMonitor = ambientNoiseMonitor,
                                    barometricHeightMonitor = barometricHeightMonitor,
                                    ambientNoiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true,
                                    barometricContextOptIn = tokenStorage.getBarometricContextOptIn() ?: true,
                                )
                            }
                        },
                        connectionId = reconnectConnectionId,
                        peerUserId = reconnectPeerId,
                        currentUserId = userId,
                        lockIntentInProgress = calendarLockInProgress,
                        onLockIntent = { gap: AvailabilityOverlapGap ->
                            scope.launch {
                                calendarLockInProgress = true
                                val ok =
                                    withContext(Dispatchers.Default) {
                                        lockAvailabilityIntentForGap(
                                            repository = supabaseRepo,
                                            userId = userId,
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
                                onProximityFinalizeStart()
                            }
                            scope.launch {
                                ambientNoiseOptIn = noiseOptIn
                                tokenStorage.saveAmbientNoiseOptIn(noiseOptIn)
                                val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                                val sensors =
                                    captureConnectionSensorContext(
                                        ambientNoiseMonitor = ambientNoiseMonitor,
                                        barometricHeightMonitor = barometricHeightMonitor,
                                        ambientNoiseOptIn = noiseOptIn,
                                        barometricContextOptIn = baroOptIn,
                                    )
                                if (tagging.requiresSelection) {
                                    connectionViewModel.confirmHostProximitySelection(
                                        selectedPeerIds = selectedIds.toList(),
                                        currentUserId = userId,
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
                                        ambientNoiseMonitor = ambientNoiseMonitor,
                                        barometricHeightMonitor = barometricHeightMonitor,
                                        ambientNoiseOptIn = noiseOptIn,
                                        barometricContextOptIn = baroOptIn,
                                    )
                                }
                            }
                        },
                    )
                }

                // Instructions at bottom
                if (connectionState is ConnectionState.ProximityHandshaking) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Stay close — broadcasting and listening for nearby taps.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    proximityManager.stopAll()
                                    connectionViewModel.resetConnectionState()
                                },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                    ),
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}
