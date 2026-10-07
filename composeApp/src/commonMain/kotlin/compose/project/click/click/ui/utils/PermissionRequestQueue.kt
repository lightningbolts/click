package compose.project.click.click.ui.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PermissionKind {
    Location,
    Microphone,
    Camera,
    ProximityHardware,
    Calendar,
    Contacts,
}

data class PendingPermissionRequest(
    val kind: PermissionKind,
    val onComplete: (() -> Unit)? = null,
    val onResult: ((Boolean) -> Unit)? = null,
)

/**
 * FIFO queue so native permission dialogs never stack. [PermissionCoordinatorHost] shows a
 * priming sheet for [current], then launches the platform requester.
 *
 * Call from the UI thread only (Compose / unit tests).
 */
object PermissionRequestQueue {
    private val waiting = ArrayDeque<PendingPermissionRequest>()
    private val _current = MutableStateFlow<PendingPermissionRequest?>(null)
    val current: StateFlow<PendingPermissionRequest?> = _current.asStateFlow()

    fun enqueue(
        kind: PermissionKind,
        onComplete: (() -> Unit)? = null,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        waiting.addLast(PendingPermissionRequest(kind, onComplete, onResult))
        pump()
    }

    fun completeCurrent(granted: Boolean = true) {
        val done = _current.value
        _current.value = null
        done?.onComplete?.invoke()
        done?.onResult?.invoke(granted)
        pump()
    }

    fun dismissCurrent() {
        val done = _current.value
        _current.value = null
        done?.onComplete?.invoke()
        done?.onResult?.invoke(false)
        pump()
    }

    /**
     * Cancels the primer without pretending the OS denied anything.
     *
     * Proximity uses this path because "Not now" is user cancellation, not a permission
     * decision; the Connect screen should remain idle and retryable.
     */
    fun cancelCurrentWithoutResult() {
        _current.value = null
        pump()
    }

    fun resetForTests() {
        waiting.clear()
        _current.value = null
    }

    private fun pump() {
        if (_current.value != null) return
        _current.value = waiting.removeFirstOrNull()
    }
}

/** Matches the iOS purpose strings (`INFOPLIST_KEY_NS*UsageDescription`), worded for Android. */
fun permissionPrimeCopy(kind: PermissionKind): Pair<String, String> =
    when (kind) {
        PermissionKind.Location ->
            "Location" to
                "Click uses your location to show the map, find people and events near you, and remember where you met someone when you connect."
        PermissionKind.Microphone ->
            "Microphone" to
                "Click uses your microphone for Tap to Connect ultrasonic sensing and voice notes."
        PermissionKind.Camera ->
            "Camera" to
                "Click uses your camera to scan QR codes and capture photos."
        PermissionKind.ProximityHardware ->
            "Nearby devices" to
                "Microphone access enables Tap to Connect. Bluetooth improves nearby matching when available, but Click can fall back to ultrasonic audio and GPS if Bluetooth is unavailable."
        PermissionKind.Calendar ->
            "Calendar" to
                "Click checks your calendar on this phone to show whether you're free for plans and events. " +
                    "It reads only busy times, and only adds events you ask it to."
        PermissionKind.Contacts ->
            "Contacts" to
                "Click hashes phone numbers and emails on this device to find people you already know. Your address book never leaves your phone."
    }
