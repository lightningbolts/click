package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import compose.project.click.click.ui.components.GlassAdaptiveBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.viewmodel.AvailabilityIntentDuration // pragma: allowlist secret
import compose.project.click.click.viewmodel.AvailabilityViewModel // pragma: allowlist secret
import kotlinx.coroutines.launch

/**
 * Availability intent editor — same shell as Memory Map’s connection sheet ([MapScreen] + [AdaptiveBottomSheet]):
 * slide up/down, drag handle, [surfaceContainerHigh], and matching insets/padding.
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
    val editingIntentId by viewModel.editingAvailabilityIntentId.collectAsState()
    val isEditing = !editingIntentId.isNullOrBlank()

    val canSubmit = tag.trim().isNotEmpty() && !submitting

    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun dismissWithSheetAnimation() {
        scope.launch {
            try {
                sheetState.hide()
            } catch (_: Exception) {
            }
        }.invokeOnCompletion {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        try {
            sheetState.show()
        } catch (_: Exception) {
        }
    }

    GlassAdaptiveBottomSheet(
        onDismissRequest = { dismissWithSheetAnimation() },
        adaptiveSheetState = sheetState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(GlassSheetTokens.OledBlack),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (isEditing) "Edit availability" else "Share availability",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GlassSheetTokens.OnOled,
                )
                Text(
                    text = if (isEditing) {
                        "Time window starts again from now with the length you pick. Update your tag or timeframe below."
                    } else {
                        "Pick how long you're open, and a short tag so connections know what you're up for."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassSheetTokens.OnOledMuted,
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
                            dismissWithSheetAnimation()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !submitting,
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            viewModel.submitAvailabilityIntent(onSuccess = { dismissWithSheetAnimation() })
                        },
                        modifier = Modifier.weight(1f),
                        enabled = canSubmit,
                    ) {
                        Text(
                            when {
                                submitting -> "Saving…"
                                isEditing -> "Save"
                                else -> "Post"
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
