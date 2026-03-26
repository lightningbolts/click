package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mohamedrejeb.calf.ui.toggle.AdaptiveSwitch
import androidx.compose.material3.Text
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue

data class PermissionsOnboardingSelection(
    val connectionSnapEnabled: Boolean,
    val showOnMapEnabled: Boolean,
    val includeInInsightsEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val ambientNoiseEnabled: Boolean,
    val barometricContextEnabled: Boolean,
)

@Composable
fun PermissionsOnboardingScreen(
    initialConnectionSnapEnabled: Boolean,
    initialShowOnMapEnabled: Boolean,
    initialIncludeInInsightsEnabled: Boolean,
    initialNotificationsEnabled: Boolean,
    initialAmbientNoiseEnabled: Boolean,
    initialBarometricContextEnabled: Boolean,
    isLoading: Boolean = false,
    onContinue: (PermissionsOnboardingSelection) -> Unit,
) {
    var connectionSnapEnabled by remember { mutableStateOf(initialConnectionSnapEnabled) }
    var showOnMapEnabled by remember { mutableStateOf(initialShowOnMapEnabled) }
    var includeInInsightsEnabled by remember { mutableStateOf(initialIncludeInInsightsEnabled) }
    var notificationsEnabled by remember { mutableStateOf(initialNotificationsEnabled) }
    var ambientNoiseEnabled by remember { mutableStateOf(initialAmbientNoiseEnabled) }
    var barometricContextEnabled by remember { mutableStateOf(initialBarometricContextEnabled) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = topInset + 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Set up your permissions",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose how Click works before your first connection. You can change these anytime in Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            PermissionPreferenceCard(
                icon = Icons.Default.Place,
                title = "Connection location snap",
                description = "Capture a single GPS snapshot when you connect. No background tracking.",
                checked = connectionSnapEnabled,
                onCheckedChange = {
                    connectionSnapEnabled = it
                    if (!it) {
                        showOnMapEnabled = false
                        includeInInsightsEnabled = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            PermissionPreferenceCard(
                icon = Icons.Default.Map,
                title = "Show on my Memory Map",
                description = "Save your own connections to a private map you can revisit later.",
                checked = showOnMapEnabled,
                enabled = connectionSnapEnabled,
                onCheckedChange = { showOnMapEnabled = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            PermissionPreferenceCard(
                icon = Icons.Default.Terrain,
                title = "Movement & elevation context",
                description = "During a connection, optionally read barometric pressure once to infer a coarse height band. No continuous fitness or health tracking.",
                checked = barometricContextEnabled,
                enabled = connectionSnapEnabled,
                onCheckedChange = { barometricContextEnabled = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            PermissionPreferenceCard(
                icon = Icons.Default.Mic,
                title = "Enable ambient sound enrichment",
                description = "Store only a 2-second sound category for each encounter. No raw audio is saved.",
                checked = ambientNoiseEnabled,
                enabled = connectionSnapEnabled,
                onCheckedChange = { ambientNoiseEnabled = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            PermissionPreferenceCard(
                icon = Icons.Default.PrivacyTip,
                title = "Include in business insights",
                description = "Share only anonymized venue and campus trends. Never your identity or raw path.",
                checked = includeInInsightsEnabled,
                enabled = connectionSnapEnabled,
                onCheckedChange = { includeInInsightsEnabled = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            PermissionPreferenceCard(
                icon = Icons.Default.Notifications,
                title = "Allow message and call alerts",
                description = "Get notified when connections message or call you.",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Next you'll pick at least 5 interests so Click can personalize your connections.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            val btnStyle = LocalPlatformStyle.current
            Button(
                onClick = {
                    onContinue(
                        PermissionsOnboardingSelection(
                            connectionSnapEnabled = connectionSnapEnabled,
                            showOnMapEnabled = showOnMapEnabled,
                            includeInInsightsEnabled = includeInInsightsEnabled,
                            notificationsEnabled = notificationsEnabled,
                            ambientNoiseEnabled = ambientNoiseEnabled,
                            barometricContextEnabled = barometricContextEnabled,
                        )
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(if (btnStyle.isIOS) 14.dp else 28.dp),
                elevation = if (btnStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isLoading) {
                    AdaptiveCircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PermissionPreferenceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val style = LocalPlatformStyle.current
    val cornerRadius = if (style.isIOS) 16.dp else 20.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = if (enabled) {
                    if (style.isIOS) 0.92f else 0.96f
                } else {
                    if (style.isIOS) 0.65f else 0.72f
                }
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
            AdaptiveSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
