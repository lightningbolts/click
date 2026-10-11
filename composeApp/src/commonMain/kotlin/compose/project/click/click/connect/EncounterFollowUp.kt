package compose.project.click.click.connect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.math.sqrt

/**
 * Location fixes for the refinement plus linear acceleration (`TYPE_LINEAR_ACCELERATION`) for
 * stillness. Implementations never prompt; [start] returns false without permission.
 */
interface StillLocationFeed {
    fun start(
        onFix: (LocationFix) -> Unit,
        onLinearAcceleration: (x: Double, y: Double, z: Double) -> Unit,
    ): Boolean

    fun stop()
}

expect fun createStillLocationFeed(): StillLocationFeed

/**
 * Sends one follow-up body (`POST /api/connections/encounter-location`). On Android an expedited
 * `CoroutineWorker`, so the send survives the result screen closing.
 */
expect fun enqueueEncounterFollowUpPost(
    path: String,
    body: String,
)

/**
 * Walking detection from linear acceleration (m/s²): per-second RMS (in g) above
 * [ConnectionLocationQuality.STATIONARY_ACCELERATION_RMS_G] for
 * [ConnectionLocationQuality.MOVING_SECONDS] seconds in a row means the phone is being carried
 * (lifting it once does not count). Port of iOS `StillLocationFeed.record`.
 */
class StillnessDetector(
    private val samplesPerSecond: Int = 10,
) {
    private val second = mutableListOf<Double>()
    private var activeSeconds = 0
    var hasMoved: Boolean = false
        private set

    fun record(
        x: Double,
        y: Double,
        z: Double,
    ) {
        if (hasMoved) return
        val gx = x / G
        val gy = y / G
        val gz = z / G
        second += gx * gx + gy * gy + gz * gz
        if (second.size < samplesPerSecond) return
        val rms = sqrt(second.sum() / second.size)
        second.clear()
        activeSeconds = if (rms > ConnectionLocationQuality.STATIONARY_ACCELERATION_RMS_G) activeSeconds + 1 else 0
        if (activeSeconds >= ConnectionLocationQuality.MOVING_SECONDS) hasMoved = true
    }

    private companion object {
        const val G = 9.80665
    }
}

/**
 * Refines this phone's location for a connection in the seconds after it, while the people
 * who met are still together on the result screen (port of iOS `EncounterFollowUp`, G4-7):
 * sampling for up to 20 s while still; a clearly tighter fix of the same spot is sent to
 * `POST /api/connections/encounter-location` once the flow confirms its connection IDs (≤120 s).
 * The server fills only this user's rows for that exact moment and never makes a reading worse.
 * Failures are silent; nothing is sent if the flow cancels.
 */
