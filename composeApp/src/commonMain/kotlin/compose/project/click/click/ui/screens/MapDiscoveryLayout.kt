package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.ui.components.AppScreenDefaults
import compose.project.click.click.ui.components.DiscoveryFloatingHeader
import compose.project.click.click.ui.components.GlassSheetTokens
import compose.project.click.click.ui.components.headerCollapseFraction
import compose.project.click.click.ui.components.rememberBottomChromePadding
import compose.project.click.click.ui.components.rememberFabAboveNavPadding
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.utils.CommunityHubPin
import compose.project.click.click.ui.utils.ConnectionMapPoint
import compose.project.click.click.ui.utils.MapRenderData
import compose.project.click.click.ui.utils.displayTypeTitle
import compose.project.click.click.ui.utils.haversineDistance
import kotlinx.datetime.Clock

internal enum class DiscoverySortMode {
    Distance,
    Recent,
}

internal sealed class DiscoveryFeedItem {
    abstract val sortDistanceM: Double
    abstract val sortRecentEpochMs: Long
    abstract val key: String

    data class Hub(
        val hub: CommunityHubPin,
        val distanceM: Double,
        val ttlLabel: String,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = 0L
        override val key: String = "hub-${hub.hubId}"
    }

    data class Beacon(
        val beacon: MapBeacon,
        val distanceM: Double,
        val ttlLabel: String,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = beacon.createdAtEpochMs ?: 0L
        override val key: String = "beacon-${beacon.id}"
    }

    data class Connection(
        val point: ConnectionMapPoint,
        val distanceM: Double,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = point.connection.created
        override val key: String = "conn-${point.connection.id}"
    }
}

internal fun buildDiscoveryFeedItems(
    hubs: List<CommunityHubPin>,
    beacons: List<MapBeacon>,
    renderData: MapRenderData,
    userLat: Double?,
    userLon: Double?,
): List<DiscoveryFeedItem> {
    val now = Clock.System.now().toEpochMilliseconds()
    fun dist(lat: Double, lon: Double): Double =
        if (userLat != null && userLon != null) {
            haversineDistance(userLat, userLon, lat, lon)
        } else {
            Double.MAX_VALUE
        }

    val hubRows = hubs.map { hub ->
        DiscoveryFeedItem.Hub(
            hub = hub,
            distanceM = dist(hub.latitude, hub.longitude),
            ttlLabel = "Ephemeral · ${hub.activeUserCount} here",
        )
    }

    val beaconRows = beacons
        .filter { b ->
            val exp = b.expiresAtEpochMs
            exp == null || exp > now
        }
        .map { beacon ->
            val exp = beacon.expiresAtEpochMs
            val ttlLabel = if (exp != null) {
                val mins = ((exp - now) / 60_000L).coerceAtLeast(0L)
                when {
                    mins < 60 -> "Expires in ${mins}m"
                    else -> "Expires in ${mins / 60}h"
                }
            } else {
                "Active beacon"
            }
            DiscoveryFeedItem.Beacon(
                beacon = beacon,
                distanceM = dist(beacon.latitude, beacon.longitude),
                ttlLabel = ttlLabel,
            )
        }

    val connectionPoints = when (renderData) {
        is MapRenderData.IndividualPins -> renderData.points
        is MapRenderData.Clusters -> renderData.clusters.flatMap { it.points }
    }
    val connRows = connectionPoints.map { point ->
        DiscoveryFeedItem.Connection(
            point = point,
            distanceM = dist(point.latitude, point.longitude),
        )
    }

    return hubRows + beaconRows + connRows
}

internal fun sortDiscoveryFeedItems(
    items: List<DiscoveryFeedItem>,
    mode: DiscoverySortMode,
): List<DiscoveryFeedItem> =
    when (mode) {
        DiscoverySortMode.Distance -> items.sortedBy { it.sortDistanceM }
        DiscoverySortMode.Recent -> items.sortedByDescending { it.sortRecentEpochMs }
    }

private val PipPreviewWidth = 120.dp
private val PipPreviewHeight = 160.dp
private val PipDockExtraGap = AppScreenDefaults.FabGapAboveTabBar

