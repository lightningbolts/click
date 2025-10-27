package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveButton
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.components.SectionTitle

@Composable
fun HomeScreen() {
    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                PageHeader(
                    title = "Hi Bob!",
                    subtitle = "Who are you clicking with today?"
                )
            }

            item {
                // Online Friends - Click Prompts using AdaptiveCard
                AdaptiveCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Online Friends",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Online friends list
                        OnlineFriendItem("Alice", "Available to click!")
                        OnlineFriendItem("Charlie", "Free now")
                        OnlineFriendItem("Diana", "Let's connect!")
                    }
                }
            }

            item {
                // Recent Clicks Section
                SectionTitle(
                    text = "Recent Clicks",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            items(3) { index ->
                RecentClickCard(
                    name = listOf("Alice", "Charlie", "Diana")[index],
                    time = listOf("2h ago", "Yesterday", "3d ago")[index],
                    location = listOf("Coffee Shop", "Park", "Downtown")[index]
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle(
                    text = "Your Stats",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item {
                // Stats Grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Check,
                        value = "47",
                        label = "Total Clicks"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.DateRange,
                        value = "12",
                        label = "This Week"
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.LocationOn,
                        value = "8",
                        label = "Locations"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Person,
                        value = "15",
                        label = "Connections"
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineFriendItem(name: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.first().toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AdaptiveButton(
            onClick = { },
            modifier = Modifier,
            content = {
                Text("Click", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            }
        )
    }
}

@Composable
fun RecentClickCard(name: String, time: String, location: String) {
    AdaptiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.first().toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Clicked with $name",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "$location • $time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    AdaptiveCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
