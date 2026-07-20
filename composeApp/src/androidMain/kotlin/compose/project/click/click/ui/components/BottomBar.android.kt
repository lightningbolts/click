package compose.project.click.click.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.navigation.NavigationItem
import compose.project.click.click.ui.theme.NeonPurple
import compose.project.click.click.ui.theme.PrimaryBlue

private val BarContentHeight = 80.dp

private fun NavigationItem.androidIcon(): ImageVector = when (this) {
    NavigationItem.AddClick -> Icons.Filled.AddCircle
    NavigationItem.Connections -> Icons.Filled.Groups
    else -> icon
}

@Composable
actual fun PlatformBottomBar(
    items: List<NavigationItem>,
    currentRoute: String,
    onItemSelected: (NavigationItem) -> Unit,
    visible: Boolean,
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val chromeHeight = BarContentHeight + navBarBottom
    LaunchedEffect(chromeHeight) {
        AppScreenChromeState.updateBottomChromeHeight(chromeHeight)
    }

    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val haptics = LocalHapticFeedback.current

    val barAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "bottom_bar_visibility",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = barAlpha },
        color = scheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navBarBottom)
                .height(BarContentHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                MaterialBottomBarItem(
                    item = item,
                    selected = selected,
                    enabled = visible,
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!visible) return@MaterialBottomBarItem
                        if (!selected) {
                            if (item.route == NavigationItem.AddClick.route) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                        }
                        onItemSelected(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun MaterialBottomBarItem(
    item: NavigationItem,
    selected: Boolean,
    enabled: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isAdd = item.route == NavigationItem.AddClick.route
    val accent = if (isDark) NeonPurple else PrimaryBlue

    val iconColor by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
            isAdd -> accent
            selected -> if (isDark) scheme.onSecondaryContainer else scheme.onPrimary
            else -> scheme.onSurfaceVariant
        },
        label = "nav_icon_${item.route}",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
            selected || isAdd -> accent
            else -> scheme.onSurfaceVariant
        },
        label = "nav_label_${item.route}",
    )

    val interactionSource = remember { MutableInteractionSource() }

    // No clip here — clipping was cutting off "Settings" / "Add Click".
    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 36.dp, color = accent.copy(alpha = 0.2f)),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .then(
                    if (selected && !isAdd) {
                        Modifier.background(
                            color = if (isDark) scheme.secondaryContainer else PrimaryBlue,
                            shape = RoundedCornerShape(16.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Icon(
                imageVector = item.androidIcon(),
                contentDescription = item.title,
                tint = iconColor,
                modifier = Modifier.size(if (isAdd) 26.dp else 24.dp),
            )
        }

        FittedNavLabel(
            text = item.title,
            color = labelColor,
            selected = selected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shrinks label font until the full string fits — no ellipsis / clip. */
@Composable
private fun FittedNavLabel(
    text: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val weight = if (selected) FontWeight.SemiBold else FontWeight.Medium

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val fontSize = remember(text, maxWidthPx, selected) {
            var sizeSp = 11f
            while (sizeSp >= 8f) {
                val result = measurer.measure(
                    text = text,
                    style = TextStyle(
                        fontSize = sizeSp.sp,
                        fontWeight = weight,
                        letterSpacing = (-0.1).sp,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
                if (result.size.width <= maxWidthPx) break
                sizeSp -= 0.5f
            }
            sizeSp.sp
        }

        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = fontSize,
                fontWeight = weight,
                lineHeight = (fontSize.value + 2f).sp,
                letterSpacing = (-0.1).sp,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
