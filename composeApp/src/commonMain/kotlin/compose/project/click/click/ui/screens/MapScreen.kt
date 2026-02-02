package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveButton
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.PlatformMap
import compose.project.click.click.ui.components.MapPin
import compose.project.click.click.ui.components.MapClusterPin
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.components.toClusterPin
import compose.project.click.click.ui.utils.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.viewmodel.MapViewModel
import compose.project.click.click.viewmodel.MapState
import compose.project.click.click.viewmodel.MapSelection
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel { MapViewModel() },
    onNavigateToChat: ((String) -> Unit)? = null
) {
    val mapState by viewModel.mapState.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val renderData by viewModel.renderData.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val ghostModeEnabled by viewModel.ghostModeEnabled.collectAsState()
    val cameraTarget by viewModel.cameraTarget.collectAsState()

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    
    // Snackbar state for ghost mode notification
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show snackbar when ghost mode changes
    LaunchedEffect(ghostModeEnabled) {
        if (ghostModeEnabled) {
            snackbarHostState.showSnackbar(
                message = "You are off the grid",
                duration = SnackbarDuration.Short
            )
        }
    }

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    
    // Update sheet visibility based on selection
    LaunchedEffect(selection) {
        showBottomSheet = selection is MapSelection.ConnectionSelected
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            GhostModeFAB(
                isEnabled = ghostModeEnabled,
                onClick = { viewModel.toggleGhostMode() }
            )
        }
    ) { paddingValues ->
        // Apply grayscale filter when ghost mode is enabled
        val grayscaleModifier = if (ghostModeEnabled) {
            Modifier.alpha(0.7f)
        } else {
            Modifier
        }

        AdaptiveBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(grayscaleModifier)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with stats
                Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                    val stats = viewModel.getMapStats()
                    when (val state = mapState) {
                        is MapState.Success -> {
                            MapHeader(
                                totalConnections = stats.totalConnections,
                                liveCount = stats.liveCount,
                                ghostMode = ghostModeEnabled
                            )
                        }
                        else -> {
                            PageHeader(title = "Memory Map", subtitle = "Loading...")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Map container
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    val mapShape = RoundedCornerShape(20.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp)
                            .clip(mapShape)
                            .border(
                                1.dp,
                                if (ghostModeEnabled) Color.Gray.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant,
                                mapShape
                            )
                            .background(
                                if (ghostModeEnabled) Color.DarkGray.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surface
                            )
                    ) {
                        when (val state = mapState) {
                            is MapState.Loading -> {
                                LoadingState()
                            }
                            is MapState.Error -> {
                                ErrorState(message = state.message, onRetry = { viewModel.refresh() })
                            }
                            is MapState.Success -> {
                                MapContent(
                                    renderData = renderData,
                                    zoom = zoomLevel,
                                    ghostMode = ghostModeEnabled,
                                    cameraTarget = cameraTarget,
                                    onPinTapped = { pin ->
                                        // Find the connection point for this pin
                                        val points = when (val rd = renderData) {
                                            is MapRenderData.IndividualPins -> rd.points
                                            is MapRenderData.Clusters -> rd.clusters.flatMap { it.points }
                                        }
                                        points.find { it.connection.id == pin.id }?.let {
                                            viewModel.onConnectionTapped(it)
                                        }
                                    },
                                    onClusterTapped = { clusterPin ->
                                        val clusters = (renderData as? MapRenderData.Clusters)?.clusters ?: return@MapContent
                                        clusters.find { it.id == clusterPin.id }?.let {
                                            viewModel.onClusterTapped(it)
                                        }
                                    },
                                    onZoomChanged = { viewModel.setZoomLevel(it) },
                                    onCameraAnimationComplete = { viewModel.onCameraAnimationComplete() }
                                )

                                // Zoom controls - extra bottom padding to avoid overlap with Ghost Mode FAB
                                ZoomControls(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 16.dp, bottom = 80.dp),
                                    onZoomIn = { viewModel.zoomIn() },
                                    onZoomOut = { viewModel.zoomOut() }
                                )

                                // Stats overlay
                                MapStatsOverlay(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp),
                                    renderData = renderData
                                )

                                // Zoom level indicator
                                ZoomIndicator(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp),
                                    zoomLevel = zoomLevel,
                                    showingClusters = renderData is MapRenderData.Clusters
                                )
                            }
                        }
                    }
                }

                // Time legend
                TimeLegend(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )

                // Connections list
                ConnectionsList(
                    mapState = mapState,
                    onConnectionClick = { point ->
                        viewModel.onConnectionTapped(point)
                    },
                    onRefresh = { viewModel.refresh() }
                )
            }
        }
    }

    // Connection Bottom Sheet
    if (showBottomSheet && selection is MapSelection.ConnectionSelected) {
        val connectionSelection = selection as MapSelection.ConnectionSelected
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                viewModel.clearSelection()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            ConnectionMarkerSheet(
                point = connectionSelection.point,
                otherUser = connectionSelection.otherUser,
                onMessage = { connectionId ->
                    showBottomSheet = false
                    viewModel.clearSelection()
                    onNavigateToChat?.invoke(connectionId)
                },
                onNudge = { /* TODO: Implement nudge */ },
                onDismiss = {
                    showBottomSheet = false
                    viewModel.clearSelection()
                }
            )
        }
    }
}

