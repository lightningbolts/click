package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionEncounter
import compose.project.click.click.data.models.WeatherSnapshot
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatAmbientColorSeedsTest {

    private fun baseConnection(id: String = "c1"): Connection = Connection(
        id = id,
        created = 0L,
        expiry = 0L,
        user_ids = listOf("a", "b"),
    )

    @Test
    fun hubNeutral_returnsThreeTriples() {
        val t = ChatAmbientColorSeeds.hubNeutralRgbTriples()
        assertEquals(3, t.size)
        assertTrue(t.all { it.first in 0f..1f && it.second in 0f..1f && it.third in 0f..1f })
    }

    @Test
    fun rainyWeather_skewsTowardCoolChannel() {
        val enc = ConnectionEncounter(
            id = "e1",
            connectionId = "c1",
            encounteredAt = "2026-01-01T00:00:00Z",
            weatherSnapshot = WeatherSnapshot(condition = "Heavy rain"),
        )
        val c = baseConnection().copy(connectionEncounters = listOf(enc))
        val t = ChatAmbientColorSeeds.rgbTriples01(c)
        val blueish = t[0].third
        assertTrue(blueish > t[0].first, "Rain palette should lean blue vs red")
    }

    @Test
    fun drizzle_isLighterThanStorm_forBlueChannel() {
        val drizzleEnc = ConnectionEncounter(
            id = "e1",
            connectionId = "c1",
            encounteredAt = "2026-01-02T00:00:00Z",
            weatherSnapshot = WeatherSnapshot(condition = "Light drizzle"),
        )
        val stormEnc = ConnectionEncounter(
            id = "e2",
            connectionId = "c1",
            encounteredAt = "2026-01-03T00:00:00Z",
            weatherSnapshot = WeatherSnapshot(condition = "Severe thunderstorm"),
        )
        val d = ChatAmbientColorSeeds.rgbTriples01(baseConnection().copy(connectionEncounters = listOf(drizzleEnc)))[0]
        val s = ChatAmbientColorSeeds.rgbTriples01(baseConnection().copy(connectionEncounters = listOf(stormEnc)))[0]
        assertTrue(d.third > s.third, "Drizzle read lighter than storm on blue channel")
    }

    @Test
    fun coldTemperature_nudgesTowardBlue() {
        val hot = baseConnection().copy(
            connectionEncounters = listOf(
                ConnectionEncounter(
                    id = "e1",
                    connectionId = "c1",
                    encounteredAt = "2026-01-01T00:00:00Z",
                    weatherSnapshot = WeatherSnapshot(condition = "Clear", temperatureCelsius = 34.0),
                ),
            ),
        )
        val cold = baseConnection().copy(
            connectionEncounters = listOf(
                ConnectionEncounter(
                    id = "e1",
                    connectionId = "c1",
                    encounteredAt = "2026-01-01T00:00:00Z",
                    weatherSnapshot = WeatherSnapshot(condition = "Clear", temperatureCelsius = -8.0),
                ),
            ),
        )
        val th = ChatAmbientColorSeeds.rgbTriples01(hot)[0]
        val tc = ChatAmbientColorSeeds.rgbTriples01(cold)[0]
        assertTrue(tc.third > th.third, "Cold scene should carry more blue than hot clear")
    }

    @Test
    fun latestEncounter_winsOverOlderCrossing() {
        val old = ConnectionEncounter(
            id = "old",
            connectionId = "c1",
            encounteredAt = "2020-01-01T00:00:00Z",
            weatherSnapshot = WeatherSnapshot(condition = "Clear"),
        )
        val new = ConnectionEncounter(
            id = "new",
            connectionId = "c1",
            encounteredAt = "2026-06-01T12:00:00Z",
            weatherSnapshot = WeatherSnapshot(condition = "Heavy rain"),
        )
        val c = baseConnection().copy(connectionEncounters = listOf(old, new))
        val t = ChatAmbientColorSeeds.rgbTriples01(c)
        val rainRef = ChatAmbientColorSeeds.rgbTriples01(
            baseConnection("x").copy(
                connectionEncounters = listOf(
                    new.copy(id = "only"),
                ),
            ),
        )[0]
        assertTrue(abs(rainRef.third - t[0].third) < 0.04f)
    }

    @Test
    fun semanticLocation_changesHueBetweenConnections() {
        val a = baseConnection("a").copy(semantic_location = "North Campus Library")
        val b = baseConnection("b").copy(semantic_location = "South Waterfront Stadium")
        val ta = ChatAmbientColorSeeds.rgbTriples01(a)
        val tb = ChatAmbientColorSeeds.rgbTriples01(b)
        assertTrue(ta[0] != tb[0] || ta[1] != tb[1], "Distinct semantics should not collide to an identical palette")
    }
}
