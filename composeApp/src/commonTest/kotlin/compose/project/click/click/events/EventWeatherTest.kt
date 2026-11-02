package compose.project.click.click.events // pragma: allowlist secret

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventWeatherTest {
    // Shape from click-web lib/server/weather.ts (`PlaceWeather`).
    private val body =
        Json.parseToJsonElement(
            """
            {"now":{"temperature_c":17.8,"condition":"Cloudy","icon":"cloudy","is_day":true},
             "at":{"temperature_c":14.4,"condition":"Rain","icon":"rain","is_day":false,
                   "precipitation_probability":70,"time":"2026-11-07T03:00"}}
            """.trimIndent(),
        )

    @Test
    fun parsesNowAndForecast() {
        val w = PlaceWeather.parse(body)
        assertEquals("64°", w.now!!.temperatureLabel)
        assertEquals("Rain", w.atStart!!.condition)
        assertEquals(70, w.atStart!!.precipitationChance)
        assertEquals("rain", w.leadReading!!.icon)
    }

    @Test
    fun summaryNowOnlyWithoutStart() {
        assertEquals("64° Cloudy now", PlaceWeather.parse(body).summary(startEpochMs = null))
    }

    @Test
    fun summaryAddsStartForecastAndRainChance() {
        val summary = PlaceWeather.parse(body).summary(startEpochMs = 1_700_000_000_000L)!!
        assertEquals(true, summary.startsWith("64° Cloudy now · 58° Rain at "), summary)
        assertEquals(true, summary.endsWith(", 70% chance of rain"), summary)
    }

    @Test
    fun lowRainChanceIsLeftOut() {
        val w =
            PlaceWeather.parse(
                Json.parseToJsonElement(
                    """{"now":null,"at":{"temperature_c":0,"condition":"Snow","icon":"snow","precipitation_probability":20}}""",
                ),
            )
        assertEquals(false, w.summary(1_700_000_000_000L)!!.contains("chance"))
        assertEquals("32°", w.atStart!!.temperatureLabel)
    }

    @Test
    fun failuresReadAsNoWeather() {
        assertNull(PlaceWeather.parse(Json.parseToJsonElement("""{"now":null,"at":null}""")).summary(null))
        assertNull(PlaceWeather.parse(Json.parseToJsonElement("""{"now":{"condition":"Clear"}}""")).now)
        assertNull(PlaceWeather.parse(null).leadReading)
    }

    @Test
    fun forecastOnlyForTheComingWeek() {
        val now = 1_000_000_000_000L
        val day = 24 * 60 * 60 * 1000L
        assertEquals(now + day, weatherForecastStartOrNull(now + day, now))
        assertNull(weatherForecastStartOrNull(now - 1, now), "started events ask for now only")
        assertNull(weatherForecastStartOrNull(now + 8 * day, now), "beyond a week")
        assertNull(weatherForecastStartOrNull(null, now))
    }

    @Test
    fun cacheIsFreshForTenMinutes() {
        val key = PlaceWeatherCache.key(47.6062, -122.3321, 1_700_000_000_000L)
        val w = PlaceWeather.parse(body)
        PlaceWeatherCache.put(key, w, nowEpochMs = 0L)
        assertEquals(w, PlaceWeatherCache.get(key, nowEpochMs = 9 * 60_000L))
        assertNull(PlaceWeatherCache.get(key, nowEpochMs = 11 * 60_000L))
    }
}
