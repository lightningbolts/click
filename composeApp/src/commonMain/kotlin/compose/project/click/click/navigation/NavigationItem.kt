package compose.project.click.click.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val sfSymbol: String
) {
    object Home : NavigationItem("home", "Home", Icons.Filled.Home, "house.fill")
    object AddClick : NavigationItem("add_click", "Add Click", Icons.Filled.Add, "plus.circle.fill")
    object Connections : NavigationItem("connections", "Clicks", Icons.Filled.Person, "person.2.fill")
    object Map : NavigationItem("map", "Map", Icons.Filled.LocationOn, "location.fill")
    object Settings : NavigationItem("settings", "Settings", Icons.Filled.Settings, "gearshape.fill")
    object Search : NavigationItem("search", "Search", Icons.Filled.Search, "magnifyingglass")
}

val bottomNavItems = listOf(
    NavigationItem.Home,
    NavigationItem.AddClick,
    NavigationItem.Connections,
    NavigationItem.Map,
    NavigationItem.Settings
)
