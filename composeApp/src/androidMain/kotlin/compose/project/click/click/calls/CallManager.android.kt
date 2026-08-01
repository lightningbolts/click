package compose.project.click.click.calls

import android.Manifest
import android.app.Activity
import android.content.Context.AUDIO_SERVICE
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

internal data class PendingCallStart(
    val roomName: String,
    val token: String,
    val wsUrl: String,
    val videoEnabled: Boolean,
)

internal object AndroidCallRuntime {
    private var applicationContext: Context? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var permissionRequester: ((Array<String>) -> Unit)? = null
    private var pendingCallStart: PendingCallStart? = null
    private var onPermissionGranted: ((PendingCallStart) -> Unit)? = null
    private var onPermissionDenied: (() -> Unit)? = null
    private var deferredStart: PendingCallStart? = null
    private var onDeferredStartReady: ((PendingCallStart) -> Unit)? = null

    fun init(context: Context, activity: Activity? = null) {
        applicationContext = context.applicationContext
        if (activity != null) {
            currentActivityRef = WeakReference(activity)
            tryFlushDeferredStart()
        }
    }

    fun currentActivity(): Activity? = currentActivityRef?.get()

    fun appContext(): Context? = applicationContext

    fun registerPermissionRequester(requester: ((Array<String>) -> Unit)?) {
        permissionRequester = requester
        if (requester != null) {
            tryFlushDeferredStart()
        }
    }

    fun requestCallPermissions(
        permissions: Array<String>,
        pending: PendingCallStart,
        onGranted: (PendingCallStart) -> Unit,
        onDenied: () -> Unit,
    ): Boolean {
        val requester = permissionRequester
        if (requester == null) {
            return false
        }
        clearDeferredStart()
        pendingCallStart = pending
        onPermissionGranted = onGranted
        onPermissionDenied = onDenied
        requester(permissions)
        return true
    }

    /**
     * Keeps a start queued while [MainActivity] is not yet able to request permissions.
     * Flushed from [init] / [registerPermissionRequester] when the Activity Result launcher is ready.
     */
    fun deferStartUntilRequesterReady(
        pending: PendingCallStart,
        onReady: (PendingCallStart) -> Unit,
    ) {
        deferredStart = pending
        onDeferredStartReady = onReady
        tryFlushDeferredStart()
    }

    fun handlePermissionResult(allGranted: Boolean) {
        val pending = pendingCallStart
        val grantedCb = onPermissionGranted
        val deniedCb = onPermissionDenied
        clearPendingPermissionRequest()
        if (allGranted && pending != null && grantedCb != null) {
            grantedCb(pending)
        } else {
            deniedCb?.invoke()
        }
    }

    fun clearPendingPermissionRequest() {
        pendingCallStart = null
        onPermissionGranted = null
        onPermissionDenied = null
        clearDeferredStart()
    }

    private fun clearDeferredStart() {
        deferredStart = null
        onDeferredStartReady = null
    }

    private fun tryFlushDeferredStart() {
        if (permissionRequester == null) return
        val pending = deferredStart ?: return
        val onReady = onDeferredStartReady ?: return
        clearDeferredStart()
        onReady(pending)
    }
}

