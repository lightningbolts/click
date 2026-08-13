package compose.project.click.click.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.PollPairSuggestion
import compose.project.click.click.events.HomeEventReminder
import compose.project.click.click.data.api.ActivityRecapDto // pragma: allowlist secret
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.utils.userFacingLabel
import compose.project.click.click.viewmodel.MapLayerFilter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun OnlineFriendItem(name: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(2.dp, clickBorderColor(), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.first().toString(),
                color = NeonPurple,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AdaptiveButton(onClick = { }) {
            Text("Click", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun RecentClickCard(name: String, time: String, location: String) {
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, clickBorderColor(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.first().toString(),
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Clicked with $name",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "$location • $time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Time-of-day Home header title — used with [AppScreenScaffold] floating
 * [LiquidGlassPageHeader] so vertical position matches other tab roots.
 */
fun homeGreetingTitle(
    firstName: String,
    hour: Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour,
): String {
    val salutation = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
    return "$salutation, $firstName."
}

/** Default Home header subtitle under [homeGreetingTitle]. */
const val HomeGreetingSubtitle = "Ready to connect today?"

/**
 * Bordered search pill — opens unified search (not an inline text field).
 */
@Composable
fun HomeSearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search people, places, events…",
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(clickCardSurface())
            .border(2.dp, clickBorderColor(), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Featured upcoming event hero — driven by [HomeEventReminder], not a hardcoded click.
 */
@Composable
fun FeaturedEventSection(
    reminder: HomeEventReminder,
    onViewMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Featured Event",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onViewMap) {
                Text(
                    text = "View Map",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        FeaturedEventCard(
            reminder = reminder,
            onViewMap = onViewMap,
        )
    }
}

@Composable
fun FeaturedEventCard(
    reminder: HomeEventReminder,
    onViewMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val title = reminder.title?.takeIf { it.isNotBlank() }
        ?: reminder.description.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        ?: "Upcoming event"
    val timeBadge = formatFeaturedEventTimeBadge(reminder.startEpochMs)
    val showDescription = reminder.description.isNotBlank() &&
        reminder.title != null &&
        reminder.description.trim() != reminder.title.trim()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(clickCardSurface())
            .border(2.dp, clickBorderColor(), shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Event,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, clickBorderColor(), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = timeBadge,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showDescription) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = reminder.description.take(80),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = onViewMap,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Text("View on Map", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatFeaturedEventTimeBadge(startEpochMs: Long): String {
    val now = Clock.System.now()
    val start = Instant.fromEpochMilliseconds(startEpochMs)
    val local = start.toLocalDateTime(TimeZone.currentSystemDefault())
    val nowLocal = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = local.hour
    val minute = local.minute
    val amPm = if (hour < 12) "AM" else "PM"
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val timeStr = if (minute == 0) {
        "$hour12 $amPm"
    } else {
        "$hour12:${minute.toString().padStart(2, '0')} $amPm"
    }
    return when {
        local.date == nowLocal.date -> "Today $timeStr"
        else -> "${local.month.name.take(3)} ${local.dayOfMonth} · $timeStr"
    }
}

/**
 * Nearby explore tile — only kinds/hubs with live counts > 0.
 */
data class HomeExploreTile(
    val id: String,
    val label: String,
    val count: Int,
    val layerFilter: MapLayerFilter,
    val icon: ImageVector,
)

fun mapBeaconKindToLayerFilter(kind: MapBeaconKind): MapLayerFilter =
    when (kind) {
        MapBeaconKind.SOUNDTRACK -> MapLayerFilter.SOUNDTRACKS
        MapBeaconKind.SOS, MapBeaconKind.HAZARD, MapBeaconKind.UTILITY, MapBeaconKind.STUDY ->
            MapLayerFilter.ALERTS_UTILITIES
        MapBeaconKind.EVENT -> MapLayerFilter.EVENTS
        MapBeaconKind.SOCIAL_VIBE, MapBeaconKind.OTHER -> MapLayerFilter.SOCIAL_VIBES
    }

fun mapBeaconKindIcon(kind: MapBeaconKind): ImageVector =
    when (kind) {
        MapBeaconKind.SOUNDTRACK -> Icons.Filled.MusicNote
        MapBeaconKind.HAZARD -> Icons.Filled.Warning
        MapBeaconKind.UTILITY -> Icons.Filled.Build
        MapBeaconKind.SOS -> Icons.Filled.NotificationsActive
        MapBeaconKind.STUDY -> Icons.Filled.MenuBook
        MapBeaconKind.EVENT -> Icons.Filled.Event
        MapBeaconKind.SOCIAL_VIBE, MapBeaconKind.OTHER -> Icons.Filled.Groups
    }

fun MapBeaconKind.toHomeExploreTile(count: Int): HomeExploreTile =
    HomeExploreTile(
        id = "kind-${apiValue}",
        label = userFacingLabel(),
        count = count,
        layerFilter = mapBeaconKindToLayerFilter(this),
        icon = mapBeaconKindIcon(this),
    )

@Composable
fun ExploreNearbyBeaconsSection(
    tiles: List<HomeExploreTile>,
    onTileClick: (HomeExploreTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tiles.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Explore nearby",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val rows = tiles.chunked(2)
        rows.forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowTiles.forEach { tile ->
                    ExploreBeaconCategoryTile(
                        tile = tile,
                        onClick = { onTileClick(tile) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowTiles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Day/week activity rollup on Home, shown above saved events.
 */
@Composable
fun ActivityRecapSection(
    recap: ActivityRecapDto,
    window: String,
    onWindowChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Your recap",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecapWindowChip(
                label = "Day",
                selected = window == "day",
                onClick = { onWindowChange("day") },
            )
            RecapWindowChip(
                label = "Week",
                selected = window == "week",
                onClick = { onWindowChange("week") },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(2.dp, clickBorderColor(), shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecapStatRow("Connections formed", recap.connectionsFormed)
            RecapStatRow("Messages sent", recap.messagesSent)
            RecapStatRow("Messages received", recap.messagesReceived)
            RecapStatRow("Beacons created", recap.beaconsCreated)
            RecapStatRow("Events RSVP’d", recap.eventsRsvped)
            RecapStatRow("Check-ins", recap.eventsCheckedIn)
            RecapStatRow("Events saved", recap.eventsSaved)
        }
    }
}

@Composable
private fun RecapWindowChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .border(2.dp, clickBorderColor(), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun RecapStatRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Bookmarked events on Home — same square category tiles as [ExploreNearbyBeaconsSection].
 */
@Composable
fun SavedEventsSection(
    bookmarks: List<compose.project.click.click.data.api.EventBookmarkItemDto>,
    onBookmarkClick: (compose.project.click.click.data.api.EventBookmarkItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bookmarks.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Saved events",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val rows = bookmarks.chunked(2)
        rows.forEach { rowBookmarks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowBookmarks.forEach { bookmark ->
                    SavedEventCategoryTile(
                        bookmark = bookmark,
                        onClick = { onBookmarkClick(bookmark) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowBookmarks.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SavedEventCategoryTile(
    bookmark: compose.project.click.click.data.api.EventBookmarkItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val title = bookmark.title?.takeIf { it.isNotBlank() } ?: "Saved event"
    val timeBadge = bookmark.eventStartAt
        ?.let { iso -> runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull() }
        ?.let { formatHomeSavedEventTimeBadge(it) }
        ?: "Bookmarked"
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(2.dp, clickBorderColor(), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, clickBorderColor(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeBadge,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatHomeSavedEventTimeBadge(startEpochMs: Long): String {
    val now = Clock.System.now()
    val start = Instant.fromEpochMilliseconds(startEpochMs)
    val local = start.toLocalDateTime(TimeZone.currentSystemDefault())
    val nowLocal = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = local.hour
    val minute = local.minute
    val amPm = if (hour < 12) "AM" else "PM"
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val timeStr = if (minute == 0) {
        "$hour12 $amPm"
    } else {
        "$hour12:${minute.toString().padStart(2, '0')} $amPm"
    }
    return when {
        local.date == nowLocal.date -> "Today · $timeStr"
        else -> "${local.month.name.take(3)} ${local.dayOfMonth} · $timeStr"
    }
}

@Composable
private fun ExploreBeaconCategoryTile(
    tile: HomeExploreTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val countLabel = if (tile.count == 1) "1 nearby" else "${tile.count} nearby"
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(2.dp, clickBorderColor(), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, clickBorderColor(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tile.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = tile.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Hero Poll-Pair card with opaque surface and hard border.
 */
@Composable
fun PollPairCard(
    suggestion: PollPairSuggestion,
    onOpenChat: () -> Unit,
    onSendIcebreaker: () -> Unit,
    modifier: Modifier = Modifier,
    icebreakerSendEnabled: Boolean = true,
    icebreakerCooldownSec: Int = 0,
) {
    val outerShape = RoundedCornerShape(16.dp)
    val displayName = suggestion.otherUserName ?: "your click"
    val subtitle = when {
        suggestion.daysSinceContact <= 0 -> "No recent messages — say hi?"
        suggestion.daysSinceContact == 1 -> "1 day since you last chatted"
        else -> "${suggestion.daysSinceContact} days since you last chatted"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(clickCardSurface())
            .border(2.dp, clickBorderColor(), outerShape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, clickBorderColor(), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Poll-Pair",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "It's been a while! Say hi to $displayName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenChat,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    Icons.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open chat", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onSendIcebreaker,
                enabled = icebreakerSendEnabled,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, clickBorderColor()),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (icebreakerSendEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (icebreakerCooldownSec > 0) "Icebreaker (${icebreakerCooldownSec}s)" else "Icebreaker",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    AdaptiveCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, clickBorderColor(), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
