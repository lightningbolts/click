package compose.project.click.click.connect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.pow

/** One pressure-sensor reading (hPa) on the wall clock. */
data class PressureSample(
    val pressureHpa: Double,
    val observedAtMs: Long,
)

/**
 * This phone's own location and pressure sensors for one connection flow. Implementations
 * never prompt: [startLocation] returns false without permission (or with location off).
 */
interface ConnectionSensorFeed {
    /** Starts high-accuracy fused location; seeds a fresh cached fix if one exists. */
    fun startLocation(onFix: (LocationFix) -> Unit): Boolean

    /** Starts the pressure sensor (`TYPE_PRESSURE`); false when the phone has none. */
    fun startPressure(onSample: (PressureSample) -> Unit): Boolean

    fun stopLocation()

    fun stopPressure()

    val hasPressureSensor: Boolean
}

/** The platform feed (Fused high accuracy + `SensorManager` pressure on Android). */
expect fun createConnectionSensorFeed(): ConnectionSensorFeed

/** Pressure altitude (m) in the standard atmosphere, `SensorManager.getAltitude` equivalent. */
fun pressureAltitudeMeters(
    pressureHpa: Double,
    referenceHpa: Double = 1013.25,
): Double = 44330.0 * (1.0 - (pressureHpa / referenceHpa).pow(1.0 / 5.255))

/** The barometer at the moment: the nearest pressure reading and its change since the capture's first. */
data class BarometerReading(
    val pressureHpa: Double,
    val relativeAltitudeMeters: Double?,
    val observedAtMs: Long,
) {
    val pressureKpa: Double get() = pressureHpa / 10.0
}

/** This phone's readings around one connection moment (iOS `ConnectionCaptureSession.Snapshot`). */
data class ConnectionCaptureSnapshot(
    val momentMs: Long,
    val startedAtMs: Long?,
    val finishedAtMs: Long,
    val location: LocationFix?,
    /** Every useful fix in the selection window (for the fused estimate and the trail). */
    val locationFixes: List<LocationFix> = emptyList(),
    val locationUpdates: Int = 0,
    val barometer: BarometerReading? = null,
    val barometerStartedAtMs: Long? = null,
    val pressureSamples: List<PressureSample> = emptyList(),
) {
    /** True when the moment's fix could still tighten (G4-7 starts a follow-up). */
    val locationCouldRefine: Boolean
        get() = (location?.horizontalAccuracyMeters ?: 0.0) > ConnectionLocationQuality.EXCELLENT_ACCURACY_M
}

/**
 * Ephemeral, foreground capture for one connection flow (Tap or QR; port of iOS
 * `ConnectionCaptureSession`, 07 §6 G4-1). While the flow is visible it warms high-accuracy
 * location and the pressure sensor, keeps small in-memory buffers, and at the connection moment
 * hands back everything around it. Never prompts: callers start location only when Location
 * snap is on and permission was already granted. Idles out after [ConnectionLocationQuality.MAXIMUM_WARM_MS].
 */
