package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.qr.toQrPayloadOrNull
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.components.QRScanner
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

/**
 * Result types for QR code scanning
 */
sealed class QRScanResult {
    data class Success(val userId: String) : QRScanResult()
    data class InvalidFormat(val rawData: String) : QRScanResult()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onQRCodeScanned: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    
    // Error state for invalid QR codes
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lastScannedRaw by remember { mutableStateOf<String?>(null) }
    
    // Auto-dismiss error after delay
    LaunchedEffect(showError) {
        if (showError) {
            delay(4000)
            showError = false
            lastScannedRaw = null // Allow re-scanning after error dismisses
        }
    }
    
    // Handle scanned QR data
    fun handleQRResult(qrData: String) {
        // Prevent duplicate scans of the same invalid QR
        if (qrData == lastScannedRaw && showError) return
        
        val payload = qrData.toQrPayloadOrNull()
        if (payload != null && payload.userId.isNotBlank()) {
            // Valid Click QR code - pass through to parent
            onQRCodeScanned(qrData)
        } else {
            // Invalid format - show error
            lastScannedRaw = qrData
            errorMessage = "This QR code isn't a Click profile. Please scan a valid Click QR code."
            showError = true
        }
    }

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                    PageHeader(
                        title = "Scan QR Code",
                        subtitle = "Point camera at a code",
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
                        .padding(24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            width = 1.dp,
                            color = if (showError) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    QRScanner(
                        modifier = Modifier.fillMaxSize(),
                        onResult = { qrData -> handleQRResult(qrData) }
                    )
                    
                    // Scanning overlay frame
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(48.dp)
                            .border(
                                2.dp,
                                if (showError) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                } else {
                                    Color.White.copy(alpha = 0.5f)
                                },
                                RoundedCornerShape(16.dp)
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Error notification banner - styled to match app design
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
            
            // Tap to dismiss hint
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
