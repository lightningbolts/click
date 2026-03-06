package compose.project.click.click.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer

@Composable
actual fun CallVideoSurface(
    callManager: CallManager,
    isLocal: Boolean,
    modifier: Modifier,
) {
    var renderer by remember { mutableStateOf<TextureViewRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureViewRenderer(context).also { view ->
                renderer = view
                callManager.bindRenderer(view, isLocal)
            }
        },
        update = { view ->
            renderer = view
            callManager.bindRenderer(view, isLocal)
        },
    )

    DisposableEffect(callManager, renderer) {
        val activeRenderer = renderer
        onDispose {
            if (activeRenderer != null) {
                callManager.unbindRenderer(activeRenderer)
                activeRenderer.release()
            }
        }
    }
}