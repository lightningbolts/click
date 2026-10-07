package compose.project.click.click.data.repository

import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.utils.LocationResult
import compose.project.click.click.utils.LocationService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Opt-in hangout detection (iOS `AppEnvironment.reportPresenceIfEnabled`): when the app comes to
 * the foreground, post a fresh, accurate location to `/api/me/presence` at most every 10 minutes.
 * The server compares it with Clicks who also opted in and asks both "Hanging out?". Never runs in
 * the background, and turning it off deletes the server-side ping.
 */
object HangoutPresence {
    const val MIN_INTERVAL_MS: Long = 10 * 60 * 1000L
    const val MAX_ACCURACY_METERS: Double = 100.0

    private val mutex = Mutex()
    private var lastPingEpochMs: Long? = null

    fun isThrottled(
        lastPingEpochMs: Long?,
        nowEpochMs: Long,
    ): Boolean = lastPingEpochMs != null && nowEpochMs - lastPingEpochMs < MIN_INTERVAL_MS

    /** Only precise fixes count; a coarse fix would prompt people who aren't together. */
    fun isUsableFix(fix: LocationResult?): Boolean {
        val accuracy = fix?.accuracyMeters ?: return false
        return accuracy <= MAX_ACCURACY_METERS && !(fix.latitude == 0.0 && fix.longitude == 0.0)
    }

    suspend fun isEnabled(storage: TokenStorage): Boolean = storage.getHangoutDetectionOptIn() == true

    /** Returns true when a ping was sent. */
    suspend fun reportIfEnabled(
        storage: TokenStorage,
        locationService: LocationService,
        api: ApiClient,
        nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean =
        mutex.withLock {
            if (!isEnabled(storage) || isThrottled(lastPingEpochMs, nowEpochMs)) return@withLock false
            if (!locationService.hasLocationPermission()) return@withLock false
            val fix = runCatching { locationService.getHighAccuracyLocation() }.getOrNull()
            if (!isUsableFix(fix)) return@withLock false
            val sent = api.reportPresence(fix!!.latitude, fix.longitude).isSuccess
            if (sent) lastPingEpochMs = nowEpochMs
            sent
        }

    /** Persists the opt-in; opting out clears the server-side presence right away. */
    suspend fun setEnabled(
        storage: TokenStorage,
        api: ApiClient,
        enabled: Boolean,
    ) {
        storage.saveHangoutDetectionOptIn(enabled)
        if (!enabled) {
            mutex.withLock { lastPingEpochMs = null }
            api.clearPresence()
        }
    }
}
