package compose.project.click.click.calls

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private const val CALL_PERMISSION_REQUEST_CODE = 4013

private object AndroidCallRuntime {
    private var applicationContext: Context? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    fun init(context: Context, activity: Activity? = null) {
        applicationContext = context.applicationContext
        if (activity != null) {
            currentActivityRef = WeakReference(activity)
        }
    }

    fun currentActivity(): Activity? = currentActivityRef?.get()

    fun appContext(): Context? = applicationContext
}

actual class CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    actual val callState: StateFlow<CallState> = _callState.asStateFlow()
    private var room: Room? = null
    private var eventsJob: Job? = null
    private var microphoneEnabled = true
    private var cameraEnabled = false
    private var videoRequested = false
    private val rendererBindings = linkedMapOf<TextureViewRenderer, Boolean>()
    private val attachedTracks = linkedMapOf<TextureViewRenderer, VideoTrack?>()

    actual fun startCall(roomName: String, token: String, wsUrl: String, videoEnabled: Boolean) {
        val context = AndroidCallRuntime.appContext()
        val activity = AndroidCallRuntime.currentActivity()
        if (context == null || activity == null) {
            _callState.value = CallState.Ended("Call context unavailable")
            return
        }

        val requiredPermissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (videoEnabled) add(Manifest.permission.CAMERA)
        }

        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            activity.runOnUiThread {
                ActivityCompat.requestPermissions(
                    activity,
                    missingPermissions.toTypedArray(),
                    CALL_PERMISSION_REQUEST_CODE
                )
            }
            _callState.value = CallState.Ended("Camera or microphone permission required")
            return
        }

        cleanupRoom()
        microphoneEnabled = true
        cameraEnabled = videoEnabled
        videoRequested = videoEnabled
        _callState.value = CallState.Connecting(videoRequested = videoEnabled)

        val liveKitRoom = LiveKit.create(
            appContext = context,
            options = RoomOptions(
                adaptiveStream = true,
                dynacast = true,
            ),
        )
        room = liveKitRoom

        eventsJob = scope.launch {
            liveKitRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed,
                    is RoomEvent.TrackUnsubscribed,
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected,
                    is RoomEvent.Reconnected,
                    -> syncStateFromRoom()

                    is RoomEvent.Reconnecting -> {
                        _callState.value = CallState.Connecting(videoRequested = videoRequested)
                    }

                    is RoomEvent.Disconnected -> {
                        cleanupRoom(releaseState = false)
                        _callState.value = CallState.Ended(
                            event.reason.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                        )
                    }

                    else -> Unit
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            try {
                liveKitRoom.connect(
                    url = wsUrl,
                    token = token,
                    options = ConnectOptions(autoSubscribe = true),
                )
                liveKitRoom.localParticipant.setMicrophoneEnabled(true)
                if (videoEnabled) {
                    liveKitRoom.localParticipant.setCameraEnabled(true)
                }
                syncStateFromRoom()
            } catch (error: Throwable) {
                cleanupRoom(releaseState = false)
                _callState.value = CallState.Ended(error.message ?: "Unable to connect call")
            }
        }
    }

    actual fun setMicrophoneEnabled(enabled: Boolean) {
        val activeRoom = room ?: return
        scope.launch(Dispatchers.IO) {
            try {
                activeRoom.localParticipant.setMicrophoneEnabled(enabled)
                microphoneEnabled = enabled
                syncStateFromRoom()
            } catch (error: Throwable) {
                _callState.value = CallState.Ended(error.message ?: "Unable to update microphone")
            }
        }
    }

    actual fun setCameraEnabled(enabled: Boolean) {
        val activeRoom = room ?: return
        scope.launch(Dispatchers.IO) {
            try {
                activeRoom.localParticipant.setCameraEnabled(enabled)
                videoRequested = enabled
                cameraEnabled = enabled
                syncStateFromRoom()
            } catch (error: Throwable) {
                _callState.value = CallState.Ended(error.message ?: "Unable to update camera")
            }
        }
    }

    actual fun endCall() {
        cleanupRoom()
        _callState.value = CallState.Idle
    }

    internal fun bindRenderer(renderer: TextureViewRenderer, isLocal: Boolean) {
        rendererBindings[renderer] = isLocal
        room?.initVideoRenderer(renderer)
        syncRenderer(renderer, isLocal)
    }

    internal fun unbindRenderer(renderer: TextureViewRenderer) {
        attachedTracks.remove(renderer)?.removeRenderer(renderer)
        rendererBindings.remove(renderer)
    }

    private fun syncStateFromRoom() {
        val activeRoom = room ?: return
        val localTrack = activeRoom.localParticipant
            .getTrackPublication(Track.Source.CAMERA)
            ?.track as? VideoTrack
        val remoteTrack = currentRemoteVideoTrack(activeRoom)

        cameraEnabled = localTrack != null && videoRequested
        _callState.value = CallState.Connected(
            videoRequested = videoRequested,
            microphoneEnabled = microphoneEnabled,
            cameraEnabled = cameraEnabled,
            remoteVideoAvailable = remoteTrack != null,
            localVideoAvailable = localTrack != null,
        )

        scope.launch {
            rendererBindings.forEach { (renderer, isLocal) ->
                syncRenderer(renderer, isLocal)
            }
        }
    }

    private fun syncRenderer(renderer: TextureViewRenderer, isLocal: Boolean) {
        val activeRoom = room
        if (activeRoom == null) {
            attachedTracks.remove(renderer)?.removeRenderer(renderer)
            renderer.visibility = View.INVISIBLE
            return
        }

        val targetTrack = if (isLocal) {
            activeRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        } else {
            currentRemoteVideoTrack(activeRoom)
        }

        val currentTrack = attachedTracks[renderer]
        if (currentTrack !== targetTrack) {
            currentTrack?.removeRenderer(renderer)
            targetTrack?.addRenderer(renderer)
            attachedTracks[renderer] = targetTrack
        }

        renderer.visibility = if (targetTrack != null) View.VISIBLE else View.INVISIBLE
        if (isLocal) {
            renderer.setMirror(true)
        }
    }

    private fun currentRemoteVideoTrack(activeRoom: Room): VideoTrack? {
        val participant = activeRoom.remoteParticipants.values.firstOrNull() ?: return null
        return participant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
            ?: participant.getTrackPublication(Track.Source.SCREEN_SHARE)?.track as? VideoTrack
    }

    private fun cleanupRoom(releaseState: Boolean = true) {
        eventsJob?.cancel()
        eventsJob = null

        attachedTracks.forEach { (renderer, track) ->
            track?.removeRenderer(renderer)
            renderer.visibility = View.INVISIBLE
        }
        attachedTracks.clear()

        val activeRoom = room
        room = null
        if (activeRoom != null) {
            try {
                activeRoom.disconnect()
            } catch (_: Throwable) {
            }
            try {
                activeRoom.release()
            } catch (_: Throwable) {
            }
        }

        if (releaseState) {
            microphoneEnabled = true
            cameraEnabled = false
            videoRequested = false
        }
    }
}

fun initCallManager(context: Context, activity: Activity? = null) {
    AndroidCallRuntime.init(context, activity)
}

actual fun createCallManager(): CallManager = CallManager()