@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
    "ktlint:standard:indent",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mohamedrejeb.calf.ui.toggle.AdaptiveSwitch
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveCard // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButtonVariant // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickInsetDivider // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickNavRow // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSettingsDividerIndent // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.utils.LocationPermissionDisplayState // pragma: allowlist secret

/**
 * Inline permissions hub rendered inside the "Privacy & data" card via
 * [AnimatedVisibility]. Shows live status badges for mic, location, and Bluetooth
 * with a single "System Settings" deep link to the OS app settings page.
 */
@Composable
internal fun InlinePermissionsPanel(
    microphoneGranted: Boolean,
    locationStatus: PermissionHubStatus,
    isRequestingMic: Boolean,
    isRequestingLocation: Boolean,
    onRequestMicrophone: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        PermissionRow(
            icon = Icons.Default.Mic,
            title = "Microphone",
            description = "Short ambient sample during handshake.",
            status = if (microphoneGranted) PermissionHubStatus.Granted else PermissionHubStatus.NotSet,
            primaryEnabled = !isRequestingMic,
            onPrimaryClick = onRequestMicrophone,
        )
        PermissionRowDivider()
        PermissionRow(
            icon = Icons.Default.LocationOn,
            title = "Location",
            description = "One pin at the moment of a connection.",
            status = locationStatus,
            primaryEnabled = !isRequestingLocation,
            onPrimaryClick =
                when (locationStatus) {
                    PermissionHubStatus.Denied -> onOpenSystemSettings
                    else -> onRequestLocation
                },
        )
        PermissionRowDivider()
        PermissionRow(
            icon = Icons.Default.BluetoothSearching,
            title = "Bluetooth",
            description = "Used for nearby tap handshake.",
            status = PermissionHubStatus.SystemManaged,
            primaryEnabled = true,
            onPrimaryClick = {},
        )

        Spacer(modifier = Modifier.height(10.dp))
        ClickButton(
            onClick = onOpenSystemSettings,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            variant = ClickButtonVariant.Secondary,
        ) {
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("System Settings", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    status: PermissionHubStatus,
    primaryEnabled: Boolean,
    onPrimaryClick: () -> Unit,
) {
    val primaryLabel =
        status.primaryActionLabel(
            permissionName = title.lowercase(),
        )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
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
                PermissionStatusBadge(status = status)
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
                    enabled = primaryEnabled,
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
internal fun PermissionStatusBadge(status: PermissionHubStatus) {
    val (color, label, icon) =
        when (status) {
            PermissionHubStatus.Granted ->
                Triple(Color(0xFF2E7D32), "Granted", Icons.Default.CheckCircle)
            PermissionHubStatus.NotSet ->
                Triple(Color(0xFFF59E0B), "Not set", Icons.Default.WarningAmber)
            PermissionHubStatus.Denied ->
                Triple(MaterialTheme.colorScheme.error, "Denied", Icons.Default.WarningAmber)
            PermissionHubStatus.SystemManaged ->
                Triple(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "System-managed",
                    Icons.Default.WarningAmber,
                )
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
internal fun PermissionRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 36.dp, top = 2.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    )
}

internal fun profileAvatarInitials(user: User?): String {
    if (user == null) return "?"
    val (f, l) = namePartsForEditor(user)
    val initials =
        buildString {
            f.firstOrNull()?.uppercaseChar()?.let { append(it) }
            if (length < 2) l.firstOrNull()?.uppercaseChar()?.let { append(it) }
        }
    return initials.ifEmpty { "?" }
}

/** Values shown in settings and prefilled in the editor (falls back to splitting [User.name]). */
internal fun namePartsForEditor(user: User?): Pair<String, String> {
    if (user == null) return "" to ""
    val fn = user.firstName?.trim()?.takeIf { it.isNotEmpty() }
    val ln = user.lastName?.trim()?.takeIf { it.isNotEmpty() }
    if (fn != null || ln != null) {
        return (fn ?: "") to (ln ?: "")
    }
    val n = user.name?.trim().orEmpty()
    if (n.isEmpty()) return "" to ""
    val sp = n.indexOf(' ')
    return if (sp < 0) {
        n to ""
    } else {
        n.take(sp).trim() to n.substring(sp + 1).trim()
    }
}

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun SettingsProfileHeader(
    user: User?,
    avatarUploading: Boolean,
    onChangePhoto: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val avatarUrl = user?.image
    val (first, last) = namePartsForEditor(user)
    val displayName =
        listOf(first, last)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank {
                user
                    ?.name
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "—" }
            }
    val email = user?.email?.trim()?.takeIf { it.isNotEmpty() }

    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(96.dp)) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(88.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !avatarUploading, onClick = onChangePhoto),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(PrimaryBlue.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = profileAvatarInitials(user),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    if (avatarUploading) {
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue)
                            .clickable(enabled = !avatarUploading, onClick = onChangePhoto),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Change profile photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (email != null) {
                    Text(
                        text = "@${email.substringBefore("@")}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ClickButton(
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth(),
                variant = ClickButtonVariant.Secondary,
            ) {
                Text("Edit Profile", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun SettingsSignOutButton(onSignOut: () -> Unit) {
    val sStyle = LocalPlatformStyle.current
    Button(
        onClick = onSignOut,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (sStyle.isIOS) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                contentColor =
                    if (sStyle.isIOS) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onError
                    },
            ),
        elevation =
            if (sStyle.isIOS) {
                ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
            } else {
                ButtonDefaults.buttonElevation()
            },
        shape = RoundedCornerShape(if (sStyle.isIOS) 10.dp else 12.dp),
        border =
            BorderStroke(
                width = sStyle.cardBorderWidth,
                color = if (sStyle.isIOS) MaterialTheme.colorScheme.error else clickBorderColor(),
            ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Sign out", fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SettingsHubNavCard(onOpen: (SettingsPage) -> Unit) {
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsHubNavRow(
                icon = Icons.Default.EventAvailable,
                title = "Availability",
                subtitle = "Free this week and intent posts",
                onClick = { onOpen(SettingsPage.Availability) },
                accentSlot = 0,
            )
            SettingsDivider()
            SettingsHubNavRow(
                icon = Icons.Default.Notifications,
                title = "Alerts",
                subtitle = "Messages, events, hubs",
                onClick = { onOpen(SettingsPage.Alerts) },
                accentSlot = 5,
            )
            SettingsDivider()
            SettingsHubNavRow(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy & data",
                subtitle = "Ghost mode, location, permissions",
                onClick = { onOpen(SettingsPage.Privacy) },
                accentSlot = 1,
            )
            SettingsDivider()
            SettingsHubNavRow(
                icon = Icons.Default.Star,
                title = "Interests",
                subtitle = "Common Ground tags",
                onClick = { onOpen(SettingsPage.Interests) },
                accentSlot = 6,
            )
            SettingsDivider()
            SettingsHubNavRow(
                icon = Icons.Default.Person,
                title = "Personality",
                subtitle = "Five traits that describe you",
                onClick = { onOpen(SettingsPage.Personality) },
                accentSlot = 2,
            )
            SettingsDivider()
            SettingsHubNavRow(
                icon = Icons.Default.Bookmark,
                title = "Saved events",
                subtitle = "Bookmarks from Home and the map",
                onClick = { onOpen(SettingsPage.Saved) },
                accentSlot = 7,
            )
            SettingsDivider()
            SettingsHubNavRow(
                icon = Icons.Default.DarkMode,
                title = "Appearance",
                subtitle = "Dark mode and home layout",
                onClick = { onOpen(SettingsPage.Appearance) },
                accentSlot = 3,
            )
        }
    }
}

@Composable
internal fun SettingsHubNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentSlot: Int = 0,
) {
    ClickNavRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leadingIcon = icon,
        leadingTint = MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
internal fun YourDataLocationRows(
    locationPreferences: LocationPreferences,
    ghostModeEnabled: Boolean,
    locationPermissionState: LocationPermissionDisplayState,
    onConnectionSnapCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsToggleRow(
            icon = Icons.Outlined.VisibilityOff,
            title = "Ghost Mode",
            subtitle = "Go off the grid — hide your location, pause matching, and mute presence.",
            iconTint = if (ghostModeEnabled) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            checked = ghostModeEnabled,
            onCheckedChange = { AppDataManager.toggleGhostMode() },
        )
        if (ghostModeEnabled) {
            Text(
                "Ghost mode is on — location not shared.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 36.dp, top = 2.dp, bottom = 8.dp),
            )
        }

        SettingsDivider()

        SettingsToggleRow(
            icon = Icons.Default.LocationOn,
            title = "Location snap",
            subtitle = "GPS recorded at moment of tap",
            checked = locationPreferences.connectionSnapEnabled,
            onCheckedChange = onConnectionSnapCheckedChange,
        )
        locationSnapHint(locationPermissionState)?.let { hint ->
            if (locationPreferences.connectionSnapEnabled) {
                val hintColor =
                    when (locationPermissionState) {
                        LocationPermissionDisplayState.Denied ->
                            MaterialTheme.colorScheme.error
                        LocationPermissionDisplayState.NotSet ->
                            Color(0xFFF59E0B)
                        LocationPermissionDisplayState.Granted ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = hintColor,
                    modifier = Modifier.padding(start = 36.dp, top = 4.dp, end = 4.dp),
                )
            }
        }

        SettingsDivider()

        SettingsToggleRow(
            icon = Icons.Default.Map,
            title = "Memory Map",
            subtitle = "Personal only, never shared",
            checked = locationPreferences.showOnMapEnabled,
            onCheckedChange = { AppDataManager.setShowOnMapEnabled(it) },
        )

        SettingsDivider()

        SettingsToggleRow(
            icon = Icons.Default.PrivacyTip,
            title = "Business insights",
            subtitle = "Anonymized venue trends",
            checked = locationPreferences.includeInInsightsEnabled,
            onCheckedChange = { AppDataManager.setIncludeInInsightsEnabled(it) },
        )
    }
}

@Composable
internal fun SettingsDivider() {
    ClickInsetDivider(startIndent = ClickSettingsDividerIndent)
}

@Composable
internal fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
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
                title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        AdaptiveSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = PrimaryBlue,
                    checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f),
                ),
        )
    }
}
