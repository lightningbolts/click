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
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.platform.rememberReduceMotionEnabled // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButtonVariant // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickChip // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickTextFieldMinHeight // pragma: allowlist secret
import compose.project.click.click.ui.components.SuccessBeat // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret

@Composable
internal fun NfcIdleContent(
    onOpenAppSettings: () -> Unit,
    onStartScanning: () -> Unit,
    supportsTap: Boolean,
    capabilityNote: String,
    showHowItWorksCard: Boolean,
    onOpenSettings: () -> Unit,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    val (haloScale, haloAlpha) =
        if (reduceMotion || !supportsTap) {
            1f to 0.12f
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "tap_idle")
            val haloScale by infiniteTransition.animateFloat(
                initialValue = 0.98f,
                targetValue = 1.02f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(MotionTokens.Pulse.Scanning, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "tap_idle_halo_scale",
            )
            val haloAlpha by infiniteTransition.animateFloat(
                initialValue = 0.08f,
                targetValue = 0.14f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(MotionTokens.Pulse.Scanning, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "tap_idle_halo_alpha",
            )
            haloScale to haloAlpha
        }
    val idleScale = haloScale
    val idleAlpha = haloAlpha
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
                        .scale(idleScale)
                        .alpha(idleAlpha)
                        .border(clickBorderWidth(), if (supportsTap) PrimaryBlue.copy(alpha = 0.35f) else clickBorderColor(), CircleShape),
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
                    "Hold phones close and tap Connect. Bluetooth and microphone stay on for the handshake."
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
            ClickButton(
                onClick = onStartScanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect")
            }
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onOpenAppSettings) {
                Text(
                    "Open app settings",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            ClickButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                variant = ClickButtonVariant.Secondary,
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Settings")
            }
        }
    }
}

@Composable
internal fun NfcFetchingLocationContent(pulseActive: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = PrimaryBlue,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Getting location…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This tags the connection with where you met.",
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
    val reduceMotion = rememberReduceMotionEnabled()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "tap_scan_rings")

        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!reduceMotion) {
                repeat(3) { index ->
                    val delay = index * 333
                    val circleScale by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.5f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(MotionTokens.Pulse.Scanning, easing = LinearEasing, delayMillis = delay),
                                repeatMode = RepeatMode.Restart,
                            ),
                        label = "scan_ring_scale_$index",
                    )

                    val circleAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.6f,
                        targetValue = 0f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(MotionTokens.Pulse.Scanning, easing = LinearEasing, delayMillis = delay),
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
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(160.dp)
                            .alpha(0.18f)
                            .border(clickBorderWidth(), PrimaryBlue.copy(alpha = 0.35f), CircleShape),
                )
            }

            Icon(
                Icons.Default.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = PrimaryBlue,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Searching…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Hold phones close. Bluetooth and audio are on.",
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
            ClickButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                variant = ClickButtonVariant.Secondary,
            ) {
                Text("Cancel")
            }

            ClickButton(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
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
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun NfcMatchingPeersContent(pulseActive: Boolean = false) {
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
            text = "Person detected…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Confirming the nearby handshake.",
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
    var sayHiMessage by remember { mutableStateOf("") }
    var messageSent by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        SuccessBeat(
            trigger = connection.id,
            hapticsEnabled = false,
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = PrimaryBlue,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You're connected",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
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
                border = BorderStroke(clickBorderWidth(), clickBorderColor()),
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
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Message sent!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
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
            ClickButton(
                onClick = onViewConnection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Connection")
            }

            ClickButton(
                onClick = onCreateAnother,
                modifier = Modifier.fillMaxWidth(),
                variant = ClickButtonVariant.Secondary,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Another")
            }
        }
    }
}

/**
 * Overlapping interest tags as a quiet conversation prompt.
 */
@Composable
internal fun CommonGroundSection(tags: List<String>) {
    if (tags.isEmpty()) return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Common ground",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.take(3).forEach { tag ->
                ClickChip(
                    label = tag,
                    selected = true,
                    onClick = {},
                    compact = true,
                )
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
            text = "Couldn't connect",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
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
            ClickButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                variant = ClickButtonVariant.Secondary,
            ) {
                Text("Dismiss")
            }

            ClickButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
            ) {
                Text("Try Again")
            }
        }
    }
}
