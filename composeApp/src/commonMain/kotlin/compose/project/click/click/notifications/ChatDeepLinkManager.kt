package compose.project.click.click.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges notification tap intents to Compose navigation.
 * [MainActivity] writes the pending connection ID; [App] observes and navigates.
 */
object ChatDeepLinkManager {
    private val _pendingConnectionId = MutableStateFlow<String?>(null)
    val pendingConnectionId: StateFlow<String?> = _pendingConnectionId.asStateFlow()

    fun setPendingChat(connectionId: String) {
        _pendingConnectionId.value = connectionId
    }

    fun consume(): String? {
        val value = _pendingConnectionId.value
        _pendingConnectionId.value = null
        return value
    }
}
