package compose.project.click.click.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.utils.CommunityHubPin
import compose.project.click.click.ui.utils.ConnectionMapPoint
import compose.project.click.click.ui.utils.TimeState
import compose.project.click.click.ui.utils.displayTypeTitle
import compose.project.click.click.ui.utils.haversineDistance
import compose.project.click.click.ui.utils.MapRenderData
import kotlinx.datetime.Clock

internal sealed class DiscoveryFeedItem {
    abstract val sortDistanceM: Double
    abstract val key: String

    data class Hub(
        val hub: CommunityHubPin,
        val distanceM: Double,
        val ttlLabel: String,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val key: String = "hub-${hub.hubId}"
    }

    data class Beacon(
        val beacon: MapBeacon,
        val distanceM: Double,
        val ttlLabel: String,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val key: String = "beacon-${beacon.id}"
    }

    data class Connection(
        val point: ConnectionMapPoint,
        val distanceM: Double,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
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
    }.sortedBy { it.sortDistanceM }

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
        .sortedBy { it.sortDistanceM }

    val connectionPoints = when (renderData) {
        is MapRenderData.IndividualPins -> renderData.points
        is MapRenderData.Clusters -> renderData.clusters.flatMap { it.points }
    }
    val connRows = connectionPoints
        .sortedWith(
            compareBy<ConnectionMapPoint> {
                when (it.timeState) {
                    TimeState.LIVE -> 0
                    TimeState.RECENT -> 1
                    TimeState.ARCHIVE -> 2
                }
            }.thenBy { it.connection.created },
        )
        .map { point ->
            DiscoveryFeedItem.Connection(
                point = point,
                distanceM = dist(point.latitude, point.longitude),
            )
        }

    return hubRows + beaconRows + connRows
}

@Composable
internal fun MapDiscoveryScreen(
    feedItems: List<DiscoveryFeedItem>,
    bottomListPadding: androidx.compose.ui.unit.Dp,
    mapPipExpanded: Boolean,
    onMapPipExpandedChange: (Boolean) -> Unit,
    statsLine: String,
    ghostMode: Boolean,
    mapContent: @Composable (Modifier) -> Unit,
    onHubClick: (CommunityHubPin) -> Unit,
    onBeaconClick: (MapBeacon) -> Unit,
    onConnectionClick: (ConnectionMapPoint) -> Unit,
) {
    LaunchedEffect(mapPipExpanded) {
        onMapPipExpandedChange(mapPipExpanded)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 88.dp,
                bottom = bottomListPadding + if (mapPipExpanded) 0.dp else 188.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "discovery_header") {
                Text(
                    text = "Discovery",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = statsLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            if (feedItems.isEmpty()) {
                item(key = "discovery_empty") {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Nothing nearby yet. Drop a beacon or join a hub from the map.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(feedItems, key = { it.key }) { item ->
                    DiscoveryFeedCard(
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

        val pipShape = RoundedCornerShape(16.dp)
        val pipModifier = if (mapPipExpanded) {
            Modifier
                .fillMaxSize()
                .zIndex(8f)
                .clickable(enabled = false) {}
        } else {
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomListPadding + 12.dp)
                .size(width = 120.dp, height = 160.dp)
                .zIndex(6f)
                .clip(pipShape)
                .clickable { onMapPipExpandedChange(true) }
        }

        Box(
            modifier = pipModifier
                .animateContentSize(animationSpec = tween(320, easing = FastOutSlowInEasing))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            mapContent(Modifier.fillMaxSize())
            if (mapPipExpanded) {
                FloatingActionButton(
                    onClick = { onMapPipExpandedChange(false) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .zIndex(9f),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Minimize map")
                }
            }
        }
    }
}

@Composable
private fun DiscoveryFeedCard(
    item: DiscoveryFeedItem,
    onClick: () -> Unit,
) {
    AdaptiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PrimaryBlue.copy(alpha = 0.14f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (item) {
                            is DiscoveryFeedItem.Hub -> Icons.Filled.Groups
                            is DiscoveryFeedItem.Beacon -> when (item.beacon.kind) {
                                MapBeaconKind.SOUNDTRACK -> Icons.Filled.MusicNote
                                else -> Icons.Filled.Place
                            }
                            is DiscoveryFeedItem.Connection -> Icons.Filled.Person
                        },
                        contentDescription = null,
                        tint = PrimaryBlue,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (item) {
                        is DiscoveryFeedItem.Hub -> item.hub.name
                        is DiscoveryFeedItem.Beacon -> item.beacon.displayTypeTitle()
                        is DiscoveryFeedItem.Connection -> item.point.displayName
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (item) {
                        is DiscoveryFeedItem.Hub -> item.ttlLabel
                        is DiscoveryFeedItem.Beacon -> item.ttlLabel
                        is DiscoveryFeedItem.Connection -> item.point.locationLabel
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.sortDistanceM < Double.MAX_VALUE / 2) {
                    Text(
                        text = formatDiscoveryDistance(item.sortDistanceM),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
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
