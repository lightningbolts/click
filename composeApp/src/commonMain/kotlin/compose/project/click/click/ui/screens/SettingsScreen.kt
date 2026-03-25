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
    
    // Full name change dialog state
    var showNameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Use full status bar inset to prevent overlap with phone hardware
    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                PageHeader(title = "Settings")
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Availability Section
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Availability",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = if (currentAvailability?.isFreeThisWeek == true) 
                                        PrimaryBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Free currently",
                                    modifier = Modifier.weight(1f)
                                )
                                AdaptiveSwitch(
                                    checked = currentAvailability?.isFreeThisWeek ?: false,
                                    onCheckedChange = { availabilityViewModel.toggleFreeThisWeek() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PrimaryBlue,
                                        checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    NotificationSettingsCard(notificationPreferences = notificationPreferences)
                }

                // Your Data — location privacy (Ghost mode overrides when active)
                item {
                    YourDataLocationCard(
                        locationPreferences = locationPreferences,
                        ghostModeEnabled = ghostModeEnabled
                    )
                }
                
                // Appearance Section
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Appearance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DarkMode, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Dark Mode", modifier = Modifier.weight(1f))
                                AdaptiveSwitch(
                                    checked = isDarkMode,
                                    onCheckedChange = { onToggleDarkMode() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }

                // Account Section
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Username row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Full Name",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                        tint = PrimaryBlue
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val sStyle = LocalPlatformStyle.current
                            Button(
                                onClick = onSignOut,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sStyle.isIOS) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.error,
                                    contentColor = if (sStyle.isIOS) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onError
                                ),
                                elevation = if (sStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation(),
                                shape = RoundedCornerShape(if (sStyle.isIOS) 10.dp else 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign Out",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }
        }
        
        // Full name change dialog
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
private fun YourDataLocationCard(
    locationPreferences: LocationPreferences,
    ghostModeEnabled: Boolean
) {
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PrivacyTip,
                    contentDescription = null,
                    tint = PrimaryBlue
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Your Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Location is enabled by default so your map and anonymous trends work right away. Turn off anything you do not want.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ghostModeEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Ghost mode is on — location is not shared until you turn it off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SettingsToggleRow(
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                title = "Connection location snap",
                subtitle = "Records GPS at the moment you tap (not continuous tracking)",
                checked = locationPreferences.connectionSnapEnabled,
                onCheckedChange = { AppDataManager.setConnectionSnapEnabled(it) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsToggleRow(
                icon = { Icon(Icons.Default.Map, contentDescription = null) },
                title = "Show on my Memory Map",
                subtitle = "Personal only — never shared with others",
                checked = locationPreferences.showOnMapEnabled,
                onCheckedChange = { AppDataManager.setShowOnMapEnabled(it) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsToggleRow(
                icon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                title = "Include in business insights",
                subtitle = "Anonymized venue/campus trends are on by default. Turn this off if you do not want to be included.",
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
            Text(
                "Notifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Control whether Click can alert you about new messages and incoming calls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            SettingsToggleRow(
                icon = {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                },
                title = "Chat push notifications",
                checked = notificationPreferences.messagePushEnabled,
                onCheckedChange = { AppDataManager.setMessageNotificationsEnabled(it) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsToggleRow(
                icon = {
                    Icon(Icons.Default.PhoneInTalk, contentDescription = null)
                },
                title = "Incoming call alerts",
                checked = notificationPreferences.callPushEnabled,
                onCheckedChange = { AppDataManager.setCallNotificationsEnabled(it) }
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
