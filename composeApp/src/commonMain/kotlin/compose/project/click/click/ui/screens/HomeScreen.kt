package compose.project.click.click.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveButton
import compose.project.click.click.ui.components.GlassCard
import compose.project.click.click.ui.components.GlassCardCompact
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.components.OnlineFriendItem
import compose.project.click.click.ui.components.RecentClickCard
import compose.project.click.click.ui.components.StatCard
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.viewmodel.HomeViewModel
import compose.project.click.click.viewmodel.HomeState
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionInsights
import compose.project.click.click.data.models.ReconnectReminder
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

// Spacing constants matching web's px-6 md:px-12 (converted to dp)
private val ScreenPaddingHorizontal = 24.dp
private val CardSpacing = 24.dp

/**
 * Creates a gradient brush for section headers
 * Matches web's text-gradient effect (White to Zinc-400)
 */
@Composable
private fun headerGradientBrush() = Brush.horizontalGradient(
    colors = listOf(GradientTextStart, GradientTextEnd)
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel { HomeViewModel() },
    onNavigateToChat: (String) -> Unit = {}
) {
    val homeState by viewModel.homeState.collectAsState()
    val reconnectReminders by viewModel.reconnectReminders.collectAsState()
    val connectionInsights by viewModel.connectionInsights.collectAsState()
    val showInsightsPanel by viewModel.showInsightsPanel.collectAsState()

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Apply deep dark background (Zinc-950)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when (val state = homeState) {
            is HomeState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }
            is HomeState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ScreenPaddingHorizontal),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Error loading home data",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceDark.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue
                        )
                    ) {
                        Text("Retry")
                    }
                }
            }
            is HomeState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.padding(
                            start = ScreenPaddingHorizontal,
                            top = topInset,
                            end = ScreenPaddingHorizontal
                        )
                    ) {
                        PageHeader(
                            title = "Home",
                            subtitle = "Welcome back, ${state.user.name ?: "User"}!"
                        )
                    }
                    Spacer(modifier = Modifier.height(CardSpacing))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ScreenPaddingHorizontal,
                            end = ScreenPaddingHorizontal,
                            bottom = ScreenPaddingHorizontal
                        ),
                        verticalArrangement = Arrangement.spacedBy(CardSpacing)
                    ) {
                        // Recent Connections Section
                        if (state.stats.recentConnections.isNotEmpty()) {
                            item {
                                GradientSectionHeader(text = "Recent Connections")
                            }

                            items(state.stats.recentConnections) { connection ->
                                ConnectionCard(connection, state.user.id)
                            }
                        } else {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.TouchApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = PrimaryBlue
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "No Connections Yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurfaceDark
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Start making connections by tapping Add Click",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = OnSurfaceDark.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Reconnect Reminders Section
                        if (reconnectReminders.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                GradientSectionHeader(text = "Reconnect")
                                Text(
                                    "Connections you haven't talked to in a while",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDark.copy(alpha = 0.6f)
                                )
                            }
                            
                            items(reconnectReminders) { reminder ->
                                ReconnectReminderCard(
                                    reminder = reminder,
                                    onReconnect = { onNavigateToChat(reminder.connectionId) },
                                    onDismiss = { viewModel.dismissReminder(reminder.connectionId) }
                                )
                            }
                        }
                        
                        // Connection Insights Button
                        if (connectionInsights != null && state.stats.totalConnections > 0) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                ConnectionInsightsCard(
                                    insights = connectionInsights!!,
                                    expanded = showInsightsPanel,
                                    onToggle = { viewModel.toggleInsightsPanel() }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            GradientSectionHeader(text = "Your Stats")
                        }

                        item {
                            // Stats Grid using Glass Cards
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GlassStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Check,
                                    value = state.stats.totalConnections.toString(),
                                    label = "Total Clicks"
                                )
                                GlassStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.LocationOn,
                                    value = state.stats.uniqueLocations.toString(),
                                    label = "Locations"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section header with gradient text effect
 * Matches web's text-gradient (White to Zinc-400)
 */
@Composable
private fun GradientSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium.merge(
            TextStyle(brush = headerGradientBrush())
        ),
        fontWeight = FontWeight.Bold
    )
}

