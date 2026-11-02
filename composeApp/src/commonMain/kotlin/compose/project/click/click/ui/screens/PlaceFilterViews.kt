@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.places.EnergyLabel // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceCategory // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceCopy // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceFilterChip // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceFilters // pragma: allowlist secret
import compose.project.click.click.data.places.minReportsLabel // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickChip // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickPlatformSheet // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import kotlinx.datetime.Clock

/**
 * Nearby's Place filter chips (§6.6). Mirrors iOS `PlaceFilterChips`
 * (click-ios `Click/Features/Places/PlaceFilterViews.swift`): active chips are filled, "More…"
 * opens every filter, and "Clear" appears once anything is on.
 */
@Composable
internal fun PlaceFilterChipsRow(
    filters: PlaceFilters,
    onFiltersChange: (PlaceFilters) -> Unit,
) {
    var showingAll by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceFilterChip.entries.forEach { chip ->
            ClickChip(
                label = chip.label,
                selected = chip.isOn(filters),
                onClick = {
                    PlatformHapticsPolicy.lightImpact()
                    onFiltersChange(chip.toggle(filters))
                },
                compact = true,
            )
        }
        ClickChip(label = PlaceFilterChip.MORE_LABEL, selected = false, onClick = { showingAll = true }, compact = true)
        if (filters.isActive) {
            ClickChip(
                label = PlaceFilterChip.CLEAR_LABEL,
                selected = false,
                onClick = {
                    PlatformHapticsPolicy.lightImpact()
                    onFiltersChange(PlaceFilters.NONE)
                },
                compact = true,
            )
        }
    }
    if (showingAll) {
        PlaceFiltersSheet(
            filters = filters,
            onFiltersChange = onFiltersChange,
            onDismiss = { showingAll = false },
        )
    }
}

/** Every Place filter, including categories and minimum reports (iOS `PlaceFiltersSheet`). */
@Composable
private fun PlaceFiltersSheet(
    filters: PlaceFilters,
    onFiltersChange: (PlaceFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    ClickPlatformSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onFiltersChange(PlaceFilters.NONE) }, enabled = filters.isActive) { Text("Clear") }
                Text(
                    text = "Filter Places",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onDismiss) { Text("Done") }
            }

            FilterSectionTitle("Right now")
            FilterToggle("Pulse now", filters.pulseNow) { onFiltersChange(filters.copy(pulseNow = it)) }
            Text(
                text = "Minimum reports",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceFilters.MIN_REPORT_CHOICES.forEach { value ->
                    ClickChip(
                        label = minReportsLabel(value),
                        selected = filters.minReports == value,
                        onClick = { onFiltersChange(filters.copy(minReports = value)) },
                        compact = true,
                    )
                }
            }
            FilterToggle("Here now", filters.hereNow) { onFiltersChange(filters.copy(hereNow = it)) }
            FilterToggle("Events today", filters.eventsToday) { onFiltersChange(filters.copy(eventsToday = it)) }
            FilterToggle("Open now", filters.openNow) { onFiltersChange(filters.copy(openNow = it)) }

            FilterSectionTitle("Energy")
            EnergyLabel.entries.forEach { label ->
                FilterToggle(label.title, label in filters.energies) { on ->
                    onFiltersChange(filters.copy(energies = if (on) filters.energies + label else filters.energies - label))
                }
            }

            FilterSectionTitle("You and your Clicks")
            FilterToggle("I've been here", filters.beenHere) { onFiltersChange(filters.copy(beenHere = it)) }
            FilterToggle("My Clicks have been here", filters.clicksBeenHere) { onFiltersChange(filters.copy(clicksBeenHere = it)) }
            FilterToggle("Has a Place Hub", filters.hasHub) { onFiltersChange(filters.copy(hasHub = it)) }

            FilterSectionTitle("Category")
            PlaceCategory.entries.forEach { category ->
                FilterToggle(category.label, category in filters.categories) { on ->
                    onFiltersChange(
                        filters.copy(categories = if (on) filters.categories + category else filters.categories - category),
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FilterSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { onCheckedChange(!checked) }
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * One Place in the Nearby list (iOS `NearbyRow` for `.place`): name, the map subtitle (accent
 * while a Pulse or event is live), address or city, and distance.
 */
@Composable
internal fun DiscoveryPlaceCard(
    item: DiscoveryFeedItem.Place,
    distanceText: String?,
    onOpen: () -> Unit,
) {
    val place = item.place
    val shape = RoundedCornerShape(16.dp)
    val live = place.pulse.isLive || place.nextEvent?.isLive == true
    val subtitle = remember(place) { PlaceCopy.mapSubtitle(place, Clock.System.now().toEpochMilliseconds()) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(clickCardSurface())
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .clickable(onClick = onOpen)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
            color = if (live) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val line = listOfNotNull(place.addressLine ?: place.city, distanceText).joinToString(" · ")
        if (line.isNotEmpty()) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
