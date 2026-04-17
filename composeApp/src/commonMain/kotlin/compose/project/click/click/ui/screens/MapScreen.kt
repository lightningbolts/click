package compose.project.click.click.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.components.AdaptiveButton
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.LiquidGlassPill
import compose.project.click.click.ui.components.PlatformMap
import compose.project.click.click.ui.components.MapPin
import compose.project.click.click.ui.components.MapClusterPin
import compose.project.click.click.ui.components.toClusterPin
import compose.project.click.click.ui.components.ProfileBottomSheet
import compose.project.click.click.ui.components.ProfileSheetBadge
import compose.project.click.click.ui.components.ProfileSheetState
import compose.project.click.click.ui.components.ProfileSheetTimelineItem
import androidx.compose.ui.graphics.graphicsLayer
import compose.project.click.click.ui.utils.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.viewmodel.MapViewModel
import compose.project.click.click.viewmodel.MapState
import compose.project.click.click.viewmodel.MapSelection
import compose.project.click.click.data.models.User
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator

/**
 * Map screen — Phase 2 refactor (B1, C10, C11):
 *
 *  * Full-bleed [PlatformMap] that reaches every edge — no header, no rounded corners, no
 *    side padding. The map itself _is_ the screen.
 *  * [LiquidGlassPill] top-left overlay that surfaces the memories / live count in Material 3
 *    "Liquid Glass" styling. Replaces the old [PageHeader] + top-right stats chip.
 *  * GhostMode FAB is gone — the toggle now lives in Settings (per directive Q5). Ghost mode
 *    state itself still flows from the view model so tinting/snackbars remain correct.
 *  * The memories list was extracted into [MemoriesListSection] and is consumed from the
 *    connections-nav tab + profile sheet Timeline subtab instead of cluttering the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel { MapViewModel() },
    onNavigateToChat: ((String) -> Unit)? = null,
) {
    val mapState by viewModel.mapState.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val renderData by viewModel.renderData.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val ghostModeEnabled by viewModel.ghostModeEnabled.collectAsState()
    val cameraTarget by viewModel.cameraTarget.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onMapScreenEntered()
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(ghostModeEnabled) {
        if (ghostModeEnabled) {
            snackbarHostState.showSnackbar(
                message = "You are off the grid",
                duration = SnackbarDuration.Short,
            )
        }
    }

    val nudgeResult by viewModel.nudgeResult.collectAsState()
    LaunchedEffect(nudgeResult) {
        nudgeResult?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearNudgeResult()
        }
    }

    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(selection) {
        showBottomSheet = selection is MapSelection.ConnectionSelected
    }

    // C14: Parallax — when the ProfileBottomSheet is revealed, drift the map surface
    // upward a few dozen dp so the tapped pin remains visible above the sheet and the
    // whole view gains a subtle depth effect. Kept intentionally small (-56.dp) to
    // avoid fighting the map's own gesture handling.
    val parallaxOffset by animateFloatAsState(
        targetValue = if (showBottomSheet) -56f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "profile_sheet_map_parallax",
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        val grayscaleModifier = if (ghostModeEnabled) Modifier.alpha(0.7f) else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(grayscaleModifier)
                .graphicsLayer { translationY = parallaxOffset }
                .background(
                    if (ghostModeEnabled) Color.DarkGray.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface,
                ),
        ) {
            when (val state = mapState) {
                is MapState.Loading -> LoadingState()
                is MapState.Error -> ErrorState(message = state.message, onRetry = { viewModel.refresh() })
                is MapState.Success -> {
                    MapContent(
                        renderData = renderData,
                        zoom = zoomLevel,
                        ghostMode = ghostModeEnabled,
                        cameraTarget = cameraTarget,
                        onPinTapped = { pin ->
                            val points = when (val rd = renderData) {
                                is MapRenderData.IndividualPins -> rd.points
                                is MapRenderData.Clusters -> rd.clusters.flatMap { it.points }
                            }
                            points.find { it.connection.id == pin.id }?.let {
                                viewModel.onConnectionTapped(it)
                            }
                        },
                        onClusterTapped = { clusterPin ->
                            val clusters = (renderData as? MapRenderData.Clusters)?.clusters
                                ?: return@MapContent
                            clusters.find { it.id == clusterPin.id }?.let {
                                viewModel.onClusterTapped(it)
                            }
                        },
                        onZoomChanged = { viewModel.setZoomLevel(it) },
                        onVisibleBoundsChanged = { minLat, maxLat, minLon, maxLon ->
                            viewModel.updateVisibleBounds(minLat, maxLat, minLon, maxLon)
                        },
                        onCameraAnimationComplete = { viewModel.onCameraAnimationComplete() },
                    )

                    // Liquid Glass memories pill — replaces the old PageHeader + stats chip.
                    // Directive: sit closer to the top safe area (was topInset + 12.dp
                    // which felt too low on notched devices).
                    val stats = viewModel.getMapStats()
                    LiquidGlassPill(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 16.dp, top = topInset + 4.dp),
                    ) {
                        MemoriesPillContent(
                            memories = stats.totalConnections,
                            liveCount = stats.liveCount,
                            ghostMode = ghostModeEnabled,
                        )
                    }

                    ZoomIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 16.dp, top = topInset + 4.dp),
                        zoomLevel = zoomLevel,
                        showingClusters = renderData is MapRenderData.Clusters,
                    )

                    ZoomControls(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 24.dp),
                        onZoomIn = { viewModel.zoomIn() },
                        onZoomOut = { viewModel.zoomOut() },
                    )
                }
            }
        }
    }

    if (showBottomSheet && selection is MapSelection.ConnectionSelected) {
        val connectionSelection = selection as MapSelection.ConnectionSelected
        val viewerUserId = compose.project.click.click.data.AppDataManager
            .currentUser.collectAsState().value?.id
        val sheetData = remember(connectionSelection, viewerUserId) {
            buildProfileSheetState(connectionSelection, viewerUserId)
        }
        AdaptiveBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                viewModel.clearSelection()
            },
            adaptiveSheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            ProfileBottomSheet(
                state = sheetData,
                onMessage = {
                    showBottomSheet = false
                    viewModel.clearSelection()
                    onNavigateToChat?.invoke(connectionSelection.point.connection.id)
                },
                onNudge = {
                    viewModel.sendNudge(
                        connectionId = connectionSelection.point.connection.id,
                        otherUserName = connectionSelection.otherUser?.name ?: "Someone",
                    )
                    showBottomSheet = false
                    viewModel.clearSelection()
                },
            )
        }
    }
}

/**
 * Shapes a [MapSelection.ConnectionSelected] into the data the shared
 * [ProfileBottomSheet] renders. Media / Links / Files tabs are seeded empty — C15
 * (not in this phase) plumbs the message-history query that populates them. The
 * Timeline tab always has at least one row: the connection event itself.
 */
