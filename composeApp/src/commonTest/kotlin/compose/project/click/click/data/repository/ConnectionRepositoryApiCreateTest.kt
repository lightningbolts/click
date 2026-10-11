package compose.project.click.click.data.repository

import compose.project.click.click.core.FeatureFlagGate
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.NoiseLevelCategory
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * G4-10: QR/manual connect goes through `POST /api/connections` (body keys as read by
 * click-web `app/api/connections/route.ts` POST) unless `connect_postgrest_fallback` is on.
 */
class ConnectionRepositoryApiCreateTest {
    private val originalGate = FeatureFlagGate.isEnabled

    @AfterTest
    fun restoreGate() {
        FeatureFlagGate.isEnabled = originalGate
    }

    private val base =
        ConnectionRequest(
            userId1 = "user-a",
            userId2 = "user-b",
            locationLat = 47.6553,
            locationLng = -122.3035,
            connectionMethod = "qr",
        )

    @Test
    fun apiPathIsTheDefaultForQr() {
        FeatureFlagGate.isEnabled = { false }
        assertTrue(useConnectApiPath(base))
    }

    @Test
    fun killSwitchSelectsPostgrestFallback() {
        FeatureFlagGate.isEnabled = { it == CONNECT_POSTGREST_FALLBACK_FLAG }
        assertFalse(useConnectApiPath(base))
    }

    @Test
    fun unrelatedFlagsDoNotTripTheKillSwitch() {
        FeatureFlagGate.isEnabled = { it != CONNECT_POSTGREST_FALLBACK_FLAG }
        assertTrue(useConnectApiPath(base))
    }

    @Test
    fun proximityNeverUsesTheConnectApiPath() {
        FeatureFlagGate.isEnabled = { false }
        assertFalse(useConnectApiPath(base.copy(connectionMethod = "proximity")))
    }

    @Test
    fun killSwitchFlagName() {
        assertEquals("connect_postgrest_fallback", CONNECT_POSTGREST_FALLBACK_FLAG)
    }

    @Test
    fun bodyCarriesTheRouteKeys() {
        val request =
            base.copy(
                initiatorId = "user-b",
                responderId = "user-a",
                noiseLevelCategory = NoiseLevelCategory.QUIET,
                exactNoiseLevelDb = 42.5,
                exactBarometricElevationMeters = 12.25,
                luxLevel = 300.0,
                motionVariance = 0.02,
                compassAzimuth = 180.0,
                batteryLevel = 77,
                weatherSnapshotLabel = " 15°C, Clear ",
                observationKeys =
                    buildJsonObject {
                        put("gps_accuracy_m", 4.0)
                        put("sensor_observation", buildJsonObject { put("version", 3) })
                    },
            )

        val body = connectionsCreateBody(request, "user-b", 1_234L, " tag-1 ", -420)

        assertEquals("user-a", body["userId1"]?.jsonPrimitive?.content)
        assertEquals("user-b", body["userId2"]?.jsonPrimitive?.content)
        assertEquals("qr", body["connectionMethod"]?.jsonPrimitive?.content)
        assertEquals(1_234L, body["tokenAgeMs"]?.jsonPrimitive?.long)
        val location1 = body["location1"]!!.jsonObject
        assertEquals(47.6553, location1["lat"]?.jsonPrimitive?.double)
        assertEquals(-122.3035, location1["lon"]?.jsonPrimitive?.double)
        assertEquals("user-b", body["initiator_id"]?.jsonPrimitive?.content)
        assertEquals("user-a", body["responder_id"]?.jsonPrimitive?.content)
        assertEquals(listOf(JsonPrimitive("tag-1")), body["context_tags"]?.jsonArray?.toList())
        assertEquals("QUIET", body["noise_level"]?.jsonPrimitive?.content)
        assertEquals(42.5, body["exact_noise_level_db"]?.jsonPrimitive?.double)
        assertEquals(12.25, body["exact_barometric_elevation_m"]?.jsonPrimitive?.double)
        assertEquals(300.0, body["lux_level"]?.jsonPrimitive?.double)
        assertEquals(0.02, body["motion_variance"]?.jsonPrimitive?.double)
        assertEquals(180.0, body["compass_azimuth"]?.jsonPrimitive?.double)
        assertEquals(77, body["battery_level"]?.jsonPrimitive?.int)
        assertEquals("15°C, Clear", body["weather_snapshot"]?.jsonPrimitive?.content)
        assertEquals(-420, body["timezone_offset_minutes"]?.jsonPrimitive?.int)
        assertEquals(4.0, body["gps_accuracy_m"]?.jsonPrimitive?.double)
        assertEquals(3, body["sensor_observation"]!!.jsonObject["version"]?.jsonPrimitive?.int)
    }

    @Test
    fun venueQrNeverSendsThisPhonesLocation() {
        val body = connectionsCreateBody(base.copy(venueId = "venue-1"), "user-b", null, null, 0)
        assertFalse("location1" in body)
        assertFalse("tokenAgeMs" in body)
    }

    @Test
    fun nullIslandAndMissingOptionalsAreOmitted() {
        val request = base.copy(locationLat = 0.0, locationLng = 0.0, batteryLevel = 150, exactNoiseLevelDb = Double.NaN)
        val body = connectionsCreateBody(request, "user-b", null, "  ", 0)
        assertFalse("location1" in body)
        assertFalse("context_tags" in body)
        assertFalse("battery_level" in body)
        assertFalse("exact_noise_level_db" in body)
        assertFalse("initiator_id" in body)
        assertFalse("weather_snapshot" in body)
        assertEquals(0, body["timezone_offset_minutes"]?.jsonPrimitive?.int)
    }
}
