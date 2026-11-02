package compose.project.click.click.connect

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G4-1: ported from iOS `ConnectionLocationQualityTests` (same fixtures, epoch-ms times). */
class ConnectionLocationQualityTest {
    private val q = ConnectionLocationQuality
    private val t0 = 1_790_000_000_000L

    private fun at(offsetS: Double) = t0 + (offsetS * 1000).toLong()

    private fun fix(
        accuracy: Double,
        offsetS: Double,
        vertical: Double? = 6.0,
        fullAccuracy: Boolean = true,
        floor: Int? = null,
    ) = LocationFix(
        latitude = 47.6101,
        longitude = -122.3421,
        horizontalAccuracyMeters = accuracy,
        verticalAccuracyMeters = vertical,
        altitudeMslMeters = if (vertical == null) null else 52.3,
        ellipsoidalAltitudeMeters = if (vertical == null) null else 71.1,
        observedAtMs = at(offsetS),
        floorLevel = floor,
        isFullAccuracy = fullAccuracy,
    )

    private fun north(
        accuracy: Double,
        offsetS: Double,
        meters: Double,
    ) = LocationFix(
        latitude = 47.6101 + meters / 111_320,
        longitude = -122.3421,
        horizontalAccuracyMeters = accuracy,
        observedAtMs = at(offsetS),
    )

    @Test
    fun tierBoundaries() {
        assertEquals("excellent", q.tier(5.0))
        assertEquals("good", q.tier(5.01))
        assertEquals("good", q.tier(10.0))
        assertEquals("usable", q.tier(25.0))
        assertEquals("coarse", q.tier(50.0))
        assertEquals("unusable", q.tier(50.01))
    }

    @Test
    fun consistentFixesFuseOutliersAndLoneFixesDoNot() {
        val fixes = listOf(north(5.0, -0.2, 0.0), north(5.0, -0.6, 4.0), north(20.0, -1.0, 300.0))
        val fused = assertNotNull(q.fused(fixes, t0, t0))
        assertEquals(2, fused.fixCount)
        val northMeters = (fused.latitude - 47.6101) * 111_320
        assertTrue(northMeters > 1 && northMeters < 3)
        assertTrue(fused.radiusMeters >= q.effectiveRadius(fixes[0], t0) / 2)
        assertNull(q.fused(listOf(north(5.0, -0.2, 0.0)), t0, t0))
    }

    @Test
    fun refinementTightensButNotMarginallyOrAfterAMove() {
        val original = north(14.0, -0.5, 0.0)
        val later = listOf(north(9.0, 4.0, 3.0), north(5.0, 9.0, 2.0), north(6.0, 12.0, 1.0))
        val refined = assertNotNull(q.refined(original, later, t0))
        assertEquals(5.0, refined.horizontalAccuracyMeters)
        assertEquals(at(9.0), refined.observedAtMs)
        val n = (refined.latitude - 47.6101) * 111_320
        assertTrue(n > 1 && n < 3)
        assertNull(q.refined(original, listOf(north(12.0, 5.0, 0.0)), t0))
        assertNull(q.refined(original, listOf(north(5.0, -1.0, 0.0)), t0))
        assertNull(q.refined(original, listOf(north(5.0, 8.0, 60.0)), t0))
    }

    @Test
    fun movingFixesNeverRefine() {
        val original = north(14.0, -0.5, 0.0)
        val walking = north(5.0, 6.0, 1.0).copy(speedMetersPerSecond = 1.2)
        assertNull(q.refined(original, listOf(walking), t0))
    }

    @Test
    fun slightlyWorseFixAtTheMomentBeatsBetterOneEarlier() {
        val older = fix(3.0, -2.0)
        val current = fix(4.0, -0.1)
        assertEquals(current, q.best(listOf(older, current), t0, t0))
    }

    @Test
    fun twentyMeterFirstFixDoesNotSettle() {
        val startedAt = at(-1.6)
        val first = fix(20.0, -1.3)
        assertFalse(q.isSettled(first, t0, startedAt, startedAt + 300, ConnectionLocationQuality.Wait.InFlow))
        val best = q.best(listOf(first, fix(8.0, -0.2)), t0, t0)
        assertEquals(8.0, best?.horizontalAccuracyMeters)
        assertTrue(q.isSettled(best, t0, startedAt, t0, ConnectionLocationQuality.Wait.InFlow))
    }

    @Test
    fun excellentFixStillWaitsMinimumWindow() {
        val excellent = fix(4.0, 0.3)
        assertFalse(q.isSettled(excellent, t0, t0, at(0.4), ConnectionLocationQuality.Wait.InFlow))
        assertTrue(q.isSettled(excellent, t0, t0, at(1.6), ConnectionLocationQuality.Wait.InFlow))
    }

