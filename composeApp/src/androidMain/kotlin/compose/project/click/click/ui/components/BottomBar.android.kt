package compose.project.click.click.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    // Fully clear chrome — tab content must look identical under the icons as above them.
    NavigationBar(
        containerColor = Color.Transparent,
        contentColor = scheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                selected = selected,
                onClick = { onItemSelected(item) },
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
