package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import compose.project.click.click.navigation.NavigationItem

@Composable
expect fun PlatformBottomBar(
    items: List<NavigationItem>,
    currentRoute: String,
    onItemSelected: (NavigationItem) -> Unit
)

