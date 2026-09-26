@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
    "ktlint:standard:indent",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.toggle.AdaptiveSwitch
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickInsetDivider // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickListRow // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSettingsDividerIndent // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.utils.LocationPermissionDisplayState // pragma: allowlist secret

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
internal fun YourDataLocationRows(
    locationPreferences: LocationPreferences,
    locationPermissionState: LocationPermissionDisplayState,
    onConnectionSnapCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
    enabled: Boolean = true,
    /** Saving this row's change: a spinner replaces the switch until the server answers. */
    pending: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    ClickListRow(
        title = title,
        subtitle = subtitle,
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = iconTint,
            )
        },
        trailing = {
            if (pending) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                AdaptiveSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = PrimaryBlue,
                            checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f),
                        ),
                )
            }
        },
        showDivider = false,
    )
}
