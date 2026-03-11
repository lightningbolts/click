package compose.project.click.click.calls

sealed class CallState {
    data object Idle : CallState()
    data class Connecting(val videoRequested: Boolean) : CallState()
    data class Connected(
        val videoRequested: Boolean,
        val microphoneEnabled: Boolean,
        val speakerEnabled: Boolean,
        val cameraEnabled: Boolean,
        val remoteVideoAvailable: Boolean,
        val localVideoAvailable: Boolean,
    ) : CallState() {
        val hasVideo: Boolean
            get() = cameraEnabled || remoteVideoAvailable || localVideoAvailable
    }
    data class Ended(val reason: String? = null) : CallState()
}