class EncounterFollowUp internal constructor(
    private val originalFix: LocationFix,
    /** Must be the `sensor_observation.connection_moment` of the same capture. */
    val momentMs: Long,
    private val feed: StillLocationFeed,
    private val send: (path: String, body: String) -> Unit,
    private val now: () -> Long,
    private val sleep: suspend (Long) -> Unit,
) {
    private val _refinedAccuracyMeters = MutableStateFlow<Double?>(null)

    /** The radius of the tightest fix so far, once better than the capture's (live "Location ±N m"). */
    val refinedAccuracyMeters: StateFlow<Double?> = _refinedAccuracyMeters.asStateFlow()

    @Volatile private var fixes: List<LocationFix> = emptyList()

    @Volatile private var connectionIds: List<String>? = null

    @Volatile private var isCancelled = false
    private val stillness = StillnessDetector()
    private var job: Job? = null

    /** The flow logged encounters on these connections; the reading is sent for them once known. */
    fun confirm(ids: List<String>) {
        if (isCancelled || connectionIds != null) return
        connectionIds =
            ids
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAXIMUM_CONNECTIONS)
    }

    /** The connection did not happen: stop and send nothing. Ignored once confirmed. */
    fun cancel() {
        if (connectionIds != null) return
        isCancelled = true
        _refinedAccuracyMeters.value = null
        feed.stop()
        job?.cancel()
    }

    internal fun start(scope: CoroutineScope): Boolean {
        val started =
            feed.start(
                onFix = { fix ->
                    if ((fix.speedMetersPerSecond ?: 0.0) >= ConnectionLocationQuality.STATIONARY_SPEED_MPS) {
                        markMoved()
                    } else {
                        fixes = (fixes + fix).takeLast(ConnectionLocationQuality.BUFFER_CAPACITY)
                    }
                },
                onLinearAcceleration = { x, y, z ->
                    stillness.record(x, y, z)
                    if (stillness.hasMoved) markMoved()
                },
            )
        if (!started) return false
        job = scope.launch { run() }
        return true
    }

    @Volatile private var moved = false

    private fun markMoved() {
        if (moved) return
        moved = true
        feed.stop()
    }

    internal suspend fun run() {
        val refined = refine()
        // The server accepts (0, 50] m only.
        if (isCancelled || refined == null || refined.horizontalAccuracyMeters <= 0) return
        val confirmDeadline = now() + CONFIRMATION_WINDOW_MS
        while (!isCancelled && connectionIds == null && now() < confirmDeadline) sleep(POLL_MS)
        val ids = connectionIds
        if (isCancelled || ids.isNullOrEmpty()) return
        send(LOCATION_PATH, locationBody(refined, momentMs, ids).toString())
    }

    /** The tightest still fix of the moment's spot, published as it improves; null unless clearly better. */
    internal suspend fun refine(): LocationFix? {
        val deadline = momentMs + ConnectionLocationQuality.REFINEMENT_WINDOW_MS
        var refined: LocationFix? = null
        try {
            while (!isCancelled && now() < deadline) {
                val next = ConnectionLocationQuality.refined(originalFix, fixes, momentMs)
                if (next != null && next.horizontalAccuracyMeters < (refined?.horizontalAccuracyMeters ?: Double.POSITIVE_INFINITY)) {
                    refined = next
                    _refinedAccuracyMeters.value = next.horizontalAccuracyMeters
                }
                // Nothing tighter is worth waiting for once the phone moved or the fix is excellent.
                if (moved ||
                    (refined?.horizontalAccuracyMeters ?: Double.POSITIVE_INFINITY) <= ConnectionLocationQuality.EXCELLENT_ACCURACY_M
                ) {
                    break
                }
                sleep(POLL_MS)
            }
        } finally {
            feed.stop()
        }
        return if (isCancelled) null else refined
    }

    companion object {
        const val CONFIRMATION_WINDOW_MS: Long = 120_000L
        const val MAXIMUM_CONNECTIONS: Int = 20
        const val LOCATION_PATH: String = "/api/connections/encounter-location"
        private const val POLL_MS: Long = 250L

        /**
         * Starts a follow-up when the moment's fix (as sent) was worse than 5 m; otherwise null.
         * [includeLocation] false (Location snap off) never starts one.
         */
        fun begin(
            snapshot: ConnectionCaptureSnapshot,
            includeLocation: Boolean,
            scope: CoroutineScope,
            feed: StillLocationFeed = createStillLocationFeed(),
            send: (String, String) -> Unit = ::enqueueEncounterFollowUpPost,
            now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
            sleep: suspend (Long) -> Unit = { delay(it) },
        ): EncounterFollowUp? {
            if (!includeLocation || !snapshot.locationCouldRefine) return null
            val fix = snapshot.location ?: return null
            val followUp = EncounterFollowUp(fix, snapshot.momentMs, feed, send, now, sleep)
            return if (followUp.start(scope)) followUp else null
        }

        /** `POST /api/connections/encounter-location` body; `connection_moment` uses [isoMillis] like the observation. */
        fun locationBody(
            fix: LocationFix,
            momentMs: Long,
            connectionIds: List<String>,
        ): JsonObject =
            buildJsonObject {
                put("connection_ids", JsonArray(connectionIds.map { JsonPrimitive(it) }))
                put("connection_moment", isoMillis(momentMs))
                put("observed_at", isoMillis(fix.observedAtMs))
                put("gps_lat", fix.latitude)
                put("gps_lon", fix.longitude)
                put("gps_horizontal_accuracy_m", rounded(fix.horizontalAccuracyMeters, 2))
            }
    }
}
