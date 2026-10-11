package compose.project.click.click.events // pragma: allowlist secret

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ongoing "live activity" notifications for events you host or go to (sheet 08 G3-17). */
object EventLiveUpdates {
    private val _checkedIn = MutableStateFlow<Set<String>>(emptySet())

    /** Events the viewer is checked in to ("You're in" on the notification). */
    val checkedIn: StateFlow<Set<String>> = _checkedIn.asStateFlow()

    fun setCheckedIn(
        beaconId: String,
        checkedIn: Boolean,
    ) {
        _checkedIn.value = if (checkedIn) _checkedIn.value + beaconId else _checkedIn.value - beaconId
    }
}
