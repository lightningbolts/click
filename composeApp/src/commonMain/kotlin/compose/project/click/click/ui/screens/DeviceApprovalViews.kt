@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalHttpException // pragma: allowlist secret
import compose.project.click.click.data.devices.DeviceApproval // pragma: allowlist secret
import compose.project.click.click.data.devices.DeviceApprovalCopy // pragma: allowlist secret
import compose.project.click.click.data.devices.DeviceApprovalStore // pragma: allowlist secret
import compose.project.click.click.data.refreshDeviceApprovals // pragma: allowlist secret
import compose.project.click.click.data.repository.askForHistoryImpl // pragma: allowlist secret
import compose.project.click.click.data.repository.decideDeviceApprovalImpl // pragma: allowlist secret
import compose.project.click.click.data.repository.emailDeviceApprovalLinkImpl // pragma: allowlist secret
import compose.project.click.click.data.shareHistoryAfterApproval // pragma: allowlist secret
import compose.project.click.click.platform.DeviceOwnerAuthCancelled // pragma: allowlist secret
import compose.project.click.click.platform.DeviceOwnerAuthUnavailable // pragma: allowlist secret
import compose.project.click.click.platform.authenticateDeviceOwner // pragma: allowlist secret
import compose.project.click.click.platform.deviceKindLabel // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickPlatformSheet // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 409 (already decided) and 410 (expired / device gone): nothing left to decide on this request. */
internal fun isApprovalClosed(error: Throwable): Boolean = error is DeviceApprovalHttpException && error.status in setOf(409, 410)

/** Phases of the approval sheet; pure so the copy and buttons can be tested. */
internal sealed interface ApprovalPhase {
    data object Asking : ApprovalPhase

    data object Approving : ApprovalPhase

    data object Denying : ApprovalPhase

    data object Approved : ApprovalPhase

    data object Denied : ApprovalPhase

    data class Failed(
        val message: String,
        /** The request can no longer be decided (409 / 410): only "Done" is offered. */
        val closed: Boolean = false,
    ) : ApprovalPhase
}

/** What a failed decision says (iOS `DeviceApprovalSheet.decide`); null means "back to asking". */
internal fun approvalFailureMessage(error: Throwable): String? =
    when (error) {
        is DeviceOwnerAuthCancelled, is CancellationException -> null
        is DeviceOwnerAuthUnavailable ->
            "Device authentication is required to approve this request. Set a screen lock, or use the link we emailed you."
        is DeviceApprovalHttpException ->
            when (error.status) {
                409 -> "This request was already decided on another device."
                410 -> "This request expired or that device was signed out. It can ask again from there."
                else -> "Couldn't reach Click. Check your connection and try again."
            }
        else -> "Couldn't reach Click. Check your connection and try again."
    }

/**
 * Shows "Approve new sign-in?" for the first pending request from another of the account's devices.
 * Swiping it away decides later (it comes back next launch).
 */
@Composable
fun DeviceApprovalHost() {
    val approvals by DeviceApprovalStore.state.collectAsState()
    val deferred by DeviceApprovalStore.deferred.collectAsState()
    val next = approvals.incoming.firstOrNull { it.id !in deferred } ?: return
    DeviceApprovalSheet(approval = next, onDismiss = { DeviceApprovalStore.defer(next.id) })
}

