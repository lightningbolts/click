package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import compose.project.click.click.qr.QrParseResult
import compose.project.click.click.qr.parseQrCode
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.components.QRScanner
import compose.project.click.click.ui.components.QrScannerDetection
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.math.roundToInt

/**
 * Result types for QR code scanning.
 * Now supports both token-based (new) and userId-only (legacy) results.
 */
sealed class QRScanResult {
    /** Token-based scan — includes the single-use QR token for server-side redemption. */
    data class TokenSuccess(val userId: String, val token: String) : QRScanResult()
    /** Legacy scan — userId only, no token timing data. */
    data class LegacySuccess(val userId: String) : QRScanResult()
    /** Invalid QR format. */
    data class InvalidFormat(val rawData: String) : QRScanResult()
    /** Expired token. */
    data class Expired(val rawData: String) : QRScanResult()
}

private enum class QrScannerPresentationState {
    Searching,
    TargetAcquired,
    Connecting,
    Error
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onQRCodeScanned: (String) -> Unit,
    onQRCodeScannedWithToken: ((userId: String, token: String) -> Unit)? = null,
    onNavigateBack: () -> Unit
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scope = rememberCoroutineScope()
    
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lastScannedRaw by remember { mutableStateOf<String?>(null) }
    var liveDetection by remember { mutableStateOf<QrScannerDetection?>(null) }
    var isProcessingResult by remember { mutableStateOf(false) }
    
    LaunchedEffect(showError) {
        if (showError) {
            delay(4000)
            showError = false
            lastScannedRaw = null
        }
    }

    val presentationState = when {
        showError -> QrScannerPresentationState.Error
        isProcessingResult -> QrScannerPresentationState.Connecting
        liveDetection != null -> QrScannerPresentationState.TargetAcquired
        else -> QrScannerPresentationState.Searching
    }

    fun lockAndContinue(onContinue: () -> Unit) {
        if (isProcessingResult) return
        isProcessingResult = true
        scope.launch {
            delay(420)
            onContinue()
        }
    }
    
