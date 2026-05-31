package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.BeaconVisibilityAudience // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret

/**
 * Beacon drop types exposed in the map FAB flow.
 */
enum class BeaconDropCategory {
    SOUNDTRACK,
    HAZARD,
    UTILITY,
    SOS,
    STUDY,
    EVENT,
    COMMUNITY_HUB,
}

/**
 * Beacon time-to-live presets. Independent of availability-intent durations so beacons can live up
 * to 7 days (backend caps at 30 days) while keeping the full short-duration granularity. The label
 * maps directly to the chip text.
 */
enum class BeaconDuration(val durationMs: Long, val label: String) {
    FIFTEEN_MIN(15L * 60_000L, "15 min"),
    THIRTY_MIN(30L * 60_000L, "30 min"),
    FORTY_FIVE_MIN(45L * 60_000L, "45 min"),
    ONE_HOUR(60L * 60_000L, "1 hour"),
    NINETY_MIN(90L * 60_000L, "90 min"),
    TWO_HOURS(2L * 60L * 60_000L, "2 hours"),
    THREE_HOURS(3L * 60L * 60_000L, "3 hours"),
    SIX_HOURS(6L * 60L * 60_000L, "6 hours"),
    TWENTY_FOUR_HOURS(24L * 60L * 60_000L, "24 hours"),
    TWO_DAYS(2L * 24L * 60L * 60_000L, "2 days"),
    THREE_DAYS(3L * 24L * 60L * 60_000L, "3 days"),
    FOUR_DAYS(4L * 24L * 60L * 60_000L, "4 days"),
    FIVE_DAYS(5L * 24L * 60L * 60_000L, "5 days"),
    SIX_DAYS(6L * 24L * 60L * 60_000L, "6 days"),
    SEVEN_DAYS(7L * 24L * 60L * 60_000L, "7 days"),
}

