package compose.project.click.click.events

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Local-only bookmark / check-in flags keyed by beacon id.
 * Reserved for future event-info API expansion — not synced to the server.
 */
object EventLocalFlagsStore {
    private val _bookmarkedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _checkedInIds = MutableStateFlow<Set<String>>(emptySet())

    val bookmarkedIds: StateFlow<Set<String>> = _bookmarkedIds.asStateFlow()
    val checkedInIds: StateFlow<Set<String>> = _checkedInIds.asStateFlow()

    fun isBookmarked(beaconId: String): Boolean = beaconId in _bookmarkedIds.value

    fun isCheckedIn(beaconId: String): Boolean = beaconId in _checkedInIds.value

    fun toggleBookmark(beaconId: String) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        _bookmarkedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun toggleCheckIn(beaconId: String) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        _checkedInIds.update { current ->
            if (id in current) current - id else current + id
        }
    }
}
