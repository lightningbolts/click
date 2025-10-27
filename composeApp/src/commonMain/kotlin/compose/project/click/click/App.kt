package compose.project.click.click

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.navigation.NavigationItem
import compose.project.click.click.navigation.bottomNavItems
import compose.project.click.click.ui.screens.*
import compose.project.click.click.ui.theme.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryBlue,
            secondary = AccentBlue,
            background = BackgroundLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            primaryContainer = SoftBlue,
            onPrimaryContainer = DeepBlue
        )
    ) {
        var currentRoute by remember { mutableStateOf("home") }

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceLight.copy(alpha = 0.95f),
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentRoute == item.route,
                            onClick = { currentRoute = item.route },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBlue,
                                selectedTextColor = PrimaryBlue,
                                indicatorColor = SoftBlue,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier.padding(paddingValues),
                color = BackgroundLight
            ) {
                when (currentRoute) {
                    NavigationItem.Home.route -> HomeScreen()
                    NavigationItem.AddClick.route -> AddClickScreen()
                    NavigationItem.Connections.route -> ConnectionsScreen()
                    NavigationItem.Map.route -> MapScreen()
                    NavigationItem.Clicktivities.route -> ClicktivitiesScreen()
                }
            }
        }
    }
}