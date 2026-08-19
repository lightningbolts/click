@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.telemetry.TelemetryBatcher // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.MapClusterPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPinKind // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformMap // pragma: allowlist secret
import compose.project.click.click.ui.components.toClusterPin // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.util.oneToOnePeerPairKey // pragma: allowlist secret

@Composable
internal fun MapContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    renderData: MapRenderData,
    communityHubs: List<CommunityHubPin>,
    zoom: Double,
    ghostMode: Boolean,
    mapGesturesEnabled: Boolean = true,
    showCompass: Boolean = true,
    cameraTarget: compose.project.click.click.viewmodel.CameraTarget?, // pragma: allowlist secret
    userLat: Double? = null,
    userLon: Double? = null,
    currentUserId: String? = null,
    onPinTapped: (MapPin) -> Unit,
    onClusterTapped: (MapClusterPin) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit,
    onCameraAnimationComplete: () -> Unit,
    onMapGesture: () -> Unit = {},
) {
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val hubPins =
        remember(communityHubs) {
            communityHubs.map { MapPin.fromCommunityHub(it) }
        }
    val pins =
        remember(renderData, connectedUsers, currentUserId, hubPins) {
            when (renderData) {
                is MapRenderData.IndividualPins -> {
                    val conn =
                        renderData.points
                            .distinctBy { oneToOnePeerPairKey(it.connection.user_ids) ?: it.connection.id }
                            .map { point ->
                                val peerId = point.connection.user_ids.firstOrNull { it != currentUserId }
                                val peer = peerId?.let { connectedUsers[it] }
                                MapPin.fromConnectionPoint(
                                    point,
                                    imageUrl = peer?.image,
                                    avatarSeed = peerId ?: point.connection.id,
                                )
                            }
                    val bc = renderData.beacons.map { MapPin.fromBeacon(it) }
                    (conn + bc + hubPins).sortedByDescending { it.zIndex }
                }
                is MapRenderData.Clusters -> {
                    val standalone = renderData.standaloneBeacons.map { MapPin.fromBeacon(it) }
                    (standalone + hubPins).sortedByDescending { it.zIndex }
                }
            }
        }

    val clusters =
        remember(renderData) {
            when (renderData) {
                is MapRenderData.Clusters -> renderData.clusters.map { it.toClusterPin() }
                is MapRenderData.IndividualPins -> emptyList()
            }
        }

    // Drive the native map only while a programmatic CameraTarget is active.
    // After onCameraAnimationComplete clears it, pass null centers so PlatformMap
    // keeps the settled viewport (do not snap back to GPS / default user zoom).
    val mapCenterLat = cameraTarget?.latitude
    val mapCenterLon = cameraTarget?.longitude
    val mapZoom = zoom

    PlatformMap(
        modifier = modifier,
        pins = pins,
        clusters = clusters,
        zoom = mapZoom,
        centerLat = mapCenterLat,
        centerLon = mapCenterLon,
        ghostMode = ghostMode,
        mapGesturesEnabled = mapGesturesEnabled,
        showCompass = showCompass,
        onPinTapped = onPinTapped,
        onClusterTapped = onClusterTapped,
        onZoomChanged = onZoomChanged,
        onVisibleBoundsChanged = onVisibleBoundsChanged,
        onCameraAnimationComplete = onCameraAnimationComplete,
        onMapGesture = onMapGesture,
    )
}

/** Stable callback identity so [MapContent] / [PlatformMap] can skip when Events opens. */
@Composable
internal fun rememberMapPinTapHandler(
    onConnection: (pinId: String) -> Unit,
    onClearConnection: () -> Unit,
    onPin: (MapPin) -> Unit,
): (MapPin) -> Unit {
    val onConnectionState = rememberUpdatedState(onConnection)
    val onClearConnectionState = rememberUpdatedState(onClearConnection)
    val onPinState = rememberUpdatedState(onPin)
    return remember {
        { pin: MapPin ->
            TelemetryBatcher.recordActionTaken()
            if (pin.kind == MapPinKind.CONNECTION) {
                onConnectionState.value(pin.id)
            } else {
                onClearConnectionState.value()
            }
            onPinState.value(pin)
        }
    }
}

