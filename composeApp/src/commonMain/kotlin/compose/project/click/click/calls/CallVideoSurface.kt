package compose.project.click.click.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CallVideoSurface(
    callManager: CallManager,
    isLocal: Boolean,
    modifier: Modifier = Modifier,
)