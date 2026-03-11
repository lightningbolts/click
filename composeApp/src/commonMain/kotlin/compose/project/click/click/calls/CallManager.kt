package compose.project.click.click.calls

import kotlinx.coroutines.flow.StateFlow

expect class CallManager() {
    val callState: StateFlow<CallState>

    fun startCall(roomName: String, token: String, wsUrl: String, videoEnabled: Boolean)

    fun setMicrophoneEnabled(enabled: Boolean)

    fun setSpeakerEnabled(enabled: Boolean)

    fun setCameraEnabled(enabled: Boolean)

    fun endCall()
}

expect fun createCallManager(): CallManager