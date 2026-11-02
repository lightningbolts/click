@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebRequestException // pragma: allowlist secret
import compose.project.click.click.data.beacons.AlertConfirmationState
import compose.project.click.click.data.beacons.AlertPhase
import compose.project.click.click.data.beacons.AlertVote
import compose.project.click.click.data.beacons.AlertVoteRejectedException
import compose.project.click.click.data.beacons.AlertVoteRejection
import compose.project.click.click.data.beacons.BEACON_REPORT_REASONS
import compose.project.click.click.data.beacons.confirmAlert
import compose.project.click.click.data.beacons.getAlertConfirmation
import compose.project.click.click.data.beacons.reportBeacon
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Section state: loading, loaded, hidden (flag off / not visible), or a retryable failure. */
private sealed interface AlertLoad {
    data object Loading : AlertLoad

    data object Hidden : AlertLoad

    data object Failed : AlertLoad

    data class Loaded(
        val state: AlertConfirmationState,
    ) : AlertLoad
}

/**
 * "Is it still there?" on a hazard alert (iOS `AlertConfirmationSection`). Location is read only when
 * already allowed and only to cast a vote; the creator takes it down from anywhere.
 */
@Composable
internal fun AlertConfirmationSection(
    beaconId: String,
    onEnded: () -> Unit,
    api: ApiClient = remember { ApiClient() },
    location: LocationService = remember { LocationService() },
) {
    val scope = rememberCoroutineScope()
    var load by remember(beaconId) { mutableStateOf<AlertLoad>(AlertLoad.Loading) }
    var pending by remember { mutableStateOf<AlertVote?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(beaconId, reloadKey) {
        api.getAlertConfirmation(beaconId).fold(
            onSuccess = {
                load = AlertLoad.Loaded(it)
                if (it.phase != AlertPhase.ACTIVE) onEnded()
            },
            onFailure = { err ->
                // 404: the flag is off for this viewer, or the alert isn't visible. Never an error.
                load = if ((err as? ClickWebRequestException)?.statusCode == 404) AlertLoad.Hidden else AlertLoad.Failed
            },
        )
    }

    val current = load
    if (current == AlertLoad.Hidden) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Is it still there?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        when (current) {
            AlertLoad.Loading -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            AlertLoad.Failed ->
                TextButton(onClick = { reloadKey++ }) { Text("Couldn't load. Try again") }
            is AlertLoad.Loaded -> {
                val state = current.state
                if (state.phase == AlertPhase.ACTIVE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        AlertVote.entries.forEach { vote ->
                            val chosen = state.myVote == vote
                            OutlinedButton(
                                onClick = {
                                    pending = vote
                                    scope.launch {
                                        // The creator clears from anywhere; everyone else is checked
                                        // against the pin server-side, with a location only if allowed.
                                        val needsFix = !(state.isCreator && vote == AlertVote.CLEARED)
                                        val fix =
                                            if (needsFix && location.hasLocationPermission()) location.getCurrentLocation() else null
                                        if (needsFix && fix == null) {
                                            message = AlertVoteRejection.NEEDS_LOCATION.message
                                            pending = null
                                            return@launch
                                        }
                                        api.confirmAlert(beaconId, vote, fix?.latitude, fix?.longitude).fold(
                                            onSuccess = { expiresAt ->
                                                message = null
                                                if (expiresAt != null && expiresAt <= Clock.System.now().toEpochMilliseconds()) onEnded()
                                                reloadKey++
                                            },
                                            onFailure = { err ->
                                                val rejection = (err as? AlertVoteRejectedException)?.rejection
                                                message = rejection?.message ?: "Couldn't send that. Try again."
                                                if (rejection == AlertVoteRejection.ENDED) onEnded()
                                            },
                                        )
                                        pending = null
                                    }
                                },
                                enabled = pending == null && state.canVote(vote),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                if (pending == vote) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        when {
                                            chosen -> Icons.Default.Check
                                            vote == AlertVote.STILL_HERE -> Icons.Default.Warning
                                            else -> Icons.Default.CheckCircle
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when {
                                        vote == AlertVote.STILL_HERE -> "Still here"
                                        state.isCreator -> "Take it down"
                                        else -> "Cleared"
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Text(
                        state.caption(Clock.System.now().toEpochMilliseconds()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AlertLoad.Hidden -> Unit
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A quiet Report for people who didn't post the beacon (iOS beacon share menu). */
@Composable
internal fun BeaconReportButton(
    beaconId: String,
    api: ApiClient = remember { ApiClient() },
) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var status by remember(beaconId) { mutableStateOf<String?>(null) }
    Box {
        TextButton(onClick = { open = true }, enabled = status == null) {
            Text(status ?: "Report", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BEACON_REPORT_REASONS.forEach { reason ->
                DropdownMenuItem(
                    text = { Text(reason) },
                    onClick = {
                        open = false
                        scope.launch {
                            status = if (api.reportBeacon(beaconId, reason).isSuccess) "Reported. Thanks." else null
                        }
                    },
                )
            }
        }
    }
}
