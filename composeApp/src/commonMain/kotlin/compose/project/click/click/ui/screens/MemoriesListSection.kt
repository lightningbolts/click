package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.theme.LightBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.utils.BoundingBox // pragma: allowlist secret
import compose.project.click.click.ui.utils.ConnectionMapPoint // pragma: allowlist secret
import compose.project.click.click.ui.utils.TimeState // pragma: allowlist secret
import compose.project.click.click.ui.utils.toMapPoint // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapState // pragma: allowlist secret

/**
 * Vertical list of connection memories, extracted from [MapScreen] so the map itself can be
 * full-bleed (Phase 2 — B1). The section is still used from:
 *   * the map's [ProfileBottomSheet] Timeline subtab (re-used as-is),
 *   * the Memories tab in the connections nav destination,
 *   * the Profile sheet's Timeline subtab.
 *
 * Rendering behaviour matches the previous inline implementation exactly — no visual regressions,
 * just a file split so the map screen no longer owns the list.
 */
@Composable
fun MemoriesListSection(
    mapState: MapState,
    visibleBounds: BoundingBox?,
    onConnectionClick: (ConnectionMapPoint) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mapState) {
        is MapState.Success -> {
            if (mapState.connections.isEmpty()) {
                EmptyMemoriesState(modifier)
            } else {
                val allPoints = mapState.connections.mapNotNull {
                    try { it.toMapPoint() } catch (e: Exception) { null }
                }

                val visiblePoints = if (visibleBounds != null) {
                    allPoints.filter { point ->
                        point.latitude in visibleBounds.minLat..visibleBounds.maxLat &&
                            point.longitude in visibleBounds.minLon..visibleBounds.maxLon
                    }
                } else {
                    allPoints
                }

                val sortedPoints = visiblePoints.sortedByDescending { it.connection.created }

                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Your Memories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (visibleBounds != null && visiblePoints.size != allPoints.size) {
                                Text(
                                    "${visiblePoints.size} of ${allPoints.size} in view",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(items = sortedPoints, key = { it.connection.id }) { point ->
                        MemoryLocationCard(point = point, onClick = { onConnectionClick(point) })
                    }
                }
            }
        }
        is MapState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                AdaptiveCircularProgressIndicator()
            }
        }
        is MapState.Error -> {
            // Error surface handled by the host screen.
        }
    }
}

@Composable
private fun EmptyMemoriesState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ChatAmbientMeshBackground(
            connection = null,
            isHubNeutral = true,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .alpha(0.35f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Memories Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Make connections to build your memory map",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MemoryLocationCard(
    point: ConnectionMapPoint,
    onClick: () -> Unit,
) {
    val timeColor = when (point.timeState) {
        TimeState.LIVE -> PrimaryBlue
        TimeState.RECENT -> LightBlue
        TimeState.ARCHIVE -> Color.Gray
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth().alpha(point.opacity),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(timeColor, timeColor.copy(alpha = 0.6f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (point.shouldPulse) {
                    MemoryPulsingIndicator()
                }
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    point.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        point.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                MemoryTimeStateBadge(timeState = point.timeState)
            }
        }
    }
}

@Composable
private fun MemoryPulsingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .alpha(alpha)
            .background(PrimaryBlue.copy(alpha = 0.3f), CircleShape),
    )
}

@Composable
private fun MemoryTimeStateBadge(timeState: TimeState) {
    val (color, label, icon) = when (timeState) {
        TimeState.LIVE -> Triple(PrimaryBlue, "Live Now", Icons.Filled.Bolt)
        TimeState.RECENT -> Triple(LightBlue, "Recent", Icons.Filled.AccessTime)
        TimeState.ARCHIVE -> Triple(Color.Gray, "Memory", Icons.Filled.History)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
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
