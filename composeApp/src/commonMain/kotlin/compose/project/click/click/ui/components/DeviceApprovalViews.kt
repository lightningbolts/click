@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.chat.DeviceHistoryBannerState // pragma: allowlist secret
import compose.project.click.click.chat.DeviceLabels // pragma: allowlist secret
import compose.project.click.click.chat.deviceActivityAgo // pragma: allowlist secret
import compose.project.click.click.chat.deviceHistoryBannerState // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.DeviceApprovals // pragma: allowlist secret
import compose.project.click.click.data.shareHistoryWithApprovedDevicesNow // pragma: allowlist secret
import compose.project.click.click.navigation.AppRoute // pragma: allowlist secret
import compose.project.click.click.navigation.AppRouter // pragma: allowlist secret
import compose.project.click.click.platform.thisDeviceLabel // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import compose.project.click.click.viewmodel.DeviceApprovalViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.DeviceApprovalViewModel.Phase // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Presents [AppRoute.DeviceApproval] over the shell for the signed-in [currentUserId]. */
@Composable
fun DeviceApprovalHost(currentUserId: String) {
    val pending by AppRouter.pending.collectAsState()
    val route = pending as? AppRoute.DeviceApproval ?: return
    if (currentUserId.isBlank()) return
    DeviceApprovalDialog(
        requestId = route.requestId,
        currentUserId = currentUserId,
        onDismiss = { AppRouter.consume(route) },
    )
}

private fun DeviceLabels.Icon.vector(): ImageVector =
    when (this) {
        DeviceLabels.Icon.Phone -> Icons.Default.PhoneAndroid
        DeviceLabels.Icon.Tablet -> Icons.Default.TabletAndroid
        DeviceLabels.Icon.Laptop -> Icons.Default.Laptop
    }

/**
 * "Approve new sign-in?" with Approve and "This wasn't me" (iOS `DeviceApprovalSheet`), from the
 * global prompt or Settings › Devices. [onDecided] is told once the decision is saved.
 */
@Composable
internal fun DeviceApprovalDialog(
    requestId: String,
    currentUserId: String,
    onDismiss: () -> Unit,
    onDecided: (approved: Boolean) -> Unit = {},
) {
    val vm: DeviceApprovalViewModel =
        viewModel(key = "device-approval-$requestId") {
            DeviceApprovalViewModel(
                requestId = requestId,
                onApproved = { AppDataManager.shareHistoryWithApprovedDevicesNow(currentUserId) },
            )
        }
    val state by vm.state.collectAsState()
    val label = state.deviceLabel
    val phase = state.phase
    LaunchedEffect(phase) {
        if (phase == Phase.Approved || phase == Phase.Denied) onDecided(phase == Phase.Approved)
    }
    val title =
        when (phase) {
            Phase.Approved -> "Approved"
            Phase.Denied -> "Not approved"
            is Phase.Failed -> if (phase.error.retryable) "Approve new sign-in?" else "Can't approve this sign-in"
            else -> "Approve new sign-in?"
        }
    val detail =
        when (phase) {
            Phase.Loading -> "Checking this sign-in…"
            Phase.Approved -> DeviceLabels.approvedDetail(label)
            Phase.Denied -> DeviceLabels.deniedDetail(label)
            is Phase.Failed -> phase.error.message
            else -> DeviceLabels.askingDetail(label, deviceActivityAgo(state.createdAt, Clock.System.now().toEpochMilliseconds()))
        }
    val icon =
        when (phase) {
            Phase.Approved -> Icons.Default.GppGood
            Phase.Denied -> Icons.Default.GppBad
            else -> DeviceLabels.icon(label).vector()
        }
    GlassAlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        icon = {
            Box(
                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (phase == Phase.Denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(detail, textAlign = TextAlign.Center) },
        confirmButton = {
            when {
                state.finished -> TextButton(onClick = onDismiss) { Text("Done") }
                phase is Phase.Failed && phase.error.retryable -> TextButton(onClick = vm::retry) { Text("Try again") }
                else ->
                    TextButton(onClick = vm::approve, enabled = state.loaded && !state.busy) {
                        if (phase == Phase.Approving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Approve", fontWeight = FontWeight.SemiBold)
                        }
                    }
            }
        },
        dismissButton = {
            if (!state.finished && phase !is Phase.Failed) {
                TextButton(
                    onClick = vm::deny,
                    enabled = state.loaded && !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(if (phase == Phase.Denying) "Denying…" else "This wasn't me") }
            } else if (phase is Phase.Failed && phase.error.retryable) {
                TextButton(onClick = onDismiss) { Text("Not now", color = GlassSheetTokens.OnOledMuted()) }
            }
        },
    )
}

/**
 * On a device waiting for permission to read past messages (atop Clicks): how to unlock them,
 * the devices that can approve it, the emailed link as the fallback, and "Ask again" after a
 * denial or expiry. Gone once approved (iOS `DeviceHistoryBanner`).
 */
@Composable
fun DeviceHistoryBanner(modifier: Modifier = Modifier) {
    val approvals by DeviceApprovals.state.collectAsState()
    val deviceName = remember { thisDeviceLabel() }
    val banner =
        deviceHistoryBannerState(
            own = approvals.own,
            approvers = approvals.approvers,
            emailedHere = approvals.emailedHere,
            thisDevice = deviceName,
            nowMs = Clock.System.now().toEpochMilliseconds(),
        ) ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(banner.action) { failed = false }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(clickCardSurface(), shape)
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(banner.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(banner.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            banner.approverLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (failed) {
                Text(
                    "Couldn't reach Click. Try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            banner.action?.let { action ->
                TextButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            val result =
                                when (action) {
                                    DeviceHistoryBannerState.Action.AskAgain -> DeviceApprovals.askAgain()
                                    DeviceHistoryBannerState.Action.EmailLink -> DeviceApprovals.emailLink()
                                }
                            failed = result.isFailure
                            DeviceApprovals.refresh()
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) { Text(action.title, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
