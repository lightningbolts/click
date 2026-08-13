package compose.project.click.click.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val sfSymbol: String,
    val maestroTestTag: String,
) {
    object Home : NavigationItem("home", "Home", Icons.Filled.Home, "house.fill", "nav-home")
    object AddClick : NavigationItem("add_click", "Add Click", Icons.Filled.Add, "plus.circle.fill", "nav-add-click")
    object Connections : NavigationItem("connections", "Clicks", Icons.Filled.Person, "person.2.fill", "nav-clicks")
    object Map : NavigationItem("map", "Map", Icons.Filled.LocationOn, "location.fill", "nav-map")
    object Settings : NavigationItem("settings", "Settings", Icons.Filled.Settings, "gearshape.fill", "nav-settings")
    object Search : NavigationItem("search", "Search", Icons.Filled.Search, "magnifyingglass", "nav-search")
}

val bottomNavItems = listOf(
    NavigationItem.Home,
    NavigationItem.AddClick,
    NavigationItem.Connections,
    NavigationItem.Map,
    NavigationItem.Settings
)
