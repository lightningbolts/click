package compose.project.click.click.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.project.click.click.navigation.NavigationItem
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.theme.clickBorderColor

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

    val borderWidth = LocalPlatformStyle.current.cardBorderWidth
    val scheme = MaterialTheme.colorScheme

    NavigationBar(
        modifier = Modifier.border(width = borderWidth, color = clickBorderColor()),
        // Translucent so tab content remains visible scrolling underneath.
        containerColor = scheme.surface.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                selected = currentRoute == item.route,
                onClick = { onItemSelected(item) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = scheme.onPrimary,
                    // Label sits on the bar surface, not the purple indicator — use bright on-surface.
                    selectedTextColor = scheme.onSurface,
                    indicatorColor = PrimaryBlue,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                ),
                alwaysShowLabel = true,
            )
        }
    }
}
