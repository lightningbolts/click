package compose.project.click.click.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders the LiveKit camera track for [participantId] (local or remote identity).
 * Prefer binding by identity so Grid / Speaker layouts can show every remote.
 */
@Composable
expect fun CallVideoSurface(
    callManager: CallManager,
    participantId: String,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
)
