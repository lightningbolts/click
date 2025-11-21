package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.PlatformMap
import compose.project.click.click.ui.components.MapPin
import compose.project.click.click.ui.components.PageHeader
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.viewmodel.MapViewModel
import compose.project.click.click.viewmodel.MapState
import compose.project.click.click.data.models.Connection
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class MapLocation(
    val id: String,
    val name: String,
    val connectionId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double
)

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel { MapViewModel() }
) {
    var zoom by remember { mutableStateOf(12.0) }
    val mapState by viewModel.mapState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadConnections()
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerTop = if (topInset > 32.dp) topInset - 32.dp else 0.dp

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Box(modifier = Modifier.padding(start = 20.dp, top = headerTop, end = 20.dp)) {
                when (val state = mapState) {
                    is MapState.Success -> {
                        PageHeader(
                            title = "Map",
                            subtitle = "${state.connections.size} ${if (state.connections.size == 1) "connection" else "connections"}"
                        )
                    }
                    else -> {
                        PageHeader(title = "Map", subtitle = "Loading...")
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Map container - Larger size
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                val mapShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp) // Increased from 300dp
                        .clip(mapShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, mapShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    when (val state = mapState) {
                        is MapState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is MapState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Error loading map",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.refresh() }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        is MapState.Success -> {
                            val locations = remember(state.connections) { state.connections.mapNotNull { parseConnectionLocation(it) } }
                            val pins = remember(locations, zoom) { locations.map { MapPin(it.name, it.latitude, it.longitude, true) } }

                            // Platform map - key includes zoom to force recomposition
                            key(zoom) {
                                PlatformMap(
                                    modifier = Modifier.fillMaxSize(),
                                    pins = pins,
                                    zoom = zoom,
                                    onPinTapped = { }
                                )
                            }

                            // Zoom controls styled to match app theme
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledIconButton(
                                    onClick = { zoom = minOf(zoom + 1.0, 20.0) },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                                }
                                FilledIconButton(
                                    onClick = { zoom = maxOf(zoom - 1.0, 2.0) },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                                }
                            }

                            // Stats overlay
                            AdaptiveCard(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "${locations.size} ${if (locations.size == 1) "location" else "locations"}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Connections list
            when (val state = mapState) {
                is MapState.Success -> {
                    if (state.connections.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.LocationOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No Connections Yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Make connections to see them on the map",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    "Connection Locations",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            items(state.connections) { connection ->
                                ConnectionLocationCard(connection)
                            }
                        }
                    }
                }
                is MapState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is MapState.Error -> {
                    // Error already shown in map area
                }
            }
        }
    }
}

// Helper function to parse location from connection
private fun parseConnectionLocation(connection: Connection): MapLocation? {
    return try {
        // Use geo_location from the connection (lat/lon pair)
        val lat = connection.geo_location.lat
        val lon = connection.geo_location.lon

        // Use semantic_location as the display name if available
        val locationName = connection.semantic_location ?: run {
            val instant = Instant.fromEpochMilliseconds(connection.created)
            val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${dateTime.dayOfMonth}, ${dateTime.year}"
        }

        MapLocation(
            id = connection.id,
            name = locationName,
            connectionId = connection.id,
            timestamp = connection.created,
            latitude = lat,
            longitude = lon
        )
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ConnectionLocationCard(connection: Connection) {
    val instant = Instant.fromEpochMilliseconds(connection.created)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val dateStr = "${dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${dateTime.dayOfMonth}, ${dateTime.year}"

    AdaptiveCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { /* Navigate to connection details */ }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Location icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryBlue, LightBlue)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Location details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    connection.semantic_location ?: "Connection Location",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Connection status badge - connection is active if either user wants to continue
                val isActive = connection.should_continue.contains(true)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) PrimaryBlue.copy(alpha = 0.1f) else SoftBlue
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isActive) Icons.Filled.CheckCircle else Icons.Filled.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isActive) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isActive) "Active" else "Connected",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isActive) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
