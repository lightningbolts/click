package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.proximity.ProximityManager
import compose.project.click.click.sensors.AmbientNoiseMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitorProvider // pragma: allowlist secret
import compose.project.click.click.sensors.captureConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.OpenMeteoWeatherService
import compose.project.click.click.data.models.toConnectionPayloadWeatherJson
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.UserProfile
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.ConnectionContextSheet
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.utils.openApplicationSystemSettings
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.utils.rememberLocationPermissionRequester
import compose.project.click.click.sensors.HardwareVibeMonitor
import compose.project.click.click.viewmodel.ConnectionState
import compose.project.click.click.viewmodel.ConnectionViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

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
    val capabilityNote = remember(proximityManager) { proximityManager.capabilityNote() }
    val ambientNoiseMonitor = AmbientNoiseMonitorProvider.current
    val barometricHeightMonitor = BarometricHeightMonitorProvider.current
    val tokenStorage = remember { createTokenStorage() }
    val openMeteoWeather = remember { OpenMeteoWeatherService() }
    val scope = rememberCoroutineScope()
    var ambientNoiseOptIn by remember { mutableStateOf(false) }
    val locationService = remember { compose.project.click.click.utils.LocationService() }
    val requestLocationPermissionThen = rememberLocationPermissionRequester()

    LaunchedEffect(Unit) {
        ambientNoiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
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

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header - consistent with MyQRCodeScreen and QRScannerScreen
                Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                    PageHeader(
                        title = "Tap to Connect",
                        subtitle = "BLE + ultrasonic handshake",
                        navigationIcon = {
                            IconButton(onClick = onBackPressed) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { proximityManager.openRadiosSettings() }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Bluetooth and audio settings",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
                }

                // Main content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = connectionState) {
                        is ConnectionState.Idle -> {
                            NfcIdleContent(
                                onOpenAppSettings = { openApplicationSystemSettings() },
                                onStartScanning = {
                                    if (!AppDataManager.shouldCaptureLocationAtTap()) {
                                        connectionViewModel.startTapProximityHandshake(
                                            httpClient = httpClient,
                                            proximityManager = proximityManager,
                                            jwt = authToken,
                                            currentUserId = userId,
                                            locationService = locationService,
                                            skipLocation = true,
                                            barometricHeightMonitor = barometricHeightMonitor,
                                        )
                                    } else if (!locationService.hasLocationPermission()) {
                                        requestLocationPermissionThen {
                                            connectionViewModel.startTapProximityHandshake(
                                                httpClient = httpClient,
                                                proximityManager = proximityManager,
                                                jwt = authToken,
                                                currentUserId = userId,
                                                locationService = locationService,
                                                skipLocation = !locationService.hasLocationPermission(),
                                                barometricHeightMonitor = barometricHeightMonitor,
                                            )
                                        }
                                    } else {
                                        connectionViewModel.startTapProximityHandshake(
                                            httpClient = httpClient,
                                            proximityManager = proximityManager,
                                            jwt = authToken,
                                            currentUserId = userId,
                                            locationService = locationService,
                                            skipLocation = false,
                                            barometricHeightMonitor = barometricHeightMonitor,
                                        )
                                    }
                                },
                                supportsTap = supportsTap,
                                capabilityNote = capabilityNote,
                                onOpenSettings = { proximityManager.openRadiosSettings() },
                            )
                        }
                        is ConnectionState.ProximityFetchingLocation -> {
                            NfcFetchingLocationContent()
                        }
                        is ConnectionState.ProximityHandshaking -> {
                            NfcScanningContent()
                        }
                        is ConnectionState.ProximityResolving -> {
                            NfcMatchingPeersContent()
                        }
                        is ConnectionState.PendingConfirmation -> {
                            ProximityConfirmConnectionsContent(
                                users = state.users,
                                onConfirmAll = {
                                    onProximityFinalizeStart()
                                    scope.launch {
                                        val vibe = withContext(Dispatchers.Default) {
                                            runCatching { HardwareVibeMonitor().takeSnapshot() }.getOrNull()
                                        }
                                        val (la, lo) = connectionViewModel.lastProximityCoordinates()
                                        val weatherLabel = withContext(Dispatchers.Default) {
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
                                        connectionViewModel.confirmProximityConnection(
                                            peerUsers = state.users,
                                            currentUserId = userId,
                                            hardwareVibe = vibe,
                                            weatherSnapshotLabel = weatherLabel,
                                        )
                                    }
                                },
                                onCancel = { connectionViewModel.resetConnectionState() },
                            )
                        }
                        is ConnectionState.TaggingContext -> {
                            ProximityAwaitingContextContent(targetUsers = state.targetUsers)
                        }
                        is ConnectionState.Loading -> {
                            NfcCreatingConnectionContent()
                        }
                        is ConnectionState.ProximityCapturedOfflineSyncing -> {
                            ProximityOfflineCapturedContent(
                                message = state.message,
                                onTryNow = {
                                    connectionViewModel.tryFlushPendingProximityHandshakes(authToken)
                                },
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
                                }
                            )
                        }
                        is ConnectionState.QrAwaitingContext -> {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                        is ConnectionState.Error -> {
                            NfcErrorContent(
                                message = state.message,
                                onRetry = {
                                    connectionViewModel.startTapProximityHandshake(
                                        httpClient = httpClient,
                                        proximityManager = proximityManager,
                                        jwt = authToken,
                                        currentUserId = userId,
                                        locationService = locationService,
                                        skipLocation = false,
                                        barometricHeightMonitor = barometricHeightMonitor,
                                    )
                                },
                                onDismiss = { connectionViewModel.resetConnectionState() }
                            )
                        }
                    }
                }

                if (connectionState is ConnectionState.TaggingContext) {
                    val tagging = connectionState as ConnectionState.TaggingContext
                    val finishWithoutTags: () -> Unit = {
                        scope.launch {
                            val noiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
                            val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                            val sensors = captureConnectionSensorContext(
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
                            )
                        }
                    }
                    ConnectionContextSheet(
                        connectedUsers = tagging.targetUsers,
                        locationName = null,
                        initialNoiseOptIn = ambientNoiseOptIn,
                        noisePermissionGranted = ambientNoiseMonitor.hasPermission,
                        onSkip = finishWithoutTags,
                        onDismiss = finishWithoutTags,
                        onConfirm = { contextTag, noiseOptIn ->
                            onProximityFinalizeStart()
                            scope.launch {
                                ambientNoiseOptIn = noiseOptIn
                                tokenStorage.saveAmbientNoiseOptIn(noiseOptIn)
                                val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true
                                val sensors = captureConnectionSensorContext(
                                    ambientNoiseMonitor = ambientNoiseMonitor,
                                    barometricHeightMonitor = barometricHeightMonitor,
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
                                )
                            }
                        },
                    )
                }

                // Instructions at bottom
                if (connectionState is ConnectionState.ProximityHandshaking) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Stay close — broadcasting and listening for nearby taps.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    proximityManager.stopAll()
                                    connectionViewModel.resetConnectionState()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
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

@Composable
private fun ProximityConfirmConnectionsContent(
    users: List<User>,
    onConfirmAll: () -> Unit,
    onCancel: () -> Unit,
) {
    val headline = if (users.size <= 1) {
        "Confirm your tap"
    } else {
        "Confirm this group"
    }
    val subtitle = if (users.size <= 1) {
        "You'll add optional context next."
    } else {
        "You'll connect with everyone listed, then add one shared context tag."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(users, key = { it.id }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.name ?: "User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (!user.email.isNullOrBlank()) {
                                Text(
                                    text = user.email!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onConfirmAll,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
        ) {
            Text(
                if (users.size <= 1) "Connect" else "Connect with everyone",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ProximityOfflineCapturedContent(
    message: String,
    onTryNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            tint = PrimaryBlue.copy(alpha = 0.9f),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Connection Captured",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onTryNow,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
        ) {
            Text("Try sync now", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onDismiss) {
            Text("Dismiss")
        }
    }
}

@Composable
private fun ProximityAwaitingContextContent(targetUsers: List<UserProfile>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = Color(0xFF4CAF50),
        )
        Text(
            text = if (targetUsers.size <= 1) "You're connected" else "You're all connected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Add a quick tag below (optional). It applies to this meetup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun NfcIdleContent(
    onOpenAppSettings: () -> Unit,
    onStartScanning: () -> Unit,
    supportsTap: Boolean,
    capabilityNote: String,
    onOpenSettings: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.BluetoothSearching,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = if (supportsTap) {
                PrimaryBlue
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (supportsTap) "Ready to Connect" else "Tap to Connect unavailable",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (supportsTap) {
                "Tap Connect together with someone nearby. Both phones should enable Bluetooth and microphone access for the handshake."
            } else {
                capabilityNote
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "How Tap to Connect works",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = capabilityNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (supportsTap) {
            Button(
                onClick = onStartScanning,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Connect",
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onOpenAppSettings) {
                Text(
                    "Open app settings",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue
                )
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Settings", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun NfcFetchingLocationContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated GPS icon
        val infiniteTransition = rememberInfiniteTransition()
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .alpha(alpha),
            tint = PrimaryBlue
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Fetching Location...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Getting your GPS coordinates for this connection",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        AdaptiveCircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = PrimaryBlue,
            strokeWidth = 3.dp
        )
    }
}

@Composable
private fun NfcScanningContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated NFC icon
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing circles
            repeat(3) { index ->
                val delay = index * 333
                val circleScale by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing, delayMillis = delay),
                        repeatMode = RepeatMode.Restart
                    )
                )

                val circleAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing, delayMillis = delay),
                        repeatMode = RepeatMode.Restart
                    )
                )

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(circleScale)
                        .alpha(circleAlpha)
                        .background(
                            color = PrimaryBlue,
                            shape = CircleShape
                        )
                )
            }

            Icon(
                Icons.Default.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale)
                    .alpha(alpha),
                tint = PrimaryBlue
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Handshaking…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Stay within a few feet — BLE and audio are active",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun NfcSendingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AdaptiveCircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = PrimaryBlue,
            strokeWidth = 6.dp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Sharing Info...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NfcUserDetectedContent(
    userId: String,
    userName: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = PrimaryBlue
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "User Detected!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = userName ?: "Unknown User",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "ID: ${userId.take(8)}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun NfcCreatingConnectionContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AdaptiveCircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = PrimaryBlue,
            strokeWidth = 6.dp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Creating Connection...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NfcMatchingPeersContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AdaptiveCircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = PrimaryBlue,
            strokeWidth = 6.dp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Matching nearby taps…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Hang tight — this step is quick.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun NfcSuccessContent(
    connection: compose.project.click.click.data.models.Connection,
    connectedUser: compose.project.click.click.data.models.User?,
    onViewConnection: () -> Unit,
    onCreateAnother: () -> Unit
) {
    var showConfetti by remember { mutableStateOf(true) }
    var sayHiMessage by remember { mutableStateOf("") }
    var messageSent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3000)
        showConfetti = false
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connection Created!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show connected user's name if available
        if (connectedUser?.name != null) {
            Text(
                text = "You met ${connectedUser.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ---- Context Tag / Location Info ----
        if (connection.semanticLocation != null || connection.displayLocationLabel != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = connection.displayLocationLabel ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 48-hour async prompt
        Text(
            text = "Say hi within 48 hours to keep this connection alive",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Common Ground Section ----
        if (connectedUser != null && connectedUser.tags.isNotEmpty()) {
            CommonGroundSection(tags = connectedUser.tags)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ---- "Say Hi" message input ----
        if (!messageSent) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextField(
                        value = sayHiMessage,
                        onValueChange = { sayHiMessage = it },
                        placeholder = {
                            Text(
                                "Say hi! 👋",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = PrimaryBlue
                        ),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            if (sayHiMessage.trim().isNotEmpty()) {
                                messageSent = true
                                // Navigate to the connection chat where the message will be sent
                                onViewConnection()
                            }
                        },
                        enabled = sayHiMessage.trim().isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (sayHiMessage.trim().isNotEmpty()) PrimaryBlue 
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Message sent!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onViewConnection,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                Icon(Icons.Default.ChatBubble, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Connection", fontSize = 18.sp)
            }

            OutlinedButton(
                onClick = onCreateAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Another", fontSize = 18.sp)
            }
        }
    }
}

/**
 * "Common Ground" section — displays overlapping interest tags
 * in vibrant neon-highlighted chips for immediate conversation starters.
 */
@Composable
private fun CommonGroundSection(tags: List<String>) {
    if (tags.isEmpty()) return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = NeonPurple
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Common Ground",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = NeonPurple
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Display up to 3 tags as neon chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tags.take(3).forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryBlue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = tag,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonPurple
                    )
                }
            }
        }
    }
}

@Composable
private fun NfcErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Dismiss")
            }

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                Text("Try Again")
            }
        }
    }
}