@Composable
private fun MapHeader(
    totalConnections: Int,
    liveCount: Int,
    ghostMode: Boolean
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (ghostMode) "Ghost Mode" else "Memory Map",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (ghostMode) Color.Gray else MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (ghostMode) "You are hidden" 
                    else "$totalConnections memories • $liveCount live",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (liveCount > 0 && !ghostMode) {
                LiveIndicator(count = liveCount)
            }
        }
    }
}

@Composable
private fun LiveIndicator(count: Int) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PrimaryBlue.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "$count Live",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryBlue
            )
        }
    }
}

@Composable
private fun MapContent(
    renderData: MapRenderData,
    zoom: Double,
    ghostMode: Boolean,
    cameraTarget: compose.project.click.click.viewmodel.CameraTarget?,
    onPinTapped: (MapPin) -> Unit,
    onClusterTapped: (MapClusterPin) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onCameraAnimationComplete: () -> Unit
) {
    val pins = when (renderData) {
        is MapRenderData.IndividualPins -> renderData.points.map { MapPin.fromConnectionPoint(it) }
        is MapRenderData.Clusters -> emptyList()
    }
    
    val clusters = when (renderData) {
        is MapRenderData.Clusters -> renderData.clusters.map { it.toClusterPin() }
        is MapRenderData.IndividualPins -> emptyList()
    }

    PlatformMap(
        modifier = Modifier.fillMaxSize(),
        pins = pins,
        clusters = clusters,
        zoom = zoom,
        centerLat = cameraTarget?.latitude,
        centerLon = cameraTarget?.longitude,
        ghostMode = ghostMode,
        onPinTapped = onPinTapped,
        onClusterTapped = onClusterTapped,
        onZoomChanged = onZoomChanged
    )
}

@Composable
private fun ZoomControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AdaptiveButton(
            onClick = onZoomIn,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Zoom in")
        }
        AdaptiveButton(
            onClick = onZoomOut,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
        }
    }
}

