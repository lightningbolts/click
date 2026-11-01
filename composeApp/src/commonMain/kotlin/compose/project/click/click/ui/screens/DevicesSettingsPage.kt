@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.api.MyChatDeviceDto // pragma: allowlist secret
import compose.project.click.click.data.devices.DEVICES_FOOTER
import compose.project.click.click.data.devices.deviceActivityLabel
import compose.project.click.click.data.devices.deviceDisplayName
import compose.project.click.click.data.devices.orderDevicesThisFirst
import compose.project.click.click.ui.components.ClickListRow // pragma: allowlist secret
import kotlinx.datetime.Clock

/**
 * Me → Devices (iOS `DevicesSettingsView`, spec §7.8): this account's chat devices, this one first,
 * with Remove on the others. Approving new sign-ins from here comes with device approval (G7-1).
 */
@Composable
internal fun DevicesPageContent(
    load: PageLoad<List<MyChatDeviceDto>>,
    thisDeviceId: String?,
    removing: Set<String>,
    errorMessage: String?,
    online: Boolean,
    onRemove: (MyChatDeviceDto) -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (load) {
            PageLoad.Loading ->
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp))
                }
            PageLoad.Failed ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(if (online) "Couldn't load your devices" else "You're offline", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (online) "Check your connection and try again." else "Your devices show up here once you're back online.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) { Text("Try Again") }
                }
            is PageLoad.Loaded ->
                if (load.value.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No chat devices yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Devices show up here once you open a chat on them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val now = Clock.System.now().toEpochMilliseconds()
                    Column {
                        orderDevicesThisFirst(load.value, thisDeviceId).forEachIndexed { index, device ->
                            if (index > 0) SettingsDivider()
                            val mine = device.deviceId == thisDeviceId
                            val name = deviceDisplayName(device, mine)
                            ClickListRow(
                                title = name,
                                subtitle = deviceActivityLabel(device, now).takeIf { it.isNotEmpty() },
                                leading = {
                                    Icon(
                                        if (mine) Icons.Default.PhoneAndroid else Icons.Default.Devices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailing = {
                                    if (mine) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                            Text(
                                                "This device",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            )
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { onRemove(device) },
                                            enabled = online && device.deviceId !in removing,
                                            modifier = Modifier.semantics { contentDescription = "Remove $name" },
                                        ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                                    }
                                },
                                showDivider = false,
                            )
                        }
                    }
                }
        }
        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (!online) {
            Text(
                "You're offline. Changes wait until you're back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(DEVICES_FOOTER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
