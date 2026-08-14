package compose.project.click.click.ui.theme // pragma: allowlist secret

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** WCAG 2.1 AA minimum for body text. */
const val WCAG_BODY_TEXT_MIN_RATIO = 4.5f

/** WCAG 2.1 AA minimum for large / bold text and non-text UI. */
const val WCAG_LARGE_TEXT_MIN_RATIO = 3.0f

private const val SCRIM_ALPHA_FLOOR = 0.28f
private const val SCRIM_ALPHA_CEILING = 0.82f
private const val SCRIM_ALPHA_STEP = 0.02f

/**
 * WCAG 2.1 contrast ratio between two opaque colors: 1.0 when identical, 21.0 for black on white.
 * [Color.luminance] already returns WCAG relative luminance, so this only applies the ratio formula.
 */
fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val lighter = max(a.luminance(), b.luminance())
    val darker = min(a.luminance(), b.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Composites [scrim] at [alpha] over an opaque [base]. Compose blends in sRGB space, so this
 * matches what is actually drawn when a translucent scrim Box sits on top of [base].
 */
fun compositeOver(
    scrim: Color,
    alpha: Float,
    base: Color,
): Color {
    val a = alpha.coerceIn(0f, 1f)
    return Color(
        red = scrim.red * a + base.red * (1f - a),
        green = scrim.green * a + base.green * (1f - a),
        blue = scrim.blue * a + base.blue * (1f - a),
    )
}

/**
 * Smallest scrim alpha that keeps [onContent] readable over **every** color in [backgrounds].
 *
 * Generated card visuals may pick bright hues (gold, coral, mint), so a fixed luminance heuristic is
 * not enough: this walks alpha upward until white-on-scrim clears [minRatio] against the worst stop.
 * Always returns at least [SCRIM_ALPHA_FLOOR] so patterns never fight the title, and never more than
 * [SCRIM_ALPHA_CEILING] so the hue is still recognisable.
 */
fun scrimAlphaForContrast(
    backgrounds: List<Color>,
    onContent: Color,
    minRatio: Float = WCAG_BODY_TEXT_MIN_RATIO,
    scrim: Color = Color.Black,
): Float {
    if (backgrounds.isEmpty()) return SCRIM_ALPHA_FLOOR
    var alpha = SCRIM_ALPHA_FLOOR
    while (alpha < SCRIM_ALPHA_CEILING) {
        val readable =
            backgrounds.all { base ->
                contrastRatio(onContent, compositeOver(scrim, alpha, base)) >= minRatio
            }
        if (readable) return alpha
        alpha += SCRIM_ALPHA_STEP
    }
    return SCRIM_ALPHA_CEILING
}