/**
 * Stat card using glass aesthetic
 */
@Composable
private fun GlassStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    GlassCard(
        modifier = modifier,
        usePrimaryBorder = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = PrimaryBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDark.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ConnectionCard(connection: Connection, currentUserId: String) {
    val otherUserId = connection.user_ids.firstOrNull { it != currentUserId }

    val instant = Instant.fromEpochMilliseconds(connection.created)
    val now = kotlinx.datetime.Clock.System.now()
    val duration = (now.toEpochMilliseconds() - connection.created).milliseconds

    val timeAgo = when {
        duration.inWholeMinutes < 1 -> "Just now"
        duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
        duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
        duration.inWholeDays < 7 -> "${duration.inWholeDays}d ago"
        else -> {
            val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${dateTime.month.name.take(3)} ${dateTime.dayOfMonth}"
        }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { /* Navigate to connection details */ }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glowing Icon with radial gradient
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(PrimaryBlue.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                )
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    connection.semantic_location ?: "Connection",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = OnSurfaceDark.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        timeAgo,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDark.copy(alpha = 0.6f)
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "View details",
                tint = OnSurfaceDark.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Card for displaying a reconnect reminder - Glass styled
 */
@Composable
fun ReconnectReminderCard(
    reminder: ReconnectReminder,
    onReconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        usePrimaryBorder = true,
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User avatar placeholder
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(20.dp),
                color = PrimaryBlue.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        reminder.userName?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LightBlue
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.userName ?: "Someone",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceDark
                )
                Text(
                    "${reminder.daysSinceContact} days since last chat",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDark.copy(alpha = 0.6f)
                )
            }
            
            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(18.dp),
                        tint = OnSurfaceDark.copy(alpha = 0.6f)
                    )
                }
                Button(
                    onClick = onReconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Say Hi", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Expandable card for displaying connection insights - Glass styled
 */
@Composable
fun ConnectionInsightsCard(
    insights: ConnectionInsights,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        usePrimaryBorder = true
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Connection Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = OnSurfaceDark.copy(alpha = 0.7f)
                )
            }
            
            // Quick stats row (always visible)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InsightStat(
                    value = "${insights.keepRate.toInt()}%",
                    label = "Keep Rate"
                )
                InsightStat(
                    value = insights.activeConnections.toString(),
                    label = "Active"
                )
                InsightStat(
                    value = insights.dormantConnections.toString(),
                    label = "Need Attention"
                )
            }
            
            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InsightRow(
                        icon = Icons.Filled.Group,
                        label = "Total Connections",
                        value = insights.totalConnections.toString()
                    )
                    
                    InsightRow(
                        icon = Icons.Filled.Favorite,
                        label = "Connections Kept",
                        value = insights.keptConnections.toString()
                    )
                    
                    if (insights.longestConnectionDays > 0) {
                        InsightRow(
                            icon = Icons.Filled.AccessTime,
                            label = "Longest Connection",
                            value = "${insights.longestConnectionDays} days${insights.longestConnectionName?.let { " ($it)" } ?: ""}"
                        )
                    }
                    
                    InsightRow(
                        icon = Icons.Filled.CalendarToday,
                        label = "New This Week",
                        value = insights.connectionsThisWeek.toString()
                    )
                    
                    InsightRow(
                        icon = Icons.Filled.DateRange,
                        label = "New This Month",
                        value = insights.connectionsThisMonth.toString()
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightStat(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceDark.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun InsightRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = PrimaryBlue
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDark.copy(alpha = 0.8f)
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = OnSurfaceDark
        )
    }
}
