package compose.project.click.click.calls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

actual class CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    actual val callState: StateFlow<CallState> = _callState.asStateFlow()

    actual fun startCall(roomName: String, token: String, wsUrl: String, videoEnabled: Boolean) {
        scope.launch {
            _callState.value = CallState.Connecting
            delay(250)
            _callState.value = CallState.Connected(hasVideo = videoEnabled)
        }
    }

    actual fun endCall() {
        _callState.value = CallState.Ended()
        scope.launch {
            delay(150)
            _callState.value = CallState.Idle
        }
    }
}

actual fun createCallManager(): CallManager = CallManager()