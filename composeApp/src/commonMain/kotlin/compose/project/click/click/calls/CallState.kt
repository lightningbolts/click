package compose.project.click.click.calls

sealed class CallState {
    data object Idle : CallState()
    data object Connecting : CallState()
    data class Connected(val hasVideo: Boolean) : CallState()
    data class Ended(val reason: String? = null) : CallState()
}