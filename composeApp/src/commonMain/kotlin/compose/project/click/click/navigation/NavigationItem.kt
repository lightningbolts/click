package compose.project.click.click.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : NavigationItem("home", "Home", Icons.Filled.Home)
    object AddClick : NavigationItem("add_click", "Add Click", Icons.Filled.Add)
    object Connections : NavigationItem("connections", "Clicks", Icons.Filled.Person)
    object Map : NavigationItem("map", "Map", Icons.Filled.LocationOn)
    object Clicktivities : NavigationItem("clicktivities", "Clicktivities", Icons.Filled.Star)
}

val bottomNavItems = listOf(
    NavigationItem.Home,
    NavigationItem.AddClick,
    NavigationItem.Connections,
    NavigationItem.Map,
    NavigationItem.Clicktivities
)

