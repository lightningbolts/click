package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import theme.ClickColors
import ui.components.*

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNFCTapClick: () -> Unit = {},
    onConnectionsClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onVibeCheckClick: () -> Unit = {},
    onReconnectClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    var currentDestination by remember { mutableStateOf(NavDestination.HOME) }

    Scaffold(
        backgroundColor = ClickColors.Background,
        topBar = {
            TopAppBar(
                backgroundColor = Color.Transparent,
                elevation = 0.dp,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.caption,
                        color = ClickColors.TextSecondary
                    )
                    Text(
                        text = "Click",
                        style = MaterialTheme.typography.h1,
                        color = ClickColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(
                currentDestination = currentDestination,
                onNavigate = { destination ->
                    currentDestination = destination
                    when (destination) {
                        NavDestination.HOME -> {}
                        NavDestination.CLICK -> onNFCTapClick()
                        NavDestination.CONNECTIONS -> onConnectionsClick()
                        NavDestination.MAP -> onMapClick()
                        NavDestination.SETTINGS -> onSettingsClick()
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Hero Section - NFC Tap
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = MaterialTheme.shapes.large,
                    elevation = 0.dp,
                    backgroundColor = ClickColors.Primary
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "NFC Tap",
                                modifier = Modifier.size(56.dp),
                                tint = Color.White
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Ready to Connect",
                                style = MaterialTheme.typography.h3,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tap phones to exchange contact",
                                style = MaterialTheme.typography.body2,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }
                    }
                }
            }

            // Stats Overview
            item {
                Column {
                    Text(
                        text = "Your Network",
                        style = MaterialTheme.typography.h4,
                        fontWeight = FontWeight.SemiBold,
                        color = ClickColors.TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatsCard(
                            value = "24",
                            label = "Connections",
                            color = ClickColors.Primary,
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp)
                        )
                        StatsCard(
                            value = "8",
                            label = "Cities",
                            color = ClickColors.Secondary,
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp)
                        )
                        StatsCard(
                            value = "3",
                            label = "This Week",
                            color = ClickColors.Accent,
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp)
                        )
                    }
                }
            }

            // Quick Actions - Feature Cards
            item {
                Column {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.h4,
                        fontWeight = FontWeight.SemiBold,
                        color = ClickColors.TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))

                    CompactFeatureCard(
                        title = "View Connections Map",
                        subtitle = "See where you've met people",
                        icon = Icons.Default.Place,
                        iconBackground = ClickColors.Secondary,
                        onClick = onMapClick
                    )

                    Spacer(Modifier.height(12.dp))

                    CompactFeatureCard(
                        title = "Reconnect with Someone",
                        subtitle = "Get a random suggestion to reach out",
                        icon = Icons.Default.Refresh,
                        iconBackground = ClickColors.Accent,
                        onClick = onReconnectClick
                    )

                    Spacer(Modifier.height(12.dp))

                    CompactFeatureCard(
                        title = "30-Minute Vibe Check",
                        subtitle = "Start a temporary chat",
                        icon = Icons.Default.Star,
                        iconBackground = ClickColors.Primary,
                        onClick = onVibeCheckClick
                    )
                }
            }

            // Featured Sections
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Explore Features",
                            style = MaterialTheme.typography.h4,
                            fontWeight = FontWeight.SemiBold,
                            color = ClickColors.TextPrimary
                        )
                        TextButton(onClick = onConnectionsClick) {
                            Text(
                                text = "See All",
                                color = ClickColors.Primary
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            FeatureCard(
                                title = "Connections",
                                description = "Browse your network",
                                icon = Icons.Default.Person,
                                gradient = listOf(
                                    ClickColors.Primary,
                                    ClickColors.PrimaryVariant
                                ),
                                onClick = onConnectionsClick,
                                modifier = Modifier.width(280.dp)
                            )
                        }

                        item {
                            FeatureCard(
                                title = "Map View",
                                description = "Explore where you've connected",
                                icon = Icons.Default.Place,
                                gradient = listOf(
                                    ClickColors.Secondary,
                                    ClickColors.SecondaryVariant
                                ),
                                onClick = onMapClick,
                                modifier = Modifier.width(280.dp)
                            )
                        }

                        item {
                            FeatureCard(
                                title = "Let's Meet Up",
                                description = "Schedule a real-world meetup",
                                icon = Icons.Default.DateRange,
                                gradient = listOf(
                                    ClickColors.Accent,
                                    ClickColors.AccentLight
                                ),
                                onClick = {},
                                modifier = Modifier.width(280.dp)
                            )
                        }
                    }
                }
            }

            // Recent Activity Section
            item {
                Column {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.h4,
                        fontWeight = FontWeight.SemiBold,
                        color = ClickColors.TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        elevation = 0.dp,
                        backgroundColor = ClickColors.Surface
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            RecentActivityItem(
                                title = "Connected with Sarah",
                                location = "Coffee House, Downtown",
                                time = "2 hours ago",
                                icon = Icons.Default.Person
                            )
                            Divider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = ClickColors.Divider
                            )
                            RecentActivityItem(
                                title = "Vibe Check with Mike",
                                location = "Tech Conference, San Francisco",
                                time = "Yesterday",
                                icon = Icons.Default.Star
                            )
                            Divider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = ClickColors.Divider
                            )
                            RecentActivityItem(
                                title = "Reconnected with Alex",
                                location = "Park Meetup",
                                time = "3 days ago",
                                icon = Icons.Default.Refresh
                            )
                        }
                    }
                }
            }

            // Bottom spacing
            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RecentActivityItem(
    title: String,
    location: String,
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = ClickColors.Primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Medium,
                color = ClickColors.TextPrimary
            )
            Text(
                text = location,
                style = MaterialTheme.typography.caption,
                color = ClickColors.TextSecondary
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.caption,
            color = ClickColors.TextTertiary
        )
    }
}
