package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import compose.project.click.click.data.ContextTagTaxonomy
import compose.project.click.click.data.models.ContextTag
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val CUSTOM_CONTEXT_MAX_LENGTH = 48

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConnectionContextSheet(
    otherUserName: String?,
    locationName: String?,
    initialNoiseOptIn: Boolean,
    noisePermissionGranted: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ContextTag?, Boolean) -> Unit
) {
    val hourOfDay = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    }
    val suggestions = remember(locationName, hourOfDay) {
        ContextTagTaxonomy.suggest(locationName = locationName, hourOfDay = hourOfDay)
    }
    val allTags = remember { ContextTagTaxonomy.all }
    var selectedTagId by remember { mutableStateOf<String?>(suggestions.firstOrNull()?.id) }
    var customTagText by remember { mutableStateOf("") }
    var ambientNoiseOptIn by remember(initialNoiseOptIn) { mutableStateOf(initialNoiseOptIn) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isCustomSelectionInvalid = selectedTagId == "custom" && customTagText.isBlank()

    fun resolveSelectedTag(): ContextTag? {
        return if (selectedTagId == "custom") {
            customTagText.trim().takeIf { it.isNotEmpty() }?.let {
                ContextTag(id = "custom", label = it, emoji = "✏️")
            }
        } else {
            allTags.firstOrNull { it.id == selectedTagId }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (otherUserName.isNullOrBlank()) "Add context" else "Add context for $otherUserName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Pick what best describes this physical encounter. You can leave it blank and keep going.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!locationName.isNullOrBlank()) {
                Text(
                    text = "Location hint: $locationName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Suggested",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { tag ->
                    FilterChip(
                        selected = selectedTagId == tag.id,
                        onClick = { selectedTagId = tag.id },
                        label = { Text("${tag.emoji} ${tag.label}") }
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "All tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTagId == tag.id,
                        onClick = { selectedTagId = tag.id },
                        label = { Text("${tag.emoji} ${tag.label}") }
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Custom activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "If none of the presets fit, write what you were doing. Short, natural labels work best.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilterChip(
                selected = selectedTagId == "custom",
                onClick = { selectedTagId = "custom" },
                label = { Text("✏️ Write your own") }
            )

            OutlinedTextField(
                value = customTagText,
                onValueChange = { input ->
                    customTagText = input
                        .replace(Regex("\\s+"), " ")
                        .trimStart()
                        .take(CUSTOM_CONTEXT_MAX_LENGTH)
                    if (customTagText.isNotBlank()) {
                        selectedTagId = "custom"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom activity") },
                placeholder = { Text("Dorm lounge, coffee line, hackathon kickoff...") },
                supportingText = {
                    Text(
                        text = if (isCustomSelectionInvalid) {
                            "Add a quick label before continuing."
                        } else {
                            "${customTagText.length}/$CUSTOM_CONTEXT_MAX_LENGTH characters"
                        }
                    )
                },
                isError = isCustomSelectionInvalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                )
            )

            HorizontalDivider()

            Text(
                text = "Ambient noise",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = when {
                    ambientNoiseOptIn && noisePermissionGranted ->
                        "Ambient sound enrichment is enabled from onboarding. Click stores only a 2-second noise category for this encounter."
                    ambientNoiseOptIn ->
                        "Ambient sound enrichment is enabled, but microphone permission is unavailable right now so Click will skip it for this encounter."
                    else ->
                        "Ambient sound enrichment is currently off. You can change it later in Settings."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Skip")
                }
                Button(
                    onClick = { onConfirm(resolveSelectedTag(), ambientNoiseOptIn) },
                    modifier = Modifier.weight(1f),
                    enabled = !isCustomSelectionInvalid
                ) {
                    Text("Continue")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}