private val hubCategoryOptions = listOf(
    "general", "music", "study", "sports", "food", "nightlife",
    "gaming", "tech", "art", "fitness", "networking", "party",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BeaconDropSheetContent(
    errorMessage: String?,
    onDismissError: () -> Unit,
    onSubmit: (
        MapBeaconKind,
        String,
        ttlMs: Long?,
        showCreatorName: Boolean,
        visibilityAudience: BeaconVisibilityAudience,
        onRejectedEarly: () -> Unit,
    ) -> Unit,
    onCreateHub: (name: String, category: String) -> Unit = { _, _ -> },
    submitLocked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var isSubmitting by remember { mutableStateOf(false) }
    val category = remember { mutableStateOf(BeaconDropCategory.SOUNDTRACK) }
    val text = remember { mutableStateOf("") }
    val expiration = remember { mutableStateOf(BeaconDuration.THREE_HOURS) }

    var hubNameDraft by remember { mutableStateOf("") }
    var hubCategory by remember { mutableStateOf(hubCategoryOptions.first()) }
    var showCreatorName by remember { mutableStateOf(false) }
    var visibilityAudience by remember { mutableStateOf(BeaconVisibilityAudience.EVERYONE) }

    val isHubMode = category.value == BeaconDropCategory.COMMUNITY_HUB

    val kind = when (category.value) {
        BeaconDropCategory.SOUNDTRACK -> MapBeaconKind.SOUNDTRACK
        BeaconDropCategory.HAZARD -> MapBeaconKind.HAZARD
        BeaconDropCategory.UTILITY -> MapBeaconKind.UTILITY
        BeaconDropCategory.SOS -> MapBeaconKind.SOS
        BeaconDropCategory.STUDY -> MapBeaconKind.STUDY
        BeaconDropCategory.EVENT -> MapBeaconKind.EVENT
        BeaconDropCategory.COMMUNITY_HUB -> MapBeaconKind.OTHER
    }
    val beaconLabel = when (category.value) {
        BeaconDropCategory.SOUNDTRACK -> "Spotify, Apple Music, or YouTube link"
        BeaconDropCategory.HAZARD -> "Hazard note (max 140)"
        BeaconDropCategory.UTILITY -> "What's here? (max 140)"
        BeaconDropCategory.SOS -> "SOS message (max 140)"
        BeaconDropCategory.STUDY -> "Study spot note (max 140)"
        BeaconDropCategory.EVENT -> "Event details (max 140)"
        BeaconDropCategory.COMMUNITY_HUB -> ""
    }
    val maxLen = if (category.value == BeaconDropCategory.SOUNDTRACK) 2000 else 140
    val chipContainer = MaterialTheme.colorScheme.surfaceContainerHighest
    val chipSelected = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Drop a community beacon",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = BeaconDropCategory.entries.toList(),
                key = { it.name },
            ) { cat ->
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
                                BeaconDropCategory.HAZARD -> "Hazard"
                                BeaconDropCategory.UTILITY -> "Utility"
                                BeaconDropCategory.SOS -> "SOS"
                                BeaconDropCategory.STUDY -> "Study"
                                BeaconDropCategory.EVENT -> "Event"
                                BeaconDropCategory.COMMUNITY_HUB -> "Hub"
                            },
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = chipContainer,
                        selectedContainerColor = chipSelected,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }

        if (isHubMode) {
            OutlinedTextField(
                value = hubNameDraft,
                onValueChange = { hubNameDraft = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hub name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hubCategoryOptions.forEach { c ->
                    FilterChip(
                        selected = hubCategory == c,
                        onClick = { hubCategory = c },
                        label = { Text(c.replaceFirstChar { ch -> ch.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = chipContainer,
                            selectedContainerColor = chipSelected,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        } else {
            if (category.value != BeaconDropCategory.SOUNDTRACK) {
                Text(
                    text = "Visible for",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = BeaconDuration.entries.toList(),
                        key = { it.name },
                    ) { opt ->
                        FilterChip(
                            selected = expiration.value == opt,
                            onClick = {
                                expiration.value = opt
                                onDismissError()
                            },
                            label = { Text(opt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = chipContainer,
                                selectedContainerColor = chipSelected,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }

            val isUrlField = category.value == BeaconDropCategory.SOUNDTRACK
            OutlinedTextField(
                value = text.value,
                onValueChange = {
                    if (it.length <= maxLen) {
                        text.value = it
                        onDismissError()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(beaconLabel) },
                singleLine = isUrlField,
                keyboardOptions = if (isUrlField) {
                    KeyboardOptions(keyboardType = KeyboardType.Uri)
                } else {
                    KeyboardOptions.Default
                },
                minLines = if (isUrlField) 1 else 3,
                maxLines = if (isUrlField) 3 else 6,
                trailingIcon = if (isUrlField) {
                    {
                        IconButton(
                            onClick = {
                                clipboardManager.getText()?.text?.let { pasted ->
                                    text.value = pasted.trim()
                                    onDismissError()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Paste link",
                            )
                        }
                    }
                } else {
                    null
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }

        if (!isHubMode) {
            Text(
                text = "Who can see this",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = BeaconVisibilityAudience.entries.toList(),
                    key = { it.name },
                ) { option ->
                    FilterChip(
                        selected = visibilityAudience == option,
                        onClick = { visibilityAudience = option },
                        label = {
                            Text(
                                when (option) {
                                    BeaconVisibilityAudience.EVERYONE -> "Everyone"
                                    BeaconVisibilityAudience.CONNECTIONS -> "Connections only"
                                    BeaconVisibilityAudience.CORE_CONNECTIONS -> "Core connections only"
                                },
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = chipContainer,
                            selectedContainerColor = chipSelected,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Display my name",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Show your name on the map pin for others nearby.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showCreatorName,
                    onCheckedChange = { showCreatorName = it },
                )
            }
        }

        errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = {
                if (isSubmitting) return@Button
                isSubmitting = true
                if (isHubMode) {
                    onCreateHub(hubNameDraft.trim(), hubCategory)
                    isSubmitting = false
                } else {
                    val ttl = if (category.value == BeaconDropCategory.SOUNDTRACK) {
                        null
                    } else {
                        expiration.value.durationMs
                    }
                    onSubmit(kind, text.value, ttl, showCreatorName, visibilityAudience) {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting && !submitLocked && if (isHubMode) hubNameDraft.isNotBlank() else true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(if (isHubMode) "Create hub" else "Drop pin")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
