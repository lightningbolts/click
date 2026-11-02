package compose.project.click.click.data.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * G4-2/G4-3: the quality keys and `sensor_observation` ride at the top level of the
 * `/api/connections/proximity` and `/api/qr` bodies (click-web `parseEncounterObservation(body)`).
 */
class ConnectBodyTopLevelKeysTest {
    private val base =
        buildJsonObject {
            put("my_token", "abc")
            put("latitude", 47.6)
        }

    @Test
    fun extrasAreMergedAtTheTopLevel() {
        val extra =
            buildJsonObject {
                put("gps_horizontal_accuracy_m", 4.5)
                put("sensor_observation", buildJsonObject { put("schema_version", 3) })
            }
        val merged = withTopLevelKeys(base, extra)
        assertEquals("abc", merged["my_token"]?.jsonPrimitive?.content)
        assertEquals(4.5, merged["gps_horizontal_accuracy_m"]?.jsonPrimitive?.double)
        assertEquals(3, merged["sensor_observation"]!!.jsonObject["schema_version"]?.jsonPrimitive?.int)
    }

    @Test
    fun requestFieldsWinOverExtras() {
        val merged = withTopLevelKeys(base, buildJsonObject { put("latitude", 0.0) })
        assertEquals(47.6, merged["latitude"]?.jsonPrimitive?.double)
    }

    @Test
    fun noExtrasLeavesTheBodyUnchanged() {
        assertSame(base, withTopLevelKeys(base, null))
        assertSame(base, withTopLevelKeys(base, JsonObject(emptyMap())))
    }
}
