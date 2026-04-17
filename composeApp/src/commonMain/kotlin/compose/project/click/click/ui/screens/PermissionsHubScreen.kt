package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.platformForegroundTickFlow
import compose.project.click.click.proximity.ProximityManager
import compose.project.click.click.sensors.AmbientNoiseMonitor
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.utils.openApplicationSystemSettings
import compose.project.click.click.utils.LocationService

/**
 * Phase 2 — Q4 / C9: Permissions Hub surfaced from Settings.
 *
 * Live view of the three permissions the Tri-Factor handshake depends on:
 * Microphone, Location, and Bluetooth. Users can request the system permission
 * inline or deep-link into OS settings to flip a previously denied switch.
 *
 * The original [PermissionsOnboardingScreen] stays intact for the first-run flow;
 * this hub re-uses the same on-device status APIs so the two surfaces never
 * disagree.
 */
@Composable
fun PermissionsHubScreen(
    locationService: LocationService,
    ambientNoiseMonitor: AmbientNoiseMonitor,
    proximityManager: ProximityManager,
    requestLocationPermissionThen: ((onComplete: () -> Unit) -> Unit),
    requestMicrophonePermissionThen: ((onComplete: () -> Unit) -> Unit),
    onBack: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val foregroundSyncTick by platformForegroundTickFlow().collectAsState()

    var micPermissionBump by remember { mutableIntStateOf(0) }
    var locationPermissionBump by remember { mutableIntStateOf(0) }
    var locationFlowRunning by remember { mutableStateOf(false) }
    var micFlowRunning by remember { mutableStateOf(false) }

    val microphoneGranted = remember(micPermissionBump, foregroundSyncTick) {
        ambientNoiseMonitor.hasPermission
    }
    val locationGranted = remember(locationPermissionBump, foregroundSyncTick) {
        locationService.hasLocationPermission()
    }

    LaunchedEffect(foregroundSyncTick) {
        micPermissionBump++
        locationPermissionBump++
    }

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = topInset, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    PageHeader(
                        title = "Permissions",
                        subtitle = "Review and fix anything that might block Tap or Click handshakes.",
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    PermissionStatusRow(
                        icon = Icons.Default.Mic,
                        title = "Microphone",
                        description = "Used during Click handshake for a short ambient sound sample. Never recorded.",
                        granted = microphoneGranted,
                        primaryLabel = if (microphoneGranted) null else "Allow microphone",
                        onPrimaryClick = primary@{
                            if (micFlowRunning) return@primary
                            micFlowRunning = true
                            requestMicrophonePermissionThen {
                                micFlowRunning = false
                                micPermissionBump++
                            }
                        },
                    )
                    HubDivider()
                    PermissionStatusRow(
                        icon = Icons.Default.LocationOn,
                        title = "Location",
                        description = "Used only at the moment of a connection to drop one pin on your private Memory Map.",
                        granted = locationGranted,
                        primaryLabel = if (locationGranted) null else "Allow location",
                        onPrimaryClick = primary@{
                            if (locationFlowRunning) return@primary
                            locationFlowRunning = true
                            requestLocationPermissionThen {
                                locationFlowRunning = false
                                locationPermissionBump++
                            }
                        },
                    )
                    HubDivider()
                    // Bluetooth permission isn't exposed by KMP uniformly — we can only deep-link
                    // the user to OS radios settings. Treat as "unknown" with an informational row.
                    PermissionStatusRow(
                        icon = Icons.Default.BluetoothSearching,
                        title = "Bluetooth",
                        description = "Nearby Tap handshake uses Bluetooth Low Energy. The OS will prompt on first handshake; " +
                            "if Tap fails, make sure Bluetooth is on and the app has permission.",
                        granted = null,
                        primaryLabel = "Open Bluetooth & radios",
                        onPrimaryClick = { proximityManager.openRadiosSettings() },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { openApplicationSystemSettings() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open system Settings", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean?,
    primaryLabel: String?,
    onPrimaryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp).padding(top = 2.dp),
            tint = PrimaryBlue,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                PermissionStatusBadge(granted = granted)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (primaryLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onPrimaryClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusBadge(granted: Boolean?) {
    val (color, label, icon) = when (granted) {
        true -> Triple(Color(0xFF2E7D32), "Granted", Icons.Default.CheckCircle)
        false -> Triple(MaterialTheme.colorScheme.error, "Denied", Icons.Default.WarningAmber)
        null -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "System-managed", Icons.Default.WarningAmber)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun HubDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 36.dp, top = 2.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    )
}
