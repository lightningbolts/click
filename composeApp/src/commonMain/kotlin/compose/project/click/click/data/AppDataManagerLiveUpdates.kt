package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.activity.ActivityStore // pragma: allowlist secret
import compose.project.click.click.data.realtime.LiveBatch // pragma: allowlist secret
import compose.project.click.click.data.realtime.LiveKind // pragma: allowlist secret
import compose.project.click.click.data.realtime.LiveUpdates // pragma: allowlist secret
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the app-wide data layer refetches for one Live updates batch (screens handle the rest). */
internal data class LiveRefetchPlan(
    /** Clicks list: a connection was made, changed or removed. */
    val inbox: Boolean,
    /** The Home bell and Activity sheet. */
    val activity: Boolean,
    /** The shared nearby-beacon prefetch (feeds Home and the Map's first paint). */
    val beacons: Boolean,
)

/**
 * Mirrors the flush in click-ios `Click/App/LiveUpdates.swift`: `connections` refreshes the inbox,
 * `activity` the bell, and a beacon change near [here] (or anywhere/unknown) the nearby read.
 * `home` is the local catch-up after the user topic was down, so it refetches the inbox too.
 * Shared drops (`drops`, and `connections`) are refetched by `SharedDropsViewModel` itself.
 */
internal fun liveRefetchPlan(
    batch: LiveBatch,
    here: Pair<Double, Double>?,
): LiveRefetchPlan =
    LiveRefetchPlan(
        inbox = LiveKind.CONNECTIONS in batch.kinds || LiveKind.HOME in batch.kinds,
        activity = LiveKind.ACTIVITY in batch.kinds,
        beacons = batch.beaconChange?.affects(here?.first, here?.second) == true,
    )

private var liveUpdatesRefetchJob: Job? = null

/** Joins the Live updates topics for [userId] and refetches the app-wide data a hint names. */
internal fun AppDataManager.startLiveUpdatesRefetch(userId: String) {
    scope.launch { runCatching { LiveUpdates.start(userId) } }
    if (liveUpdatesRefetchJob?.isActive == true) return
    liveUpdatesRefetchJob =
        scope.launch {
            LiveUpdates.batches.collect { batch ->
                if (_currentUser.value == null) return@collect
                val plan = liveRefetchPlan(batch, _lastKnownDeviceLocation.value)
                if (plan.inbox) refreshInboxFromCoordinator(force = false)
                if (plan.activity) launch { runCatching { ActivityStore.refresh() } }
                if (plan.beacons) {
                    beaconPrefetchJob?.cancel()
                    startBeaconPrefetch()
                }
            }
        }
}
