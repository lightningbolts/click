package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.util.formatClockTime // pragma: allowlist secret
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.roundToInt

/**
 * Weather at an event's location (`GET /api/geo/weather`, click-web `lib/server/weather.ts`; iOS
 * `EventWeather.swift`): right now, and for an event up to a week out, the forecast for its start.
 */
data class PlaceWeatherReading(
    val temperatureCelsius: Double,
    val condition: String,
    /** Server icon key: clear, cloudy, fog, drizzle, rain, snow, thunder. */
    val icon: String,
    val isDay: Boolean,
    val precipitationChance: Int?,
) {
    /** Fahrenheit, matching the rest of the Android app's weather labels. */
    val temperatureLabel: String get() = "${(temperatureCelsius * 9.0 / 5.0 + 32.0).roundToInt()}°"
}

data class PlaceWeather(
    val now: PlaceWeatherReading?,
    val atStart: PlaceWeatherReading?,
) {
    /** "64° Cloudy now · 58° Rain at 7:00 PM, 70% chance of rain" (iOS `PlaceWeather.summary`). */
    fun summary(startEpochMs: Long?): String? {
        val parts = mutableListOf<String>()
        now?.let { parts += "${it.temperatureLabel} ${it.condition} now" }
        if (atStart != null && startEpochMs != null) {
            var line = "${atStart.temperatureLabel} ${atStart.condition} at ${formatClockTime(startEpochMs)}"
            atStart.precipitationChance?.takeIf { it >= 30 }?.let { line += ", $it% chance of rain" }
            parts += line
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** The reading that matters most: the start forecast when there is one. */
    val leadReading: PlaceWeatherReading? get() = atStart ?: now

    companion object {
        /** Parses the route's `{now, at}`; a missing or malformed reading is simply absent. */
        fun parse(body: JsonElement?): PlaceWeather {
            val root = body as? JsonObject
            return PlaceWeather(now = reading(root?.get("now")), atStart = reading(root?.get("at")))
        }

        private fun reading(element: JsonElement?): PlaceWeatherReading? {
            val row = element as? JsonObject ?: return null

            fun prim(key: String) = row[key] as? JsonPrimitive
            val temperature = prim("temperature_c")?.doubleOrNull?.takeIf { it.isFinite() } ?: return null
            return PlaceWeatherReading(
                temperatureCelsius = temperature,
                condition = prim("condition")?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: "Clear",
                icon = prim("icon")?.contentOrNull ?: "clear",
                isDay = prim("is_day")?.booleanOrNull ?: true,
                precipitationChance = prim("precipitation_probability")?.intOrNull,
            )
        }
    }
}

/** Only ask for a start-time forecast when the start is ahead and within a week (the server's horizon). */
fun weatherForecastStartOrNull(
    startEpochMs: Long?,
    nowEpochMs: Long,
): Long? = startEpochMs?.takeIf { it > nowEpochMs && it - nowEpochMs <= 7L * 24 * 60 * 60 * 1000 }

/**
 * Session cache in front of the weather route: the same spot and hour within 10 minutes reuses the
 * answer (the server caches the same way).
 */
object PlaceWeatherCache {
    private const val FRESH_MS = 10 * 60 * 1000L
    private val entries = mutableMapOf<String, Pair<PlaceWeather, Long>>()

    fun key(
        latitude: Double,
        longitude: Double,
        startEpochMs: Long?,
    ): String {
        fun cell(v: Double) = (v * 1000).roundToInt()
        return "${cell(latitude)},${cell(longitude)},${startEpochMs?.div(3_600_000L)}"
    }

    fun get(
        key: String,
        nowEpochMs: Long,
    ): PlaceWeather? = entries[key]?.takeIf { nowEpochMs - it.second < FRESH_MS }?.first

    fun put(
        key: String,
        weather: PlaceWeather,
        nowEpochMs: Long,
    ) {
        if (entries.size > 64) entries.clear()
        entries[key] = weather to nowEpochMs
    }
}