@Composable
internal fun rememberMapClusterTapHandler(onCluster: (clusterId: String) -> Unit): (MapClusterPin) -> Unit {
    val onClusterState = rememberUpdatedState(onCluster)
    return remember {
        { clusterPin: MapClusterPin ->
            TelemetryBatcher.recordActionTaken()
            onClusterState.value(clusterPin.id)
        }
    }
}

@Composable
internal fun rememberStableZoomHandler(onZoom: (Double) -> Unit): (Double) -> Unit {
    val state = rememberUpdatedState(onZoom)
    return remember { { z: Double -> state.value(z) } }
}

@Composable
internal fun rememberStableBoundsHandler(
    onBounds: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit,
): (Double, Double, Double, Double) -> Unit {
    val state = rememberUpdatedState(onBounds)
    return remember {
        { minLat: Double, maxLat: Double, minLon: Double, maxLon: Double ->
            state.value(minLat, maxLat, minLon, maxLon)
        }
    }
}

@Composable
internal fun rememberStableUnitHandler(onInvoke: () -> Unit): () -> Unit {
    val state = rememberUpdatedState(onInvoke)
    return remember { { state.value() } }
}

@Composable
internal fun OverlappingMapPinsChooser(
    pins: List<MapPin>,
    onChoose: (MapPin) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Which pin?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "A few pins are stacked here — pick the one you meant.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pins.forEach { pin ->
            val kindLabel =
                when (pin.kind) {
                    MapPinKind.CONNECTION -> "Connection"
                    MapPinKind.COMMUNITY_HUB -> "Hub"
                    MapPinKind.BEACON_SOUNDTRACK -> "Soundtrack"
                    MapPinKind.BEACON_ALERT -> "Alert"
                    MapPinKind.BEACON_SOCIAL -> "Event"
                    MapPinKind.BEACON_OTHER -> "Beacon"
                }
            val shape = RoundedCornerShape(16.dp)
            val rowSurface = clickCardSurface()
            val rowBorder = clickBorderColor()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(rowSurface)
                        .border(clickBorderWidth(), rowBorder, shape)
                        .clickable { onChoose(pin) }
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectionListUserAvatarFace(
                    displayName = pin.title,
                    email = null,
                    avatarUrl = pin.imageUrl,
                    userId = pin.avatarUserId ?: pin.id,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .border(clickBorderWidth(), rowBorder, CircleShape)
                            .clip(CircleShape),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pin.title.ifBlank { kindLabel },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = kindLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
internal fun ZoomControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    MapZoomGlassControls(
        modifier = modifier,
        onZoomIn = onZoomIn,
        onZoomOut = onZoomOut,
        glassStrength = if (LocalPlatformStyle.current.isIOS) 0.78f else 0.4f,
    )
}

@Composable
internal fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AdaptiveCircularProgressIndicator()
    }
}

@Composable
internal fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
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
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(sheetBg)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        when (point.timeState) {
                            TimeState.LIVE -> PrimaryBlue
                            TimeState.RECENT -> MaterialTheme.colorScheme.primaryContainer
                            TimeState.ARCHIVE -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ).border(clickBorderWidth(), clickBorderColor(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (point.shouldPulse) {
                PulsingRing()
            }
            Text(
                otherUser?.name?.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color =
                    when (point.timeState) {
                        TimeState.LIVE -> Color.White
                        TimeState.RECENT -> MaterialTheme.colorScheme.onPrimaryContainer
                        TimeState.ARCHIVE -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
            "Met at ${point.locationLabel}",
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
            modifier =
                Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
        )
    }
}

@Composable
internal fun MarkerSheetTimeStateBadge(timeState: TimeState) {
    val (color, label, icon) =
        when (timeState) {
            TimeState.LIVE -> Triple(PrimaryBlue, "Live Now", Icons.Filled.Bolt)
            TimeState.RECENT -> Triple(LightBlue, "Recent", Icons.Filled.AccessTime)
            TimeState.ARCHIVE -> Triple(Color.Gray, "Memory", Icons.Filled.History)
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(clickBorderWidth(), color),
    ) {
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
internal fun PulsingRing() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    Box(
        modifier =
            Modifier
                .size(100.dp)
                .scale(scale)
                .border(3.dp, PrimaryBlue.copy(alpha = alpha), CircleShape),
    )
}

internal enum class EventsListTransitionMode {
    Tap,
    Gesture,
}
