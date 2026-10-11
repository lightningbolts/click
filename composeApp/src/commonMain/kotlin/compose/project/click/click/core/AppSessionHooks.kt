package compose.project.click.click.core

import compose.project.click.click.data.auth.SessionEnd
import compose.project.click.click.data.realtime.LiveUpdates
import compose.project.click.click.telemetry.ProductTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The few app-lifecycle moments the foundations (flags, Live updates, caches, pilot telemetry)
 * hang off, so app startup and sign-out each call one place. Features that keep per-user state
 * on device register a [registerSessionWipe] block instead of editing the sign-out path.
 */
object AppSessionHooks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wipes = mutableListOf<suspend () -> Unit>()
    private var signedInUserId: String? = null

    /** Launch, before the first frame: restore the last user's flags. */
    fun onLaunch() {
        FeatureFlags.install()
    }

    /** The signed-in user is known (sign-in, or the first profile load after launch). */
    fun onUserResolved(userId: String) {
        if (userId.isBlank()) return
        signedInUserId = userId
        scope.launch { SessionEnd.onSignedIn(userId) }
        FeatureFlags.onSignedIn(userId)
        ProductTelemetry.onSignedInAndActive()
    }

    fun onForeground() {
        FeatureFlags.refreshInBackground()
        LiveUpdates.setActive(true)
        if (signedInUserId != null) ProductTelemetry.onSignedInAndActive()
    }

    fun onBackground() {
        LiveUpdates.setActive(false)
    }

    /** Per-user on-device state another feature owns; runs at sign-out and on a rejected session. */
    fun registerSessionWipe(block: suspend () -> Unit) {
        wipes += block
    }

    /**
     * Sign-out and a session the server ended: flags off, Live updates left, disk caches wiped.
     * (The local message store is the caller's call; see AppDataManager.clearData.)
     */
    suspend fun onSessionEnded() {
        signedInUserId = null
        FeatureFlags.clear()
        runCatching { LiveUpdates.stop() }
        UserDiskCache.wipeAll()
        wipes.toList().forEach { wipe -> runCatching { wipe() } }
    }
}