actual class CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    actual val callState: StateFlow<CallState> = _callState.asStateFlow()
    private var room: Room? = null
    private var eventsJob: Job? = null
    private var deferIdleAfterEndJob: Job? = null
    private var microphoneEnabled = true
    private var speakerEnabled = false
    private var cameraEnabled = false
    private var videoRequested = false
    /** True once a remote participant has joined — used to end the call when they leave. */
    private var hadRemoteParticipant = false
    /** Polls briefly after a peer joins so remote camera is bound as soon as the track exists. */
    private var remoteVideoPollJob: Job? = null
    private val localVideoListeners = mutableListOf<(VideoTrack?) -> Unit>()
    private val remoteVideoListeners = mutableListOf<(VideoTrack?) -> Unit>()

    actual fun startCall(roomName: String, token: String, wsUrl: String, videoEnabled: Boolean) {
        val context = AndroidCallRuntime.appContext()
        if (context == null) {
            markEndedAndDeferIdle("Call context unavailable")
            return
        }

        val requiredPermissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (videoEnabled) add(Manifest.permission.CAMERA)
        }

        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            val pending = PendingCallStart(
                roomName = roomName,
                token = token,
                wsUrl = wsUrl,
                videoEnabled = videoEnabled,
            )
            val resumeStart: (PendingCallStart) -> Unit = { granted ->
                startCall(
                    roomName = granted.roomName,
                    token = granted.token,
                    wsUrl = granted.wsUrl,
                    videoEnabled = granted.videoEnabled,
                )
            }
            _callState.value = CallState.Connecting(videoRequested = videoEnabled)
            val requested = AndroidCallRuntime.requestCallPermissions(
                permissions = missingPermissions.toTypedArray(),
                pending = pending,
                onGranted = resumeStart,
                onDenied = {
                    markEndedAndDeferIdle("Camera or microphone permission required")
                },
            )
            if (!requested) {
                // Keep Connecting; retry when MainActivity registers the permission launcher.
                AndroidCallRuntime.deferStartUntilRequesterReady(pending, resumeStart)
            }
            return
        }

        deferIdleAfterEndJob?.cancel()
        deferIdleAfterEndJob = null
        AndroidCallRuntime.clearPendingPermissionRequest()
        cleanupRoom()
        microphoneEnabled = true
        speakerEnabled = videoEnabled
        cameraEnabled = videoEnabled
        videoRequested = videoEnabled
        updateAudioRoute(videoEnabled)
        _callState.value = CallState.Connecting(videoRequested = videoEnabled)

        val liveKitRoom = LiveKit.create(
            appContext = context,
            options = RoomOptions(
                // Keep both off for 1:1 calls. adaptiveStream+dynacast can pause layers when
                // Compose TextureViewRenderer visibility is flaky, which looks like "no remote video".
                adaptiveStream = false,
                dynacast = false,
            ),
        )
        room = liveKitRoom

        eventsJob = scope.launch {
            liveKitRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected,
                    is RoomEvent.TrackSubscribed,
                    is RoomEvent.TrackUnsubscribed,
                    is RoomEvent.TrackPublished,
                    is RoomEvent.TrackUnpublished,
                    is RoomEvent.TrackMuted,
                    is RoomEvent.TrackUnmuted,
                    is RoomEvent.LocalTrackSubscribed,
                    is RoomEvent.TrackStreamStateChanged,
                    is RoomEvent.TrackSubscriptionPermissionChanged,
                    is RoomEvent.Reconnected,
                    -> {
                        syncStateFromRoom()
                        if (
                            event is RoomEvent.TrackSubscribed ||
                            event is RoomEvent.TrackPublished ||
                            event is RoomEvent.TrackUnmuted
                        ) {
                            // Peer camera often appears as publish/subscribe shortly after join.
                            if (currentRemoteVideoTrack(liveKitRoom) == null) {
                                startRemoteVideoPoll(liveKitRoom)
                            }
                        }
                    }

                    is RoomEvent.ParticipantConnected -> {
                        syncStateFromRoom()
                        startRemoteVideoPoll(liveKitRoom)
                    }

                    is RoomEvent.ParticipantDisconnected -> {
                        syncStateFromRoom()
                        // 1:1: when the other party leaves the room, end locally even if Realtime cancel was missed.
                        if (
                            hadRemoteParticipant &&
                            liveKitRoom.remoteParticipants.isEmpty() &&
                            room === liveKitRoom
                        ) {
                            hadRemoteParticipant = false
                            cleanupRoom(releaseState = false)
                            markEndedAndDeferIdle("Call ended")
                        }
                    }

                    is RoomEvent.TrackSubscriptionFailed,
                    is RoomEvent.TrackPublicationFailed,
                    -> syncStateFromRoom()

                    is RoomEvent.Reconnecting -> {
                        _callState.value = CallState.Connecting(videoRequested = videoRequested)
                    }

                    is RoomEvent.Disconnected -> {
                        cleanupRoom(releaseState = false)
                        markEndedAndDeferIdle(
                            event.reason.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        )
                    }

                    else -> Unit
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            try {
                // Publish A/V during connect so the peer can subscribe immediately instead of
                // waiting for post-connect setMicrophone/setCamera round-trips.
                liveKitRoom.connect(
                    url = wsUrl,
                    token = token,
                    options = ConnectOptions(
                        autoSubscribe = true,
                        audio = true,
                        video = videoEnabled,
                    ),
                )
                microphoneEnabled = true

                if (videoEnabled) {
                    val localCamera = liveKitRoom.localParticipant
                        .getTrackPublication(Track.Source.CAMERA)?.track
                    if (localCamera == null) {
                        val cameraOk = ensureCameraEnabled(liveKitRoom)
                        if (!cameraOk) {
                            // Stay in-call on audio so the session is usable; peer will keep
                            // "Waiting for remote video" until camera can be enabled later.
                            println("CallManager: camera failed to publish after connect")
                        }
                        cameraEnabled = cameraOk
                    } else {
                        cameraEnabled = true
                    }
                }
                syncStateFromRoom()
                if (liveKitRoom.remoteParticipants.isNotEmpty()) {
                    startRemoteVideoPoll(liveKitRoom)
                }

                if (videoEnabled) {
                    // Publication can lag the boolean success; quick retry if no local track yet.
                    delay(150)
                    if (room === liveKitRoom &&
                        liveKitRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track == null
                    ) {
                        ensureCameraEnabled(liveKitRoom)
                        syncStateFromRoom()
                    }
                }
            } catch (error: Throwable) {
                cleanupRoom(releaseState = false)
                markEndedAndDeferIdle(error.message ?: "Unable to connect call")
            }
        }
    }

    actual fun setMicrophoneEnabled(enabled: Boolean) {
        val activeRoom = room ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val ok = activeRoom.localParticipant.setMicrophoneEnabled(enabled)
                if (!ok) {
                    _callState.value = CallState.Ended("Unable to update microphone")
                    return@launch
                }
                microphoneEnabled = enabled
                syncStateFromRoom()
            } catch (error: Throwable) {
                _callState.value = CallState.Ended(error.message ?: "Unable to update microphone")
            }
        }
    }

    actual fun setSpeakerEnabled(enabled: Boolean) {
        speakerEnabled = enabled
        updateAudioRoute(enabled)
        syncStateFromRoom()
    }

    actual fun setCameraEnabled(enabled: Boolean) {
        val activeRoom = room ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val ok = if (enabled) {
                    ensureCameraEnabled(activeRoom)
                } else {
                    activeRoom.localParticipant.setCameraEnabled(false)
                }
                if (!ok) {
                    _callState.value = CallState.Ended("Unable to update camera")
                    return@launch
                }
                videoRequested = videoRequested || enabled
                cameraEnabled = enabled
                syncStateFromRoom()
            } catch (error: Throwable) {
                _callState.value = CallState.Ended(error.message ?: "Unable to update camera")
            }
        }
    }

    actual fun endCall() {
        AndroidCallRuntime.clearPendingPermissionRequest()
        cleanupRoom()
        markEndedAndDeferIdle("Call ended", clearPending = false)
    }

    /** Failed starts and hang-up: briefly show Ended, then Idle so the next invite is not stuck busy. */
    private fun markEndedAndDeferIdle(reason: String, clearPending: Boolean = true) {
        if (clearPending) {
            AndroidCallRuntime.clearPendingPermissionRequest()
        }
        deferIdleAfterEndJob?.cancel()
        _callState.value = CallState.Ended(reason)
        deferIdleAfterEndJob = scope.launch {
            delay(420)
            deferIdleAfterEndJob = null
            if (_callState.value is CallState.Ended) {
                _callState.value = CallState.Idle
            }
        }
    }

    internal fun initRenderer(renderer: TextureViewRenderer): Boolean {
        val activeRoom = room ?: return false
        activeRoom.initVideoRenderer(renderer)
        return true
    }

    internal fun currentVideoTrack(isLocal: Boolean): VideoTrack? {
        val activeRoom = room ?: return null
        return if (isLocal) {
            activeRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        } else {
            currentRemoteVideoTrack(activeRoom)
        }
    }

    internal fun addVideoTrackListener(isLocal: Boolean, listener: (VideoTrack?) -> Unit) {
        val list = if (isLocal) localVideoListeners else remoteVideoListeners
        list.add(listener)
        listener(currentVideoTrack(isLocal))
    }

    internal fun removeVideoTrackListener(isLocal: Boolean, listener: (VideoTrack?) -> Unit) {
        val list = if (isLocal) localVideoListeners else remoteVideoListeners
        list.remove(listener)
    }

    private fun notifyVideoTrackListeners() {
        val local = currentVideoTrack(isLocal = true)
        val remote = currentVideoTrack(isLocal = false)
        localVideoListeners.toList().forEach { it(local) }
        remoteVideoListeners.toList().forEach { it(remote) }
    }

    private fun syncStateFromRoom() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            scope.launch { syncStateFromRoom() }
            return
        }
        val activeRoom = room ?: return
        ensureRemoteVideoSubscribed(activeRoom)
        val localTrack = currentVideoTrack(isLocal = true)
        val remoteTrack = currentVideoTrack(isLocal = false)

        cameraEnabled = localTrack != null && videoRequested
        if (activeRoom.remoteParticipants.isNotEmpty()) {
            hadRemoteParticipant = true
        }
        _callState.value = CallState.Connected(
            videoRequested = videoRequested,
            microphoneEnabled = microphoneEnabled,
            speakerEnabled = speakerEnabled,
            cameraEnabled = cameraEnabled,
            remoteVideoAvailable = remoteTrack != null,
            localVideoAvailable = localTrack != null,
            hasRemoteParticipant = activeRoom.remoteParticipants.isNotEmpty(),
        )
        notifyVideoTrackListeners()
    }

    private fun currentRemoteVideoTrack(activeRoom: Room): VideoTrack? {
        var fallback: VideoTrack? = null
        for (participant in activeRoom.remoteParticipants.values) {
            for (publication in participant.trackPublications.values) {
                if (publication.kind != Track.Kind.VIDEO) continue
                val remotePub = publication as? RemoteTrackPublication
                if (remotePub != null && !remotePub.subscribed) {
                    remotePub.setSubscribed(true)
                }
                val track = publication.track as? VideoTrack ?: continue
                // Prefer live camera immediately; don't wait for an unmute edge if already usable.
                if (publication.source == Track.Source.CAMERA && !publication.muted) {
                    return track
                }
                if (fallback == null && !publication.muted) {
                    fallback = track
                } else if (fallback == null) {
                    fallback = track
                }
            }
        }
        return fallback
    }

    private fun ensureRemoteVideoSubscribed(activeRoom: Room) {
        for (participant in activeRoom.remoteParticipants.values) {
            for (publication in participant.trackPublications.values) {
                if (publication.kind != Track.Kind.VIDEO) continue
                val remotePub = publication as? RemoteTrackPublication ?: continue
                if (!remotePub.subscribed) {
                    remotePub.setSubscribed(true)
                }
            }
        }
    }

    private fun startRemoteVideoPoll(activeRoom: Room) {
        if (!videoRequested) return
        if (currentRemoteVideoTrack(activeRoom) != null) {
            remoteVideoPollJob?.cancel()
            remoteVideoPollJob = null
            return
        }
        remoteVideoPollJob?.cancel()
        remoteVideoPollJob = scope.launch {
            // Peer camera publish can trail ParticipantConnected by a few hundred ms.
            repeat(20) {
                delay(100)
                if (room !== activeRoom) return@launch
                ensureRemoteVideoSubscribed(activeRoom)
                syncStateFromRoom()
                if (currentRemoteVideoTrack(activeRoom) != null) return@launch
            }
        }
    }

    private suspend fun ensureCameraEnabled(activeRoom: Room): Boolean {
        if (activeRoom.localParticipant.setCameraEnabled(true)) {
            return true
        }
        delay(120)
        return activeRoom.localParticipant.setCameraEnabled(true)
    }

    private fun cleanupRoom(releaseState: Boolean = true) {
        eventsJob?.cancel()
        eventsJob = null
        remoteVideoPollJob?.cancel()
        remoteVideoPollJob = null
        hadRemoteParticipant = false

        val activeRoom = room
        room = null
        notifyVideoTrackListeners()

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

        // Always leave telephony/VoIP audio mode so the next incoming ToneGenerator ring is audible.
        updateAudioRoute(false, reset = true)

        if (releaseState) {
            microphoneEnabled = true
            speakerEnabled = false
            cameraEnabled = false
            videoRequested = false
        }
    }

    private fun updateAudioRoute(enabled: Boolean, reset: Boolean = false) {
        val audioManager = AndroidCallRuntime.appContext()?.getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        if (reset) {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            return
        }

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = enabled
    }
}

fun initCallManager(context: Context, activity: Activity? = null) {
    AndroidCallRuntime.init(context, activity)
}

actual fun createCallManager(): CallManager = CallManager()
