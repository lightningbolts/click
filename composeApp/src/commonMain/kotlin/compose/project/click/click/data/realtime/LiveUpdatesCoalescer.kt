package compose.project.click.click.data.realtime // pragma: allowlist secret

import compose.project.click.click.encounter.distanceMeters // pragma: allowlist secret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a Live updates hint names (`changed` payload `kind`, migration `20261021000000`). */
enum class LiveKind(
    val wire: String,
) {
    DROPS("drops"),
    BEACONS("beacons"),
    NUDGES("nudges"),
    AVAILABILITY("availability"),
    ACTIVITY("activity"),
    CONNECTIONS("connections"),

    /** Every Home module. Local only: a catch-up after the user channel was down. */
    HOME("home"),
    ;

    companion object {
        /** Server kinds only; `home` (and anything unknown) on the wire is ignored. */
        fun fromWire(raw: String?): LiveKind? = entries.firstOrNull { it.wire == raw && it != HOME }

        /** What a rejoin of the user topic catches up on, since hints sent meanwhile were missed. */
        val catchUp: Set<LiveKind> = setOf(HOME, DROPS, ACTIVITY, CONNECTIONS)
    }
}

/** Where beacons changed: a 0.1° cell (about 11 km), as the server rounds it. */
data class LiveCell(
    val latitude: Double,
    val longitude: Double,
)

/** One coalesced batch of beacon changes. [revision] makes every batch distinct. */
data class BeaconChange(
    val revision: Int,
    /** null: anywhere (a beacon without a location, or a catch-up after the channel was down). */
    val cells: Set<LiveCell>?,
) {
    /** Whether a read centred here could include a changed beacon (unknown center: yes). */
    fun affects(
        latitude: Double?,
        longitude: Double?,
    ): Boolean {
        val cells = cells ?: return true
        if (latitude == null || longitude == null) return true
        return cells.any { distanceMeters(latitude, longitude, it.latitude, it.longitude) <= REACH_METERS }
    }

    companion object {
        /** The discovery radius (50 km) plus a cell's reach (rounding moves a point at most ~8 km). */
        const val DISCOVERY_RADIUS_METERS = 50_000.0
        const val REACH_METERS = DISCOVERY_RADIUS_METERS + 10_000.0
    }
}

/** One refetch round: every kind that changed since the last flush. */
data class LiveBatch(
    val kinds: Set<LiveKind>,
    /** Set when [kinds] contains [LiveKind.BEACONS]. */
    val beaconChange: BeaconChange? = null,
)

/**
 * Coalesces hints into one refetch per kind (iOS `LiveUpdates`): bursts within [coalesceMs] become
 * one [LiveBatch]. While inactive (backgrounded) hints only accumulate; [setActive] true flushes.
 */
class LiveUpdatesCoalescer(
    private val scope: CoroutineScope,
    private val coalesceMs: Long = COALESCE_MS,
    private val onBatch: suspend (LiveBatch) -> Unit,
) {
    private val mutex = Mutex()
    private var isActive = true
    private var pendingKinds = emptySet<LiveKind>()
    private var pendingCells = emptySet<LiveCell>()
    private var beaconsAnywhere = false
    private var revision = 0
    private var flushJob: Job? = null

    suspend fun receive(
        kindWire: String?,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        val kind = LiveKind.fromWire(kindWire) ?: return
        mutex.withLock {
            if (kind == LiveKind.BEACONS) {
                if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite()) {
                    pendingCells = pendingCells + LiveCell(latitude, longitude)
                } else {
                    beaconsAnywhere = true
                }
            }
            enqueueLocked(setOf(kind))
        }
    }

    /** The user topic rejoined after a drop. */
    suspend fun userTopicRejoined() = mutex.withLock { enqueueLocked(LiveKind.catchUp) }

    /** The beacons topic rejoined after a drop: anything anywhere may have changed. */
    suspend fun beaconsTopicRejoined() =
        mutex.withLock {
            beaconsAnywhere = true
            enqueueLocked(setOf(LiveKind.BEACONS))
        }

    /** Foreground (`ON_START`) flushes what accumulated; background only collects. */
    suspend fun setActive(active: Boolean) =
        mutex.withLock {
            isActive = active
            if (!active) {
                flushJob?.cancel()
                flushJob = null
            } else {
                scheduleFlushLocked()
            }
        }

    /** Sign-out: drop everything pending. */
    suspend fun reset() =
        mutex.withLock {
            flushJob?.cancel()
            flushJob = null
            pendingKinds = emptySet()
            pendingCells = emptySet()
            beaconsAnywhere = false
        }

    private fun enqueueLocked(kinds: Set<LiveKind>) {
        pendingKinds = pendingKinds + kinds
        scheduleFlushLocked()
    }

    private fun scheduleFlushLocked() {
        if (!isActive || flushJob?.isActive == true || pendingKinds.isEmpty()) return
        flushJob =
            scope.launch {
                delay(coalesceMs)
                val batch = mutex.withLock { takeBatchLocked() } ?: return@launch
                onBatch(batch)
            }
    }

    private fun takeBatchLocked(): LiveBatch? {
        flushJob = null
        if (!isActive) return null
        val kinds = pendingKinds
        if (kinds.isEmpty()) return null
        pendingKinds = emptySet()
        val change =
            if (LiveKind.BEACONS in kinds) {
                revision += 1
                BeaconChange(revision, if (beaconsAnywhere) null else pendingCells).also {
                    pendingCells = emptySet()
                    beaconsAnywhere = false
                }
            } else {
                null
            }
        return LiveBatch(kinds, change)
    }

    companion object {
        const val COALESCE_MS = 600L
    }
}
