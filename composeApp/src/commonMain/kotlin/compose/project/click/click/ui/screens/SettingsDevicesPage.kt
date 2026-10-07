@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.project.click.click.chat.DeviceLabels // pragma: allowlist secret
import compose.project.click.click.chat.deviceActivityAgo // pragma: allowlist secret
import compose.project.click.click.data.api.MyDeviceDto // pragma: allowlist secret
import compose.project.click.click.openInAppBrowser // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickListRow // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.LegalLinks // pragma: allowlist secret
import compose.project.click.click.viewmodel.DevicesSettingsViewModel // pragma: allowlist secret
import kotlinx.datetime.Clock

/** Privacy Policy and Terms of Service (Me and Privacy & data, as on iOS). */
@Composable
internal fun LegalLinkRows() {
    Column {
        MeExternalLinkRow(icon = Icons.Default.Lock, title = "Privacy Policy", onClick = { openInAppBrowser(LegalLinks.PRIVACY_URL) })
        SettingsDivider()
        MeExternalLinkRow(
            icon = Icons.AutoMirrored.Filled.Article,
            title = "Terms of Service",
            onClick = { openInAppBrowser(LegalLinks.TERMS_URL) },
        )
    }
}

/** "This device · Active just now", "Last seen 2 days ago". */
internal fun deviceSubtitle(
    device: MyDeviceDto,
    isThisDevice: Boolean,
    nowMs: Long,
): String {
    val seen = deviceActivityAgo(device.lastSeenAt, nowMs)
    return when {
        isThisDevice -> "This device"
        seen != null -> "Last active $seen"
        else -> "Not seen recently"
    }
}

/** Settings › Devices: the account's active devices; Remove signs another one out of messages. */
@Composable
internal fun SettingsDevicesSection(viewModel: DevicesSettingsViewModel) {
    val state by viewModel.state.collectAsState()
    var confirmRemove by remember { mutableStateOf<MyDeviceDto?>(null) }
    val nowMs = remember(state.devices) { Clock.System.now().toEpochMilliseconds() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionHeader("Signed-in devices")
        when {
            state.loading && state.devices.isEmpty() ->
                CircularProgressIndicator(modifier = Modifier.padding(16.dp).size(22.dp), strokeWidth = 2.dp)
            state.loadFailed && state.devices.isEmpty() ->
                Column {
                    Text(
                        "Couldn't load your devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    TextButton(onClick = viewModel::load) { Text("Try again") }
                }
            else ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    state.devices.forEachIndexed { index, device ->
                        if (index > 0) SettingsDivider()
                        val isThis = device.deviceId == state.thisDeviceId
                        val icon =
                            when (DeviceLabels.icon(device.label)) {
                                DeviceLabels.Icon.Phone -> Icons.Default.PhoneAndroid
                                DeviceLabels.Icon.Tablet -> Icons.Default.TabletAndroid
                                DeviceLabels.Icon.Laptop -> Icons.Default.Laptop
                            }
                        ClickListRow(
                            title = device.label?.takeIf { it.isNotBlank() } ?: "Unknown device",
                            subtitle = deviceSubtitle(device, isThis, nowMs),
                            leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailing =
                                if (isThis) {
                                    null
                                } else {
                                    {
                                        if (device.deviceId in state.removing) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            TextButton(
                                                onClick = { confirmRemove = device },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            ) { Text("Remove") }
                                        }
                                    }
                                },
                            showDivider = false,
                        )
                    }
                }
        }
        Text(
            "Removing a device signs it out of secure messages; it can't read new messages until it signs in again. " +
                "To remove this device, sign out.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    confirmRemove?.let { device ->
        val name = device.label?.takeIf { it.isNotBlank() } ?: "this device"
        GlassAlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove $name?") },
            text = { Text("It will no longer receive your messages. You can sign in on it again later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = null
                        viewModel.remove(device.deviceId)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel", color = GlassSheetTokens.OnOledMuted()) }
            },
        )
    }
}
