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
    var isDarkMode by remember { mutableStateOf(false) }
    var currentRoute by remember { mutableStateOf("home") }

    MaterialTheme(
        colorScheme = if (isDarkMode) darkColorScheme(
            primary = PrimaryBlue,
            secondary = AccentBlue,
            background = BackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            onSurfaceVariant = Color(0xFFB0B0B0),
            primaryContainer = Color(0xFF1E3A5F),
            onPrimaryContainer = LightBlue,
            surfaceVariant = Color(0xFF2A2A2A),
            onBackground = OnSurfaceDark
        ) else lightColorScheme(
            primary = PrimaryBlue,
            secondary = AccentBlue,
            background = BackgroundLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            onSurfaceVariant = OnSurfaceVariant,
            primaryContainer = SoftBlue,
            onPrimaryContainer = DeepBlue,
            surfaceVariant = GlassLight,
            onBackground = OnSurfaceLight
        )
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = null, // Icon-only navigation
                            selected = currentRoute == item.route,
                            onClick = { currentRoute = item.route },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier.padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                when (currentRoute) {
                    NavigationItem.Home.route -> HomeScreen()
                    NavigationItem.AddClick.route -> AddClickScreen()
                    NavigationItem.Connections.route -> ConnectionsScreen()
                    NavigationItem.Map.route -> MapScreen()
                    NavigationItem.Settings.route -> SettingsScreen(
                        isDarkMode = isDarkMode,
                        onDarkModeToggle = { isDarkMode = it }
                    )
                }
            }
        }
    }
}