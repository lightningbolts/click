package compose.project.click.click.ui.theme // pragma: allowlist secret

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret

/**
 * Deterministic per-id visual identity for pile photos, beacon pins, and detail sheets.
 *
 * Hash buckets are 5/8 purple-dominant and 3/8 blue-dominant (62.5 / 37.5), inside the
 * product 60/40–65/35 purple/blue accent ratio. The same id always yields the same result.
 */
data class CardVisual(
    val id: String,
    val hash: Int,
    val gradient: List<Color>,
    val pattern: CardPattern,
    val purpleDominant: Boolean,
    val contentScrim: Color,
    val onContent: Color,
    val pinShape: BeaconPinShape,
) {
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

private val PurpleFamily =
    listOf(
        PrimaryBlue,
        LightBlue,
        DeepBlue,
        AccentBlue,
        Color(0xFF4C1D95),
        NeonPurple,
    )

private val BlueFamily =
    listOf(
        SecondaryAccent,
        Color(0xFF3D63FF),
        Color(0xFF1A3FD9),
        Color(0xFF0D2BB8),
        Color(0xFF6B8CFF),
        Color(0xFF102A9E),
    )

private val Patterns = CardPattern.entries.toTypedArray()

/** 5 purple buckets of 8 = 62.5% purple-dominant gradients. */
internal const val CARD_VISUAL_PURPLE_BUCKETS = 5
internal const val CARD_VISUAL_TOTAL_BUCKETS = 8

fun generateCardVisual(
    id: String,
    pinShape: BeaconPinShape = BeaconPinShape.ROUNDED_SQUARE,
): CardVisual {
    val seed = id.ifBlank { "click" }
    val hash = fnv1a32(seed)
    val unsigned = hash.toUInt()
    val purpleDominant = (unsigned % CARD_VISUAL_TOTAL_BUCKETS.toUInt()).toInt() < CARD_VISUAL_PURPLE_BUCKETS
    val primaryFamily = if (purpleDominant) PurpleFamily else BlueFamily
    val secondaryFamily = if (purpleDominant) BlueFamily else PurpleFamily
    val stopA = primaryFamily[(unsigned / 8u % primaryFamily.size.toUInt()).toInt()]
    val stopB = primaryFamily[(unsigned / 64u % primaryFamily.size.toUInt()).toInt()]
    val stopC = secondaryFamily[(unsigned / 512u % secondaryFamily.size.toUInt()).toInt()]
    val gradient = listOf(stopA, stopB, stopC).distinct()
    val pattern = Patterns[(unsigned / 7u % Patterns.size.toUInt()).toInt()]
    val avgLum =
        gradient
            .map { it.luminance() }
            .average()
            .toFloat()
    val scrimAlpha = if (avgLum > 0.45f) 0.52f else 0.38f
    return CardVisual(
        id = seed,
        hash = hash,
        gradient = gradient,
        pattern = pattern,
        purpleDominant = purpleDominant,
        contentScrim = Color.Black.copy(alpha = scrimAlpha),
        onContent = Color.White,
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

/**
 * Seeded layout jitter so pile clusters stay scattered but stable across recompositions.
 * [index] and [total] space clusters across the board; hash of [id] adds a small offset/rotation.
 */
data class PileLayoutSlot(
    val xFrac: Float,
    val yFrac: Float,
    val rotationDeg: Float,
)

fun pileSlotForCluster(
    id: String,
    index: Int,
    total: Int,
): PileLayoutSlot {
    val hash = fnv1a32(id).toUInt()
    val cols = if (total <= 2) 1 else 2
    val col = index % cols
    val row = index / cols
    val rows = ((total + cols - 1) / cols).coerceAtLeast(1)
    val baseX = (col + 0.5f) / cols.toFloat()
    val baseY = (row + 0.5f) / rows.toFloat()
    val jitterX = ((hash % 17u).toInt() - 8) / 120f
    val jitterY = (((hash / 17u) % 17u).toInt() - 8) / 140f
    val rotation = ((hash / 289u) % 11u).toInt() - 5
    return PileLayoutSlot(
        xFrac = (baseX + jitterX).coerceIn(0.18f, 0.82f),
        yFrac = (baseY + jitterY).coerceIn(0.16f, 0.84f),
        rotationDeg = rotation.toFloat(),
    )
}
