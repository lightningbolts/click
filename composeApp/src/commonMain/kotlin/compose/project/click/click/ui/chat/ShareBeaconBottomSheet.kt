@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.beaconIdFromMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.beaconShareUrlFromMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.beaconTitleFromMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.beaconTypeFromMetadata // pragma: allowlist secret
import compose.project.click.click.events.buildEventShareUrl // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.events.formatEventScheduleRange // pragma: allowlist secret
import compose.project.click.click.ui.components.CardVisualHero // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberCardVisual // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import compose.project.click.click.ui.utils.beaconTypeDisplayLabel // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayTypeTitle // pragma: allowlist secret
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
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
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    beacons.forEach { beacon ->
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
                    border = BorderStroke(clickBorderWidth(), border),
                    colors =
                        ButtonDefaults.buttonColors(
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
                border = BorderStroke(clickBorderWidth(), border),
                colors =
                    ButtonDefaults.outlinedButtonColors(
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
    val locationLabel: String? = null,
    val signedUp: Boolean = false,
    val bookmarked: Boolean = false,
    val checkedIn: Boolean = false,
) {
    companion object {
        fun fromMapBeacon(beacon: MapBeacon): BeaconPreviewModel {
            val title = beacon.displayDynamicTitle()
            val rawDescription =
                beacon.metadata.description
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(beacon.metadata.artistName, beacon.metadata.trackName)
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() && it != title }
            val location =
                beacon.metadata.locationName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: beacon.metadata.formattedAddress
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
            return BeaconPreviewModel(
                beaconId = beacon.id,
                title = title,
                kindLabel = beacon.displayTypeTitle(),
                kind = beacon.kind,
                description = rawDescription?.takeIf { it != title },
                scheduleLabel = beacon.eventSchedule()?.let { formatEventScheduleRange(it) },
                shareUrl = buildEventShareUrl(beacon.id),
                albumArtUrl =
                    beacon.metadata.albumArtUrl?.takeIf {
                        beacon.kind == MapBeaconKind.SOUNDTRACK && it.isNotBlank()
                    },
                locationLabel = location,
            )
        }

        fun fromMessage(
            message: Message,
            knownBeacon: MapBeacon? = null,
            signedUp: Boolean = false,
            bookmarked: Boolean = false,
            checkedIn: Boolean = false,
        ): BeaconPreviewModel {
            val fromKnown = knownBeacon?.let { fromMapBeacon(it) }
            val root = message.metadata as? JsonObject
            val title =
                fromKnown?.title
                    ?: beaconTitleFromMetadata(message.metadata)
                    ?: message.content
                        .removePrefix("Beacon:")
                        .trim()
                        .ifBlank { "Beacon" }
            val typeRaw = beaconTypeFromMetadata(message.metadata)
            val kind = fromKnown?.kind ?: MapBeaconKind.fromRaw(typeRaw)
            val shareUrl =
                fromKnown?.shareUrl
                    ?: beaconShareUrlFromMetadata(message.metadata)
                    ?: beaconIdFromMetadata(message.metadata)?.let { buildEventShareUrl(it) }
                    ?: ""
            val description =
                fromKnown?.description
                    ?: root
                        ?.get("description")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && it != title }
            val schedule =
                fromKnown?.scheduleLabel
                    ?: root
                        ?.get("schedule_label")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
            val albumArt =
                fromKnown?.albumArtUrl
                    ?: root
                        ?.get("album_art_url")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
            val location =
                fromKnown?.locationLabel
                    ?: root
                        ?.get("location_name")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
            return BeaconPreviewModel(
                beaconId =
                    knownBeacon?.id
                        ?: beaconIdFromMetadata(message.metadata).orEmpty(),
                title = title,
                kindLabel = fromKnown?.kindLabel ?: beaconTypeDisplayLabel(typeRaw, kind),
                kind = kind,
                description = description,
                scheduleLabel = schedule,
                shareUrl = shareUrl,
                albumArtUrl = albumArt,
                locationLabel = location,
                signedUp = signedUp,
                bookmarked = bookmarked,
                checkedIn = checkedIn,
            )
        }
    }
}

private fun MapBeaconKind.previewIcon(): ImageVector =
    when (this) {
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
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(clickCardSurface())
                .border(clickBorderWidth(), borderColor, shape)
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                ),
    ) {
        // Shared generated hero: the same beacon looks identical here, on the pile, in lists, and on
        // its map pin. Previously a flat surfaceContainerLow band made every share preview identical.
        val visual = rememberCardVisual(model.beaconId, model.kind)
        CardVisualHero(
            id = model.beaconId,
            visual = visual,
            imageUrl = model.albumArtUrl,
            chipLabel = model.kindLabel,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
        ) {
            if (model.albumArtUrl == null) {
                Icon(
                    model.kind.previewIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 28.dp else 36.dp),
                    tint = visual.onContent,
                )
            }
        }
        Column(
            modifier =
                Modifier
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
                minLines = 1,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (model.locationLabel != null) {
                Text(
                    text = model.locationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val engagementBits =
                buildList {
                    if (model.signedUp) add("Going")
                    if (model.bookmarked) add("Saved")
                    if (model.checkedIn) add("Checked in")
                }
            if (engagementBits.isNotEmpty()) {
                Text(
                    text = engagementBits.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (model.description != null && !compact) {
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    minLines = 2,
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
