@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.ui.theme.CardPattern // pragma: allowlist secret
import compose.project.click.click.ui.theme.CardVisual // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.generateCardVisual // pragma: allowlist secret

/**
 * Polaroid-style photo used by [PileCluster] and beacon detail chrome.
 * Visuals come from [generateCardVisual]; a scrim keeps title/body readable.
 */
@Composable
fun PhotoCard(
    id: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    imageUrl: String? = null,
    visual: CardVisual = remember(id) { generateCardVisual(id) },
    cornerRadius: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val brush = Brush.linearGradient(visual.gradient)
    val cardModifier =
        modifier
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .border(clickBorderWidth(), clickBorderColor(), shape)
            .background(visual.gradient.first())
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.pointerInput(onClick, onLongClick) {
                        detectTapGestures(
                            onTap = { onClick?.invoke() },
                            onLongPress = { onLongClick?.invoke() },
                        )
                    }
                } else {
                    Modifier
                },
            )
    Box(modifier = cardModifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = brush)
            drawCardPattern(visual.pattern, Color.White.copy(alpha = 0.14f))
        }
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(visual.contentScrim),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = visual.onContent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.onContent.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content?.invoke(this@Box)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCardPattern(
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
                    drawCircle(color.copy(alpha = color.alpha * 0.7f), radius = 0.8.dp.toPx(), center = Offset(x, y))
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
