@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.CardVisual // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.generateCardVisual // pragma: allowlist secret

/**
 * Polaroid-style photo used by [PileCluster] and beacon detail chrome.
 *
 * Visuals come from [generateCardVisual] via the shared [CardVisualHero]; the scrim it paints keeps
 * title/body readable on every hue. Titles ellipsize rather than wrap without bound, and pile layers
 * only ever peek right/down, so a label is never hidden under a neighbouring card.
 */
@Composable
fun PhotoCard(
    id: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    imageUrl: String? = null,
    visual: CardVisual = rememberCardVisual(id),
    cornerRadius: Dp = 12.dp,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val cardModifier =
        modifier
            .shadow(elevation, shape, clip = false)
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
    CardVisualHero(
        id = id,
        visual = visual,
        imageUrl = imageUrl,
        modifier = cardModifier,
        contentAlignment = Alignment.BottomStart,
    ) {
        val heroScope = this
        Column(
            modifier =
                Modifier
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
            content?.invoke(heroScope)
        }
    }
}
