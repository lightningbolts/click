package compose.project.click.click.navigation // pragma: allowlist secret

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-app destinations opened over the current screen (from a push, Activity or the app itself). */
sealed interface AppRoute {
    /** "Approve new sign-in?" for one of the account's newer devices (`device_approval {request_id}`). */
    data class DeviceApproval(
        val requestId: String,
    ) : AppRoute
}

/** The route waiting to be shown; the shell's hosts consume the ones they present. */
object AppRouter {
    private val _pending = MutableStateFlow<AppRoute?>(null)
    val pending: StateFlow<AppRoute?> = _pending.asStateFlow()

    fun open(route: AppRoute) {
        _pending.value = route
    }

    fun consume(route: AppRoute) {
        _pending.compareAndSet(route, null)
    }
}