@Composable
private fun DeviceApprovalSheet(
    approval: DeviceApproval,
    onDismiss: () -> Unit,
) {
    var phase by remember(approval.id) { mutableStateOf<ApprovalPhase>(ApprovalPhase.Asking) }
    val scope = rememberCoroutineScope()
    val busy = phase == ApprovalPhase.Approving || phase == ApprovalPhase.Denying
    val label = approval.deviceLabel

    fun decide(approve: Boolean) {
        val userId = AppDataManager.currentUser.value?.id
        phase = if (approve) ApprovalPhase.Approving else ApprovalPhase.Denying
        scope.launch {
            try {
                AppDataManager.chatRepository.decideDeviceApprovalImpl(
                    requestId = approval.id,
                    approve = approve,
                    authorize =
                        if (approve) {
                            {
                                authenticateDeviceOwner("Confirm that you want to share Click message history with this device.")
                                // A session switch while the prompt was open must never approve the old account.
                                if (AppDataManager.currentUser.value?.id != userId) throw DeviceOwnerAuthCancelled()
                            }
                        } else {
                            null
                        },
                )
                phase = if (approve) ApprovalPhase.Approved else ApprovalPhase.Denied
                if (approve) AppDataManager.shareHistoryAfterApproval()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase = approvalFailureMessage(e)?.let { ApprovalPhase.Failed(it, closed = isApprovalClosed(e)) } ?: ApprovalPhase.Asking
            }
        }
    }

    fun close() {
        if (phase == ApprovalPhase.Approved || phase == ApprovalPhase.Denied || (phase as? ApprovalPhase.Failed)?.closed == true) {
            DeviceApprovalStore.decided(approval.id)
            AppDataManager.refreshDeviceApprovals()
        } else {
            onDismiss()
        }
    }

    ClickPlatformSheet(onDismissRequest = { if (!busy) close() }, expandable = false) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val decided = phase == ApprovalPhase.Approved || phase == ApprovalPhase.Denied
            val finished = decided || (phase as? ApprovalPhase.Failed)?.closed == true
            Surface(shape = RoundedCornerShape(38.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector =
                            when {
                                decided -> Icons.Filled.Shield
                                DeviceApprovalCopy.isBrowser(label) &&
                                    label?.endsWith(" on Android") != true &&
                                    label?.endsWith(" on iPhone") != true -> Icons.Outlined.Laptop
                                else -> Icons.Filled.PhoneAndroid
                            },
                        contentDescription = null,
                        tint = if (phase == ApprovalPhase.Denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text =
                        when (phase) {
                            ApprovalPhase.Approved -> "Approved"
                            ApprovalPhase.Denied -> "Not approved"
                            else -> "Approve new sign-in?"
                        },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text =
                        when (val p = phase) {
                            ApprovalPhase.Approved -> DeviceApprovalCopy.approvedDetail(label)
                            ApprovalPhase.Denied -> DeviceApprovalCopy.deniedDetail(label)
                            is ApprovalPhase.Failed -> p.message
                            else -> DeviceApprovalCopy.askingDetail(label)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (finished) {
                Button(onClick = ::close, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    Text("Done")
                }
            } else {
                Button(
                    onClick = { decide(approve = true) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) {
                    if (phase == ApprovalPhase.Approving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Approve")
                    }
                }
                TextButton(
                    onClick = { decide(approve = false) },
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) {
                    Text(if (phase == ApprovalPhase.Denying) "Denying…" else "This wasn't me")
                }
            }
        }
    }
}

/**
 * On a device waiting for permission to read past messages: how to unlock them, with the emailed
 * link as the fallback and "Ask again" after a denial. Gone once approved.
 */
@Composable
fun DeviceHistoryBanner(modifier: Modifier = Modifier) {
    val approvals by DeviceApprovalStore.state.collectAsState()
    val own = approvals.own ?: return
    if (own.status == DeviceApproval.Status.APPROVED) return
    var busy by remember { mutableStateOf(false) }
    var emailed by remember(own.id) { mutableStateOf(false) }
    var actionError by remember(own.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val emailSent = own.emailSent || emailed
    val denied = own.status == DeviceApproval.Status.DENIED
    val action = DeviceApprovalCopy.waitingAction(own, emailSent)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (denied) "Older messages stay locked" else "Unlock older messages",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = DeviceApprovalCopy.waitingSubtitle(own, emailSent, deviceKindLabel()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                actionError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (action != null) {
                    TextButton(
                        enabled = !busy,
                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                        onClick = {
                            busy = true
                            scope.launch {
                                actionError = null
                                runCatching {
                                    when (action) {
                                        DeviceApprovalCopy.WaitingAction.ASK_AGAIN -> {
                                            val updated =
                                                AppDataManager.chatRepository.askForHistoryImpl(reopen = true)
                                                    ?: error("Not signed in")
                                            DeviceApprovalStore.set(updated)
                                        }
                                        DeviceApprovalCopy.WaitingAction.EMAIL_LINK -> {
                                            AppDataManager.chatRepository.emailDeviceApprovalLinkImpl(own.id)
                                            emailed = true
                                        }
                                    }
                                }.onFailure { e ->
                                    if (e is CancellationException) throw e
                                    actionError = "Couldn't reach Click. Check your connection and try again."
                                }
                                busy = false
                                AppDataManager.refreshDeviceApprovals()
                            }
                        },
                    ) {
                        Text(
                            text = if (action == DeviceApprovalCopy.WaitingAction.ASK_AGAIN) "Ask again" else "Email me a link instead",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