@Composable
private fun MapPipPreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(GlassSheetTokens.OledBlack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Map,
            contentDescription = null,
            tint = GlassSheetTokens.OnOledMuted.copy(alpha = 0.45f),
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
internal fun MapDiscoveryScreen(
    feedItems: List<DiscoveryFeedItem>,
    mapPipExpanded: Boolean,
    onMapPipExpandedChange: (Boolean) -> Unit,
    statsLine: String,
    onOpenSearch: (() -> Unit)?,
    onDropBeacon: () -> Unit,
    mapContent: @Composable (Modifier, Boolean) -> Unit,
    expandedMapChrome: @Composable () -> Unit,
    onHubClick: (CommunityHubPin) -> Unit,
    onBeaconClick: (MapBeacon) -> Unit,
    onConnectionClick: (ConnectionMapPoint) -> Unit,
) {
    val platformStyle = LocalPlatformStyle.current
    var sortMode by remember { mutableIntStateOf(0) }
    var frozenCollapseFraction by remember { mutableFloatStateOf(Float.NaN) }
    val discoverySortMode = if (sortMode == 0) DiscoverySortMode.Distance else DiscoverySortMode.Recent
    val sortedFeed = remember(feedItems, discoverySortMode) {
        sortDiscoveryFeedItems(feedItems, discoverySortMode)
    }

    // Do not use rememberLazyListState(): it is saveable and can restore a previous scroll offset
    // before first paint when the tab route is reattached.
    val listState = remember { LazyListState(0, 0) }
    val collapseFraction by remember(listState, frozenCollapseFraction) {
        derivedStateOf {
            if (!frozenCollapseFraction.isNaN()) {
                frozenCollapseFraction
            } else {
                listState.headerCollapseFraction()
            }
        }
    }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomChrome = rememberBottomChromePadding()
    val dockBottom = rememberFabAboveNavPadding()

    val listTopPadding = remember(collapseFraction, statusBarTop) {
        val compactHeader = collapseFraction > 0.42f
        if (compactHeader) {
            statusBarTop + 76.dp
        } else {
            val collapsed = AppScreenDefaults.FloatingHeaderCompactHeight
            val expanded = AppScreenDefaults.FloatingHeaderLargeHeight
            val headerH = statusBarTop + collapsed + (expanded - collapsed) * (1f - collapseFraction)
            headerH + 76.dp + 8.dp
        }
    }

    LaunchedEffect(frozenCollapseFraction, sortMode) {
        if (frozenCollapseFraction.isNaN()) return@LaunchedEffect
        withFrameNanos { }
        frozenCollapseFraction = Float.NaN
    }

    val pipShape = RoundedCornerShape(16.dp)
    val pipInteraction = remember { MutableInteractionSource() }
    val expandMap: () -> Unit = { onMapPipExpandedChange(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassSheetTokens.OledBlack),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                start = AppScreenDefaults.HorizontalPadding,
                end = AppScreenDefaults.HorizontalPadding,
                top = listTopPadding,
                bottom = bottomChrome + PipPreviewHeight + PipDockExtraGap + 16.dp,
            ),
        ) {
            if (sortedFeed.isEmpty()) {
                item(key = "discovery_empty") {
                    DiscoveryFeedRow(
                        title = "Nothing nearby yet",
                        subtitle = "Drop a beacon or join a hub from the map preview.",
                        icon = Icons.Filled.Place,
                        onClick = onDropBeacon,
                    )
                }
            } else {
                items(
                    items = sortedFeed,
                    key = { it.key },
                    contentType = { "discovery_row" },
                ) { item ->
                    DiscoveryFeedRow(
                        item = item,
                        onClick = {
                            when (item) {
                                is DiscoveryFeedItem.Hub -> onHubClick(item.hub)
                                is DiscoveryFeedItem.Beacon -> onBeaconClick(item.beacon)
                                is DiscoveryFeedItem.Connection -> onConnectionClick(item.point)
                            }
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .fillMaxWidth()
                .padding(
                    start = AppScreenDefaults.HorizontalPadding,
                    end = AppScreenDefaults.HorizontalPadding,
                    top = statusBarTop,
                ),
        ) {
            DiscoveryFloatingHeader(
                collapseFraction = collapseFraction,
                title = "Discovery",
                subtitle = statsLine,
                selectedSortIndex = sortMode,
                onSortSelected = { index ->
                    if (index == sortMode) return@DiscoveryFloatingHeader
                    val keepExpandedHeader = collapseFraction <= 0.42f
                    frozenCollapseFraction = collapseFraction
                    val nextMode = if (index == 0) DiscoverySortMode.Distance else DiscoverySortMode.Recent
                    val nextFeed = sortDiscoveryFeedItems(feedItems, nextMode)
                    if (keepExpandedHeader) {
                        listState.requestScrollToItem(0, 0)
                    } else {
                        val anchorKey = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key != null && it.key != "discovery_empty" }
                            ?.key as? String
                            ?: sortedFeed.getOrNull(listState.firstVisibleItemIndex)?.key
                        val anchorIndex = anchorKey?.let { key -> nextFeed.indexOfFirst { it.key == key } }
                            ?.takeIf { it >= 0 }
                            ?: listState.firstVisibleItemIndex.coerceAtMost(nextFeed.lastIndex.coerceAtLeast(0))
                        listState.requestScrollToItem(anchorIndex, listState.firstVisibleItemScrollOffset)
                    }
                    sortMode = index
                },
                onOpenSearch = onOpenSearch,
            )
        }

        if (!mapPipExpanded) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .zIndex(10f)
                    .padding(
                        start = 16.dp,
                        end = 16.dp + PipPreviewWidth + 12.dp,
                        bottom = dockBottom,
                    ),
                verticalAlignment = Alignment.Bottom,
            ) {
                FloatingActionButton(
                    onClick = onDropBeacon,
                    modifier = Modifier.size(56.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Filled.AddLocationAlt, contentDescription = "Drop beacon")
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = dockBottom)
                    .size(width = PipPreviewWidth, height = PipPreviewHeight)
                    .zIndex(11f)
                    .clip(pipShape)
                    .border(1.dp, GlassSheetTokens.GlassBorder, pipShape)
                    .clickable(
                        interactionSource = pipInteraction,
                        indication = ripple(bounded = true),
                        onClick = expandMap,
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { expandMap() })
                    },
            ) {
                if (platformStyle.isIOS) {
                    mapContent(Modifier.fillMaxSize(), false)
                } else {
                    MapPipPreviewPlaceholder(Modifier.fillMaxSize())
                }
                IconButton(
                    onClick = expandMap,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .zIndex(4f)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSheetTokens.GlassSurface),
                ) {
                    Icon(
                        Icons.Filled.OpenInFull,
                        contentDescription = "Expand map",
                        tint = GlassSheetTokens.OnOled,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = mapPipExpanded,
            enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(0.92f, 0.92f),
                ),
            exit = fadeOut(tween(140, easing = FastOutSlowInEasing)) +
                scaleOut(
                    animationSpec = tween(190, easing = FastOutSlowInEasing),
                    targetScale = 0.92f,
                    transformOrigin = TransformOrigin(0.92f, 0.92f),
                ),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(30f),
            label = "map_fullscreen_overlay",
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GlassSheetTokens.OledBlack),
            ) {
                mapContent(Modifier.fillMaxSize(), true)
                expandedMapChrome()
            }
        }
    }
}

