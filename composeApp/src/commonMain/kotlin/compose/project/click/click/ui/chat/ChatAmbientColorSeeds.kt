package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.Connection
import kotlin.math.abs

/**
 * Derives three RGB triples (0..1) for the chat ambient mesh from the active
 * [Connection]: latest encounter [compose.project.click.click.data.models.WeatherSnapshot]
 * and [Connection.semanticLocation] / resolved place labels.
 *
 * Kept free of Compose types so the same logic is unit-testable on every target.
 */
internal object ChatAmbientColorSeeds {

    private const val H = 17
    private const val M = 0x7fff

    fun rgbTriples01(connection: Connection?): List<Triple<Float, Float, Float>> {
        val weather = connection?.resolvedWeatherCondition
            ?: connection?.latestEncounter()?.weatherSnapshot?.condition
            ?: connection?.latestEncounter()?.weatherSnapshot?.iconCode
        val semantic = connection?.semanticLocation?.lowercase().orEmpty()
        val hue01 = hueFromSemantic(semantic)
        val (a, b, c) = weatherMoodRgb(weather, hue01)
        return listOf(a, b, c)
    }

    fun hubNeutralRgbTriples(): List<Triple<Float, Float, Float>> {
        val a = Triple(0.22f, 0.42f, 0.92f)
        val b = Triple(0.45f, 0.72f, 1.00f)
        val c = Triple(0.12f, 0.28f, 0.55f)
        return listOf(a, b, c)
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

    private fun weatherMoodRgb(
        weatherRaw: String?,
        hue01: Float,
    ): Triple<Triple<Float, Float, Float>, Triple<Float, Float, Float>, Triple<Float, Float, Float>> {
        val w = weatherRaw?.lowercase().orEmpty()
        val warm = Triple(0.98f, 0.72f, 0.35f)
        val cool = Triple(0.35f, 0.62f, 0.95f)
        val mist = Triple(0.62f, 0.68f, 0.78f)
        val forest = Triple(0.32f, 0.72f, 0.48f)
        val storm = Triple(0.28f, 0.36f, 0.55f)
        val snow = Triple(0.82f, 0.90f, 1.00f)

        val primary = when {
            w.contains("snow") || w.contains("ice") || w.contains("hail") -> snow
            w.contains("rain") || w.contains("storm") || w.contains("drizzle") -> storm
            w.contains("fog") || w.contains("mist") || w.contains("cloud") || w.contains("overcast") -> mist
            w.contains("clear") || w.contains("sun") || w.contains("fair") || w.contains("hot") -> warm
            w.contains("wind") -> cool
            w.isNotEmpty() && (w.contains("green") || w.contains("tree")) -> forest
            w.isNotEmpty() -> lerpTriple(cool, warm, hue01)
            else -> lerpTriple(cool, mist, hue01)
        }
        val secondary = lerpTriple(primary, cool, 0.35f)
        val tertiary = lerpTriple(primary, warm, 0.25f + 0.15f * hue01)
        return Triple(primary, secondary, tertiary)
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
