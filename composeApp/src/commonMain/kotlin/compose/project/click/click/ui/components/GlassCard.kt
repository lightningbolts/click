package compose.project.click.click.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*

@Composable
private fun platformCardShape(): RoundedCornerShape {
    val style = LocalPlatformStyle.current
    return RoundedCornerShape(style.cardCornerRadius)
}

@Composable
private fun platformCardBackground(): Color =
    MaterialTheme.colorScheme.surface

@Composable
private fun platformCardBorder(usePrimary: Boolean): Color {
    return if (usePrimary) PrimaryBlue else BorderHard
}

@Composable
private fun platformBorderWidth(): Dp = LocalPlatformStyle.current.cardBorderWidth

/** Legacy name — Functional Clarity uses 16.dp card corners. */
val GlassCornerRadius: Dp = 16.dp
val GlassCardShape = RoundedCornerShape(GlassCornerRadius)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    usePrimaryBorder: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalPlatformStyle.current
    val shape = platformCardShape()
    val bg = platformCardBackground()
    val borderColor = platformCardBorder(usePrimaryBorder)
    val borderWidth = platformBorderWidth()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressOffset = if (onClick != null && pressed) style.pressOffset else 0.dp

    val cardModifier = modifier
        .offset(x = pressOffset, y = pressOffset)
        .clip(shape)
        .background(bg)
        .border(width = borderWidth, color = borderColor, shape = shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
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
    val shape = platformCardShape()
    val bg = platformCardBackground()
    val borderColor = platformCardBorder(usePrimaryBorder)
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
    val borderColor = if (usePrimaryBorder) PrimaryBlue else BorderHard
    return this
        .clip(GlassCardShape)
        .background(SurfaceLight)
        .border(2.dp, borderColor, GlassCardShape)
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
    val bg = platformCardBackground()
    val borderColor = platformCardBorder(false)
    val borderWidth = platformBorderWidth()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressOffset = if (onClick != null && pressed) style.pressOffset else 0.dp

    val cardModifier = modifier
        .offset(x = pressOffset, y = pressOffset)
        .clip(compactShape)
        .background(bg)
        .border(borderWidth, borderColor, compactShape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
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
