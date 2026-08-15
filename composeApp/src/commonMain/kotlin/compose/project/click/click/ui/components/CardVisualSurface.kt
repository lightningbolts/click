@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.ui.theme.CardPattern // pragma: allowlist secret
import compose.project.click.click.ui.theme.CardVisual // pragma: allowlist secret
import compose.project.click.click.ui.theme.generateCardVisual // pragma: allowlist secret

/**
 * The single source of generated visual identity for every card surface in the app.
 *
 * A given beacon / event / connection must look the same wherever it is rendered — home pile,
 * Events list, share sheets, profile Beacons tab, detail-sheet headers — so all of those surfaces
 * paint through [cardVisualBackground] or [CardVisualHero] rather than rolling their own gradient.
 * Seed with the raw entity id (`beacon.id`), never a list-key prefix, or the same item will get two
 * different gradients on two screens.
 */
@Composable
fun rememberCardVisual(
    id: String,
    kind: MapBeaconKind? = null,
    typeKey: String? = null,
): CardVisual =
    remember(id, kind, typeKey) {
        if (kind == null) generateCardVisual(id) else generateCardVisual(id, kind, typeKey)
    }

/** Paints the deterministic gradient plus its pattern overlay behind content. */
fun Modifier.cardVisualBackground(visual: CardVisual): Modifier =
    this.drawBehind {
        drawRect(brush = Brush.linearGradient(visual.gradient))
        drawCardPattern(visual.pattern, Color.White.copy(alpha = 0.14f))
    }

/**
 * True when [CardVisualHero] should paint the generated gradient/pattern instead of a cover photo.
 * A non-blank [imageUrl] wins until Coil reports an error.
 */
fun cardVisualHeroUsesGeneratedPattern(
    imageUrl: String?,
    imageFailed: Boolean = false,
): Boolean = imageUrl?.trim().isNullOrEmpty() || imageFailed

/**
 * Decorative gradient/pattern band used as a card hero or detail-sheet header.
 *
 * Deliberately has no title/subtitle parameters: detail sheets render title, date, and location in
 * the structured section below, so repeating them here would duplicate the same text twice on one
 * screen. [chipLabel] is the only text allowed, for a short category tag.
 */
@Composable
fun CardVisualHero(
    id: String,
    modifier: Modifier = Modifier,
    kind: MapBeaconKind? = null,
    typeKey: String? = null,
    visual: CardVisual = rememberCardVisual(id, kind, typeKey),
    imageUrl: String? = null,
    chipLabel: String? = null,
    scrim: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    val trimmedImage = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    var imageFailed by remember(trimmedImage) { mutableStateOf(false) }
    val useGenerated = cardVisualHeroUsesGeneratedPattern(trimmedImage, imageFailed)
    Box(
        modifier =
            if (useGenerated) {
                modifier.cardVisualBackground(visual)
            } else {
                modifier.background(visual.gradient.first())
            },
        contentAlignment = contentAlignment,
    ) {
        if (trimmedImage != null) {
            AsyncImage(
                model = trimmedImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                onSuccess = { imageFailed = false },
            )
        }
        if (scrim) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(visual.contentScrim),
            )
        }
        content?.invoke(this)
        if (!chipLabel.isNullOrBlank()) {
            CardVisualChip(
                label = chipLabel,
                visual = visual,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
            )
        }
    }
}

/** Small translucent tag that stays legible on every hue the generator can produce. */
@Composable
fun CardVisualChip(
    label: String,
    visual: CardVisual,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.38f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = visual.onContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun DrawScope.drawCardPattern(
    pattern: CardPattern,
    color: Color,
) {
    val w = size.width
    val h = size.height
    when (pattern) {
        CardPattern.DOTS -> {
            val step = 14.dp.toPx()
            var y = step / 2f
            while (y < h) {
                var x = step / 2f
                while (x < w) {
                    drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        CardPattern.DIAGONALS -> {
            val step = 12.dp.toPx()
            var x = -h
            while (x < w + h) {
                drawLine(
                    color = color,
                    start = Offset(x, 0f),
                    end = Offset(x + h, h),
                    strokeWidth = 1.2.dp.toPx(),
                )
                x += step
            }
        }
        CardPattern.GRAIN -> {
            val step = 5.dp.toPx()
            var y = 0f
            var row = 0
            while (y < h) {
                var x = if (row % 2 == 0) 0f else step / 2f
                while (x < w) {
                    drawCircle(
                        color.copy(alpha = color.alpha * 0.7f),
                        radius = 0.8.dp.toPx(),
                        center = Offset(x, y),
                    )
                    x += step
                }
                y += step
                row++
            }
        }
        CardPattern.GRID -> {
            val step = 16.dp.toPx()
            var x = 0f
            while (x < w) {
                drawLine(color, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
                x += step
            }
            var y = 0f
            while (y < h) {
                drawLine(color, Offset(0f, y), Offset(w, y), 1.dp.toPx())
                y += step
            }
        }
        CardPattern.CHEVRON -> {
            val step = 18.dp.toPx()
            var y = 0f
            while (y < h + step) {
                val path =
                    Path().apply {
                        moveTo(0f, y)
                        var x = 0f
                        var up = true
                        while (x < w) {
                            x += step
                            lineTo(x, if (up) y - step / 2f else y + step / 2f)
                            up = !up
                        }
                    }
                drawPath(path, color, style = Stroke(width = 1.4.dp.toPx()))
                y += step
            }
        }
    }
}
