package compose.project.click.click.ui.theme // pragma: allowlist secret

import androidx.compose.ui.graphics.Color
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CardVisualTest {
    @Test
    fun sameIdIsDeterministic() {
        val a = generateCardVisual("event-42")
        val b = generateCardVisual("event-42")
        assertEquals(a.hash, b.hash)
        assertEquals(a.gradient, b.gradient)
        assertEquals(a.pattern, b.pattern)
        assertEquals(a.hueFamily, b.hueFamily)
        assertEquals(a.pinShape, b.pinShape)
    }

    @Test
    fun differentIdsDiverge() {
        val a = generateCardVisual("event-1")
        val b = generateCardVisual("event-2")
        assertNotEquals(a.hash, b.hash)
    }

    /**
     * Purple stays the heaviest bucket so generated content still reads on-brand, but it is no
     * longer ~62% the way the UI accent ratio is — that ratio now lives only in [ClickAccent].
     */
    @Test
    fun purpleRemainsWeightedAnchor() {
        val samples = (0 until 800).map { generateCardVisual("id-$it") }
        val purple = samples.count { it.hueFamily == CardHueFamily.PURPLE }
        val ratio = purple.toDouble() / samples.size
        assertTrue(ratio in 0.25..0.40, "purple ratio was $ratio")
    }

    @Test
    fun paletteCoversEveryHueFamily() {
        val seen = (0 until 800).map { generateCardVisual("id-$it").hueFamily }.toSet()
        assertEquals(CardHueFamily.entries.toSet(), seen, "unused hue families: ${CardHueFamily.entries - seen}")
    }

    @Test
    fun beaconShapesAreTypeStable() {
        assertEquals(
            BeaconPinShape.CIRCLE,
            generateCardVisual("a", MapBeaconKind.SOUNDTRACK).pinShape,
        )
        assertEquals(
            BeaconPinShape.TRIANGLE,
            generateCardVisual("a", MapBeaconKind.HAZARD).pinShape,
        )
        assertEquals(
            BeaconPinShape.DIAMOND,
            generateCardVisual("a", MapBeaconKind.SOS).pinShape,
        )
        assertEquals(
            BeaconPinShape.HEXAGON,
            generateCardVisual("a", MapBeaconKind.UTILITY).pinShape,
        )
        assertEquals(
            BeaconPinShape.ROUNDED_SQUARE,
            generateCardVisual("a", MapBeaconKind.EVENT).pinShape,
        )
        assertEquals(
            BeaconPinShape.ROUNDED_RECT,
            generateCardVisual("a", MapBeaconKind.STUDY).pinShape,
        )
    }

    @Test
    fun contentAlwaysUsesLightOnScrim() {
        val visual = generateCardVisual("contrast-check")
        assertEquals(1f, visual.onContent.alpha, 0.01f)
        assertTrue(visual.contentScrim.alpha > 0f)
    }

    /**
     * The palette now includes gold and coral, which are far brighter than the old purple/blue-only
     * set. Titles are white, so the scrim has to be searched per visual rather than fixed.
     */
    @Test
    fun whiteTextMeetsWcagOnEveryGeneratedVisual() {
        (0 until 800).forEach { i ->
            val visual = generateCardVisual("wcag-$i")
            visual.gradient.forEach { stop ->
                val scrimmed = compositeOver(Color.Black, visual.scrimAlpha, stop)
                val ratio = contrastRatio(visual.onContent, scrimmed)
                assertTrue(
                    ratio >= WCAG_BODY_TEXT_MIN_RATIO,
                    "wcag-$i stop $stop only reached $ratio at alpha ${visual.scrimAlpha}",
                )
            }
        }
    }
}
