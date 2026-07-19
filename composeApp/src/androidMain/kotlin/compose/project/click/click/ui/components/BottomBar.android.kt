package compose.project.click.click.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import compose.project.click.click.navigation.NavigationItem
import compose.project.click.click.ui.theme.NeonPurple
import compose.project.click.click.ui.theme.PrimaryBlue

@Composable
actual fun PlatformBottomBar(
    items: List<NavigationItem>,
    currentRoute: String,
    onItemSelected: (NavigationItem) -> Unit,
    visible: Boolean,
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Keep chrome height stable while chat overlays hide the bar — avoids list padding jumps
    // and prevents NavigationBar from leaving composition (remount flicker on return).
    val chromeHeight = AppScreenDefaults.AndroidNavBarContentHeight + navBarBottom
    LaunchedEffect(chromeHeight) {
        AppScreenChromeState.updateBottomChromeHeight(chromeHeight)
    }

    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val haptics = LocalHapticFeedback.current
    // Click-tinted glass: purple wash over surface so Android matches iOS Click material.
    val glassTop = if (isDark) {
        PrimaryBlue.copy(alpha = 0.18f)
    } else {
        PrimaryBlue.copy(alpha = 0.10f)
    }
    val glassMid = scheme.surface.copy(alpha = if (isDark) 0.78f else 0.82f)
    val glassBottom = scheme.surfaceContainer.copy(alpha = if (isDark) 0.92f else 0.94f)
    val barAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bottom_bar_visibility",
    )

    NavigationBar(
        modifier = Modifier
            .graphicsLayer {
                alpha = barAlpha
                // Keep hit-testing off while faded so the chat composer receives taps.
            }
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        glassTop,
                        glassMid,
                        glassBottom,
                    ),
                ),
            ),
        containerColor = Color.Transparent,
        contentColor = scheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val iconScale by animateFloatAsState(
                targetValue = if (selected && visible) 1.08f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "bottom_bar_${item.route}_scale",
            )
            NavigationBarItem(
                icon = {
                    if (item.route == NavigationItem.AddClick.route) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDark) NeonPurple.copy(alpha = 0.95f)
                                    else PrimaryBlue,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isDark) Color.Black else Color.White,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            )
                        }
                    } else {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                        )
                    }
                },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                selected = selected,
                enabled = visible,
                onClick = {
                    if (!visible) return@NavigationBarItem
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
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (isDark) NeonPurple else scheme.onPrimary,
                    selectedTextColor = if (isDark) NeonPurple else PrimaryBlue,
                    indicatorColor = if (item.route == NavigationItem.AddClick.route) {
                        Color.Transparent
                    } else if (isDark) {
                        PrimaryBlue.copy(alpha = 0.55f)
                    } else {
                        PrimaryBlue
                    },
                    unselectedIconColor = scheme.onSurface,
                    unselectedTextColor = scheme.onSurfaceVariant,
                    disabledIconColor = scheme.onSurface.copy(alpha = 0.4f),
                    disabledTextColor = scheme.onSurfaceVariant.copy(alpha = 0.4f),
                ),
                alwaysShowLabel = true,
            )
        }
    }
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
