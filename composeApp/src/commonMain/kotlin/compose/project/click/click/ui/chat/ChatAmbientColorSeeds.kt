package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.WeatherSnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Derives three RGB triples (0..1) for the chat ambient mesh from the active
 * [Connection], prioritizing the **most recent crossing** (`latestEncounter`)
 * for [WeatherSnapshot] (condition, temperature, wind). Semantic place still
 * seeds variety when weather is sparse.
 *
 * Kept free of Compose types so the same logic is unit-testable on every target.
 */
internal object ChatAmbientColorSeeds {

    private const val H = 17
    private const val M = 0x7fff

    fun rgbTriples01(connection: Connection?): List<Triple<Float, Float, Float>> {
        if (connection == null) return hubNeutralRgbTriples()
        val enc = connection.latestEncounter()
        val ws = enc?.weatherSnapshot ?: connection.memoryCapsule?.weatherSnapshot
        val conditionText = listOfNotNull(
            ws?.condition?.trim()?.takeIf { it.isNotEmpty() },
            connection.resolvedWeatherCondition?.trim()?.takeIf { it.isNotEmpty() },
            ws?.iconCode?.trim()?.takeIf { it.isNotEmpty() },
        ).firstOrNull()?.lowercase().orEmpty()
        val iconLower = ws?.iconCode?.trim()?.lowercase().orEmpty()
        val semantic = connection.semanticLocation?.lowercase().orEmpty()
        val hue01 = hueFromSemantic(semantic)
        val precip = classifyPrecipitation(conditionText, iconLower)
        val (a, b, c) = weatherMoodRgb(precip, hue01, ws)
        return listOf(a, b, c)
    }

    fun hubNeutralRgbTriples(): List<Triple<Float, Float, Float>> {
        val a = Triple(0.22f, 0.42f, 0.92f)
        val b = Triple(0.45f, 0.72f, 1.00f)
        val c = Triple(0.12f, 0.28f, 0.55f)
        return listOf(a, b, c)
    }

    private enum class PrecipKind {
        CLEAR,
        FOG,
        PARTLY_CLOUDY,
        CLOUDY,
        DRIZZLE,
        RAIN,
        STORM,
        THUNDER,
        SNOW,
        UNKNOWN,
    }

    private fun classifyPrecipitation(condition: String, icon: String): PrecipKind {
        val w = condition
        val i = icon
        return when {
            w.contains("thunder") || w.contains("lightning") || i.contains("thunder") -> PrecipKind.THUNDER
            w.contains("tornado") || w.contains("hurricane") -> PrecipKind.THUNDER
            w.contains("blizzard") || w.contains("snow") || w.contains("sleet") || w.contains("ice pellets") ||
                w.contains("hail") || i.contains("snow") -> PrecipKind.SNOW
            w.contains("storm") || w.contains("squall") || i.contains("storm") -> PrecipKind.STORM
            w.contains("drizzle") || w.contains("light rain") || w.contains("light shower") -> PrecipKind.DRIZZLE
            w.contains("shower") || w.contains("rain") || w.contains("wet") || i.contains("rain") ||
                i.contains("shower") -> PrecipKind.RAIN
            w.contains("overcast") || w.contains("cloudy") || w.contains("grey") || w.contains("gray") ||
                w.contains("mostly cloud") || i.contains("cloud") -> PrecipKind.CLOUDY
            w.contains("partly") || w.contains("few clouds") || w.contains("scattered") ||
                w.contains("broken clouds") || i.contains("partly") -> PrecipKind.PARTLY_CLOUDY
            w.contains("fog") || w.contains("mist") || w.contains("haze") || i.contains("fog") -> PrecipKind.FOG
            w.contains("clear") || w.contains("fair") || w.contains("sun") || w.contains("dry") ||
                i.contains("clear") || i.contains("sun") -> PrecipKind.CLEAR
            w.isNotEmpty() || i.isNotEmpty() -> PrecipKind.UNKNOWN
            else -> PrecipKind.UNKNOWN
        }
    }

    private fun hueFromSemantic(semantic: String): Float {
        if (semantic.isEmpty()) return 0.55f
        var h = 0
        for (ch in semantic) {
            h = H * h + ch.code
        }
        val u = abs(h) and M
        return u / M.toFloat()
    }

