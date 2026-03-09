package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.nfc.NfcManager
import compose.project.click.click.nfc.NfcSupportProfile
import compose.project.click.click.sensors.rememberAmbientNoiseMonitor
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.ConnectionContextSheet
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.theme.*
import compose.project.click.click.viewmodel.NfcConnectionState
import compose.project.click.click.viewmodel.NfcViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun NfcScreen(
    userId: String,
    authToken: String,
    nfcManager: NfcManager,
    onConnectionCreated: (String) -> Unit,
    onBackPressed: () -> Unit
) {
    val viewModel: NfcViewModel = viewModel { NfcViewModel(nfcManager) }
    val connectionState by viewModel.connectionState.collectAsState()
    val supportProfile = remember(nfcManager) { nfcManager.supportProfile() }
    val ambientNoiseMonitor = rememberAmbientNoiseMonitor()
    val tokenStorage = remember { createTokenStorage() }
    val scope = rememberCoroutineScope()
    var ambientNoiseOptIn by remember { mutableStateOf(false) }
    var pendingUser by remember { mutableStateOf<Pair<String, String?>?>(null) }

    LaunchedEffect(Unit) {
        ambientNoiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: false
    }

    LaunchedEffect(userId) {
        viewModel.setCurrentUser(userId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScanning()
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
                        subtitle = if (supportProfile.supportsPhoneToPhoneExchange) {
                            "Hold near another phone"
                        } else {
                            "Near-field connection tools"
                        },
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
                            IconButton(onClick = { viewModel.openNfcSettings() }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "NFC Settings",
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
                        is NfcConnectionState.Idle -> {
                            NfcIdleContent(
                                onStartScanning = { viewModel.startScanning() },
                                isNfcAvailable = viewModel.isNfcAvailable(),
                                isNfcEnabled = viewModel.isNfcEnabled(),
                                supportProfile = supportProfile,
                                onOpenSettings = { viewModel.openNfcSettings() }
                            )
                        }
                        is NfcConnectionState.FetchingLocation -> {
                            NfcFetchingLocationContent()
                        }
                        is NfcConnectionState.Scanning -> {
                            NfcScanningContent()
                        }
                        is NfcConnectionState.Sending -> {
                            NfcSendingContent()
                        }
                        is NfcConnectionState.UserDetected -> {
                            NfcUserDetectedContent(
                                userId = state.userId,
                                userName = state.userName,
                                onConfirm = { pendingUser = state.userId to state.userName },
                                onCancel = { viewModel.resetState() }
                            )
                        }
                        is NfcConnectionState.CreatingConnection -> {
                            NfcCreatingConnectionContent()
                        }
                        is NfcConnectionState.Success -> {
                            NfcSuccessContent(
                                connection = state.connection,
                                connectedUser = state.connectedUser,
                                onViewConnection = {
                                    onConnectionCreated(state.connection.id)
                                },
                                onCreateAnother = {
                                    viewModel.resetState()
                                    viewModel.startScanning()
                                }
                            )
                        }
                        is NfcConnectionState.Error -> {
                            NfcErrorContent(
                                message = state.message,
                                onRetry = { viewModel.startScanning() },
                                onDismiss = { viewModel.resetState() }
                            )
                        }
                    }
                }

                pendingUser?.let { (detectedUserId, detectedUserName) ->
                    ConnectionContextSheet(
                        otherUserName = detectedUserName,
                        locationName = null,
                        initialNoiseOptIn = ambientNoiseOptIn,
                        noisePermissionGranted = ambientNoiseMonitor.hasPermission,
                        onDismiss = { pendingUser = null },
                        onConfirm = { contextTag, noiseOptIn ->
                            scope.launch {
                                ambientNoiseOptIn = noiseOptIn
                                tokenStorage.saveAmbientNoiseOptIn(noiseOptIn)
                                val noiseLevel = if (noiseOptIn) {
                                    ambientNoiseMonitor.sampleNoiseLevel()
                                } else {
                                    null
                                }
                                pendingUser = null
                                viewModel.createConnection(
                                    otherUserId = detectedUserId,
                                    contextTag = contextTag?.label,
                                    contextTagObject = contextTag,
                                    noiseLevelCategory = noiseLevel
                                )
                            }
                        }
                    )
                }

                // Instructions at bottom
                if (connectionState is NfcConnectionState.Scanning) {
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
                                text = if (supportProfile.supportsPhoneToPhoneExchange) {
                                    "Hold your phone near another device"
                                } else {
                                    "Hold near a compatible NFC tag or supported reader"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.stopScanning() },
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
private fun NfcIdleContent(
    onStartScanning: () -> Unit,
    isNfcAvailable: Boolean,
    isNfcEnabled: Boolean,
    supportProfile: NfcSupportProfile,
    onOpenSettings: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = if (isNfcAvailable && isNfcEnabled) {
                PrimaryBlue
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = when {
                !isNfcAvailable -> "NFC Not Available"
                !isNfcEnabled -> "NFC Disabled"
                else -> "Ready to Connect"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                !isNfcAvailable -> "Your device doesn't support NFC"
                !isNfcEnabled -> "Enable NFC to connect with others"
                supportProfile.supportsPhoneToPhoneExchange -> "Tap phones together to create a connection"
                supportProfile.canReadTags -> "This build can read compatible NFC tags, but direct phone-to-phone taps are still limited."
                else -> supportProfile.note
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
                    text = "Current NFC support",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = supportProfile.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isNfcAvailable && isNfcEnabled) {
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
                Icon(Icons.Default.Nfc, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (supportProfile.supportsPhoneToPhoneExchange) {
                        "Start Scanning"
                    } else {
                        "Start NFC Reader"
                    },
                    fontSize = 18.sp
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

        CircularProgressIndicator(
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
                Icons.Default.Nfc,
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
            text = "Scanning...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Bring your phone close to another device",
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
        CircularProgressIndicator(
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
        CircularProgressIndicator(
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
        if (connection.semantic_location != null || connection.displayLocationLabel != null) {
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

