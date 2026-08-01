package compose.project.click.click.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.beaconIdFromMetadata
import compose.project.click.click.data.models.beaconShareUrlFromMetadata
import compose.project.click.click.data.models.beaconTitleFromMetadata
import compose.project.click.click.data.models.beaconTypeFromMetadata
import compose.project.click.click.events.buildEventShareUrl
import compose.project.click.click.events.eventSchedule
import compose.project.click.click.events.formatEventScheduleRange
import compose.project.click.click.ui.components.ClickFormBottomSheet
import compose.project.click.click.ui.components.ClickSheetChrome
import compose.project.click.click.ui.components.ClickSheetDefaults
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.theme.clickBorderColor
import compose.project.click.click.ui.theme.clickCardSurface
import compose.project.click.click.ui.utils.beaconTypeDisplayLabel
import compose.project.click.click.ui.utils.displayDynamicTitle
import compose.project.click.click.ui.utils.displayTypeTitle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Share-beacon picker: scroll nearby-style cards, select one, then confirm with
 * map-style full-width actions. Does not send — stages into the composer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareBeaconBottomSheet(
    beacons: List<MapBeacon>,
    onDismissRequest: () -> Unit,
    onConfirmStage: (MapBeacon) -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = beacons.firstOrNull { it.id == selectedId }
    val actionShape = RoundedCornerShape(12.dp)
    val border = clickBorderColor()

    ClickFormBottomSheet(onDismissRequest = onDismissRequest) {
        ClickSheetChrome(
            title = "Share a beacon",
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            Text(
                text = "Pick a nearby beacon, then add it to the chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (beacons.isEmpty()) {
                Text(
                    text = "No nearby beacons yet. Drop one on the map first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(beacons, key = { it.id }) { beacon ->
                        val isSelected = beacon.id == selectedId
                        BeaconPreviewCard(
                            model = BeaconPreviewModel.fromMapBeacon(beacon),
                            selected = isSelected,
                            onClick = {
                                PlatformHapticsPolicy.lightImpact()
                                selectedId = beacon.id
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selected != null) {
                Button(
                    onClick = {
                        PlatformHapticsPolicy.lightImpact()
                        onConfirmStage(selected)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = actionShape,
                    border = BorderStroke(2.dp, border),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("Add to chat", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = actionShape,
                border = BorderStroke(2.dp, border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

internal data class BeaconPreviewModel(
    val beaconId: String,
    val title: String,
    val kindLabel: String,
    val kind: MapBeaconKind,
    val description: String?,
    val scheduleLabel: String?,
    val shareUrl: String,
    val albumArtUrl: String?,
) {
    companion object {
        fun fromMapBeacon(beacon: MapBeacon): BeaconPreviewModel {
            val title = beacon.displayDynamicTitle()
            val rawDescription = beacon.metadata.description?.trim()?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(beacon.metadata.artistName, beacon.metadata.trackName)
                    .joinToString(" · ")
                    .takeIf { it.isNotBlank() && it != title }
            return BeaconPreviewModel(
                beaconId = beacon.id,
                title = title,
                kindLabel = beacon.displayTypeTitle(),
                kind = beacon.kind,
                description = rawDescription?.takeIf { it != title },
                scheduleLabel = beacon.eventSchedule()?.let { formatEventScheduleRange(it) },
                shareUrl = buildEventShareUrl(beacon.id),
                albumArtUrl = beacon.metadata.albumArtUrl?.takeIf {
                    beacon.kind == MapBeaconKind.SOUNDTRACK && it.isNotBlank()
                },
            )
        }

        fun fromMessage(message: Message): BeaconPreviewModel {
            val root = message.metadata as? JsonObject
            val title = beaconTitleFromMetadata(message.metadata)
                ?: message.content.removePrefix("Beacon:").trim().ifBlank { "Beacon" }
            val typeRaw = beaconTypeFromMetadata(message.metadata)
            val kind = MapBeaconKind.fromRaw(typeRaw)
            val shareUrl = beaconShareUrlFromMetadata(message.metadata)
                ?: beaconIdFromMetadata(message.metadata)?.let { buildEventShareUrl(it) }
                ?: ""
            val description = root?.get("description")?.jsonPrimitive?.contentOrNull
                ?.trim()?.takeIf { it.isNotEmpty() && it != title }
            val schedule = root?.get("schedule_label")?.jsonPrimitive?.contentOrNull
                ?.trim()?.takeIf { it.isNotEmpty() }
            val albumArt = root?.get("album_art_url")?.jsonPrimitive?.contentOrNull
                ?.trim()?.takeIf { it.isNotEmpty() }
            return BeaconPreviewModel(
                beaconId = beaconIdFromMetadata(message.metadata).orEmpty(),
                title = title,
                kindLabel = beaconTypeDisplayLabel(typeRaw, kind),
                kind = kind,
                description = description,
                scheduleLabel = schedule,
                shareUrl = shareUrl,
                albumArtUrl = albumArt,
            )
        }
    }
}

private fun MapBeaconKind.previewIcon(): ImageVector = when (this) {
    MapBeaconKind.SOUNDTRACK -> Icons.Filled.MusicNote
    MapBeaconKind.SOS -> Icons.Filled.Campaign
    MapBeaconKind.HAZARD -> Icons.Filled.Warning
    MapBeaconKind.UTILITY -> Icons.Filled.Build
    MapBeaconKind.STUDY -> Icons.Filled.School
    MapBeaconKind.SOCIAL_VIBE -> Icons.Filled.Celebration
    MapBeaconKind.EVENT -> Icons.Filled.Event
    MapBeaconKind.OTHER -> Icons.Filled.Place
}

/**
 * Discovery-style beacon card used in the share sheet, composer staging, and chat timeline.
 */
@Composable
internal fun BeaconPreviewCard(
    model: BeaconPreviewModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val heroHeight = if (compact) 72.dp else 96.dp
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else clickBorderColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(clickCardSurface())
            .border(2.dp, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            if (model.albumArtUrl != null) {
                AsyncImage(
                    model = model.albumArtUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    model.kind.previewIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 28.dp else 36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, clickBorderColor(), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = model.kindLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = model.title,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.scheduleLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Event,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = model.scheduleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (model.description != null && !compact) {
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (model.shareUrl.isNotBlank()) {
                Text(
                    text = "Open event",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                )
            }
        }
    }
}
