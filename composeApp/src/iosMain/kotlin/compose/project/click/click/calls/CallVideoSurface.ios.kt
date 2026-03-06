package compose.project.click.click.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun CallVideoSurface(
    callManager: CallManager,
    isLocal: Boolean,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(Color.Transparent)
    )
}