    private fun baseRgbForPrecip(kind: PrecipKind, hue01: Float): Triple<Float, Float, Float> {
        val warm = Triple(0.98f, 0.72f, 0.35f)
        val cool = Triple(0.35f, 0.62f, 0.95f)
        val mist = Triple(0.62f, 0.68f, 0.78f)
        val drizzle = Triple(0.52f, 0.68f, 0.88f)
        val rain = Triple(0.32f, 0.52f, 0.88f)
        val storm = Triple(0.22f, 0.30f, 0.48f)
        val thunder = Triple(0.38f, 0.26f, 0.58f)
        val snow = Triple(0.82f, 0.90f, 1.00f)
        val cloudy = Triple(0.50f, 0.54f, 0.60f)
        val partly = Triple(0.62f, 0.70f, 0.82f)
        return when (kind) {
            PrecipKind.THUNDER -> thunder
            PrecipKind.STORM -> storm
            PrecipKind.RAIN -> rain
            PrecipKind.DRIZZLE -> drizzle
            PrecipKind.CLOUDY -> cloudy
            PrecipKind.PARTLY_CLOUDY -> partly
            PrecipKind.FOG -> mist
            PrecipKind.SNOW -> snow
            PrecipKind.CLEAR -> warm
            PrecipKind.UNKNOWN -> lerpTriple(cool, mist, hue01)
        }
    }

    private fun weatherMoodRgb(
        kind: PrecipKind,
        hue01: Float,
        ws: WeatherSnapshot?,
    ): Triple<Triple<Float, Float, Float>, Triple<Float, Float, Float>, Triple<Float, Float, Float>> {
        var primary = baseRgbForPrecip(kind, hue01)
        if (kind == PrecipKind.UNKNOWN) {
            val w = listOfNotNull(ws?.condition, ws?.iconCode).joinToString(" ").lowercase()
            if (w.contains("wind")) {
                primary = lerpTriple(primary, Triple(0.40f, 0.58f, 0.82f), 0.55f)
            }
            if (w.contains("green") || w.contains("tree")) {
                primary = lerpTriple(primary, Triple(0.32f, 0.72f, 0.48f), 0.35f)
            }
        }

        primary = applyTemperatureBias(primary, ws?.temperatureCelsius)
        primary = applyWindSpeedDesat(primary, ws?.windSpeedKph)

        val cool = Triple(0.35f, 0.62f, 0.95f)
        val warm = Triple(0.98f, 0.72f, 0.35f)
        var secondary = lerpTriple(primary, cool, 0.32f)
        var tertiary = lerpTriple(primary, warm, 0.22f + 0.14f * hue01)

        val windNudge = windDirectionRgbNudge(ws?.windDirectionDegrees)
        secondary = channelNudge(secondary, windNudge, 0.45f)
        tertiary = channelNudge(tertiary, Triple(-windNudge.first, windNudge.second, windNudge.third), 0.40f)

        return Triple(primary, secondary, tertiary)
    }

    private fun applyTemperatureBias(
        rgb: Triple<Float, Float, Float>,
        tempC: Double?,
    ): Triple<Float, Float, Float> {
        val t = tempC?.takeIf { it.isFinite() } ?: return rgb
        val bias = ((t + 12.0) / 54.0).toFloat().coerceIn(0f, 1f)
        val cold = Triple(0.22f, 0.48f, 0.92f)
        val hot = Triple(0.98f, 0.52f, 0.28f)
        return lerpTriple(rgb, lerpTriple(cold, hot, bias), 0.16f)
    }

    private fun applyWindSpeedDesat(
        rgb: Triple<Float, Float, Float>,
        windKph: Double?,
    ): Triple<Float, Float, Float> {
        val kph = windKph?.takeIf { it.isFinite() && it >= 0.0 } ?: return rgb
        val amount = (kph / 72.0).coerceIn(0.0, 1.0).toFloat() * 0.14f
        val gray = Triple(0.55f, 0.56f, 0.58f)
        return lerpTriple(rgb, gray, amount)
    }

    /** Small RGB nudge from wind direction (meteorological degrees). */
    private fun windDirectionRgbNudge(deg: Int?): Triple<Float, Float, Float> {
        if (deg == null) return Triple(0f, 0f, 0f)
        val r = (((deg % 360) + 360) % 360) * (PI / 180.0)
        val s = sin(r).toFloat()
        val c = cos(r).toFloat()
        return Triple(s * 0.05f, c * 0.04f, (s - c) * 0.025f)
    }

    private fun channelNudge(
        rgb: Triple<Float, Float, Float>,
        d: Triple<Float, Float, Float>,
        w: Float,
    ): Triple<Float, Float, Float> {
        val x = w.coerceIn(0f, 1f)
        return Triple(
            (rgb.first + d.first * x).coerceIn(0f, 1f),
            (rgb.second + d.second * x).coerceIn(0f, 1f),
            (rgb.third + d.third * x).coerceIn(0f, 1f),
        )
    }

    private fun lerpTriple(
        a: Triple<Float, Float, Float>,
        b: Triple<Float, Float, Float>,
        t: Float,
    ): Triple<Float, Float, Float> {
        val x = t.coerceIn(0f, 1f)
        return Triple(
            a.first + (b.first - a.first) * x,
            a.second + (b.second - a.second) * x,
            a.third + (b.third - a.third) * x,
        )
    }
}
