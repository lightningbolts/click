package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveButton
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.components.Clicktivity
import compose.project.click.click.ui.components.ClicktivityCard
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars

@Composable
fun ClicktivitiesScreen() {
    val clicktivities = remember {
        listOf(
            Clicktivity(
                "McDonald's Together",
                "Order food together and enjoy a meal with your click",
                Icons.Filled.Fastfood,
                "$15-30",
                "Food"
            ),
            Clicktivity(
                "Movie Night",
                "Watch the latest movies together at nearby theaters",
                Icons.Filled.Movie,
                "$20-40",
                "Entertainment"
            ),
            Clicktivity(
                "Baseball Game",
                "Catch a game with your favorite click buddies",
                Icons.Filled.SportsBaseball,
                "$50-150",
                "Sports"
            ),
            Clicktivity(
                "Concert Tickets",
                "AI-matched concerts based on your music taste",
                Icons.Filled.MusicNote,
                "$40-200",
                "Music"
            ),
            Clicktivity(
                "Coffee Meetup",
                "Grab coffee at a recommended local spot",
                Icons.Filled.LocalCafe,
                "$5-15",
                "Food"
            ),
            Clicktivity(
                "Adventure Park",
                "Theme parks and adventure activities nearby",
                Icons.Filled.Attractions,
                "$60-120",
                "Adventure"
            ),
        )
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                PageHeader(title = "Clicktivities", subtitle = "AI-powered activities for your clicks")
            }
            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Coming after MVP • 2% transaction fee",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                items(clicktivities) { activity ->
                    ClicktivityCard(activity)
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