    @Test
    fun fullAccuracyWinsCoarseAndStaleIgnored() {
        val reduced = fix(3.0, 0.0, fullAccuracy = false)
        val full = fix(9.0, 0.0)
        assertEquals(full, q.best(listOf(reduced, full), t0, t0))
        assertNull(q.best(listOf(fix(60.0, 0.0)), t0, t0))
        assertNull(q.best(listOf(fix(4.0, -9.0)), t0, t0))
        assertNull(q.best(listOf(fix(4.0, 2.0)), t0, at(1.0)))
    }

    @Test
    fun bestFixScoringUsesDriftOfOnePointFiveMetersPerSecond() {
        // 3 m two seconds early = 6 m effective; 4 m at the moment wins.
        assertEquals(6.0, q.effectiveRadius(fix(3.0, -2.0), t0))
        assertEquals(4.15, q.effectiveRadius(fix(4.0, -0.1), t0), 1e-9)
    }

    @Test
    fun tiesPreferValidVertical() {
        val noVertical = fix(5.0, 0.0, vertical = null)
        val withVertical = fix(5.2, 0.0)
        assertEquals(withVertical, q.best(listOf(noVertical, withVertical), t0, t0))
    }

    @Test
    fun deadlines() {
        val inFlow = ConnectionLocationQuality.Wait.InFlow
        assertEquals(at(1.5), q.deadline(at(0.5), t0, inFlow))
        assertEquals(at(8.0), q.deadline(at(7.4), t0, inFlow))
        assertTrue(q.deadline(at(30.0), t0, inFlow) < at(30.0))
        assertEquals(t0 + q.COLD_CAPTURE_MS, q.deadline(t0, t0, ConnectionLocationQuality.Wait.Cold))
    }

    @Test
    fun rejectsInvalidFixes() {
        val now = t0
        assertNull(LocationFix.validated(47.6101, -122.3421, -1.0, now, now, 10_000))
        assertNull(LocationFix.validated(47.6101, -122.3421, 5.0, now - 11_000, now, 10_000))
        assertNull(LocationFix.validated(0.0, 0.0, 5.0, now, now, 10_000))
    }

    @Test
    fun invalidVerticalKeepsCoordinateButDropsAltitude() {
        val obs =
            assertNotNull(
                LocationFix.validated(
                    47.6101,
                    -122.3421,
                    6.0,
                    t0,
                    t0,
                    10_000,
                    verticalAccuracyMeters = 0.0,
                    ellipsoidalAltitudeMeters = 52.3,
                ),
            )
        assertEquals(47.6101, obs.latitude)
        assertNull(obs.verticalAccuracyMeters)
        assertNull(obs.ellipsoidalAltitudeMeters)
        val columns = obs.qualityColumns()
        assertEquals(6.0, (columns["gps_horizontal_accuracy_m"] as JsonPrimitive).double)
        assertNull(columns["gps_vertical_accuracy_m"])
        assertNull(columns["gps_altitude_m"])
    }

    @Test
    fun reducedAccuracyRecordedUnknownFloorOmitted() {
        val obs = assertNotNull(LocationFix.validated(47.6101, -122.3421, 6.0, t0, t0, 10_000, isFullAccuracy = false))
        assertFalse(obs.isFullAccuracy)
        assertEquals(false, (obs.qualityColumns()["gps_full_accuracy"] as JsonPrimitive).boolean)
        assertNull(obs.qualityColumns()["gps_floor"])
        assertEquals(3, (fix(4.0, 0.0, floor = 3).qualityColumns()["gps_floor"] as JsonPrimitive).int)
    }

    @Test
    fun qualityKeysCarryOwnFixAndOmitAbsentValues() {
        val own = fix(4.8, 0.0, floor = 3)
        val body = own.qualityColumns()
        assertEquals(4.8, (body["gps_horizontal_accuracy_m"] as JsonPrimitive).double)
        assertEquals(6.0, (body["gps_vertical_accuracy_m"] as JsonPrimitive).double)
        assertEquals(52.3, (body["gps_altitude_m"] as JsonPrimitive).double)
        assertEquals(71.1, (body["gps_ellipsoidal_altitude_m"] as JsonPrimitive).double)
        assertEquals(true, (body["gps_full_accuracy"] as JsonPrimitive).boolean)
        assertEquals(isoMillis(t0), (body["gps_observed_at"] as JsonPrimitive).content)
        assertNull(body["gps_speed_mps"])
        assertNull(body["gps_simulated"])
    }

    @Test
    fun isoMillisAlwaysHasThreeFractionalDigits() {
        assertEquals("2026-09-21T14:13:20.000Z", isoMillis(t0))
        assertEquals("2026-09-21T14:13:20.007Z", isoMillis(t0 + 7))
    }
}
