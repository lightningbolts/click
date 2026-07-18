package compose.project.click.click.deeplink

import compose.project.click.click.qr.toBeaconIdFromClickEventUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes Universal Links and custom URL schemes for `/e/{beaconId}` into Map event focus.
 * Platform entry points call [handleIncomingUrl]; [App] observes [pendingBeaconId].
 */
object EventDeepLinkRouter {
    private val _pendingBeaconId = MutableStateFlow<String?>(null)
    val pendingBeaconId: StateFlow<String?> = _pendingBeaconId.asStateFlow()

    fun parseBeaconId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.toBeaconIdFromClickEventUrl()?.takeIf { it.isNotBlank() }
    }

    /** Queue Map focus for [url]. Returns true when the URL was recognized. */
    fun handleIncomingUrl(url: String): Boolean {
        val beaconId = parseBeaconId(url) ?: return false
        _pendingBeaconId.value = beaconId
        return true
    }

    fun setPendingBeaconId(beaconId: String) {
        val trimmed = beaconId.trim()
        if (trimmed.isNotEmpty()) {
            _pendingBeaconId.value = trimmed
        }
    }

    fun consume(): String? {
        val value = _pendingBeaconId.value
        _pendingBeaconId.value = null
        return value
    }
}
