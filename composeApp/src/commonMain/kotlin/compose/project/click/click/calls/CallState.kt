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
        /** True once at least one remote participant is in the LiveKit room (audio and/or video). */
        val hasRemoteParticipant: Boolean = false,
    ) : CallState() {
        val hasVideo: Boolean
            get() = cameraEnabled || remoteVideoAvailable || localVideoAvailable
    }
    data class Ended(val reason: String? = null) : CallState()
}
