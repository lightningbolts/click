@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickTextFieldMinHeight // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberConnectionHandshakePulse // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import kotlinx.coroutines.delay

@Composable
internal fun NfcIdleContent(
    onOpenAppSettings: () -> Unit,
    onStartScanning: () -> Unit,
    supportsTap: Boolean,
    capabilityNote: String,
    showHowItWorksCard: Boolean,
    onOpenSettings: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tap_idle")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "tap_idle_halo_scale",
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.34f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "tap_idle_halo_alpha",
    )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(184.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .scale(haloScale)
                        .alpha(if (supportsTap) haloAlpha else 0.12f)
                        .border(2.dp, PrimaryBlue, CircleShape),
            )
            Surface(
                modifier = Modifier.size(128.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border =
                    BorderStroke(
                        clickBorderWidth(),
                        if (supportsTap) PrimaryBlue else clickBorderColor(),
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(58.dp),
                        tint =
                            if (supportsTap) {
                                PrimaryBlue
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = if (supportsTap) "Ready to Connect" else "Tap to Connect unavailable",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text =
                if (supportsTap) {
                    "Tap Connect together with someone nearby. Both phones should enable Bluetooth and microphone access for the handshake."
                } else {
                    capabilityNote
                },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // Simulator / emulator only — real devices never show the mock capability card.
        if (showHowItWorksCard) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                border = BorderStroke(clickBorderWidth(), clickBorderColor()),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "How Tap to Connect works",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = capabilityNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        if (supportsTap) {
            Button(
                onClick = onStartScanning,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                    ),
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Connect",
                    fontSize = 18.sp,
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                    ),
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Settings", fontSize = 18.sp)
            }
        }
    }
}

@Composable
internal fun NfcFetchingLocationContent(pulseActive: Boolean = false) {
    val (pulseScale, pulseAlpha) = rememberConnectionHandshakePulse(pulseActive)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier =
                Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha),
            tint = PrimaryBlue,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Fetching Location...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Getting your GPS coordinates for this connection",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        AdaptiveCircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = PrimaryBlue,
            strokeWidth = 3.dp,
        )
    }
}

@Composable
internal fun NfcScanningContent(pulseActive: Boolean = false) {
    val (pulseScale, pulseAlpha) = rememberConnectionHandshakePulse(pulseActive)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "tap_scan_rings")

        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Pulsing circles
            repeat(3) { index ->
                val delay = index * 333
                val circleScale by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1.5f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing, delayMillis = delay),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "scan_ring_scale_$index",
                )

                val circleAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing, delayMillis = delay),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "scan_ring_alpha_$index",
                )

                Box(
                    modifier =
                        Modifier
                            .size(200.dp)
                            .scale(circleScale)
                            .alpha(circleAlpha)
                            .background(
                                color = PrimaryBlue,
                                shape = CircleShape,
                            ),
                )
            }

            Icon(
                Icons.Default.BluetoothSearching,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                        .alpha(pulseAlpha),
                tint = PrimaryBlue,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Handshaking…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Stay within a few feet — BLE and audio are active",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
internal fun NfcSendingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AdaptiveCircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = PrimaryBlue,
            strokeWidth = 6.dp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Sharing Info...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun NfcUserDetectedContent(
    userId: String,
    userName: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp),
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = PrimaryBlue,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "User Detected!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = userName ?: "Unknown User",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "ID: ${userId.take(8)}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onConfirm,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                    ),
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
internal fun NfcCreatingConnectionContent(
    title: String = "Creating Connection...",
    pulseActive: Boolean = false,
) {
    val (pulseScale, pulseAlpha) = rememberConnectionHandshakePulse(pulseActive)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .scale(pulseScale)
                    .alpha(pulseAlpha),
        ) {
            AdaptiveCircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = PrimaryBlue,
                strokeWidth = 6.dp,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun NfcMatchingPeersContent(pulseActive: Boolean = false) {
    val (pulseScale, pulseAlpha) = rememberConnectionHandshakePulse(pulseActive)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .scale(pulseScale)
                    .alpha(pulseAlpha),
        ) {
            AdaptiveCircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = PrimaryBlue,
                strokeWidth = 6.dp,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Matching nearby taps…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Hang tight — this step is quick.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
internal fun NfcSuccessContent(
    connection: compose.project.click.click.data.models.Connection,
    connectedUser: compose.project.click.click.data.models.User?,
    onViewConnection: () -> Unit,
    onCreateAnother: () -> Unit,
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
        modifier =
            Modifier
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color(0xFF4CAF50),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connection Created!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show connected user's name if available
        if (connectedUser?.name != null) {
            Text(
                text = "You met ${connectedUser.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ---- Context Tag / Location Info ----
        if (connection.semanticLocation != null || connection.displayLocationLabel != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(clickBorderWidth(), clickBorderColor()),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryBlue,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = connection.displayLocationLabel ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(2.dp, PrimaryBlue),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.TextField(
                        value = sayHiMessage,
                        onValueChange = { sayHiMessage = it },
                        placeholder = {
                            Text(
                                "Say hi! 👋",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = ClickTextFieldMinHeight),
                        textStyle = clickTextFieldTextStyle(),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = PrimaryBlue,
                            ),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            if (sayHiMessage.trim().isNotEmpty()) {
                                messageSent = true
                                // Navigate to the connection chat where the message will be sent
                                onViewConnection()
                            }
                        },
                        enabled = sayHiMessage.trim().isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint =
                                if (sayHiMessage.trim().isNotEmpty()) {
                                    PrimaryBlue
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                },
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(clickBorderWidth(), clickBorderColor()),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF4CAF50),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Message sent!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4CAF50),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onViewConnection,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                    ),
            ) {
                Icon(Icons.Default.ChatBubble, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Connection", fontSize = 18.sp)
            }

            OutlinedButton(
                onClick = onCreateAnother,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(28.dp),
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
internal fun CommonGroundSection(tags: List<String>) {
    if (tags.isEmpty()) return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = NeonPurple,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Common Ground",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = NeonPurple,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Display up to 3 tags as neon chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.take(3).forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(clickBorderWidth(), clickBorderColor()),
                ) {
                    Text(
                        text = tag,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NfcErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp),
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text("Dismiss")
            }

            Button(
                onClick = onRetry,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                    ),
            ) {
                Text("Try Again")
            }
        }
    }
}
