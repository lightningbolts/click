package compose.project.click.click.deeplink // pragma: allowlist secret

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A full-screen event destination opened over the app shell. */
sealed interface EventPassDestination {
    val beaconId: String

    /** Your Click Pass (G3-15). */
    data class Pass(
        override val beaconId: String,
    ) : EventPassDestination

    /** The host's door scanner (G3-16). */
    data class Scanner(
        override val beaconId: String,
    ) : EventPassDestination
}

/**
 * Opens the Click Pass and the door scanner from deep links (`click://pass/{id}[/scan]`), the
 * ongoing notification's actions and the event page. The shell's `EventPassOverlayHost` shows it.
 */
object EventPassRouter {
    private val _destination = MutableStateFlow<EventPassDestination?>(null)
    val destination: StateFlow<EventPassDestination?> = _destination.asStateFlow()

    fun open(destination: EventPassDestination) {
        if (destination.beaconId.isBlank()) return
        _destination.value = destination
    }

    fun close() {
        _destination.value = null
    }
}
