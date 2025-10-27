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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*

data class MapLocation(
    val name: String,
    val address: String,
    val clickCount: Int,
    val distance: String,
    val isNearby: Boolean = false,
    val friendsHere: List<String> = emptyList()
)

@Composable
fun MapScreen() {
    var selectedFilter by remember { mutableStateOf("All") }

    val locations = remember {
        listOf(
            MapLocation("Starbucks Coffee", "123 Main St", 12, "0.2 mi", true, listOf("Alice", "Charlie")),
            MapLocation("Central Park", "Park Ave", 8, "0.5 mi", true, listOf("Diana")),
            MapLocation("Tech Hub Coworking", "456 Tech Blvd", 15, "0.8 mi", false),
            MapLocation("The Local Cafe", "789 Coffee Ln", 6, "1.2 mi", false, listOf("Eve")),
            MapLocation("Downtown Mall", "Shopping District", 10, "1.5 mi", false),
            MapLocation("Riverside Park", "River Rd", 5, "2.1 mi", false)
        )
    }

    val filteredLocations = when (selectedFilter) {
        "Nearby" -> locations.filter { it.isNearby }
        "Friends" -> locations.filter { it.friendsHere.isNotEmpty() }
        else -> locations
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // Header with Material You
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = GlassLight.copy(alpha = 0.95f),
            shadowElevation = 2.dp,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Map",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceLight
                        )
                        Text(
                            "Discover click spots near you",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { /* Center on current location */ },
                        modifier = Modifier
                            .size(48.dp)
                            .background(SoftBlue, CircleShape)
                    ) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = "My Location",
                            tint = PrimaryBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = { Text("All Spots") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "Nearby",
                        onClick = { selectedFilter = "Nearby" },
                        label = { Text("Nearby") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "Friends",
                        onClick = { selectedFilter = "Friends" },
                        label = { Text("Friends") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Map placeholder with gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SoftBlue.copy(alpha = 0.3f),
                            SoftBlue.copy(alpha = 0.1f)
                        )
                    )
                )
        ) {
            // Map pins visualization
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Map,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = PrimaryBlue.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Interactive Map View",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${filteredLocations.size} locations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Floating stats card
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${locations.count { it.isNearby }} nearby",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceLight
                    )
                }
            }
        }

        // Locations list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Click Locations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceLight
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(filteredLocations) { location ->
                LocationCard(location)
            }

            if (filteredLocations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No locations found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationCard(location: MapLocation) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = SurfaceLight
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        ),
        onClick = { /* Navigate to location details */ }
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
                        if (location.isNearby) {
                            Brush.linearGradient(
                                colors = listOf(PrimaryBlue, LightBlue)
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(SoftBlue, SoftBlue)
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = if (location.isNearby) Color.White else PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Location details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            location.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceLight
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                location.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    // Distance badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (location.isNearby) PrimaryBlue.copy(alpha = 0.1f) else SoftBlue
                    ) {
                        Text(
                            location.distance,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (location.isNearby) PrimaryBlue else OnSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Click count and friends
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${location.clickCount} clicks",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceLight,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (location.friendsHere.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((-8).dp)
                        ) {
                            location.friendsHere.take(3).forEach { friend ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(LightBlue, PrimaryBlue)
                                            )
                                        )
                                        .border(2.dp, SurfaceLight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        friend.first().toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (location.friendsHere.size > 3) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(TextSecondary)
                                        .border(2.dp, SurfaceLight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "+${location.friendsHere.size - 3}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

