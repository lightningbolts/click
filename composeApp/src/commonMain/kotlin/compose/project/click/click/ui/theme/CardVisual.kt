package compose.project.click.click.ui.theme // pragma: allowlist secret

import androidx.compose.ui.graphics.Color
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret

/**
 * Deterministic per-id visual identity for pile photos, beacon pins, list cards, and detail sheets.
 *
 * This is the **content** visual identity and is deliberately *not* the UI accent system. Chrome
 * (buttons, nav, highlights) stays on the 60/40–65/35 purple/blue ratio owned by [ClickAccent];
 * generated content draws from the full [CardHueFamily] palette so two events never look alike.
 * Purple is still the heaviest bucket so the app reads on-brand. The same id always yields the same
 * result.
 */
data class CardVisual(
    val id: String,
    val hash: Int,
    val gradient: List<Color>,
    val pattern: CardPattern,
    val hueFamily: CardHueFamily,
    val scrimAlpha: Float,
    val contentScrim: Color,
    val onContent: Color,
    val pinShape: BeaconPinShape,
) {
    /** Kept for map/pin parity with click-web; the palette is no longer purple-or-blue only. */
    val purpleDominant: Boolean get() = hueFamily == CardHueFamily.PURPLE

    val fillArgb: Int get() = composeColorToArgb(gradient.first())
    val fillArgbSecondary: Int get() = composeColorToArgb(gradient.getOrElse(1) { gradient.first() })
}

enum class CardPattern {
    DOTS,
    DIAGONALS,
    GRAIN,
    GRID,
    CHEVRON,
}

/** Hue families a generated visual can draw from. Purple is the weighted brand anchor. */
enum class CardHueFamily {
    PURPLE,
    BLUE,
    TEAL,
    CORAL,
    GOLD,
    MAGENTA,
    GREEN,
}

/** Map-pin silhouette. Type-stable so beacons are distinguishable at a glance. */
enum class BeaconPinShape {
    CIRCLE,
    ROUNDED_SQUARE,
    TRIANGLE,
    DIAMOND,
    HEXAGON,
    ROUNDED_RECT,
    SQUIRCLE,
    PENTAGON,
}

private val PurpleStops =
    listOf(
        PrimaryBlue,
        LightBlue,
        DeepBlue,
        AccentBlue,
        Color(0xFF4C1D95),
        NeonPurple,
    )

private val BlueStops =
    listOf(
        SecondaryAccent,
        Color(0xFF3D63FF),
        Color(0xFF1A3FD9),
        Color(0xFF0D2BB8),
        Color(0xFF6B8CFF),
        Color(0xFF102A9E),
    )

private val TealStops =
    listOf(
        Color(0xFF0F766E),
        Color(0xFF0D9488),
        Color(0xFF14B8A6),
        Color(0xFF115E59),
        Color(0xFF2DD4BF),
    )

private val CoralStops =
    listOf(
        Color(0xFFE11D48),
        Color(0xFFF43F5E),
        Color(0xFFBE123C),
        Color(0xFFFB7185),
        Color(0xFFEA580C),
    )

private val GoldStops =
    listOf(
        Color(0xFFD97706),
        Color(0xFFF59E0B),
        Color(0xFFB45309),
        Color(0xFFFBBF24),
    )

private val MagentaStops =
    listOf(
        Color(0xFFA21CAF),
        Color(0xFFC026D3),
        Color(0xFF86198F),
        Color(0xFFDB2777),
        Color(0xFFE879F9),
    )

private val GreenStops =
    listOf(
        Color(0xFF15803D),
        Color(0xFF16A34A),
        Color(0xFF166534),
        Color(0xFF22C55E),
        Color(0xFF4ADE80),
    )

private fun stopsFor(family: CardHueFamily): List<Color> =
    when (family) {
        CardHueFamily.PURPLE -> PurpleStops
        CardHueFamily.BLUE -> BlueStops
        CardHueFamily.TEAL -> TealStops
        CardHueFamily.CORAL -> CoralStops
        CardHueFamily.GOLD -> GoldStops
        CardHueFamily.MAGENTA -> MagentaStops
        CardHueFamily.GREEN -> GreenStops
    }

/**
 * Weighted hue buckets. Purple keeps the largest share (5/16 ≈ 31%) as the brand anchor while every
 * other family still shows up often enough that a list of items reads as visually distinct.
 */