class ConnectionCaptureSession(
    /** "tap" / "qr" / "link", for diagnostics. */
    val method: String,
    private val feed: ConnectionSensorFeed = createConnectionSensorFeed(),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    @Volatile private var fixes: List<LocationFix> = emptyList()

    @Volatile private var pressures: List<PressureSample> = emptyList()

    @Volatile private var updatesSeen = 0

    @Volatile private var locationStartedAtMs: Long? = null

    @Volatile private var pressureStartedAtMs: Long? = null

    @Volatile private var startedAtMs: Long? = null
    private var idleStop: Job? = null

    val isRunning: Boolean get() = locationStartedAtMs != null || pressureStartedAtMs != null

    /** Starts whichever parts are not running yet and (re)arms the idle cap. */
    fun start(
        location: Boolean,
        barometer: Boolean,
        scope: CoroutineScope,
    ) {
        if (location && locationStartedAtMs == null && feed.startLocation(::ingestFix)) {
            locationStartedAtMs = now()
        }
        if (barometer && pressureStartedAtMs == null && feed.startPressure(::ingestPressure)) {
            pressureStartedAtMs = now()
        }
        if (!isRunning) return
        if (startedAtMs == null) startedAtMs = now()
        idleStop?.cancel()
        idleStop =
            scope.launch {
                delay(ConnectionLocationQuality.MAXIMUM_WARM_MS)
                if (isActive) stop()
            }
    }

    fun stop() {
        idleStop?.cancel()
        idleStop = null
        feed.stopLocation()
        feed.stopPressure()
        startedAtMs = null
        locationStartedAtMs = null
        pressureStartedAtMs = null
        fixes = emptyList()
        pressures = emptyList()
        updatesSeen = 0
    }

    internal fun ingestFix(fix: LocationFix) {
        val current = fixes
        if (current.any { it.observedAtMs == fix.observedAtMs }) return
        updatesSeen += 1
        fixes = (current + fix).takeLast(ConnectionLocationQuality.BUFFER_CAPACITY)
    }

    internal fun ingestPressure(sample: PressureSample) {
        if (!sample.pressureHpa.isFinite() || sample.pressureHpa <= 0) return
        pressures = (pressures + sample).takeLast(ConnectionLocationQuality.BUFFER_CAPACITY)
    }

    /**
     * Everything this phone measured around [momentMs]. Waits only while a wait could help:
     * until the fix is settled or the policy's deadline, and for the barometer at most
     * [BAROMETER_SAMPLE_WINDOW_MS] after it started.
     */
    suspend fun snapshot(
        momentMs: Long,
        wait: ConnectionLocationQuality.Wait,
        sleep: suspend (Long) -> Unit = { delay(it) },
    ): ConnectionCaptureSnapshot {
        val pressureDeadline = pressureStartedAtMs?.let { it + BAROMETER_SAMPLE_WINDOW_MS }
        while (true) {
            val t = now()
            val locationDone =
                locationStartedAtMs?.let { started ->
                    t >= ConnectionLocationQuality.deadline(momentMs, started, wait) ||
                        ConnectionLocationQuality.isSettled(
                            ConnectionLocationQuality.best(fixes, momentMs, t),
                            momentMs,
                            started,
                            t,
                            wait,
                        )
                } ?: true
            val pressureDone = pressureDeadline?.let { t >= it || pressures.isNotEmpty() } ?: true
            if (locationDone && pressureDone) break
            sleep(POLL_MS)
        }
        return snapshotNow(momentMs)
    }

    /** The snapshot from what has arrived so far, without waiting. */
    fun snapshotNow(momentMs: Long): ConnectionCaptureSnapshot {
        val end = now()
        val earliest = momentMs - ConnectionLocationQuality.SELECTION_LOOKBACK_MS
        val allFixes = fixes
        val inWindow = pressures.filter { it.observedAtMs in earliest..end }
        return ConnectionCaptureSnapshot(
            momentMs = momentMs,
            startedAtMs = startedAtMs,
            finishedAtMs = end,
            location = ConnectionLocationQuality.best(allFixes, momentMs, end),
            locationFixes =
                allFixes.filter {
                    ConnectionLocationQuality.isUseful(it) && it.observedAtMs in earliest..end
                },
            locationUpdates = updatesSeen,
            barometer = barometerAt(momentMs, pressures, earliest, end),
            barometerStartedAtMs = pressureStartedAtMs,
            pressureSamples = inWindow,
        )
    }

    companion object {
        /** The pressure sensor never holds a connection longer than this after it started. */
        const val BAROMETER_SAMPLE_WINDOW_MS: Long = 2_000L
        private const val POLL_MS: Long = 100L

        /** Nearest reading to the moment within the window; relative to the capture's first reading. */
        fun barometerAt(
            momentMs: Long,
            samples: List<PressureSample>,
            earliestMs: Long,
            untilMs: Long,
        ): BarometerReading? {
            val first = samples.firstOrNull() ?: return null
            val nearest =
                samples
                    .filter { it.observedAtMs in earliestMs..untilMs }
                    .minByOrNull { abs(it.observedAtMs - momentMs) } ?: return null
            val relative = pressureAltitudeMeters(nearest.pressureHpa) - pressureAltitudeMeters(first.pressureHpa)
            return BarometerReading(
                pressureHpa = nearest.pressureHpa,
                relativeAltitudeMeters = relative.takeIf { it.isFinite() },
                observedAtMs = nearest.observedAtMs,
            )
        }
    }
}
