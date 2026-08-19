@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.events.buildEventShareText // pragma: allowlist secret
import compose.project.click.click.events.buildEventShareUrl // pragma: allowlist secret
import compose.project.click.click.media.rememberChatAudioPlayer // pragma: allowlist secret
import compose.project.click.click.openBeaconOriginalMediaUrl // pragma: allowlist secret
import compose.project.click.click.platform.shareText // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun CommunityBeaconDetail(
    beacon: MapBeacon,
    distanceMeters: Double?,
    viewModel: MapViewModel,
    isCreator: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareToChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val border = clickBorderColor()
    val cardSurface = clickCardSurface()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val kindLabel =
        when (beacon.kind) {
            MapBeaconKind.HAZARD -> "Hazard"
            MapBeaconKind.SOS -> "SOS"
            MapBeaconKind.UTILITY -> "Utility"
            MapBeaconKind.STUDY -> "Study"
            MapBeaconKind.SOCIAL_VIBE -> "Social"
            MapBeaconKind.OTHER -> "Beacon"
            else -> "Beacon"
        }
    val kindIcon =
        when (beacon.kind) {
            MapBeaconKind.HAZARD -> Icons.Filled.Warning
            MapBeaconKind.SOS -> Icons.Filled.NotificationsActive
            MapBeaconKind.UTILITY -> Icons.Filled.Build
            MapBeaconKind.STUDY -> Icons.Filled.MenuBook
            MapBeaconKind.SOCIAL_VIBE -> Icons.Filled.Groups
            else -> Icons.Filled.Place
        }
    val hostUserId = beacon.createdByUserId?.takeIf { it.isNotBlank() }
    val hostUser =
        hostUserId?.let { id ->
            if (id == currentUser?.id) currentUser else connectedUsers[id]
        }
    val hostDisplayName =
        beacon.creatorDisplayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: hostUser?.name?.trim()?.takeIf { it.isNotEmpty() }
    val hostAvatarUrl = hostUser?.image?.trim()?.takeIf { it.isNotEmpty() }
    val distanceLabel = distanceMeters?.let { formatBeaconDistance(it) }
    val createdLabel = formatBeaconInstant(beacon.createdAtEpochMs)
    val expiresLabel = formatBeaconInstant(beacon.expiresAtEpochMs)
    val createdParts =
        createdLabel.split(" · ").let { parts ->
            if (parts.size >= 2) {
                parts[0] to parts.drop(1).joinToString(" · ")
            } else {
                createdLabel to ""
            }
        }
    val expiresParts =
        expiresLabel.split(" · ").let { parts ->
            if (parts.size >= 2) {
                parts[0] to parts.drop(1).joinToString(" · ")
            } else {
                expiresLabel to ""
            }
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // No chip here: the structured section below already leads with an icon + kind chip.
        BeaconIdentityBanner(beacon = beacon, showTypeChip = false)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = kindIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = kindLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = beacon.displayDynamicTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                distanceLabel?.let { d ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = d,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BeaconShareMenuButton(
                    onShare = {
                        val shareUrl = buildEventShareUrl(beacon.id)
                        viewModel.recordEventShare(beacon.id, shareUrl = shareUrl)
                        shareText(
                            text = buildEventShareText(beacon, scheduleLabel = null, distanceLabel = distanceLabel),
                            subject = beacon.displayDynamicTitle(),
                        )
                    },
                    onShareToChat = onShareToChat,
                    border = border,
                    contentDescription = "Share beacon",
                )
                if (isCreator ||
                    (!currentUser?.id.isNullOrBlank() && beacon.createdByUserId == currentUser?.id)
                ) {
                    BeaconOwnerOverflowMenu(
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EventBentoCell(
                modifier = Modifier.weight(1f),
                label = "Posted",
                date = createdParts.first,
                time = createdParts.second.ifBlank { "—" },
                icon = Icons.Filled.Schedule,
                border = border,
                cardSurface = cardSurface,
            )
            EventBentoCell(
                modifier = Modifier.weight(1f),
                label = "Expires",
                date = expiresParts.first,
                time = expiresParts.second.ifBlank { "—" },
                icon = Icons.Filled.EventBusy,
                border = border,
                cardSurface = cardSurface,
            )
        }

        if (beacon.showCreatorName && !hostDisplayName.isNullOrBlank()) {
            EventHostCard(
                displayName = hostDisplayName,
                userId = hostUserId ?: "host:$hostDisplayName",
                avatarUrl = hostAvatarUrl,
                border = border,
                cardSurface = cardSurface,
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(clickBorderWidth(), border, RoundedCornerShape(12.dp))
                    .background(cardSurface, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    beacon.metadata.description
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "No description" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun SoundtrackBeaconDetail(
    beacon: MapBeacon,
    distanceMeters: Double?,
    viewModel: MapViewModel,
    isCreator: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareToChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mapBeacons by viewModel.mapBeacons.collectAsState()
    val displayBeacon =
        remember(beacon, mapBeacons) {
            mapBeacons.firstOrNull { it.id == beacon.id } ?: beacon
        }
    val trackTitle =
        displayBeacon.metadata.trackName?.takeIf { it.isNotBlank() }
            ?: displayBeacon.metadata.title?.takeIf { it.isNotBlank() }
            ?: displayBeacon.displayDynamicTitle()
    val artistLine =
        displayBeacon.metadata.artistName?.takeIf { it.isNotBlank() }
            ?: displayBeacon.metadata.artist?.takeIf { it.isNotBlank() }
    val art = displayBeacon.metadata.albumArtUrl?.takeIf { it.isNotBlank() }
    val preview = displayBeacon.metadata.previewUrl?.takeIf { it.isNotBlank() }
    val original =
        (displayBeacon.metadata.originalUrl ?: displayBeacon.metadata.musicUrl)
            ?.takeIf { it.isNotBlank() }
    val distanceLabel = distanceMeters?.let { formatBeaconDistance(it) }
    val border = clickBorderColor()
    val cardSurface = clickCardSurface()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val hostUserId = displayBeacon.createdByUserId?.takeIf { it.isNotBlank() }
    val hostUser =
        hostUserId?.let { id ->
            if (id == currentUser?.id) currentUser else connectedUsers[id]
        }
    val hostDisplayName =
        displayBeacon.creatorDisplayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: hostUser?.name?.trim()?.takeIf { it.isNotEmpty() }
    val hostAvatarUrl = hostUser?.image?.trim()?.takeIf { it.isNotEmpty() }

    LaunchedEffect(displayBeacon.id) {
        viewModel.ensureSoundtrackBeaconDetail(displayBeacon.id, seed = displayBeacon)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BeaconIdentityBanner(beacon = displayBeacon)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!artistLine.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artistLine,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                distanceLabel?.let { d ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = d,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BeaconShareMenuButton(
                    onShare = {
                        val shareUrl = buildEventShareUrl(displayBeacon.id)
                        viewModel.recordEventShare(displayBeacon.id, shareUrl = shareUrl)
                        shareText(
                            text = buildEventShareText(displayBeacon, scheduleLabel = null, distanceLabel = distanceLabel),
                            subject = trackTitle,
                        )
                    },
                    onShareToChat = onShareToChat,
                    border = border,
                    contentDescription = "Share soundtrack",
                )
                if (isCreator ||
                    (!currentUser?.id.isNullOrBlank() && displayBeacon.createdByUserId == currentUser?.id)
                ) {
                    BeaconOwnerOverflowMenu(
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardSurface)
                    .border(clickBorderWidth(), border, RoundedCornerShape(20.dp))
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(clickBorderWidth(), border, RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(clickBorderWidth(), border, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Soundtrack",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
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
            val progressed =
                remember(tick, pos, dur, isDragging) {
                    (pos.toFloat() / dur.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
            val sliderValue = if (isDragging) sliderPosition else progressed

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardSurface)
                        .border(clickBorderWidth(), border, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledIconButton(
                        onClick = { player.togglePlayPause() },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (player.isPlaying) "Pause preview" else "Play preview",
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
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
                                text =
                                    formatBeaconPreviewClock(
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
        } else {
            var waitedForPreview by remember(displayBeacon.id) { mutableStateOf(false) }
            LaunchedEffect(displayBeacon.id, preview) {
                if (preview == null) {
                    delay(2_500)
                    waitedForPreview = true
                } else {
                    waitedForPreview = false
                }
            }
            Text(
                text =
                    if (!waitedForPreview) {
                        "Loading preview…"
                    } else {
                        "No audio preview available for this track."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (original != null) {
            Button(
                onClick = { openBeaconOriginalMediaUrl(original) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open in music app")
            }
        }

        if (displayBeacon.showCreatorName && !hostDisplayName.isNullOrBlank()) {
            EventHostCard(
                displayName = hostDisplayName,
                userId = hostUserId ?: "host:$hostDisplayName",
                avatarUrl = hostAvatarUrl,
                border = border,
                cardSurface = cardSurface,
            )
        }

        displayBeacon.metadata.description?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Created · ${formatBeaconInstant(displayBeacon.createdAtEpochMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Expires · ${formatBeaconInstant(displayBeacon.expiresAtEpochMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatBeaconInstant(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "Unknown"
    return runCatching {
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
        val hour24 = dt.hour
        val h12 = ((hour24 + 11) % 12) + 1
        val amPm = if (hour24 < 12) "AM" else "PM"
        val mon =
            dt.month.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
                .take(3)
        "$mon ${dt.dayOfMonth}, ${dt.year} · $h12:${dt.minute.toString().padStart(2, '0')} $amPm"
    }.getOrElse { "Unknown" }
}

internal fun formatBeaconPreviewClock(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

internal fun formatBeaconDistance(meters: Double): String {
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
internal fun MemoriesPillContent(
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
internal fun LiveIndicator(count: Int) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse,
            ),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
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
internal fun CommunityHubBottomSheet(
    hub: CommunityHubPin,
    distanceMeters: Double?,
    canJoinGeofence: Boolean?,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = hub.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = GlassSheetTokens.OnOled(),
        )
        Text(
            text = "${hub.activeUserCount} active nearby",
            style = MaterialTheme.typography.bodyMedium,
            color = GlassSheetTokens.OnOledMuted(),
        )
        val distLabel =
            distanceMeters?.let { d ->
                if (d >= 1000) {
                    val kmTenths = (d / 100.0).toInt()
                    "${kmTenths / 10.0} km away"
                } else {
                    "${d.toInt()} m away"
                }
            } ?: if (canJoinGeofence == null) "Checking location…" else "Distance unavailable"
        Text(
            text = distLabel,
            style = MaterialTheme.typography.bodySmall,
            color = GlassSheetTokens.OnOledMuted(),
        )
        when (canJoinGeofence) {
            true -> {
                Button(
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Join Hub")
                }
            }
            false -> {
                Text(
                    text = "Move closer to join this hub.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassSheetTokens.OnOledMuted(),
                )
            }
            null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Verifying your location…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassSheetTokens.OnOledMuted(),
                    )
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}