@Composable
private fun MapStatsOverlay(
    modifier: Modifier = Modifier,
    renderData: MapRenderData
) {
    val count = when (renderData) {
        is MapRenderData.IndividualPins -> renderData.points.size
        is MapRenderData.Clusters -> renderData.clusters.sumOf { it.count }
    }

    AdaptiveCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "$count ${if (count == 1) "memory" else "memories"}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ZoomIndicator(
    modifier: Modifier = Modifier,
    zoomLevel: Double,
    showingClusters: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (showingClusters) Icons.Filled.Hub else Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (showingClusters) "Hubs" else "People",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem(
            color = PrimaryBlue,
            label = "Live (0-4h)",
            isPulsing = true
        )
        LegendItem(
            color = LightBlue,
            label = "Recent (24h)",
            isPulsing = false
        )
        LegendItem(
            color = Color.Gray.copy(alpha = 0.6f),
            label = "Archive",
            isPulsing = false
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isPulsing: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GhostModeFAB(
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = if (isEnabled) Color.DarkGray else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (isEnabled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(
            if (isEnabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (isEnabled) "Disable Ghost Mode" else "Enable Ghost Mode"
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
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
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ConnectionsList(
    mapState: MapState,
    onConnectionClick: (ConnectionMapPoint) -> Unit,
    onRefresh: () -> Unit
) {
    when (mapState) {
        is MapState.Success -> {
            if (mapState.connections.isEmpty()) {
                EmptyConnectionsState()
            } else {
                val points = mapState.connections.mapNotNull {
                    try { it.toMapPoint() } catch (e: Exception) { null }
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Your Memories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(points.sortedByDescending { it.connection.created }) { point ->
                        ConnectionLocationCard(
                            point = point,
                            onClick = { onConnectionClick(point) }
                        )
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

@Composable
private fun EmptyConnectionsState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Memories Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Make connections to build your memory map",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ConnectionLocationCard(
    point: ConnectionMapPoint,
    onClick: () -> Unit
) {
    val timeColor = when (point.timeState) {
        TimeState.LIVE -> PrimaryBlue
        TimeState.RECENT -> LightBlue
        TimeState.ARCHIVE -> Color.Gray
    }

    AdaptiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(point.opacity),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Location icon with time-based color
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(timeColor, timeColor.copy(alpha = 0.6f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Pulse animation for live connections
                if (point.shouldPulse) {
                    PulsingIndicator()
                }
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    point.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        point.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Time state badge
                TimeStateBadge(timeState = point.timeState)
            }
        }
    }
}

@Composable
private fun PulsingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .alpha(alpha)
            .background(PrimaryBlue.copy(alpha = 0.3f), CircleShape)
    )
}

@Composable
private fun TimeStateBadge(timeState: TimeState) {
    val (color, label, icon) = when (timeState) {
        TimeState.LIVE -> Triple(PrimaryBlue, "Live Now", Icons.Filled.Bolt)
        TimeState.RECENT -> Triple(LightBlue, "Recent", Icons.Filled.AccessTime)
        TimeState.ARCHIVE -> Triple(Color.Gray, "Memory", Icons.Filled.History)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

/**
 * Connection Marker Bottom Sheet
 * Shows detailed info when a specific pin is clicked
 */
@Composable
fun ConnectionMarkerSheet(
    point: ConnectionMapPoint,
    otherUser: User?,
    onMessage: (String) -> Unit,
    onNudge: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Profile photo placeholder
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = when (point.timeState) {
                            TimeState.LIVE -> listOf(PrimaryBlue, DeepBlue)
                            TimeState.RECENT -> listOf(LightBlue, PrimaryBlue)
                            TimeState.ARCHIVE -> listOf(Color.Gray, Color.DarkGray)
                        }
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing ring for live connections
            if (point.shouldPulse) {
                PulsingRing()
            }
            
            Text(
                otherUser?.name?.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // User name
        Text(
            otherUser?.name ?: "Connection",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Met at location and date
        Text(
            "Met at ${point.displayName}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            point.formattedDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Time state badge
        TimeStateBadge(timeState = point.timeState)

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Check if connection is active (either user wants to continue)
            val isActive = point.connection.should_continue.contains(true)
            val hasChat = point.connection.has_begun
            
            if (hasChat || isActive) {
                Button(
                    onClick = { onMessage(point.connection.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Icon(Icons.Filled.Message, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }
            }
            
            if (point.timeState == TimeState.LIVE || point.timeState == TimeState.RECENT) {
                OutlinedButton(
                    onClick = onNudge,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nudge")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PulsingRing() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .border(3.dp, PrimaryBlue.copy(alpha = alpha), CircleShape)
    )
}