private val HueBuckets: List<CardHueFamily> =
    listOf(
        CardHueFamily.PURPLE,
        CardHueFamily.PURPLE,
        CardHueFamily.PURPLE,
        CardHueFamily.PURPLE,
        CardHueFamily.PURPLE,
        CardHueFamily.BLUE,
        CardHueFamily.BLUE,
        CardHueFamily.BLUE,
        CardHueFamily.TEAL,
        CardHueFamily.TEAL,
        CardHueFamily.CORAL,
        CardHueFamily.CORAL,
        CardHueFamily.MAGENTA,
        CardHueFamily.MAGENTA,
        CardHueFamily.GOLD,
        CardHueFamily.GREEN,
    )

internal const val CARD_VISUAL_HUE_BUCKETS = 16

private val Patterns = CardPattern.entries.toTypedArray()

fun generateCardVisual(
    id: String,
    pinShape: BeaconPinShape = BeaconPinShape.ROUNDED_SQUARE,
): CardVisual {
    val seed = id.ifBlank { "click" }
    val hash = fnv1a32(seed)
    val unsigned = hash.toUInt()
    val hueFamily = HueBuckets[(unsigned % CARD_VISUAL_HUE_BUCKETS.toUInt()).toInt()]
    val primaryStops = stopsFor(hueFamily)
    // Second hue family for the far gradient stop, so cards read as a blend rather than one flat hue.
    val otherFamilies = CardHueFamily.entries.filter { it != hueFamily }
    val secondaryFamily = otherFamilies[(unsigned / 16u % otherFamilies.size.toUInt()).toInt()]
    val secondaryStops = stopsFor(secondaryFamily)
    val stopA = primaryStops[(unsigned / 8u % primaryStops.size.toUInt()).toInt()]
    val stopB = primaryStops[(unsigned / 64u % primaryStops.size.toUInt()).toInt()]
    val stopC = secondaryStops[(unsigned / 512u % secondaryStops.size.toUInt()).toInt()]
    val gradient = listOf(stopA, stopB, stopC).distinct()
    val pattern = Patterns[(unsigned / 7u % Patterns.size.toUInt()).toInt()]
    val onContent = Color.White
    val scrimAlpha = scrimAlphaForContrast(gradient, onContent)
    return CardVisual(
        id = seed,
        hash = hash,
        gradient = gradient,
        pattern = pattern,
        hueFamily = hueFamily,
        scrimAlpha = scrimAlpha,
        contentScrim = Color.Black.copy(alpha = scrimAlpha),
        onContent = onContent,
        pinShape = pinShape,
    )
}

fun generateCardVisual(
    id: String,
    kind: MapBeaconKind,
    typeKey: String? = null,
): CardVisual = generateCardVisual(id, beaconPinShapeFor(kind, typeKey))

fun beaconPinShapeFor(
    kind: MapBeaconKind,
    typeKey: String? = null,
): BeaconPinShape {
    val raw = typeKey?.trim()?.lowercase().orEmpty()
    return when {
        raw == "soundtrack" || kind == MapBeaconKind.SOUNDTRACK -> BeaconPinShape.CIRCLE
        raw == "event" || kind == MapBeaconKind.EVENT -> BeaconPinShape.ROUNDED_SQUARE
        raw == "hazard" || kind == MapBeaconKind.HAZARD -> BeaconPinShape.TRIANGLE
        raw == "sos" || kind == MapBeaconKind.SOS -> BeaconPinShape.DIAMOND
        raw == "utility" || kind == MapBeaconKind.UTILITY -> BeaconPinShape.HEXAGON
        raw == "study" || kind == MapBeaconKind.STUDY -> BeaconPinShape.ROUNDED_RECT
        raw == "social" || raw == "social_vibe" || kind == MapBeaconKind.SOCIAL_VIBE ->
            BeaconPinShape.SQUIRCLE
        else -> BeaconPinShape.PENTAGON
    }
}

fun composeColorToArgb(color: Color): Int {
    val a = (color.alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
    val r = (color.red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

internal fun fnv1a32(text: String): Int {
    var hash = -0x7ee3623b // 2166136261 as signed
    for (ch in text) {
        hash = hash xor ch.code
        hash *= 0x01000193
    }
    return hash
}
