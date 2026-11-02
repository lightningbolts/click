package compose.project.click.click.connect

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G4-2/G4-3: ported from iOS `ConnectionSensorObservationTests` (iOS-only blocks omitted). */
class ConnectionSensorObservationTest {
    private val t0 = 1_790_000_000_000L

    private fun snapshot(location: Boolean): ConnectionCaptureSnapshot {
        val best =
            LocationFix(
                latitude = 47.61,
                longitude = -122.34,
                horizontalAccuracyMeters = 4.8,
                observedAtMs = t0 - 220,
                floorLevel = 3,
                isFullAccuracy = true,
                isSimulated = false,
                isExternalAccessory = false,
            )
        val second =
            LocationFix(
                latitude = 47.61002,
                longitude = -122.34001,
                horizontalAccuracyMeters = 6.0,
                observedAtMs = t0 - 1200,
            )
        return ConnectionCaptureSnapshot(
            momentMs = t0,
            startedAtMs = t0 - 3000,
            finishedAtMs = t0 + 200,
            location = if (location) best else null,
            locationFixes = if (location) listOf(best, second) else emptyList(),
            locationUpdates = 7,
        )
    }

    private fun build(
        snapshot: ConnectionCaptureSnapshot,
        includeLocation: Boolean = true,
    ) = ConnectionSensorObservation.build(
        method = "qr",
        snapshot = snapshot,
        includeLocation = includeLocation,
        device = null,
        sentAtMs = t0 + 300,
        timeZoneOffsetSeconds = -25_200,
    )

    @Test
    fun payloadIsVersionedSnakeCaseOnTheMoment() {
        val payload = build(snapshot(location = true))
        assertEquals(3, payload["schema_version"]!!.jsonPrimitive.int)
        val clock = payload["clock"]!!.jsonObject
        assertEquals(isoMillis(t0 + 300), clock["sent_at"]!!.jsonPrimitive.content)
        assertEquals(-25_200, clock["time_zone_offset_s"]!!.jsonPrimitive.int)
        assertEquals(isoMillis(t0), payload["connection_moment"]!!.jsonPrimitive.content)
        assertEquals(3200L, payload["capture_duration_ms"]!!.jsonPrimitive.long)
        val location = payload["location"]!!.jsonObject
        assertEquals(4.8, location["horizontal_accuracy_m"]!!.jsonPrimitive.double)
        assertEquals(220L, location["age_at_moment_ms"]!!.jsonPrimitive.long)
        assertEquals(3, location["floor"]!!.jsonPrimitive.int)
        assertEquals(7, location["updates_seen"]!!.jsonPrimitive.int)
        val fused = location["fused"]!!.jsonObject
        assertEquals(2, fused["fix_count"]!!.jsonPrimitive.int)
        assertTrue(fused["radius_m"]!!.jsonPrimitive.double >= 2.4)
        val trail = location["trail"]!!.jsonArray.map { it.jsonObject["t_ms"]!!.jsonPrimitive.long }
        assertEquals(listOf(-1200L, -220L), trail)
        // Android never claims what it can't measure.
        for (absent in listOf("uwb", "motion", "heading", "activity", "pedometer", "bluetooth", "acoustic")) {
            assertNull(payload[absent], absent)
        }
    }

    @Test
    fun locationIsLeftOutWhenLocationSnapIsOff() {
        assertNull(build(snapshot(location = true), includeLocation = false)["location"])
        val keys = connectionQualityKeys(snapshot(location = true), includeLocation = false)
        assertNull(keys["gps_horizontal_accuracy_m"])
    }

    @Test
    fun barometerBlockAndTopLevelKeys() {
        val samples = (0 until 20).map { PressureSample(pressureHpa = 1008.1 + it * 0.01, observedAtMs = t0 - 1900 + it * 100) }
        val snap =
            snapshot(location = false).copy(
                barometer = ConnectionCaptureSession.barometerAt(t0, samples, t0 - 8000, t0),
                barometerStartedAtMs = t0 - 2000,
                pressureSamples = samples,
            )
        val barometer = build(snap)["barometer"]!!.jsonObject
        assertEquals(20, barometer["samples_collected"]!!.jsonPrimitive.int)
        assertEquals(ConnectionSensorObservation.MAXIMUM_BAROMETER_SAMPLES, barometer["samples"]!!.jsonArray.size)
        assertEquals(-2000L, barometer["started_t_ms"]!!.jsonPrimitive.long)
        assertNull(barometer["absolute_altitude_m"])
        val keys = connectionQualityKeys(snap, includeLocation = true)
        assertEquals(100.829, keys["barometric_pressure_kpa"]!!.jsonPrimitive.double)
        assertNotNull(keys["barometric_relative_altitude_m"])
        // Accuracy/precision describe an absolute altimeter fix Android doesn't have.
        assertNull(keys["barometric_accuracy_m"])
        assertNull(keys["exact_barometric_elevation_m"])
    }

    @Test
    fun oversizedPayloadTrimsBarometerSamplesThenTrail() {
        val big = build(snapshot(location = true))
        val padded =
            JsonObject(
                big +
                    (
                        "barometer" to
                            JsonObject(
                                mapOf(
                                    "pressure_kpa" to JsonPrimitive(100.8),
                                    "samples" to JsonArray(List(4_000) { JsonPrimitive("x".repeat(20)) }),
                                ),
                            )
                    ),
            )
        val bounded = assertNotNull(ConnectionSensorObservation.bounded(padded))
        assertEquals(0, bounded["barometer"]!!.jsonObject["samples"]!!.jsonArray.size)
        assertNotNull(bounded["location"]!!.jsonObject["trail"])
        assertTrue(bounded.toString().encodeToByteArray().size <= ConnectionSensorObservation.MAXIMUM_PAYLOAD_BYTES)
        assertNull(ConnectionSensorObservation.bounded(padded, maximumBytes = 10))
    }

    @Test
    fun captureQualityCarriesAggregatesOnly() {
        val quality = ConnectionSensorObservation.captureQuality("tap", snapshot(location = true), includeLocation = true)
        assertEquals(4.8, quality["location_accuracy_m"]!!.double)
        assertEquals("excellent", quality["location_accuracy_bucket"]!!.content)
        assertEquals("tap", quality["connection_method"]!!.content)
        assertEquals("false", quality["activity_available"]!!.content)
        for (forbidden in listOf("lat", "lon", "latitude", "longitude", "token")) assertNull(quality[forbidden])
    }
}
