package compose.project.click.click.ui.screens // pragma: allowlist secret

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveButton // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveCard // pragma: allowlist secret
import compose.project.click.click.ui.components.LiquidGlassPill // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformMap // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapClusterPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPinKind // pragma: allowlist secret
import compose.project.click.click.ui.components.toClusterPin // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileSheetBadge // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileSheetState // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileSheetTimelineItem // pragma: allowlist secret
import androidx.compose.ui.graphics.graphicsLayer
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapState // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapSelection // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.media.rememberChatAudioPlayer // pragma: allowlist secret
import compose.project.click.click.openBeaconOriginalMediaUrl // pragma: allowlist secret
import coil3.compose.AsyncImage // pragma: allowlist secret
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.zIndex
import compose.project.click.click.getPlatform
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import compose.project.click.click.ui.utils.displayTypeTitle
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot

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
    val mapBindingZoom by viewModel.mapBindingZoom.collectAsState()
    val renderData by viewModel.renderData.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val ghostModeEnabled by viewModel.ghostModeEnabled.collectAsState()
    val cameraTarget by viewModel.cameraTarget.collectAsState()
    val layerFilters by viewModel.selectedLayerFilters.collectAsState()
    val beaconInsertError by viewModel.beaconInsertError.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onMapScreenEntered()
    }

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
    // C12 directive: explicit state variable that drives the new ProfileBottomSheet.
    // Pin taps update this directly (in addition to the view-model selection state) so
    // sheet visibility is decoupled from any race in the selection StateFlow.
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var showBeaconDropSheet by remember { mutableStateOf(false) }
    val showBottomSheet = selectedProfileId != null && selection is MapSelection.ConnectionSelected
    val showBeaconDetailSheet = selection is MapSelection.BeaconSelected

    LaunchedEffect(selection) {
        val sel = selection
        selectedProfileId = if (sel is MapSelection.ConnectionSelected) {
            sel.point.connection.id
        } else {
            null
        }
    }

    // C14: Parallax — when the ProfileBottomSheet is revealed, drift the map surface
    // upward a few dozen dp so the tapped pin remains visible above the sheet and the
    // whole view gains a subtle depth effect. Kept intentionally small (-56.dp) to
    // avoid fighting the map's own gesture handling.
    val parallaxOffset by animateFloatAsState(
        targetValue = if (showBottomSheet || showBeaconDetailSheet) -56f else 0f,
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
                        zoom = cameraTarget?.zoom ?: mapBindingZoom,
                        ghostMode = ghostModeEnabled,
                        cameraTarget = cameraTarget,
                        onPinTapped = { pin ->
                            if (pin.kind == MapPinKind.CONNECTION) {
                                selectedProfileId = pin.id
                            }
                            viewModel.onMapPinTapped(pin)
                        },
                        onClusterTapped = { clusterPin ->
                            viewModel.onClusterTappedFromMap(clusterPin.id)
                        },
                        onZoomChanged = { viewModel.setZoomLevel(it) },
                        onVisibleBoundsChanged = { minLat, maxLat, minLon, maxLon ->
                            viewModel.updateVisibleBounds(minLat, maxLat, minLon, maxLon)
                        },
                        onCameraAnimationComplete = { viewModel.onCameraAnimationComplete() },
                    )

                    // Liquid Glass memories pill — replaces the old PageHeader + stats chip.
                    // Directive: apply Modifier.windowInsetsPadding(safeDrawing.only(Top + Horizontal))
                    // so the pill always sits *below* the system status bar / notch instead of
                    // clipping into it on notched devices.
                    val stats = viewModel.getMapStats()
                    LiquidGlassPill(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 12.dp, start = 16.dp),
                    ) {
                        MemoriesPillContent(
                            memories = stats.totalConnections,
                            liveCount = stats.liveCount,
                            ghostMode = ghostModeEnabled,
                        )
                    }

                    // Bottom bar: narrow layer control (no weight(1) so it does not span under the FAB or zoom),
                    // add-location FAB, and zoom — single row to avoid map hit-stealing and overlap.
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .zIndex(4f)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MapLayerFilterDropdown(
                            selected = layerFilters,
                            onToggle = { viewModel.toggleLayerFilter(it) },
                        )
                        FloatingActionButton(
                            onClick = { showBeaconDropSheet = true },
                            modifier = Modifier.size(56.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(Icons.Filled.AddLocationAlt, contentDescription = "Drop beacon")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        ZoomControls(
                            onZoomIn = { viewModel.zoomIn() },
                            onZoomOut = { viewModel.zoomOut() },
                        )
                    }
                }
            }
        }
    }

    if (showBeaconDropSheet) {
        val dropSheetColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val onDropSheet = MaterialTheme.colorScheme.onSurface
        MapBeaconSheetRoot(
            visible = true,
            onDismissRequest = { showBeaconDropSheet = false },
            containerColor = dropSheetColor,
            contentColor = onDropSheet,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            modifier = Modifier,
        ) {
            MapDialogChrome(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                sheetColor = dropSheetColor,
                onSurface = onDropSheet,
            ) {
                BeaconDropSheetContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    errorMessage = beaconInsertError,
                    onDismissError = { viewModel.clearBeaconInsertError() },
                    onSubmit = { kind, text, ttlMs ->
                        viewModel.submitBeaconDrop(kind, text, ttlMs) { ok ->
                            if (ok) showBeaconDropSheet = false
                        }
                    },
                )
            }
        }
    }

    if (showBeaconDetailSheet && selection is MapSelection.BeaconSelected) {
        val beaconSel = selection as MapSelection.BeaconSelected
        val detailSurface = MaterialTheme.colorScheme.surfaceContainerHigh
        val onDetailSurface = MaterialTheme.colorScheme.onSurface
        MapBeaconSheetRoot(
            visible = true,
            onDismissRequest = { viewModel.clearSelection() },
            containerColor = detailSurface,
            contentColor = onDetailSurface,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            modifier = Modifier,
        ) {
            MapDialogChrome(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                sheetColor = detailSurface,
                onSurface = onDetailSurface,
            ) {
                BeaconDetailSheetContent(
                    beacon = beaconSel.beacon,
                    distanceMeters = beaconSel.distanceMeters,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
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
        val profileSheetColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val onProfileSheet = MaterialTheme.colorScheme.onSurface
        AdaptiveBottomSheet(
            onDismissRequest = {
                selectedProfileId = null
                viewModel.clearSelection()
            },
            adaptiveSheetState = sheetState,
            containerColor = profileSheetColor,
            contentColor = onProfileSheet,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            dragHandle = null,
        ) {
            MapDialogChrome(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                sheetColor = profileSheetColor,
                onSurface = onProfileSheet,
            ) {
                ProfileBottomSheet(
                    state = sheetData,
                    onMessage = {
                        selectedProfileId = null
                        viewModel.clearSelection()
                        onNavigateToChat?.invoke(connectionSelection.point.connection.id)
                    },
                    onNudge = {
                        viewModel.sendNudge(
                            connectionId = connectionSelection.point.connection.id,
                            otherUserName = connectionSelection.otherUser?.name ?: "Someone",
                        )
                        selectedProfileId = null
                        viewModel.clearSelection()
                    },
                )
            }
        }
    }
}

@Composable
private fun MapDialogChrome(
    modifier: Modifier = Modifier,
    sheetColor: Color,
    onSurface: Color,
    useGrabber: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(sheetColor),
    ) {
        if (useGrabber) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(onSurface.copy(alpha = 0.3f)),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
        ) {
            content()
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
        // Drives the BFF-owned Media / Files hydration inside [ProfileBottomSheet]
        // via `ConnectionRepository.fetchConnectionTabs`.
        connectionId = point.connection.id,
    )
}

/** One-line label for the compact map layer control. */
private fun mapLayerFilterShortLabel(selected: Set<MapLayerFilter>): String {
    if (MapLayerFilter.ALL in selected) return "All"
    val withoutAll = selected - MapLayerFilter.ALL
    if (withoutAll.isEmpty()) return "—"
    if (withoutAll.size == 1) {
        return when (val f = withoutAll.first()) {
            MapLayerFilter.MY_CONNECTIONS -> "Conn"
            MapLayerFilter.SOUNDTRACKS -> "Audio"
            MapLayerFilter.ALERTS_UTILITIES -> "Alerts"
            MapLayerFilter.SOCIAL_VIBES -> "Social"
            else -> f.label.take(6)
        }
    }
    return "${withoutAll.size} on"
}

/**
 * Native [DropdownMenu] from a liquid-glass style pill (iOS) / solid surface (Android).
 * Menu opens **upward** (negative offset) so it stays on-screen over the bottom bar, with a
 * fully opaque container for readable text.
 */
@Composable
private fun MapLayerFilterDropdown(
    selected: Set<MapLayerFilter>,
    onToggle: (MapLayerFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    val menuSurface = MaterialTheme.colorScheme.surface
    val onMenuSurface = MaterialTheme.colorScheme.onSurface
    val menuOutline = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val itemCount = MapLayerFilter.entries.size
    // ~48dp per row + padding — negative Y pulls the popup above the anchor.
    val menuUpOffset = (itemCount * 48 + 24).dp
    val menuWidth = 240.dp
    val triggerWidth = 120.dp

    Box(
        modifier = Modifier
            .widthIn(max = triggerWidth)
            .wrapContentWidth(Alignment.Start),
    ) {
        LiquidGlassPill(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 48.dp)
                .clickable { expanded = true },
            cornerRadiusDp = 20,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = mapLayerFilterShortLabel(selected),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Same horizontal origin as the pill; full-opacity surface for legibility.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(menuWidth)
                .wrapContentWidth(Alignment.Start),
            offset = DpOffset(0.dp, -menuUpOffset),
            shape = RoundedCornerShape(if (isIOS) 14.dp else 12.dp),
            containerColor = menuSurface,
            tonalElevation = if (isIOS) 0.dp else 2.dp,
            shadowElevation = if (isIOS) 0.dp else 8.dp,
            border = if (isIOS) {
                BorderStroke(0.5.dp, menuOutline)
            } else {
                null
            },
        ) {
            MapLayerFilter.entries.forEach { filter ->
                val isSelected = when (filter) {
                    MapLayerFilter.ALL -> MapLayerFilter.ALL in selected
                    else -> filter in selected
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            filter.label,
                            color = onMenuSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onToggle(filter)
                        expanded = false
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = onMenuSurface,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BeaconDetailSheetContent(
    beacon: MapBeacon,
    distanceMeters: Double?,
    modifier: Modifier = Modifier,
) {
    when (beacon.kind) {
        MapBeaconKind.SOUNDTRACK -> MusicPreviewCard(
            beacon = beacon,
            distanceMeters = distanceMeters,
            modifier = modifier,
        )
        else -> CommunityBeaconDetail(
            beacon = beacon,
            distanceMeters = distanceMeters,
            modifier = modifier,
        )
    }
}

@Composable
private fun CommunityBeaconDetail(
    beacon: MapBeacon,
    distanceMeters: Double?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = beacon.displayTypeTitle(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Created · ${formatBeaconInstant(beacon.createdAtEpochMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Expires · ${formatBeaconInstant(beacon.expiresAtEpochMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = beacon.metadata.description?.trim().orEmpty().ifBlank { "No description" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        distanceMeters?.let { d ->
            Text(
                text = formatBeaconDistance(d),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MusicPreviewCard(
    beacon: MapBeacon,
    distanceMeters: Double?,
    modifier: Modifier = Modifier,
) {
    val trackTitle = beacon.metadata.trackName ?: beacon.metadata.title ?: "Soundtrack"
    val artistLine = beacon.metadata.artistName ?: beacon.metadata.artist
    val art = beacon.metadata.albumArtUrl?.takeIf { it.isNotBlank() }
    val preview = beacon.metadata.previewUrl?.takeIf { it.isNotBlank() }
    val original = (beacon.metadata.originalUrl ?: beacon.metadata.musicUrl)?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = beacon.displayTypeTitle(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Created · ${formatBeaconInstant(beacon.createdAtEpochMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Expires · ${formatBeaconInstant(beacon.expiresAtEpochMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = trackTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!artistLine.isNullOrBlank()) {
                    Text(
                        text = artistLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (preview != null) {
            val player = rememberChatAudioPlayer(mediaUrl = preview, durationHintMs = 30_000L)
            var tick by remember(preview) { mutableIntStateOf(0) }
            var isDragging by remember(preview) { mutableStateOf(false) }
            var sliderPosition by remember(preview) { mutableFloatStateOf(0f) }
            var wasPlayingBeforeDrag by remember(preview) { mutableStateOf(false) }
            LaunchedEffect(player.isPlaying) {
                while (player.isPlaying) {
                    delay(220)
                    tick++
                }
            }
            val pos = player.positionMs
            val dur = player.durationMs.takeIf { it > 0 } ?: 30_000L
            val progressed = remember(tick, pos, dur, isDragging) {
                (pos.toFloat() / dur.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            val sliderValue = if (isDragging) sliderPosition else progressed

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledIconButton(
                    onClick = { player.togglePlayPause() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pause preview" else "Play preview",
                    )
                }
                Column(Modifier.weight(1f)) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { newVal ->
                            if (!isDragging) {
                                wasPlayingBeforeDrag = player.isPlaying
                                if (player.isPlaying) {
                                    player.togglePlayPause()
                                }
                                isDragging = true
                            }
                            sliderPosition = newVal.coerceIn(0f, 1f)
                            tick++
                        },
                        onValueChangeFinished = {
                            val seekMs = (sliderPosition.coerceIn(0f, 1f) * dur.toFloat()).toLong()
                            player.seekTo(seekMs)
                            if (wasPlayingBeforeDrag) {
                                player.togglePlayPause()
                            }
                            isDragging = false
                            tick++
                        },
                        modifier = Modifier.fillMaxWidth(),
                        valueRange = 0f..1f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatBeaconPreviewClock(
                                if (isDragging) (sliderPosition * dur).toLong() else pos,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatBeaconPreviewClock(dur),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (original != null) {
            OutlinedButton(
                onClick = { openBeaconOriginalMediaUrl(original) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Play full song")
            }
        }

        distanceMeters?.let { d ->
            Text(
                text = formatBeaconDistance(d),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatBeaconInstant(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "Unknown"
    return runCatching {
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
        val hour24 = dt.hour
        val h12 = ((hour24 + 11) % 12) + 1
        val amPm = if (hour24 < 12) "AM" else "PM"
        val mon = dt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        "$mon ${dt.dayOfMonth}, ${dt.year} · $h12:${dt.minute.toString().padStart(2, '0')} $amPm"
    }.getOrElse { "Unknown" }
}

private fun formatBeaconPreviewClock(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun formatBeaconDistance(meters: Double): String {
    if (!meters.isFinite() || meters < 0) return ""
    return if (meters < 1000) {
        "${meters.toInt()} m away"
    } else {
        val km = meters / 1000.0
        val tenths = ((km * 10.0) + 0.5).toInt().coerceAtLeast(1)
        val whole = tenths / 10
        val frac = tenths % 10
        "$whole.$frac km away"
    }
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
        is MapRenderData.IndividualPins -> {
            val conn = renderData.points.map { MapPin.fromConnectionPoint(it) }
            val bc = renderData.beacons.map { MapPin.fromBeacon(it) }
            (conn + bc).sortedByDescending { it.zIndex }
        }
        is MapRenderData.Clusters -> {
            val standalone = renderData.standaloneBeacons.map { MapPin.fromBeacon(it) }
            standalone.sortedByDescending { it.zIndex }
        }
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
