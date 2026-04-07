package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import com.mohamedrejeb.calf.ui.toggle.AdaptiveSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.sensors.AmbientNoiseMonitor
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.utils.LocationService

data class PermissionsOnboardingSelection(
    val connectionSnapEnabled: Boolean,
    val showOnMapEnabled: Boolean,
    val includeInInsightsEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val ambientNoiseEnabled: Boolean,
    val barometricContextEnabled: Boolean,
)

private enum class PermissionsOnboardingPhase {
    PickPreferences,
    LocationExplainer,
    MicrophoneExplainer,
}

@Composable
fun PermissionsOnboardingScreen(
    initialConnectionSnapEnabled: Boolean,
    initialShowOnMapEnabled: Boolean,
    initialIncludeInInsightsEnabled: Boolean,
    initialNotificationsEnabled: Boolean,
    initialAmbientNoiseEnabled: Boolean,
    initialBarometricContextEnabled: Boolean,
    locationService: LocationService,
    ambientNoiseMonitor: AmbientNoiseMonitor,
    requestLocationPermissionThen: ((onComplete: () -> Unit) -> Unit),
    requestMicrophonePermissionThen: ((onComplete: () -> Unit) -> Unit),
    isLoading: Boolean = false,
    onContinue: (PermissionsOnboardingSelection) -> Unit,
) {
    var connectionSnapEnabled by remember { mutableStateOf(initialConnectionSnapEnabled) }
    var showOnMapEnabled by remember { mutableStateOf(initialShowOnMapEnabled) }
    var includeInInsightsEnabled by remember { mutableStateOf(initialIncludeInInsightsEnabled) }
    var notificationsEnabled by remember { mutableStateOf(initialNotificationsEnabled) }
    var ambientNoiseEnabled by remember { mutableStateOf(initialAmbientNoiseEnabled) }
    var barometricContextEnabled by remember { mutableStateOf(initialBarometricContextEnabled) }

    var phase by remember { mutableStateOf(PermissionsOnboardingPhase.PickPreferences) }
    var committedSelection by remember { mutableStateOf<PermissionsOnboardingSelection?>(null) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    fun selectionFromToggles() = PermissionsOnboardingSelection(
        connectionSnapEnabled = connectionSnapEnabled,
        showOnMapEnabled = showOnMapEnabled,
        includeInInsightsEnabled = includeInInsightsEnabled,
        notificationsEnabled = notificationsEnabled,
        ambientNoiseEnabled = ambientNoiseEnabled,
        barometricContextEnabled = barometricContextEnabled,
    )

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        when (phase) {
            PermissionsOnboardingPhase.PickPreferences -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = topInset, bottom = 24.dp),
                ) {
                    PageHeader(
                        title = "Set up your permissions",
                        subtitle = "Choose how Click works before your first connection. You can change these anytime in Settings.",
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PermissionToggleRow(
                                icon = Icons.Default.Place,
                                title = "Connection location snap",
                                description = "Capture a single GPS snapshot when you connect. No background tracking.",
                                checked = connectionSnapEnabled,
                                enabled = true,
                                onCheckedChange = {
                                    connectionSnapEnabled = it
                                    if (!it) {
                                        showOnMapEnabled = false
                                        includeInInsightsEnabled = false
                                    }
                                },
                            )
                            PermissionCardDivider()
                            PermissionToggleRow(
                                icon = Icons.Default.Map,
                                title = "Show on my Memory Map",
                                description = "Save your own connections to a private map you can revisit later.",
                                checked = showOnMapEnabled,
                                enabled = connectionSnapEnabled,
                                onCheckedChange = { showOnMapEnabled = it },
                            )
                            PermissionCardDivider()
                            PermissionToggleRow(
                                icon = Icons.Default.Terrain,
                                title = "Movement & elevation context",
                                description = "During a connection, optionally read barometric pressure once to infer a coarse height band. No continuous fitness or health tracking.",
                                checked = barometricContextEnabled,
                                enabled = connectionSnapEnabled,
                                onCheckedChange = { barometricContextEnabled = it },
                            )
                            PermissionCardDivider()
                            PermissionToggleRow(
                                icon = Icons.Default.Mic,
                                title = "Enable ambient sound enrichment",
                                description = "Store only a 2-second sound category for each encounter. No raw audio is saved.",
                                checked = ambientNoiseEnabled,
                                enabled = connectionSnapEnabled,
                                onCheckedChange = { ambientNoiseEnabled = it },
                            )
                            PermissionCardDivider()
                            PermissionToggleRow(
                                icon = Icons.Default.PrivacyTip,
                                title = "Include in business insights",
                                description = "Share only anonymized venue and campus trends. Never your identity or raw path.",
                                checked = includeInInsightsEnabled,
                                enabled = connectionSnapEnabled,
                                onCheckedChange = { includeInInsightsEnabled = it },
                            )
                            PermissionCardDivider()
                            PermissionToggleRow(
                                icon = Icons.Default.Notifications,
                                title = "Allow message and call alerts",
                                description = "Get notified when connections message or call you.",
                                checked = notificationsEnabled,
                                enabled = true,
                                onCheckedChange = { notificationsEnabled = it },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Next you'll pick at least 5 interests so Click can personalize your connections.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val btnStyle = LocalPlatformStyle.current
                    Button(
                        onClick = {
                            val sel = selectionFromToggles()
                            when {
                                sel.connectionSnapEnabled && !locationService.hasLocationPermission() -> {
                                    committedSelection = sel
                                    phase = PermissionsOnboardingPhase.LocationExplainer
                                }
                                sel.ambientNoiseEnabled && !ambientNoiseMonitor.hasPermission -> {
                                    committedSelection = sel
                                    phase = PermissionsOnboardingPhase.MicrophoneExplainer
                                }
                                else -> onContinue(sel)
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(if (btnStyle.isIOS) 14.dp else 28.dp),
                        elevation = if (btnStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    ) {
                        if (isLoading) {
                            AdaptiveCircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            PermissionsOnboardingPhase.LocationExplainer -> {
                val sel = committedSelection ?: return@AdaptiveBackground
                LocationPermissionExplainerContent(
                    topInset = topInset,
                    requestLocationPermissionThen = requestLocationPermissionThen,
                    onNext = {
                        if (sel.ambientNoiseEnabled && !ambientNoiseMonitor.hasPermission) {
                            phase = PermissionsOnboardingPhase.MicrophoneExplainer
                        } else {
                            onContinue(sel)
                        }
                    },
                )
            }

            PermissionsOnboardingPhase.MicrophoneExplainer -> {
                val sel = committedSelection ?: return@AdaptiveBackground
                MicrophonePermissionExplainerContent(
                    topInset = topInset,
                    requestMicrophonePermissionThen = requestMicrophonePermissionThen,
                    onAllowComplete = { onContinue(sel) },
                )
            }
        }
    }
}

@Composable
private fun LocationPermissionExplainerContent(
    topInset: Dp,
    requestLocationPermissionThen: ((onComplete: () -> Unit) -> Unit),
    onNext: () -> Unit,
) {
    var allowCompleted by remember { mutableStateOf(false) }
    val btnStyle = LocalPlatformStyle.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = topInset, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(
            title = "Connection location",
            subtitle = "Click records one GPS point when you connect so your Memory Map and connection context stay accurate. We don't track you in the background.",
        )
        Spacer(modifier = Modifier.height(28.dp))
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "The next screen is the system permission dialog. You can change this anytime in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                requestLocationPermissionThen {
                    allowCompleted = true
                }
            },
            enabled = !allowCompleted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(if (btnStyle.isIOS) 14.dp else 28.dp),
            elevation = if (btnStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
        ) {
            Text("Allow Location", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNext,
            enabled = allowCompleted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(if (btnStyle.isIOS) 14.dp else 28.dp),
            elevation = if (btnStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("Next", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MicrophonePermissionExplainerContent(
    topInset: Dp,
    requestMicrophonePermissionThen: ((onComplete: () -> Unit) -> Unit),
    onAllowComplete: () -> Unit,
) {
    var allowTapped by remember { mutableStateOf(false) }
    val btnStyle = LocalPlatformStyle.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = topInset, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(
            title = "Ambient sound",
            subtitle = "A short mic sample at connect time helps categorize background noise. No recordings are stored—only a rough category.",
        )
        Spacer(modifier = Modifier.height(28.dp))
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "When you're ready, tap below to open the system microphone permission.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (allowTapped) return@Button
                allowTapped = true
                requestMicrophonePermissionThen {
                    onAllowComplete()
                }
            },
            enabled = !allowTapped,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(if (btnStyle.isIOS) 14.dp else 28.dp),
            elevation = if (btnStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
        ) {
            Text("Allow microphone", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PermissionCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 36.dp, top = 2.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    )
}

/**
 * Same row geometry as [compose.project.click.click.ui.screens.SettingsToggleRow]: icon vertically centered with text block.
 */
@Composable
private fun PermissionToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        checked -> PrimaryBlue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val descriptionColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = iconTint,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = descriptionColor,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        AdaptiveSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryBlue,
                checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f),
            ),
        )
    }
}
