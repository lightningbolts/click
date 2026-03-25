package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.mohamedrejeb.calf.ui.toggle.AdaptiveSwitch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.viewmodel.AvailabilityViewModel
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.repository.NotificationPreferences
import compose.project.click.click.data.models.LocationPreferences
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onSignOut: () -> Unit = {},
    availabilityViewModel: AvailabilityViewModel = viewModel { AvailabilityViewModel() }
) {
    val currentAvailability by availabilityViewModel.currentAvailability.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val notificationPreferences by AppDataManager.notificationPreferences.collectAsState()
    val locationPreferences by AppDataManager.locationPreferences.collectAsState()
    val ghostModeEnabled by AppDataManager.ghostModeEnabled.collectAsState()

    var showNameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                PageHeader(title = "Settings")
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SettingsSectionHeader("Availability")
                }
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        SettingsToggleRow(
                            icon = Icons.Default.EventAvailable,
                            iconTint = if (currentAvailability?.isFreeThisWeek == true)
                                PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            title = "Free currently",
                            checked = currentAvailability?.isFreeThisWeek ?: false,
                            onCheckedChange = { availabilityViewModel.toggleFreeThisWeek() }
                        )
                    }
                }

                item {
                    SettingsSectionHeader("Notifications")
                }
                item {
                    NotificationSettingsCard(notificationPreferences = notificationPreferences)
                }

                item {
                    SettingsSectionHeader("Your Data")
                }
                item {
                    YourDataLocationCard(
                        locationPreferences = locationPreferences,
                        ghostModeEnabled = ghostModeEnabled
                    )
                }

                item {
                    SettingsSectionHeader("Appearance")
                }
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        SettingsToggleRow(
                            icon = Icons.Default.DarkMode,
                            title = "Dark mode",
                            checked = isDarkMode,
                            onCheckedChange = { onToggleDarkMode() }
                        )
                    }
                }

                item {
                    SettingsSectionHeader("Account")
                }
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Full name",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        currentUser?.name ?: "Not set",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        newName = currentUser?.name ?: ""
                                        showNameDialog = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit full name",
                                        modifier = Modifier.size(20.dp),
                                        tint = PrimaryBlue
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val sStyle = LocalPlatformStyle.current
                            Button(
                                onClick = onSignOut,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sStyle.isIOS) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.error,
                                    contentColor = if (sStyle.isIOS) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onError
                                ),
                                elevation = if (sStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation(),
                                shape = RoundedCornerShape(if (sStyle.isIOS) 10.dp else 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign out")
                            }
                        }
                    }
                }
            }
        }

        if (showNameDialog) {
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("Change Full Name") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Full name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                AppDataManager.updateUsername(newName.trim())
                                showNameDialog = false
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun YourDataLocationCard(
    locationPreferences: LocationPreferences,
    ghostModeEnabled: Boolean
) {
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (ghostModeEnabled) {
                Text(
                    "Ghost mode is on — location not shared.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            SettingsToggleRow(
                icon = Icons.Default.LocationOn,
                title = "Location snap",
                subtitle = "GPS recorded at moment of tap",
                checked = locationPreferences.connectionSnapEnabled,
                onCheckedChange = { AppDataManager.setConnectionSnapEnabled(it) }
            )

            SettingsDivider()

            SettingsToggleRow(
                icon = Icons.Default.Map,
                title = "Memory Map",
                subtitle = "Personal only, never shared",
                checked = locationPreferences.showOnMapEnabled,
                onCheckedChange = { AppDataManager.setShowOnMapEnabled(it) }
            )

            SettingsDivider()

            SettingsToggleRow(
                icon = Icons.Default.PrivacyTip,
                title = "Business insights",
                subtitle = "Anonymized venue trends",
                checked = locationPreferences.includeInInsightsEnabled,
                onCheckedChange = { AppDataManager.setIncludeInInsightsEnabled(it) }
            )
        }
    }
}

@Composable
private fun NotificationSettingsCard(notificationPreferences: NotificationPreferences) {
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleRow(
                icon = Icons.Default.Notifications,
                title = "Message notifications",
                checked = notificationPreferences.messagePushEnabled,
                onCheckedChange = { AppDataManager.setMessageNotificationsEnabled(it) }
            )

            SettingsDivider()

            SettingsToggleRow(
                icon = Icons.Default.PhoneInTalk,
                title = "Call alerts",
                checked = notificationPreferences.callPushEnabled,
                onCheckedChange = { AppDataManager.setCallNotificationsEnabled(it) }
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 36.dp, top = 2.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = iconTint
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        AdaptiveSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryBlue,
                checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f)
            )
        )
    }
}
