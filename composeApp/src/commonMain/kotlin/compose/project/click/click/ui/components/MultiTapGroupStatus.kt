@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.domain.MultiTapGroupState // pragma: allowlist secret

/**
 * A group Click's chat setup on its result (P2-19; iOS `PostConnectView.groupActions`):
 * "Setting up group chat…" while it runs, "Open group chat" once ready, and the failure with
 * "Try again". Nothing is shown when no setup is running.
 */
@Composable
fun MultiTapGroupStatus(
    state: MultiTapGroupState,
    onOpenGroupChat: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is MultiTapGroupState.Idle) return
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is MultiTapGroupState.Ready ->
                ClickButton(onClick = onOpenGroupChat, modifier = Modifier.fillMaxWidth()) {
                    Text("Open group chat")
                }
            is MultiTapGroupState.Preparing ->
                ClickButton(onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false) {
                    Text("Setting up group chat…")
                }
            is MultiTapGroupState.Failed -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                ClickButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
            }
            MultiTapGroupState.Idle -> Unit
        }
    }
}