@Composable
private fun DiscoveryFeedRow(
    item: DiscoveryFeedItem,
    onClick: () -> Unit,
) {
    val title = when (item) {
        is DiscoveryFeedItem.Hub -> item.hub.name
        is DiscoveryFeedItem.Beacon -> item.beacon.displayTypeTitle()
        is DiscoveryFeedItem.Connection -> item.point.displayName
    }
    val subtitle = when (item) {
        is DiscoveryFeedItem.Hub -> item.ttlLabel
        is DiscoveryFeedItem.Beacon -> item.ttlLabel
        is DiscoveryFeedItem.Connection -> item.point.locationLabel
    }
    val icon = when (item) {
        is DiscoveryFeedItem.Hub -> Icons.Filled.Groups
        is DiscoveryFeedItem.Beacon -> when (item.beacon.kind) {
            MapBeaconKind.SOUNDTRACK -> Icons.Filled.MusicNote
            else -> Icons.Filled.Place
        }
        is DiscoveryFeedItem.Connection -> Icons.Filled.Person
    }
    val distanceText = if (item.sortDistanceM < Double.MAX_VALUE / 2) {
        formatDiscoveryDistance(item.sortDistanceM)
    } else {
        null
    }
    DiscoveryFeedRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        distanceText = distanceText,
        onClick = onClick,
    )
}

@Composable
private fun DiscoveryFeedRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    distanceText: String? = null,
) {
    val borderAlpha by animateFloatAsState(
        targetValue = GlassSheetTokens.GlassBorder.alpha,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "discovery_row_border",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner))
            .border(
                width = 1.dp,
                color = GlassSheetTokens.GlassBorder.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner),
            )
            .background(GlassSheetTokens.GlassSurface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PrimaryBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = GlassSheetTokens.OnOled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = GlassSheetTokens.OnOledMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            distanceText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassSheetTokens.OnOledMuted.copy(alpha = 0.8f),
                )
            }
        }
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = GlassSheetTokens.OnOledMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun formatDiscoveryDistance(meters: Double): String =
    when {
        meters < 1_000 -> "${meters.toInt()} m away"
        else -> {
            val km = (meters / 100.0).toInt() / 10.0
            "$km km away"
        }
    }
