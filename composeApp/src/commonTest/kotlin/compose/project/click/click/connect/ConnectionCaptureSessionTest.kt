package compose.project.click.click.connect

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G4-1: warm capture lifecycle (start/warm, reuse within the warm window, idle cap, stop). */
class ConnectionCaptureSessionTest {
    private val t0 = 1_790_000_000_000L

    private class FakeFeed(
        private val locationAllowed: Boolean = true,
        override val hasPressureSensor: Boolean = true,
    ) : ConnectionSensorFeed {
        var onFix: ((LocationFix) -> Unit)? = null
        var onSample: ((PressureSample) -> Unit)? = null
        var locationStarts = 0
        var pressureStarts = 0
        var locationStops = 0
        var pressureStops = 0

        override fun startLocation(onFix: (LocationFix) -> Unit): Boolean {
            if (!locationAllowed) return false
            locationStarts += 1
            this.onFix = onFix
            return true
        }

        override fun startPressure(onSample: (PressureSample) -> Unit): Boolean {
            if (!hasPressureSensor) return false
            pressureStarts += 1
            this.onSample = onSample
            return true
        }

        override fun stopLocation() {
            locationStops += 1
            onFix = null
        }

        override fun stopPressure() {
            pressureStops += 1
            onSample = null
        }
    }

    private fun fix(
        accuracy: Double,
        atMs: Long,
    ) = LocationFix(
        latitude = 47.6101,
        longitude = -122.3421,
        horizontalAccuracyMeters = accuracy,
        observedAtMs = atMs,
    )

    @Test
    fun startWarmsBothSensorsAndRestartWithinWindowReusesThem() =
        runTest {
            val feed = FakeFeed()
            var clock = t0
            val session = ConnectionCaptureSession("tap", feed) { clock }

            session.start(location = true, barometer = true, scope = backgroundScope)
            assertTrue(session.isRunning)
            assertEquals(1, feed.locationStarts)
            assertEquals(1, feed.pressureStarts)

            feed.onFix?.invoke(fix(8.0, t0 + 200))
            clock = t0 + 30_000
            session.start(location = true, barometer = true, scope = backgroundScope)

            // Reused: the feed is not restarted and the buffered fix survives.
            assertEquals(1, feed.locationStarts)
            assertEquals(1, feed.pressureStarts)
            val snap = session.snapshotNow(t0 + 1_000)
            assertEquals(t0, snap.startedAtMs)
            assertEquals(1, snap.locationUpdates)
            assertNotNull(snap.location)
        }

    @Test
    fun idleCapStopsTheSessionAndRestartReArmsIt() =
        runTest {
            val feed = FakeFeed()
            val session = ConnectionCaptureSession("qr", feed) { t0 }

            session.start(location = true, barometer = false, scope = this)
            advanceTimeBy(ConnectionLocationQuality.MAXIMUM_WARM_MS - 1_000)
            runCurrent()
            assertTrue(session.isRunning)

            // A second start inside the window re-arms the idle cap.
            session.start(location = true, barometer = false, scope = this)
            advanceTimeBy(2_000)
            runCurrent()
            assertTrue(session.isRunning)
            assertEquals(1, feed.locationStarts)

            advanceTimeBy(ConnectionLocationQuality.MAXIMUM_WARM_MS)
            runCurrent()
            assertFalse(session.isRunning)
            assertEquals(1, feed.locationStops)
            assertEquals(1, feed.pressureStops)
        }

    @Test
    fun withoutPermissionOrSensorNothingStarts() =
        runTest {
            val feed = FakeFeed(locationAllowed = false, hasPressureSensor = false)
            val session = ConnectionCaptureSession("tap", feed) { t0 }

            session.start(location = true, barometer = true, scope = backgroundScope)

            assertFalse(session.isRunning)
            val snap = session.snapshotNow(t0)
            assertNull(snap.startedAtMs)
            assertNull(snap.location)
            assertNull(snap.barometer)
        }

