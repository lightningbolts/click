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

@Composable
private fun platformGlassShape(): RoundedCornerShape {
    val style = LocalPlatformStyle.current
    return RoundedCornerShape(style.cardCornerRadius)
}

@Composable
private fun platformGlassBackground(): Color =
    Color.White.copy(alpha = LocalPlatformStyle.current.glassBackgroundAlpha)

@Composable
private fun platformGlassBorder(usePrimary: Boolean): Color {
    val style = LocalPlatformStyle.current
    return if (usePrimary) PrimaryBlue.copy(alpha = style.glassBorderPrimaryAlpha)
    else Color.White.copy(alpha = style.glassBorderAlpha)
}

@Composable
private fun platformBorderWidth(): Dp = LocalPlatformStyle.current.cardBorderWidth

val GlassCardShape = RoundedCornerShape(24.dp)
val GlassCornerRadius: Dp = 24.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    usePrimaryBorder: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = platformGlassShape()
    val bg = platformGlassBackground()
    val borderColor = platformGlassBorder(usePrimaryBorder)
    val borderWidth = platformBorderWidth()

    val cardModifier = modifier
        .clip(shape)
        .background(bg)
        .border(width = borderWidth, color = borderColor, shape = shape)
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

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    usePrimaryBorder: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = platformGlassShape()
    val bg = platformGlassBackground()
    val borderColor = platformGlassBorder(usePrimaryBorder)
    val borderWidth = platformBorderWidth()

    Surface(
        modifier = modifier,
        shape = shape,
        color = bg,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = 0.dp,
        onClick = onClick ?: {}
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

fun Modifier.glassEffect(usePrimaryBorder: Boolean = false): Modifier {
    val borderColor = if (usePrimaryBorder) GlassBorderPrimary else GlassBorder
    return this
        .clip(GlassCardShape)
        .background(GlassWhite)
        .border(1.dp, borderColor, GlassCardShape)
}

@Composable
fun GlassCardCompact(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalPlatformStyle.current
    val compactShape = RoundedCornerShape(style.compactCardCornerRadius)
    val bg = platformGlassBackground()
    val borderColor = platformGlassBorder(false)
    val borderWidth = platformBorderWidth()

    val cardModifier = modifier
        .clip(compactShape)
        .background(bg)
        .border(borderWidth, borderColor, compactShape)
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
