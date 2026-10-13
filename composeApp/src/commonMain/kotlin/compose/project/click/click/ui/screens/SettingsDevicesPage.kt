@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.chat.DeviceLabels // pragma: allowlist secret
import compose.project.click.click.chat.deviceActivityAgo // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceHistoryRequestDto // pragma: allowlist secret
import compose.project.click.click.data.api.MyDeviceDto // pragma: allowlist secret
import compose.project.click.click.openInAppBrowser // pragma: allowlist secret
import compose.project.click.click.platform.thisDeviceLabel // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickListRow // pragma: allowlist secret
import compose.project.click.click.ui.components.DeviceApprovalDialog // pragma: allowlist secret
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

/** The label, else this device's model (this device only), else "Unnamed device". */
internal fun deviceRowName(
    label: String?,
    isThisDevice: Boolean,
    thisDeviceModel: String?,
): String = label ?: (if (isThisDevice) thisDeviceModel else null) ?: "Unnamed device"

/** "Active 2 min ago", else "Added 3 days ago", else nothing. */
internal fun deviceActivityLine(
    device: MyDeviceDto,
    nowMs: Long,
): String =
    deviceActivityAgo(device.lastSeenAt, nowMs)?.let { "Active $it" }
        ?: deviceActivityAgo(device.createdAt, nowMs)?.let { "Added $it" }
        ?: ""

/** "Signed in 5 min ago", or "Signed in recently" without a time. */
internal fun signedInLine(
    createdAt: String?,
    nowMs: Long,
): String = deviceActivityAgo(createdAt, nowMs)?.let { "Signed in $it" } ?: "Signed in recently"

/** "Remove Pixel 9?" / "Remove this device?" */
internal fun removeDeviceTitle(label: String?): String = "Remove ${label ?: "this device"}?"

private fun DeviceLabels.Icon.vector() =
    when (this) {
        DeviceLabels.Icon.Phone -> Icons.Default.PhoneAndroid
        DeviceLabels.Icon.Tablet -> Icons.Default.TabletAndroid
        DeviceLabels.Icon.Laptop -> Icons.Default.Laptop
    }

/**
 * Settings › Devices (iOS `DevicesSettingsView`): sign-ins waiting for this device to share its
 * chat history, then the account's chat devices with Remove for the others. Removing a device
 * stops it reading new messages; it stays signed in.
 */
@Composable
internal fun SettingsDevicesSection(
    viewModel: DevicesSettingsViewModel,
    currentUserId: String?,
) {
    val state by viewModel.state.collectAsState()
    var confirmRemove by remember { mutableStateOf<MyDeviceDto?>(null) }
    val nowMs = remember(state.devices, state.incoming) { Clock.System.now().toEpochMilliseconds() }
    val thisModel = remember { thisDeviceLabel() }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (state.incoming.isNotEmpty()) {
            WaitingForApprovalSection(
                incoming = state.incoming,
                online = state.online,
                nowMs = nowMs,
                onReview = viewModel::review,
            )
        }
        val devices = state.devices
        when {
            devices != null ->
                DeviceList(
                    devices = state.orderedDevices,
                    thisDeviceId = state.thisDeviceId,
                    thisModel = thisModel,
                    online = state.online,
                    error = state.error,
                    nowMs = nowMs,
                    onRemove = { confirmRemove = it },
                )
            state.loadFailed ->
                DevicesUnavailable(online = state.online, onRetry = viewModel::load)
            else ->
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                }
        }
    }

    confirmRemove?.let { device ->
        GlassAlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(removeDeviceTitle(device.label)) },
            text = { Text("It stops getting new messages until it's approved again. It stays signed in until it signs out.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = null
                        viewModel.remove(device)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Keep Device", color = GlassSheetTokens.OnOledMuted()) }
            },
        )
    }

    val reviewing = state.reviewing
    if (reviewing != null && !currentUserId.isNullOrBlank()) {
        DeviceApprovalDialog(
            requestId = reviewing,
            currentUserId = currentUserId,
            onDismiss = viewModel::reviewDismissed,
            onDecided = { viewModel.reviewDecided(reviewing) },
        )
    }
}

@Composable
private fun WaitingForApprovalSection(
    incoming: List<DeviceHistoryRequestDto>,
    online: Boolean,
    nowMs: Long,
    onReview: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionHeader("Waiting for approval")
        Column(modifier = Modifier.fillMaxWidth().alpha(if (online) 1f else 0.5f)) {
            incoming.forEachIndexed { index, request ->
                if (index > 0) SettingsDivider()
                ClickListRow(
                    title = "${DeviceLabels.withArticle(request.deviceLabel)} wants your chat history",
                    subtitle = signedInLine(request.createdAt, nowMs),
                    onClick = if (online) ({ onReview(request.id) }) else null,
                    leading = {
                        Icon(Icons.Default.GppMaybe, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailing = {
                        Text(
                            "Review",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    showDivider = false,
                )
            }
        }
        DevicesFooterText("Only approve a sign-in if it's you.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceList(
    devices: List<MyDeviceDto>,
    thisDeviceId: String?,
    thisModel: String?,
    online: Boolean,
    error: String?,
    nowMs: Long,
    onRemove: (MyDeviceDto) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (devices.isEmpty()) {
            DevicesMessage(
                icon = { Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = "No chat devices yet",
                message = "Devices show up here once you open a chat on them.",
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            devices.forEachIndexed { index, device ->
                if (index > 0) SettingsDivider()
                val mine = device.deviceId == thisDeviceId
                val name = deviceRowName(device.label, mine, thisModel)
                val row: @Composable () -> Unit = {
                    ClickListRow(
                        title = name,
                        subtitle = deviceActivityLine(device, nowMs).takeIf { it.isNotEmpty() },
                        leading = {
                            Icon(
                                DeviceLabels.icon(device.label).vector(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailing =
                            if (mine) {
                                { ThisDeviceCapsule() }
                            } else {
                                {
                                    TextButton(
                                        onClick = { onRemove(device) },
                                        enabled = online,
                                        modifier = Modifier.semantics { contentDescription = "Remove $name" },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    ) { Text("Remove") }
                                }
                            },
                        showDivider = false,
                    )
                }
                if (mine) {
                    row()
                } else {
                    // Swiping left asks to remove, like the button; the row springs back either way.
                    val swipe = rememberSwipeToDismissBoxState()
                    LaunchedEffect(swipe.currentValue) {
                        if (swipe.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            onRemove(device)
                            swipe.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }
                    SwipeToDismissBox(
                        state = swipe,
                        enableDismissFromStartToEnd = false,
                        gesturesEnabled = online,
                        backgroundContent = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Text("Remove", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.SemiBold)
                            }
                        },
                    ) { row() }
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (!online) DevicesFooterText("You're offline. Changes wait until you're back.")
        DevicesFooterText(
            "Messages are end-to-end encrypted, and each device holds its own key. " +
                "Removing a device stops it reading new messages; it doesn't sign it out.",
        )
    }
}

@Composable
private fun ThisDeviceCapsule() {
    Text(
        "This device",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** "Couldn't load your devices" or, offline, "You're offline", with "Try Again". */
@Composable
private fun DevicesUnavailable(
    online: Boolean,
    onRetry: () -> Unit,
) {
    DevicesMessage(
        icon = { Icon(Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = if (online) "Couldn't load your devices" else "You're offline",
        message = if (online) "Check your connection and try again." else "Your devices show up here once you're back online.",
        action = { TextButton(onClick = onRetry) { Text("Try Again") } },
    )
}

@Composable
private fun DevicesMessage(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
private fun DevicesFooterText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
