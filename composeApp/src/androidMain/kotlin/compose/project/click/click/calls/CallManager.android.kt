package compose.project.click.click.calls

import android.Manifest
import android.app.Activity
import android.content.Context.AUDIO_SERVICE
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoPreset169
import io.livekit.android.room.track.VideoQuality
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

internal data class PendingCallStart(
    val roomName: String,
    val token: String,
    val wsUrl: String,
    val videoEnabled: Boolean,
    val requiredPermissions: List<String>,
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

    fun handlePermissionResult(launcherResults: Map<String, Boolean> = emptyMap()) {
        val pending = pendingCallStart
        val grantedCb = onPermissionGranted
        val deniedCb = onPermissionDenied
        val context = applicationContext
        val osGranted =
            pending?.requiredPermissions.orEmpty().filter { permission ->
                context != null &&
                    ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
            }.toSet()
        val resume =
            pending != null &&
                CallPermissionResultPolicy.shouldResumeCall(
                    requiredPermissions = pending.requiredPermissions,
                    launcherResults = launcherResults,
                    osGrantedPermissions = osGranted,
                )
        clearPendingPermissionRequest()
        if (resume && pending != null && grantedCb != null) {
            grantedCb(pending)
        } else {
            deniedCb?.invoke()
        }
    }

    fun startCallForegroundService(videoEnabled: Boolean) {
        val context = applicationContext ?: return
        CallForegroundService.start(context, videoEnabled)
    }

    fun stopCallForegroundService() {
        val context = applicationContext ?: return
        CallForegroundService.stop(context)
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
    /** identity → listeners for that participant's camera VideoTrack. */
    private val videoTrackListeners = mutableMapOf<String, MutableList<(VideoTrack?) -> Unit>>()
    private var activeSpeakerIdentities: Set<String> = emptySet()

    actual fun startCall(roomName: String, token: String, wsUrl: String, videoEnabled: Boolean) {
        val context = AndroidCallRuntime.appContext()
        if (context == null) {
            markEndedAndDeferIdle("Call context unavailable")
            return
        }

        val requiredPermissions = callRequiredPermissions(videoEnabled)
        val optionalPermissions = callOptionalPermissions()

        val missingRequired = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        val missingOptional = optionalPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingRequired.isNotEmpty()) {
            val pending = PendingCallStart(
                roomName = roomName,
                token = token,
                wsUrl = wsUrl,
                videoEnabled = videoEnabled,
                requiredPermissions = requiredPermissions,
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
                permissions = (missingRequired + missingOptional).toTypedArray(),
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
        AndroidCallRuntime.startCallForegroundService(videoEnabled)

        val liveKitRoom = LiveKit.create(
            appContext = context,
            options = RoomOptions(
                // Keep both off for 1:1 calls. adaptiveStream+dynacast can pause layers when
                // Compose TextureViewRenderer visibility is flaky, which looks like "no remote video".
                adaptiveStream = false,
                dynacast = false,
                // H540 + simulcast on: faster than H720 open, still multi-layer for iOS peers.
                videoTrackCaptureDefaults = LocalVideoTrackOptions(
                    captureParams = VideoPreset169.H540.capture,
                ),
                videoTrackPublishDefaults = VideoTrackPublishDefaults(
                    videoEncoding = VideoPreset169.H540.encoding,
                    simulcast = true,
                ),
            ),
            overrides = LiveKitOverrides(
                audioOptions = AudioOptions(
                    // LiveKit 2.20 name for voice-call routing (MODE_IN_COMMUNICATION).
                    audioOutputType = AudioType.CallAudioType(),
                ),
            ),
        )
        room = liveKitRoom

        eventsJob = scope.launch {
            liveKitRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> {
                        val publication = event.publication as? RemoteTrackPublication
                        if (publication != null && publication.kind == Track.Kind.VIDEO) {
                            if (!publication.subscribed) publication.setSubscribed(true)
                            if (publication.track is VideoTrack) {
                                publication.setVideoQuality(VideoQuality.HIGH)
                            }
                        }
                        syncStateFromRoom()
                    }

                    is RoomEvent.Connected,
                    is RoomEvent.TrackUnsubscribed,
                    is RoomEvent.TrackPublished,
                    is RoomEvent.TrackUnpublished,
                    is RoomEvent.TrackMuted,
                    is RoomEvent.TrackUnmuted,
                    is RoomEvent.LocalTrackSubscribed,
                    is RoomEvent.TrackStreamStateChanged,
                    is RoomEvent.TrackSubscriptionPermissionChanged,
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.Reconnected,
                    -> syncStateFromRoom()

                    is RoomEvent.ActiveSpeakersChanged -> {
                        activeSpeakerIdentities = event.speakers.map { it.identity?.value.orEmpty() }
                            .filter { it.isNotEmpty() }
                            .toSet()
                        syncStateFromRoom()
                    }

                    is RoomEvent.ParticipantDisconnected -> {
                        syncStateFromRoom()
                        // When the last remote leaves, end locally even if Realtime cancel was missed.
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
                        _callState.value = CallState.Connecting(
                            videoRequested = videoRequested,
                            reconnecting = true,
                        )
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
                liveKitRoom.connect(
                    url = wsUrl,
                    token = token,
                    options = ConnectOptions(autoSubscribe = true),
                )
                // Serialize mic → camera (parallel CameraX + AudioRecord races on many devices).
                val micOk = ensureMicrophoneEnabled(liveKitRoom)
                microphoneEnabled = micOk
                syncStateFromRoom()
                if (!micOk) {
                    println("CallManager: microphone still unpublished after retries; staying in-call")
                }

                if (videoEnabled) {
                    // Never hard-fail accept on camera lag — audio stays up; keep retrying publish.
                    var cameraOk = ensureCameraEnabled(liveKitRoom)
                    cameraEnabled = cameraOk
                    syncStateFromRoom()
                    if (!cameraOk ||
                        liveKitRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track == null
                    ) {
                        delay(500)
                        if (room === liveKitRoom) {
                            cameraOk = ensureCameraEnabled(liveKitRoom)
                            cameraEnabled = cameraOk
                            syncStateFromRoom()
                        }
                    }
                    if (room === liveKitRoom &&
                        liveKitRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track == null
                    ) {
                        delay(750)
                        if (room === liveKitRoom) {
                            cameraEnabled = ensureCameraEnabled(liveKitRoom)
                            syncStateFromRoom()
                        }
                    }
                    if (room === liveKitRoom &&
                        liveKitRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track == null
                    ) {
                        println("CallManager: camera still unpublished after retries; staying in-call on audio")
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
                    println("CallManager: unable to update microphone")
                    return@launch
                }
                microphoneEnabled = enabled
                syncStateFromRoom()
            } catch (error: Throwable) {
                println("CallManager: unable to update microphone: ${error.message}")
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
        // Flip to Ended before teardown so UI blanks TextureViews before disconnect/release.
        markEndedAndDeferIdle("Call ended", clearPending = false)
        cleanupRoom(releaseState = false)
    }

    /** Failed starts and hang-up: briefly show Ended, then Idle so the next invite is not stuck busy. */
    private fun markEndedAndDeferIdle(reason: String, clearPending: Boolean = true) {
        if (clearPending) {
            AndroidCallRuntime.clearPendingPermissionRequest()
        }
        deferIdleAfterEndJob?.cancel()
        _callState.value = CallState.Ended(reason)
        deferIdleAfterEndJob = scope.launch {
            delay(180)
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

    internal fun currentVideoTrack(participantId: String): VideoTrack? {
        val activeRoom = room ?: return null
        val localId = activeRoom.localParticipant.identity?.value
        if (localId != null && localId == participantId) {
            return activeRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        }
        val remote = activeRoom.remoteParticipants.values.firstOrNull {
            it.identity?.value == participantId
        } ?: return null
        return videoTrackForParticipant(remote)
    }

    internal fun addVideoTrackListener(participantId: String, listener: (VideoTrack?) -> Unit) {
        val list = videoTrackListeners.getOrPut(participantId) { mutableListOf() }
        list.add(listener)
        listener(currentVideoTrack(participantId))
    }

    internal fun removeVideoTrackListener(participantId: String, listener: (VideoTrack?) -> Unit) {
        val list = videoTrackListeners[participantId] ?: return
        list.remove(listener)
        if (list.isEmpty()) videoTrackListeners.remove(participantId)
    }

    private fun notifyVideoTrackListeners() {
        val ids = videoTrackListeners.keys.toList()
        for (id in ids) {
            val track = currentVideoTrack(id)
            videoTrackListeners[id]?.toList()?.forEach { it(track) }
        }
    }

    private fun syncStateFromRoom() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            scope.launch { syncStateFromRoom() }
            return
        }
        val activeRoom = room ?: return
        ensureRemoteVideoSubscribed(activeRoom)
        val participants = buildParticipantRoster(activeRoom)
        val localTrack = participants.firstOrNull { it.isLocal }?.let { currentVideoTrack(it.identity) }
        val anyRemoteVideo = participants.any { !it.isLocal && it.hasVideo }

        cameraEnabled = localTrack != null && videoRequested
        if (activeRoom.remoteParticipants.isNotEmpty()) {
            hadRemoteParticipant = true
        }
        _callState.value = CallState.Connected(
            videoRequested = videoRequested,
            microphoneEnabled = microphoneEnabled,
            speakerEnabled = speakerEnabled,
            cameraEnabled = cameraEnabled,
            remoteVideoAvailable = anyRemoteVideo,
            localVideoAvailable = localTrack != null,
            hasRemoteParticipant = activeRoom.remoteParticipants.isNotEmpty(),
            participants = participants,
        )
        notifyVideoTrackListeners()
    }

    private fun buildParticipantRoster(activeRoom: Room): List<CallParticipant> {
        val result = mutableListOf<CallParticipant>()
        val local = activeRoom.localParticipant
        val localId = local.identity?.value.orEmpty().ifEmpty { "local" }
        val localCam = local.getTrackPublication(Track.Source.CAMERA)
        val localMic = local.getTrackPublication(Track.Source.MICROPHONE)
        val localName = local.name?.takeIf { it.isNotBlank() }
            ?: local.identity?.value
            ?: "You"
        result.add(
            CallParticipant(
                identity = localId,
                displayName = localName,
                isLocal = true,
                isMuted = localMic?.muted == true || !microphoneEnabled,
                isSpeaking = activeSpeakerIdentities.contains(localId),
                cameraEnabled = cameraEnabled && localCam?.track != null && localCam.muted != true,
                hasVideo = localCam?.track is VideoTrack && localCam.muted != true,
            ),
        )
        for (remote in activeRoom.remoteParticipants.values) {
            val id = remote.identity?.value ?: continue
            val camPub = remote.getTrackPublication(Track.Source.CAMERA) as? RemoteTrackPublication
            if (camPub != null) ensureRemotePublicationReady(camPub)
            val micPub = remote.getTrackPublication(Track.Source.MICROPHONE)
            val videoTrack = videoTrackForParticipant(remote)
            val displayName = remote.name?.takeIf { it.isNotBlank() } ?: id
            result.add(
                CallParticipant(
                    identity = id,
                    displayName = displayName,
                    isLocal = false,
                    isMuted = micPub?.muted == true,
                    isSpeaking = activeSpeakerIdentities.contains(id),
                    cameraEnabled = videoTrack != null && camPub?.muted != true,
                    hasVideo = videoTrack != null && camPub?.muted != true,
                ),
            )
        }
        return result
    }

    private fun videoTrackForParticipant(
        participant: io.livekit.android.room.participant.RemoteParticipant,
    ): VideoTrack? {
        val cameraPub = participant.getTrackPublication(Track.Source.CAMERA) as? RemoteTrackPublication
        if (cameraPub != null) {
            ensureRemotePublicationReady(cameraPub)
            val cameraTrack = cameraPub.track as? VideoTrack
            if (cameraTrack != null && !cameraPub.muted) return cameraTrack
        }
        for (publication in participant.trackPublications.values) {
            if (publication.kind != Track.Kind.VIDEO) continue
            val remotePub = publication as? RemoteTrackPublication
            if (remotePub != null) ensureRemotePublicationReady(remotePub)
            val track = publication.track as? VideoTrack ?: continue
            if (!publication.muted) return track
        }
        for (publication in participant.trackPublications.values) {
            if (publication.kind != Track.Kind.VIDEO) continue
            val track = publication.track as? VideoTrack
            if (track != null) return track
        }
        return null
    }

    private fun ensureRemoteVideoSubscribed(activeRoom: Room) {
        for (participant in activeRoom.remoteParticipants.values) {
            val cameraPub = participant.getTrackPublication(Track.Source.CAMERA) as? RemoteTrackPublication
            if (cameraPub != null) ensureRemotePublicationReady(cameraPub)
            for (publication in participant.trackPublications.values) {
                if (publication.kind != Track.Kind.VIDEO) continue
                val remotePub = publication as? RemoteTrackPublication ?: continue
                ensureRemotePublicationReady(remotePub)
            }
        }
    }

    private fun ensureRemotePublicationReady(publication: RemoteTrackPublication) {
        if (!publication.subscribed) {
            publication.setSubscribed(true)
        }
        if (publication.track is VideoTrack) {
            publication.setEnabled(true)
            publication.setVideoQuality(VideoQuality.HIGH)
        }
    }

    private suspend fun ensureMicrophoneEnabled(activeRoom: Room): Boolean {
        if (activeRoom.localParticipant.setMicrophoneEnabled(true)) {
            return true
        }
        delay(350)
        if (room !== activeRoom) return false
        if (activeRoom.localParticipant.setMicrophoneEnabled(true)) {
            return true
        }
        delay(500)
        if (room !== activeRoom) return false
        return activeRoom.localParticipant.setMicrophoneEnabled(true)
    }

    private suspend fun ensureCameraEnabled(activeRoom: Room): Boolean {
        if (activeRoom.localParticipant.setCameraEnabled(true)) {
            return true
        }
        delay(350)
        return activeRoom.localParticipant.setCameraEnabled(true)
    }

    private fun cleanupRoom(releaseState: Boolean = true) {
        eventsJob?.cancel()
        eventsJob = null
        hadRemoteParticipant = false
        activeSpeakerIdentities = emptySet()

        val activeRoom = room
        room = null
        // Drop Compose track bindings before LiveKit teardown so hangup cannot freeze on last frame.
        notifyVideoTrackListeners()

        // Always leave telephony/VoIP audio mode so the next incoming ToneGenerator ring is audible.
        updateAudioRoute(false, reset = true)
        AndroidCallRuntime.stopCallForegroundService()

        if (releaseState) {
            microphoneEnabled = true
            speakerEnabled = false
            cameraEnabled = false
            videoRequested = false
        }

        // disconnect/release can block; never run them on Main during video hangup.
        if (activeRoom != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    activeRoom.disconnect()
                } catch (_: Throwable) {
                }
                try {
                    activeRoom.release()
                } catch (_: Throwable) {
                }
            }
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

private fun callRequiredPermissions(videoEnabled: Boolean): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (videoEnabled) add(Manifest.permission.CAMERA)
}

private fun callOptionalPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}

fun initCallManager(context: Context, activity: Activity? = null) {
    AndroidCallRuntime.init(context, activity)
}

actual fun createCallManager(): CallManager = CallManager()
