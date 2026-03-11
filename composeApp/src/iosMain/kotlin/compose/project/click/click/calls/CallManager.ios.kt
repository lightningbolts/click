package compose.project.click.click.calls

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSNumber

private const val CALL_START_NOTIFICATION = "ClickCallStart"
private const val CALL_END_NOTIFICATION = "ClickCallEnd"
private const val CALL_SET_MIC_NOTIFICATION = "ClickCallSetMicrophone"
private const val CALL_SET_SPEAKER_NOTIFICATION = "ClickCallSetSpeaker"
private const val CALL_SET_CAMERA_NOTIFICATION = "ClickCallSetCamera"
private const val CALL_STATE_DID_CHANGE_NOTIFICATION = "ClickCallStateDidChange"

@OptIn(ExperimentalForeignApi::class)
actual class CallManager {
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    actual val callState: StateFlow<CallState> = _callState.asStateFlow()
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private var videoRequested = false
    private var stateObserver: Any? = null

    init {
        observeNativeCallState()
    }

    actual fun startCall(roomName: String, token: String, wsUrl: String, videoEnabled: Boolean) {
        videoRequested = videoEnabled
        _callState.value = CallState.Connecting(videoRequested = videoEnabled)
        notificationCenter.postNotificationName(
            aName = CALL_START_NOTIFICATION,
            `object` = null,
            userInfo = mapOf(
                "roomName" to roomName,
                "token" to token,
                "wsUrl" to wsUrl,
                "videoEnabled" to videoEnabled,
            )
        )
    }

    actual fun setMicrophoneEnabled(enabled: Boolean) {
        notificationCenter.postNotificationName(
            aName = CALL_SET_MIC_NOTIFICATION,
            `object` = null,
            userInfo = mapOf("enabled" to enabled)
        )
    }

    actual fun setSpeakerEnabled(enabled: Boolean) {
        notificationCenter.postNotificationName(
            aName = CALL_SET_SPEAKER_NOTIFICATION,
            `object` = null,
            userInfo = mapOf("enabled" to enabled)
        )
    }

    actual fun setCameraEnabled(enabled: Boolean) {
        videoRequested = enabled || videoRequested
        notificationCenter.postNotificationName(
            aName = CALL_SET_CAMERA_NOTIFICATION,
            `object` = null,
            userInfo = mapOf("enabled" to enabled)
        )
    }

    actual fun endCall() {
        notificationCenter.postNotificationName(aName = CALL_END_NOTIFICATION, `object` = null)
        _callState.value = CallState.Idle
    }

    private fun observeNativeCallState() {
        if (stateObserver != null) return

        stateObserver = notificationCenter.addObserverForName(
            name = CALL_STATE_DID_CHANGE_NOTIFICATION,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            val userInfo = notification?.userInfo ?: return@addObserverForName
            val status = userInfo["status"] as? String ?: return@addObserverForName
            val microphoneEnabled = userInfo.boolValue("microphoneEnabled") ?: true
            val speakerEnabled = userInfo.boolValue("speakerEnabled") ?: false
            val cameraEnabled = userInfo.boolValue("cameraEnabled") ?: false
            val remoteVideoAvailable = userInfo.boolValue("remoteVideoAvailable") ?: false
            val localVideoAvailable = userInfo.boolValue("localVideoAvailable") ?: false
            val reportedVideoRequested = userInfo.boolValue("videoRequested") ?: videoRequested
            val reason = userInfo["reason"] as? String

            _callState.value = when (status) {
                "connecting" -> CallState.Connecting(videoRequested = reportedVideoRequested)
                "connected" -> CallState.Connected(
                    videoRequested = reportedVideoRequested,
                    microphoneEnabled = microphoneEnabled,
                    speakerEnabled = speakerEnabled,
                    cameraEnabled = cameraEnabled,
                    remoteVideoAvailable = remoteVideoAvailable,
                    localVideoAvailable = localVideoAvailable,
                )
                "ended" -> CallState.Ended(reason)
                else -> CallState.Idle
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun Map<Any?, *>.boolValue(key: String): Boolean? {
    val value = this[key] ?: return null
    return when (value) {
        is Boolean -> value
        is NSNumber -> value.boolValue
        else -> null
    }
}

actual fun createCallManager(): CallManager = CallManager()