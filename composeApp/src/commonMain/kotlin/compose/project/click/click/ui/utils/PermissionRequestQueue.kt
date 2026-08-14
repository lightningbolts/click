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

    fun resetForTests() {
        waiting.clear()
        _current.value = null
    }

    private fun pump() {
        if (_current.value != null) return
        _current.value = waiting.removeFirstOrNull()
    }
}

fun permissionPrimeCopy(kind: PermissionKind): Pair<String, String> =
    when (kind) {
        PermissionKind.Location ->
            "Location" to
                "Click uses your location for the Memory Map, nearby hubs, and QR check-ins. Only you see your map."
        PermissionKind.Microphone ->
            "Microphone" to
                "Click uses the microphone for calls, voice notes, and the tap handshake."
        PermissionKind.Camera ->
            "Camera" to
                "Click uses the camera to scan QR codes and capture Disposable Roll photos."
        PermissionKind.ProximityHardware ->
            "Nearby devices" to
                "Bluetooth and microphone let Click complete a tap handshake. GPS still works if Bluetooth is off."
        PermissionKind.Calendar ->
            "Calendar" to
                "Click can add events you save to your device calendar."
        PermissionKind.Contacts ->
            "Contacts" to
                "Click hashes phone numbers and emails on this device to find people you already know. Your address book never leaves your phone."
    }
