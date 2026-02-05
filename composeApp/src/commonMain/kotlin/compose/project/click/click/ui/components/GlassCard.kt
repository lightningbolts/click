package compose.project.click.click.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*

/**
 * Glass Card shape matching web's rounded-3xl (24px)
 */
val GlassCardShape = RoundedCornerShape(24.dp)

/**
 * Default corner radius for glass cards
 */
val GlassCornerRadius: Dp = 24.dp

/**
 * GlassCard - Replicates the web's .glass CSS effect:
 * - bg-white/5 (translucent white background)
 * - border-white/10 (subtle white border)
 * - backdrop-blur (simulated via opacity layers)
 * - rounded-3xl (24dp corner radius)
 *
 * Note: Real backdrop-blur is expensive in KMP, so we simulate
 * the effect using the specific translucent colors from the web.
 *
 * @param modifier Modifier to apply to the card
 * @param onClick Optional click handler; makes the card clickable
 * @param usePrimaryBorder If true, uses a subtle violet-tinted border
 * @param contentPadding Padding inside the card (default 16.dp)
 * @param content The composable content to display inside the card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    usePrimaryBorder: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (usePrimaryBorder) GlassBorderPrimary else GlassBorder
    
    val cardModifier = modifier
        .clip(GlassCardShape)
        .background(GlassWhite)
        .border(
            width = 1.dp,
            color = borderColor,
            shape = GlassCardShape
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // Minimal ripple for glass aesthetic
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Box(modifier = cardModifier) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * GlassCard variant using Surface for Material3 integration
 * Provides elevation and ripple effects while maintaining glass aesthetic
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    usePrimaryBorder: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (usePrimaryBorder) GlassBorderPrimary else GlassBorder
    
    Surface(
        modifier = modifier,
        shape = GlassCardShape,
        color = GlassWhite,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        onClick = onClick ?: {}
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Utility modifier to apply glass effect to any composable
 * Useful for custom components that need the glass aesthetic
 */
fun Modifier.glassEffect(usePrimaryBorder: Boolean = false): Modifier {
    val borderColor = if (usePrimaryBorder) GlassBorderPrimary else GlassBorder
    return this
        .clip(GlassCardShape)
        .background(GlassWhite)
        .border(1.dp, borderColor, GlassCardShape)
}

/**
 * Smaller glass card with reduced corner radius (16dp)
 * For compact UI elements like chips, tags, small buttons
 */
@Composable
fun GlassCardCompact(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val compactShape = RoundedCornerShape(16.dp)
    
    val cardModifier = modifier
        .clip(compactShape)
        .background(GlassWhite)
        .border(1.dp, GlassBorder, compactShape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Box(modifier = cardModifier) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