    @Test
    fun locationOnlyWhenBarometerNotRequested() =
        runTest {
            val feed = FakeFeed()
            val session = ConnectionCaptureSession("tap", feed) { t0 }

            session.start(location = true, barometer = false, scope = backgroundScope)

            assertEquals(1, feed.locationStarts)
            assertEquals(0, feed.pressureStarts)
            assertNull(session.snapshotNow(t0).barometerStartedAtMs)
        }

    @Test
    fun stopReleasesSensorsAndClearsBuffers() =
        runTest {
            val feed = FakeFeed()
            val session = ConnectionCaptureSession("tap", feed) { t0 + 1_000 }

            session.start(location = true, barometer = true, scope = backgroundScope)
            feed.onFix?.invoke(fix(6.0, t0 + 500))
            feed.onSample?.invoke(PressureSample(1013.0, t0 + 500))
            session.stop()

            assertFalse(session.isRunning)
            assertEquals(1, feed.locationStops)
            assertEquals(1, feed.pressureStops)
            val snap = session.snapshotNow(t0 + 1_000)
            assertNull(snap.startedAtMs)
            assertNull(snap.location)
            assertNull(snap.barometer)
            assertEquals(0, snap.locationUpdates)
            assertTrue(snap.pressureSamples.isEmpty())

            // A later flow starts the feed again from cold.
            session.start(location = true, barometer = true, scope = backgroundScope)
            assertEquals(2, feed.locationStarts)
            assertEquals(2, feed.pressureStarts)
        }

    @Test
    fun duplicateFixesAndInvalidPressureAreIgnored() =
        runTest {
            val feed = FakeFeed()
            val session = ConnectionCaptureSession("tap", feed) { t0 + 1_000 }
            session.start(location = true, barometer = true, scope = backgroundScope)

            feed.onFix?.invoke(fix(9.0, t0 + 400))
            feed.onFix?.invoke(fix(9.0, t0 + 400))
            feed.onSample?.invoke(PressureSample(Double.NaN, t0 + 400))
            feed.onSample?.invoke(PressureSample(0.0, t0 + 400))

            val snap = session.snapshotNow(t0 + 1_000)
            assertEquals(1, snap.locationUpdates)
            assertEquals(1, snap.locationFixes.size)
            assertTrue(snap.pressureSamples.isEmpty())
        }

    @Test
    fun warmSnapshotReturnsWithoutWaitingOnceSettled() =
        runTest {
            val feed = FakeFeed()
            var clock = t0
            val session = ConnectionCaptureSession("tap", feed) { clock }
            session.start(location = true, barometer = true, scope = backgroundScope)
            feed.onFix?.invoke(fix(4.0, t0 + 1_900))
            feed.onSample?.invoke(PressureSample(1013.25, t0 + 100))
            feed.onSample?.invoke(PressureSample(1013.13, t0 + 1_950))
            clock = t0 + 2_000

            var sleeps = 0
            val snap =
                session.snapshot(t0 + 2_000, ConnectionLocationQuality.Wait.InFlow) {
                    sleeps += 1
                    clock += it
                }

            assertEquals(0, sleeps)
            assertEquals(4.0, snap.location?.horizontalAccuracyMeters)
            assertFalse(snap.locationCouldRefine)
            val baro = assertNotNull(snap.barometer)
            assertEquals(1013.13, baro.pressureHpa)
            val expected = pressureAltitudeMeters(1013.13) - pressureAltitudeMeters(1013.25)
            assertTrue(abs((baro.relativeAltitudeMeters ?: 0.0) - expected) < 1e-9)
            assertTrue(expected > 0)
        }

    @Test
    fun coldSnapshotWaitsOnlyUntilTheDeadline() =
        runTest {
            val feed = FakeFeed()
            var clock = t0
            val session = ConnectionCaptureSession("qr", feed) { clock }
            session.start(location = true, barometer = false, scope = backgroundScope)

            val wait = ConnectionLocationQuality.Wait.InFlow
            val deadline = ConnectionLocationQuality.deadline(t0, t0, wait)
            val snap =
                session.snapshot(t0, wait) { clock += it }

            assertNull(snap.location)
            assertTrue(snap.finishedAtMs >= deadline)
            assertTrue(snap.finishedAtMs < deadline + 200)
        }
}
