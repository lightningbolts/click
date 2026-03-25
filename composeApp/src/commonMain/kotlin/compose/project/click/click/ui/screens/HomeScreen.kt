package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.rotate
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
import compose.project.click.click.ui.components.PollPairCard
import compose.project.click.click.ui.components.RecentClickCard
import compose.project.click.click.ui.components.StatCard
import compose.project.click.click.ui.components.getAdaptiveCornerRadius
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.viewmodel.HomeViewModel
import compose.project.click.click.viewmodel.HomeState
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionInsights
import compose.project.click.click.data.models.ReconnectReminder
import compose.project.click.click.data.models.User
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator

// Spacing constants matching app's consistent 20.dp horizontal padding
private val ScreenPaddingHorizontal = 20.dp
private val CardSpacing = 24.dp

/**
 * Creates a gradient brush for section headers
 * Matches web's text-gradient effect (White to Zinc-400)
 */
@Composable
private fun headerGradientBrush() = Brush.horizontalGradient(
    colors = listOf(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.onSurfaceVariant
    )
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
    val locationGroupedConnections by viewModel.locationGroupedConnections.collectAsState()
    val expandedLocations by viewModel.expandedLocations.collectAsState()
    val connectedUsers by viewModel.connectedUsers.collectAsState()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val pollPairSuggestion by viewModel.pollPairSuggestion.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show nudge feedback as a snackbar
    LaunchedEffect(nudgeResult) {
        val result = nudgeResult
        if (result != null) {
            scope.launch { snackbarHostState.showSnackbar(result) }
            viewModel.clearNudgeResult()
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Apply deep dark background (Zinc-950)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = homeState) {
            is HomeState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AdaptiveCircularProgressIndicator(color = PrimaryBlue)
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                        pollPairSuggestion?.let { suggestion ->
                            item(key = "poll_pair_card") {
                                PollPairCard(
                                    suggestion = suggestion,
                                    onOpenChat = { onNavigateToChat(suggestion.connectionId) },
                                    onSendIcebreaker = { viewModel.sendPollPairIcebreaker(suggestion) }
                                )
                            }
                        }

                        // Recent Connections Section — grouped by location
                        if (locationGroupedConnections.isNotEmpty()) {
                            item {
                                GradientSectionHeader(text = "Recent Connections")
                            }
                            items(locationGroupedConnections.entries.toList()) { (location, connections) ->
                                val isExpanded = location in expandedLocations
                                LocationGroupCard(
                                    location = location,
                                    connections = connections,
                                    isExpanded = isExpanded,
                                    connectedUsers = connectedUsers,
                                    currentUserId = state.user.id,
                                    onToggleExpand = { viewModel.toggleLocationExpanded(location) },
                                    onNavigateToChat = onNavigateToChat,
                                    onNudge = { connectionId, otherUserName ->
                                        viewModel.sendNudgeByConnectionId(connectionId, otherUserName)
                                    }
                                )
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
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Start making connections by tapping Add Click",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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

        // Snackbar for nudge feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
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
        style = MaterialTheme.typography.titleLarge.merge(
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * An expandable card grouping all connections made at a single semantic location.
 * Tapping the header toggles the list of individual connections open/closed.
 */
@Composable
private fun LocationGroupCard(
    location: String,
    connections: List<Connection>,
    isExpanded: Boolean,
    connectedUsers: Map<String, User>,
    currentUserId: String,
    onToggleExpand: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNudge: (connectionId: String, otherUserName: String) -> Unit
) {
    val chevronAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "chevron"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggleExpand,
        usePrimaryBorder = isExpanded
    ) {
        Column {
            // Group header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Location pin icon with glow
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(PrimaryBlue.copy(alpha = 0.35f), Color.Transparent)
                            ),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        location,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${connections.size} connection${if (connections.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Count badge
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(PrimaryBlue, LightBlue)),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        connections.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(chevronAngle),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }

            // Expanded individual connections
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        color = GlassBorder,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    connections.forEach { connection ->
                        val otherUserId = connection.user_ids.firstOrNull { it != currentUserId }
                        val otherUser = otherUserId?.let { connectedUsers[it] }
                        ConnectionRowItem(
                            connection = connection,
                            otherUser = otherUser,
                            currentUserId = currentUserId,
                            onNavigate = { onNavigateToChat(connection.id) },
                            onNudge = {
                                onNudge(connection.id, otherUser?.name ?: "them")
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual connection row rendered inside an expanded LocationGroupCard.
 */
@Composable
private fun ConnectionRowItem(
    connection: Connection,
    otherUser: User?,
    currentUserId: String,
    onNavigate: () -> Unit,
    onNudge: () -> Unit
) {
    val duration = (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - connection.created).milliseconds
    val timeAgo = when {
        duration.inWholeMinutes < 1 -> "Just now"
        duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
        duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
        duration.inWholeDays < 7 -> "${duration.inWholeDays}d ago"
        else -> {
            val dt = Instant.fromEpochMilliseconds(connection.created)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            "${dt.month.name.take(3)} ${dt.dayOfMonth}"
        }
    }
    val displayName = otherUser?.name ?: "Connection"

    val rowStyle = LocalPlatformStyle.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (rowStyle.isIOS) 14.dp else 12.dp))
            .clickable { onNavigate() }
            .background(Color.White.copy(alpha = rowStyle.glassBackgroundAlpha))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(PrimaryBlue, LightBlue))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                displayName.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                timeAgo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }

        // Nudge button
        IconButton(
            onClick = onNudge,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = "Nudge",
                modifier = Modifier.size(18.dp),
                tint = PrimaryBlue.copy(alpha = 0.85f)
            )
        }

        // Chat button
        IconButton(
            onClick = onNavigate,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Chat,
                contentDescription = "Open chat",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        timeAgo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    val style = LocalPlatformStyle.current
    val actionShape = RoundedCornerShape(if (style.isIOS) 12.dp else 14.dp)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        usePrimaryBorder = true,
        contentPadding = 14.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(22.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reminder.userName ?: "Someone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${reminder.daysSinceContact} days since last chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = actionShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onReconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = actionShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Say hi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
