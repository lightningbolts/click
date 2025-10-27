package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Represents a point to plot on the map
data class MapPin(
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val isNearby: Boolean = false
)

// Platform-specific map implementation
@Composable
expect fun PlatformMap(
    modifier: Modifier,
    pins: List<MapPin>,
    onPinTapped: (MapPin) -> Unit = {}
)

