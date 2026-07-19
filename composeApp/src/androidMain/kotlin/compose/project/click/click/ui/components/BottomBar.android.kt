package compose.project.click.click.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
    val chromeHeight = if (visible) {
        AppScreenDefaults.AndroidNavBarContentHeight + navBarBottom
    } else {
        navBarBottom
    }
    LaunchedEffect(chromeHeight, visible) {
        AppScreenChromeState.updateBottomChromeHeight(chromeHeight)
    }

    if (!visible) return

    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val haptics = LocalHapticFeedback.current
    val materialTop = scheme.surface.copy(alpha = if (isDark) 0.62f else 0.68f)
    val materialBottom = scheme.surfaceContainer.copy(alpha = if (isDark) 0.78f else 0.82f)

    // Backdrop blur needs a hazeSource on the tab-content ancestor. Keeping that source out of this
    // platform-only actual avoids a fake/foreground blur and preserves #23: content remains visible
    // through this theme-aware material instead of being replaced by an opaque navigation band.
    NavigationBar(
        modifier = Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    materialTop,
                    materialBottom,
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
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "bottom_bar_${item.route}_scale",
            )
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                selected = selected,
                onClick = {
                    if (!selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                    onItemSelected(item)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (isDark) NeonPurple else scheme.onPrimary,
                    selectedTextColor = if (isDark) NeonPurple else PrimaryBlue,
                    indicatorColor = if (isDark) {
                        PrimaryBlue.copy(alpha = 0.28f)
                    } else {
                        PrimaryBlue
                    },
                    unselectedIconColor = scheme.onSurface,
                    unselectedTextColor = scheme.onSurfaceVariant,
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
