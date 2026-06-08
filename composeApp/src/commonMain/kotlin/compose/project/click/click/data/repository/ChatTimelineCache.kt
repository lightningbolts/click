package compose.project.click.click.data.repository

import compose.project.click.click.data.models.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Hot in-memory message timelines keyed by [connectionId].
 * Retained across chat back-navigation so re-entry paints instantly while network sync runs.
 */
class ChatTimelineCache(
    private val maxConnections: Int = 48,
) {
    private val _timelinesByConnectionId = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val timelinesByConnectionId: StateFlow<Map<String, List<Message>>> =
        _timelinesByConnectionId.asStateFlow()

    fun peek(connectionId: String): List<Message>? =
        _timelinesByConnectionId.value[connectionId]?.takeIf { it.isNotEmpty() }

    fun store(connectionId: String, messages: List<Message>) {
        if (connectionId.isBlank() || messages.isEmpty()) return
        _timelinesByConnectionId.update { current ->
            val next = current + (connectionId to messages)
            if (next.size <= maxConnections) next else pruneToMax(next)
        }
    }

    fun mergeMessage(connectionId: String, message: Message) {
        if (connectionId.isBlank()) return
        _timelinesByConnectionId.update { current ->
            val existing = current[connectionId].orEmpty()
            val idx = existing.indexOfFirst { it.id == message.id }
            val merged = when {
                idx >= 0 -> existing.toMutableList().apply { this[idx] = message }
                else -> existing + message
            }.sortedBy { it.timeCreated }
            current + (connectionId to merged)
        }
    }

    fun removeMessage(connectionId: String, messageId: String) {
        if (connectionId.isBlank() || messageId.isBlank()) return
        _timelinesByConnectionId.update { current ->
            val existing = current[connectionId] ?: return@update current
            val filtered = existing.filterNot { it.id == messageId }
            if (filtered.isEmpty()) current - connectionId else current + (connectionId to filtered)
        }
    }

    fun clear() {
        _timelinesByConnectionId.value = emptyMap()
    }

    private fun pruneToMax(map: Map<String, List<Message>>): Map<String, List<Message>> {
        if (map.size <= maxConnections) return map
        val ranked = map.entries.sortedByDescending { entry ->
            entry.value.maxOfOrNull { it.timeCreated } ?: 0L
        }
        return ranked.take(maxConnections).associate { it.key to it.value }
    }
}
