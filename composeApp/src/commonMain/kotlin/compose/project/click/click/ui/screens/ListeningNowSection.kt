@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebRequestException // pragma: allowlist secret
import compose.project.click.click.data.beacons.ListeningNow
import compose.project.click.click.data.beacons.getListening
import compose.project.click.click.data.beacons.startListening
import compose.project.click.click.data.beacons.stopListening
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Listening now" under a soundtrack (iOS `ListeningNowSection`): the count and connections' names,
 * and a Listen toggle that heartbeats on the server's cadence while this screen is resumed.
 */
@Composable
internal fun ListeningNowSection(
    beaconId: String,
    api: ApiClient = remember { ApiClient() },
) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember(beaconId) { mutableStateOf<ListeningNow?>(null) }
    var hidden by remember(beaconId) { mutableStateOf(false) }
    var toggling by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(beaconId) {
        api.getListening(beaconId).fold(
            onSuccess = { state = it },
            // 404: flag off for this viewer or the soundtrack ended. Never an error.
            onFailure = { if ((it as? ClickWebRequestException)?.statusCode == 404) hidden = true },
        )
    }
    val listening = state?.isListening == true
    val heartbeat = state?.heartbeatSeconds ?: 360
    LaunchedEffect(beaconId, listening, heartbeat) {
        if (!listening) return@LaunchedEffect
        while (true) {
            delay(heartbeat * 1000L)
            // Only while the screen is in front: wait out a background stretch, then confirm.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            api.startListening(beaconId).onSuccess { state = it }
        }
    }
    // Leaving the screen stops right away instead of waiting for the heartbeat to lapse.
    DisposableEffect(beaconId) {
        onDispose {
            if (state?.isListening == true) scope.launch { api.stopListening(beaconId) }
        }
    }
    if (hidden || state == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = state?.summary ?: "Nobody's listening right now",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        val onToggle: () -> Unit = {
            toggling = true
            scope.launch {
                val result = if (listening) api.stopListening(beaconId) else api.startListening(beaconId)
                result.fold(
                    onSuccess = {
                        state = it
                        message = null
                    },
                    onFailure = { message = "Couldn't update that. Try again." },
                )
                toggling = false
            }
        }
        val label = if (listening) "Listening" else "Listen"
        val a11y = Modifier.semantics { contentDescription = if (listening) "Listening. Double tap to stop." else "Listen" }
        if (listening) {
            Button(onClick = onToggle, enabled = !toggling, modifier = a11y, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                ToggleContent(toggling, label)
            }
        } else {
            OutlinedButton(onClick = onToggle, enabled = !toggling, modifier = a11y) { ToggleContent(toggling, label) }
        }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ToggleContent(
    toggling: Boolean,
    label: String,
) {
    if (toggling) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
    } else {
        Icon(Icons.Filled.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    Spacer(Modifier.width(6.dp))
    Text(label)
}