    fun handleQRResult(qrData: String) {
        if (isProcessingResult) return
        if (qrData == lastScannedRaw && showError) return

        when (val result = parseQrCode(qrData)) {
            is QrParseResult.TokenBased -> {
                val now = Clock.System.now().toEpochMilliseconds()

                if (now > result.payload.exp) {
                    lastScannedRaw = qrData
                    errorMessage = "This QR code has expired. Ask them to generate a new one."
                    showError = true
                    return
                }

                if (onQRCodeScannedWithToken != null) {
                    lockAndContinue {
                        onQRCodeScannedWithToken(result.payload.userId, result.payload.token)
                    }
                } else {
                    lockAndContinue {
                        onQRCodeScanned(result.payload.userId)
                    }
                }
            }
            is QrParseResult.Legacy -> {
                lockAndContinue {
                    onQRCodeScanned(result.userId)
                }
            }
            is QrParseResult.Invalid -> {
                lastScannedRaw = qrData
                errorMessage = "This QR code isn't a Click profile. Please scan a valid Click QR code."
                showError = true
            }
        }
    }

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                    PageHeader(
                        title = "Scan QR Code",
                        subtitle = when (presentationState) {
                            QrScannerPresentationState.Searching -> "Point camera at a Click code"
                            QrScannerPresentationState.TargetAcquired -> "Hold steady, almost there"
                            QrScannerPresentationState.Connecting -> "Locking in the connection"
                            QrScannerPresentationState.Error -> "That code doesn't look right"
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            width = 1.dp,
                            color = if (showError) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    QRScanner(
                        modifier = Modifier.fillMaxSize(),
                        isActive = !isProcessingResult,
                        onDetectionChanged = { detection ->
                            if (!showError && !isProcessingResult) {
                                liveDetection = detection
                            }
                        },
                        onResult = { qrData -> handleQRResult(qrData) }
                    )

                    ScannerLensOverlay(
                        modifier = Modifier.fillMaxSize(),
                        state = presentationState,
                        detection = liveDetection
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    tonalElevation = 2.dp,
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = when (presentationState) {
                                QrScannerPresentationState.Error -> MaterialTheme.colorScheme.errorContainer
                                QrScannerPresentationState.Connecting -> PrimaryBlue.copy(alpha = 0.18f)
                                QrScannerPresentationState.TargetAcquired -> PrimaryBlue.copy(alpha = 0.18f)
                                QrScannerPresentationState.Searching -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (presentationState) {
                                        QrScannerPresentationState.Error -> Icons.Filled.ErrorOutline
                                        QrScannerPresentationState.Connecting -> Icons.Filled.CheckCircle
                                        QrScannerPresentationState.TargetAcquired -> Icons.Filled.CenterFocusStrong
                                        QrScannerPresentationState.Searching -> Icons.Filled.QrCodeScanner
                                    },
                                    contentDescription = null,
                                    tint = when (presentationState) {
                                        QrScannerPresentationState.Error -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (presentationState) {
                                    QrScannerPresentationState.Searching -> "Scanning for a Click profile"
                                    QrScannerPresentationState.TargetAcquired -> "QR found. Hold your frame for the reveal."
                                    QrScannerPresentationState.Connecting -> "Connection detected. Opening the handoff."
                                    QrScannerPresentationState.Error -> "Invalid code detected"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (presentationState) {
                                    QrScannerPresentationState.Searching -> "Keep the full code inside the frame for a smooth lock-on."
                                    QrScannerPresentationState.TargetAcquired -> "The camera has eyes on the code now."
                                    QrScannerPresentationState.Connecting -> "This should feel immediate, not abrupt."
                                    QrScannerPresentationState.Error -> errorMessage.ifBlank { "Try a valid Click QR code instead." }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            AnimatedVisibility(
                visible = showError,
                enter = fadeIn(animationSpec = tween(200)) + 
                        slideInVertically(
                            animationSpec = tween(300),
                            initialOffsetY = { it }
                        ),
                exit = fadeOut(animationSpec = tween(200)) + 
                       slideOutVertically(
                           animationSpec = tween(300),
                           targetOffsetY = { it }
                       ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .padding(bottom = 48.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Invalid QR Code",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showError,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Keep scanning or tap back to exit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ScannerLensOverlay(
    modifier: Modifier,
    state: QrScannerPresentationState,
    detection: QrScannerDetection?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "qr_scanner_overlay")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line_progress"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "target_pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "target_pulse_alpha"
    )

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val badgeTopPadding = 18.dp
        val badgeBottomSpacing = 18.dp
        val frameHorizontalPadding = 44.dp
        val frameBottomPadding = 44.dp
        val frameTopPadding = badgeTopPadding + 40.dp + badgeBottomSpacing
        val frameWidth = maxWidth - (frameHorizontalPadding * 2)
        val frameHeight = maxHeight - frameTopPadding - frameBottomPadding

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = frameHorizontalPadding,
                    top = frameTopPadding,
                    end = frameHorizontalPadding,
                    bottom = frameBottomPadding
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = when (state) {
                            QrScannerPresentationState.Error -> listOf(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
                            )
                            QrScannerPresentationState.Connecting -> listOf(
                                PrimaryBlue.copy(alpha = 0.95f),
                                PrimaryBlue.copy(alpha = 0.3f)
                            )
                            QrScannerPresentationState.TargetAcquired -> listOf(
                                Color.White.copy(alpha = 0.95f),
                                PrimaryBlue.copy(alpha = 0.35f)
                            )
                            QrScannerPresentationState.Searching -> listOf(
                                Color.White.copy(alpha = 0.7f),
                                Color.White.copy(alpha = 0.16f)
                            )
                        }
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        )

        if (state != QrScannerPresentationState.Error) {
            Box(
                modifier = Modifier
                    .padding(horizontal = frameHorizontalPadding + 20.dp)
                    .offset(y = frameTopPadding + (frameHeight * scanLineProgress) - 2.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                PrimaryBlue.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(99.dp)
                    )
            )
        }

        if (detection != null && state != QrScannerPresentationState.Error) {
            val targetSize = 84.dp + (detection.normalizedSize * 60f).dp
            val xOffset = with(density) {
                (frameHorizontalPadding + (frameWidth * detection.normalizedCenterX) - (targetSize / 2)).roundToPx()
            }
            val yOffset = with(density) {
                (frameTopPadding + (frameHeight * detection.normalizedCenterY) - (targetSize / 2)).roundToPx()
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(xOffset, yOffset) }
                    .size(targetSize)
                    .scale(pulseScale)
                    .border(
                        width = 2.dp,
                        color = PrimaryBlue.copy(alpha = pulseAlpha),
                        shape = CircleShape
                    )
            )
        }

        if (state != QrScannerPresentationState.Error) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = badgeTopPadding),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.28f)
            ) {
                Text(
                    text = when (state) {
                        QrScannerPresentationState.Searching -> "Searching"
                        QrScannerPresentationState.TargetAcquired -> "Target acquired"
                        QrScannerPresentationState.Connecting -> "Revealing connection"
                        QrScannerPresentationState.Error -> "Try again"
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}
