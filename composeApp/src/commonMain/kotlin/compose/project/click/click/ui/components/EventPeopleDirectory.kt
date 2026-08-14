package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.events.AttendeeRelationship // pragma: allowlist secret
import compose.project.click.click.events.DirectoryAttendee // pragma: allowlist secret
import compose.project.click.click.events.EventAttendeeSortMode // pragma: allowlist secret
import compose.project.click.click.events.allowsDirectoryConnectActions // pragma: allowlist secret
import compose.project.click.click.events.directorySortMetricSubtitle // pragma: allowlist secret
import compose.project.click.click.events.everyoneExcludingMutualsSection // pragma: allowlist secret
import compose.project.click.click.events.mutualsAtEvent // pragma: allowlist secret
import compose.project.click.click.events.sortEventAttendees // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret

@Composable
fun EventPeopleDirectorySection(
    attendees: List<DirectoryAttendee>,
    loading: Boolean,
    mutualsSectionUnlocked: Boolean,
    /** True when personalized relationship metrics are available. */
    directoryEnriched: Boolean = true,
    onOpenDirectory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = clickBorderColor()
    val cardSurface = clickCardSurface()
    val preview = attendees.take(4)
    val overflow = (attendees.size - preview.size).coerceAtLeast(0)

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
                text = "PEOPLE (${attendees.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (attendees.isNotEmpty() || loading) {
                TextButton(onClick = onOpenDirectory) {
                    Text("Directory", fontWeight = FontWeight.Bold)
                }
            }
        }

        when {
            loading && attendees.isEmpty() ->
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            attendees.isEmpty() ->
                Text(
                    text = "No attendees yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    preview.forEachIndexed { index, attendee ->
                        ConnectionListUserAvatarFace(
                            displayName = attendee.name,
                            email = null,
                            avatarUrl = attendee.avatarUrl,
                            userId = attendee.userId,
                            modifier = Modifier
                                // Overlap via offset — negative padding crashes on iOS/KMP.
                                .offset(x = (-10 * index).dp)
                                .zIndex((preview.size - index).toFloat())
                                .size(48.dp)
                                .border(clickBorderWidth(), border, CircleShape)
                                .clip(CircleShape)
                                .background(cardSurface),
                        )
                    }
                    if (overflow > 0) {
                        Box(
                            modifier = Modifier
                                .offset(x = (-10 * preview.size).dp)
                                .zIndex(0f)
                                .size(48.dp)
                                .border(clickBorderWidth(), border, CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+$overflow",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                if (mutualsSectionUnlocked && directoryEnriched) {
                    val mutualCount = mutualsAtEvent(attendees).size
                    if (mutualCount > 0) {
                        Text(
                            text = "$mutualCount mutual${if (mutualCount == 1) "" else "s"} here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (mutualsSectionUnlocked && loading) {
                    Text(
                        text = "Loading mutuals…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun EventPeopleDirectorySheetContent(
    attendees: List<DirectoryAttendee>,
    loading: Boolean,
    mutualsSectionUnlocked: Boolean,
    /** False for the public RSVP fallback, which has names but no personalized metrics. */
    directoryEnriched: Boolean,
    onAttendeeClick: (DirectoryAttendee) -> Unit,
) {
    var sortMode by remember { mutableStateOf(EventAttendeeSortMode.Alphabetical) }
    val sorted = remember(attendees, sortMode) { sortEventAttendees(attendees, sortMode) }
    val mutuals = remember(attendees, mutualsSectionUnlocked) {
        if (mutualsSectionUnlocked) mutualsAtEvent(attendees) else emptyList()
    }
    val sortedMutuals = remember(mutuals, sortMode) {
        sortEventAttendees(mutuals, sortMode)
    }
    val border = clickBorderColor()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    // When Mutuals chip is selected, Everyone is already mutual-ordered — skip duplicate block.
    val showMutualsSection =
        mutualsSectionUnlocked &&
            mutuals.isNotEmpty() &&
            sortMode != EventAttendeeSortMode.MutualConnections
    val everyone = remember(sorted, showMutualsSection) {
        if (showMutualsSection) everyoneExcludingMutualsSection(sorted) else sorted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheetBodyScroll()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "People at this event",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = onSurface,
        )
        Text(
            text = "Sorted lists for planning who you might see. Mutuals are connections of your connections — view only.",
            style = MaterialTheme.typography.bodySmall,
            color = onVariant,
        )
        if (!directoryEnriched && !loading) {
            Text(
                text = "Interest and mutual counts are unavailable right now. Pull to reopen after reconnecting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortChip("A–Z", sortMode == EventAttendeeSortMode.Alphabetical) {
                sortMode = EventAttendeeSortMode.Alphabetical
            }
            SortChip("Interests", sortMode == EventAttendeeSortMode.InterestOverlap) {
                sortMode = EventAttendeeSortMode.InterestOverlap
            }
            SortChip("Mutuals", sortMode == EventAttendeeSortMode.MutualConnections) {
                sortMode = EventAttendeeSortMode.MutualConnections
            }
        }

        when {
            loading && sorted.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            sorted.isEmpty() -> {
                Text(
                    text = "No attendees yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                if (showMutualsSection) {
                    Text(
                        text = "Mutuals here",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface,
                        modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
                    )
                    sortedMutuals.forEach { attendee ->
                        DirectoryAttendeeRow(
                            attendee = attendee,
                            sortMode = EventAttendeeSortMode.MutualConnections,
                            border = border,
                            onClick = { onAttendeeClick(attendee) },
                        )
                    }
                }
                Text(
                    text = "Everyone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                    modifier = Modifier.padding(
                        bottom = 4.dp,
                        top = if (showMutualsSection) 12.dp else 4.dp,
                    ),
                )
                everyone.forEach { attendee ->
                    DirectoryAttendeeRow(
                        attendee = attendee,
                        sortMode = sortMode,
                        border = border,
                        onClick = { onAttendeeClick(attendee) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val border = clickBorderColor()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        clickCardSurface()
    }
    val stroke = if (selected) MaterialTheme.colorScheme.primary else border
    Box(
        modifier = Modifier
            .border(clickBorderWidth(), stroke, RoundedCornerShape(20.dp))
            .background(bg, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else onSurface,
        )
    }
}

@Composable
private fun DirectoryAttendeeRow(
    attendee: DirectoryAttendee,
    sortMode: EventAttendeeSortMode,
    border: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val accentBorder = when (attendee.relationship) {
        AttendeeRelationship.Connection -> MaterialTheme.colorScheme.primary
        AttendeeRelationship.Mutual -> MaterialTheme.colorScheme.outline
        else -> border
    }
    val subtitle = directorySortMetricSubtitle(attendee, sortMode)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(clickBorderWidth(), accentBorder, RoundedCornerShape(12.dp))
            .background(clickCardSurface(), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionListUserAvatarFace(
            displayName = attendee.name,
            email = null,
            avatarUrl = attendee.avatarUrl,
            userId = attendee.userId,
            modifier = Modifier
                .size(44.dp)
                .border(clickBorderWidth(), accentBorder, CircleShape)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attendee.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sortMode == EventAttendeeSortMode.InterestOverlap &&
                        attendee.sharedInterestCount > 0
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        onVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!allowsDirectoryConnectActions(attendee.relationship) &&
            attendee.relationship != AttendeeRelationship.Self
        ) {
            Text(
                text = "View",
                style = MaterialTheme.typography.labelMedium,
                color = onVariant,
            )
        }
    }
}
