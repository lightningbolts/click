package compose.project.click.click.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack

/**
 * Renders a LiveKit [VideoTrack] for [participantId]. Creates a fresh [TextureViewRenderer]
 * per track instance so EGL init + addRenderer always happen together.
 */
@Composable
actual fun CallVideoSurface(
    callManager: CallManager,
    participantId: String,
    modifier: Modifier,
    mirror: Boolean,
) {
    var track by remember(callManager, participantId) { mutableStateOf<VideoTrack?>(null) }

    DisposableEffect(callManager, participantId) {
        val listener: (VideoTrack?) -> Unit = { track = it }
        callManager.addVideoTrackListener(participantId, listener)
        onDispose { callManager.removeVideoTrackListener(participantId, listener) }
    }

    val activeTrack = track
    if (activeTrack == null) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    key(activeTrack) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TextureViewRenderer(context).apply {
                    isOpaque = true
                    val initialized = callManager.initRenderer(this)
                    if (initialized) {
                        setMirror(mirror)
                        activeTrack.addRenderer(this)
                        tag = activeTrack
                    }
                }
            },
            update = { view ->
                val bound = view.tag as? VideoTrack
                if (bound !== activeTrack) {
                    bound?.removeRenderer(view)
                    activeTrack.addRenderer(view)
                    view.tag = activeTrack
                    view.setMirror(mirror)
                }
            },
            onRelease = { view ->
                val bound = view.tag as? VideoTrack
                bound?.removeRenderer(view)
                view.tag = null
                runCatching { view.release() }
            },
        )
    }
}
