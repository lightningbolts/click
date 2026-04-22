package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret

/**
 * Beacon drop types exposed in the map FAB flow.
 */
enum class BeaconDropCategory {
    SOUNDTRACK,
    UTILITY,
    SOS,
    STUDY,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BeaconDropSheetContent(
    errorMessage: String?,
    onDismissError: () -> Unit,
    onSubmit: (MapBeaconKind, String) -> Unit,
) {
    val category = remember { mutableStateOf(BeaconDropCategory.SOUNDTRACK) }
    val text = remember { mutableStateOf("") }
    val kind = when (category.value) {
        BeaconDropCategory.SOUNDTRACK -> MapBeaconKind.SOUNDTRACK
        BeaconDropCategory.UTILITY -> MapBeaconKind.UTILITY
        BeaconDropCategory.SOS -> MapBeaconKind.SOS
        BeaconDropCategory.STUDY -> MapBeaconKind.STUDY
    }
    val label = when (category.value) {
        BeaconDropCategory.SOUNDTRACK -> "Spotify or Apple Music link"
        BeaconDropCategory.UTILITY -> "What’s here? (max 140)"
        BeaconDropCategory.SOS -> "SOS message (max 140)"
        BeaconDropCategory.STUDY -> "Study spot note (max 140)"
    }
    val maxLen = if (category.value == BeaconDropCategory.SOUNDTRACK) 2000 else 140

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Drop a community beacon",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BeaconDropCategory.entries.forEach { cat ->
                FilterChip(
                    selected = category.value == cat,
                    onClick = {
                        category.value = cat
                        val newMaxLen = if (cat == BeaconDropCategory.SOUNDTRACK) 2000 else 140
                        if (text.value.length > newMaxLen) text.value = text.value.take(newMaxLen)
                        onDismissError()
                    },
                    label = {
                        Text(
                            when (cat) {
                                BeaconDropCategory.SOUNDTRACK -> "Soundtrack"
                                BeaconDropCategory.UTILITY -> "Utility"
                                BeaconDropCategory.SOS -> "SOS"
                                BeaconDropCategory.STUDY -> "Study"
                            },
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
        OutlinedTextField(
            value = text.value,
            onValueChange = {
                if (it.length <= maxLen) {
                    text.value = it
                    onDismissError()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = category.value == BeaconDropCategory.SOUNDTRACK,
            minLines = if (category.value == BeaconDropCategory.SOUNDTRACK) 1 else 3,
            maxLines = if (category.value == BeaconDropCategory.SOUNDTRACK) 3 else 6,
        )
        errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = { onSubmit(kind, text.value) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Drop pin")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
