package compose.project.click.click.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges notification tap intents and OS URLs to Compose navigation.
 * [MainActivity] / iOS write pending IDs; [App] observes and navigates.
 */
object ChatDeepLinkManager {
    private val _pendingConnectionId = MutableStateFlow<String?>(null)
    val pendingConnectionId: StateFlow<String?> = _pendingConnectionId.asStateFlow()

    private val _pendingCommunityHubId = MutableStateFlow<String?>(null)
    val pendingCommunityHubId: StateFlow<String?> = _pendingCommunityHubId.asStateFlow()

    fun setPendingChat(connectionId: String) {
        _pendingConnectionId.value = connectionId
    }

    fun consume(): String? {
        val value = _pendingConnectionId.value
        _pendingConnectionId.value = null
        return value
    }

    /** From `click://hub/{id}`, https web `/hub/{id}`, or notification extras (future). */
    fun setPendingCommunityHub(hubId: String) {
        val trimmed = hubId.trim()
        if (trimmed.isNotEmpty()) {
            _pendingCommunityHubId.value = trimmed
        }
    }

    fun consumeCommunityHub(): String? {
        val value = _pendingCommunityHubId.value
        _pendingCommunityHubId.value = null
        return value
    }
}