private fun buildProfileSheetState(
    sel: MapSelection.ConnectionSelected,
    viewerUserId: String?,
): ProfileSheetState {
    val otherUser = sel.otherUser
    val point = sel.point
    val displayName = otherUser?.name?.takeIf { it.isNotBlank() }
        ?: "Connection"
    val status = when (point.timeState) {
        TimeState.LIVE -> ProfileSheetBadge("Live now", PrimaryBlue)
        TimeState.RECENT -> ProfileSheetBadge("Recent", LightBlue)
        TimeState.ARCHIVE -> ProfileSheetBadge("Memory", Color.Gray)
    }
    val timelineSeed = listOf(
        ProfileSheetTimelineItem(
            id = "conn-${point.connection.id}",
            title = "Met at ${point.displayName}",
            subtitle = point.connection.contextTagId,
            timestamp = point.formattedDate,
        ),
    )
    return ProfileSheetState(
        displayName = displayName,
        subtitle = point.displayName,
        avatarUrl = otherUser?.image,
        statusBadge = status,
        canNudge = point.timeState == TimeState.LIVE || point.timeState == TimeState.RECENT,
        timeline = timelineSeed,
        media = emptyList(),
        links = emptyList(),
        files = emptyList(),
        userId = otherUser?.id,
        viewerUserId = viewerUserId,
    )
}

@Composable
private fun MemoriesPillContent(
    memories: Int,
    liveCount: Int,
    ghostMode: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ghostMode) Icons.Filled.LocationOff else Icons.Filled.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (ghostMode) Color.Gray else PrimaryBlue,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (ghostMode) {
                "Ghost Mode"
            } else {
                "$memories ${if (memories == 1) "memory" else "memories"}"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!ghostMode && liveCount > 0) {
            Spacer(modifier = Modifier.width(10.dp))
            LiveIndicator(count = liveCount)
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
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = alpha)),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "$count Live",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryBlue,
        )
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
    onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit,
    onCameraAnimationComplete: () -> Unit,
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
        onZoomChanged = onZoomChanged,
        onVisibleBoundsChanged = onVisibleBoundsChanged,
        onCameraAnimationComplete = onCameraAnimationComplete,
    )
}

@Composable
private fun ZoomControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AdaptiveButton(onClick = onZoomIn, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Zoom in")
        }
        AdaptiveButton(onClick = onZoomOut, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
        }
    }
}

@Composable
private fun ZoomIndicator(
    modifier: Modifier = Modifier,
    zoomLevel: Double,
    showingClusters: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (showingClusters) Icons.Filled.Hub else Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (showingClusters) "Hubs" else "People",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AdaptiveCircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Error loading map",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Connection Marker Bottom Sheet — shown when a pin is tapped.
 */
@Composable
fun ConnectionMarkerSheet(
    point: ConnectionMapPoint,
    otherUser: User?,
    onMessage: (String) -> Unit,
    onNudge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetBg = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(sheetBg)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                        },
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (point.shouldPulse) {
                PulsingRing()
            }
            Text(
                otherUser?.name?.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = LightBlue.copy(alpha = 0.96f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            otherUser?.name ?: "Connection",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Met at ${point.displayName}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            point.formattedDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        MarkerSheetTimeStateBadge(timeState = point.timeState)

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isActive = point.connection.should_continue.contains(true)
            val hasChat = point.connection.has_begun

            if (hasChat || isActive) {
                Button(
                    onClick = { onMessage(point.connection.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                ) {
                    Icon(Icons.Filled.Message, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }
            }

            if (point.timeState == TimeState.LIVE || point.timeState == TimeState.RECENT) {
                OutlinedButton(onClick = onNudge, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nudge")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Spacer(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun MarkerSheetTimeStateBadge(timeState: TimeState) {
    val (color, label, icon) = when (timeState) {
        TimeState.LIVE -> Triple(PrimaryBlue, "Live Now", Icons.Filled.Bolt)
        TimeState.RECENT -> Triple(LightBlue, "Recent", Icons.Filled.AccessTime)
        TimeState.ARCHIVE -> Triple(Color.Gray, "Memory", Icons.Filled.History)
    }

    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.1f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
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
            repeatMode = RepeatMode.Restart,
        ),
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Box(
        modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .border(3.dp, PrimaryBlue.copy(alpha = alpha), CircleShape),
    )
}
