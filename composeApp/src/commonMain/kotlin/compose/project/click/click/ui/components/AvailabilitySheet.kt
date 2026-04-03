package compose.project.click.click.ui.components // pragma: allowlist secret

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import compose.project.click.click.viewmodel.AvailabilityIntentDuration // pragma: allowlist secret
import compose.project.click.click.viewmodel.AvailabilityViewModel // pragma: allowlist secret

/**
 * Bottom sheet to post a short intent tag for a fixed time window ([public.availability_intents]).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AvailabilitySheet(
    viewModel: AvailabilityViewModel,
    onDismiss: () -> Unit,
) {
    val tag by viewModel.intentTagInput.collectAsState()
    val duration by viewModel.intentDuration.collectAsState()
    val submitting by viewModel.intentSubmitting.collectAsState()
    val submitError by viewModel.intentSubmitError.collectAsState()

    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true)
    val canSubmit = tag.trim().isNotEmpty() && !submitting

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Share availability",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Pick how long you're open, and a short tag so connections know what you're up for.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Timeframe",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvailabilityIntentDuration.entries.forEach { option ->
                    FilterChip(
                        selected = duration == option,
                        onClick = { viewModel.setIntentDuration(option) },
                        enabled = !submitting,
                        label = { Text(option.label) },
                    )
                }
            }

            OutlinedTextField(
                value = tag,
                onValueChange = viewModel::updateIntentTagInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Intent tag") },
                placeholder = { Text("Coffee, study, walk…") },
                supportingText = {
                    Text("${tag.length}/${AvailabilityViewModel.AVAILABILITY_INTENT_TAG_MAX_LENGTH}")
                },
                singleLine = true,
                enabled = !submitting,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )

            submitError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        viewModel.clearIntentSubmitError()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !submitting,
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        viewModel.submitAvailabilityIntent(onSuccess = onDismiss)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit,
                ) {
                    Text(if (submitting) "Saving…" else "Post")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
