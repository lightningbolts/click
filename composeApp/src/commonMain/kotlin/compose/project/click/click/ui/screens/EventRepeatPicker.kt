@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.events.EVENT_OCCURRENCE_RANGE // pragma: allowlist secret
import compose.project.click.click.events.EventRecurrenceFrequency // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickChip // pragma: allowlist secret

/** Create-only: a repeating event becomes one event per occurrence, created together. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventRepeatPicker(
    frequency: EventRecurrenceFrequency?,
    occurrences: Int,
    onFrequency: (EventRecurrenceFrequency?) -> Unit,
    onOccurrences: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Repeats",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClickChip(label = "Never", selected = frequency == null, onClick = { onFrequency(null) }, compact = true)
            EventRecurrenceFrequency.entries.forEach { option ->
                ClickChip(
                    label = option.label,
                    selected = frequency == option,
                    onClick = { onFrequency(option) },
                    compact = true,
                )
            }
        }
        if (frequency != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onOccurrences(occurrences - 1) },
                    enabled = occurrences > EVENT_OCCURRENCE_RANGE.first,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Fewer events")
                }
                Text(
                    text = "$occurrences events",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = { onOccurrences(occurrences + 1) },
                    enabled = occurrences < EVENT_OCCURRENCE_RANGE.last,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "More events")
                }
            }
            Text(
                text = "Each date gets its own event page, RSVPs, and chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
