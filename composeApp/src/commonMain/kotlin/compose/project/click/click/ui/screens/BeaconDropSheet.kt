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
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.viewmodel.AvailabilityIntentDuration // pragma: allowlist secret

/**
 * Beacon drop types exposed in the map FAB flow.
 */
enum class BeaconDropCategory {
    SOUNDTRACK,
    HAZARD,
    UTILITY,
    SOS,
    STUDY,
    COMMUNITY_HUB,
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
    onSubmit: (MapBeaconKind, String, ttlMs: Long?, onRejectedEarly: () -> Unit) -> Unit,
    onCreateHub: (name: String, category: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var isSubmitting by remember { mutableStateOf(false) }
    val category = remember { mutableStateOf(BeaconDropCategory.SOUNDTRACK) }
    val text = remember { mutableStateOf("") }
    val expiration = remember { mutableStateOf(AvailabilityIntentDuration.THREE_HOURS) }

    var hubNameDraft by remember { mutableStateOf("") }
    var hubCategory by remember { mutableStateOf(hubCategoryOptions.first()) }

    val isHubMode = category.value == BeaconDropCategory.COMMUNITY_HUB

    val kind = when (category.value) {
        BeaconDropCategory.SOUNDTRACK -> MapBeaconKind.SOUNDTRACK
        BeaconDropCategory.HAZARD -> MapBeaconKind.HAZARD
        BeaconDropCategory.UTILITY -> MapBeaconKind.UTILITY
        BeaconDropCategory.SOS -> MapBeaconKind.SOS
        BeaconDropCategory.STUDY -> MapBeaconKind.STUDY
        BeaconDropCategory.COMMUNITY_HUB -> MapBeaconKind.OTHER
    }
    val beaconLabel = when (category.value) {
        BeaconDropCategory.SOUNDTRACK -> "Spotify, Apple Music, or YouTube link"
        BeaconDropCategory.HAZARD -> "Hazard note (max 140)"
        BeaconDropCategory.UTILITY -> "What's here? (max 140)"
        BeaconDropCategory.SOS -> "SOS message (max 140)"
        BeaconDropCategory.STUDY -> "Study spot note (max 140)"
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
                        items = AvailabilityIntentDuration.entries.toList(),
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
                    onSubmit(kind, text.value, ttl) {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting && if (isHubMode) hubNameDraft.isNotBlank() else true,
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
