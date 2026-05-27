package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalGlassAlertAnimatedDismiss // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

/**
 * Destructive / confirm flows shown after the connection action sheet has dismissed
 * so the sheet scrim cannot block taps on the alert.
 */
internal sealed class ConnectionSheetDialog {
    data object Remove : ConnectionSheetDialog()
    data object Block : ConnectionSheetDialog()
    data class Report(val reason: String = "") : ConnectionSheetDialog()
    data object LeaveGroup : ConnectionSheetDialog()
    data object DeleteGroup : ConnectionSheetDialog()
}

@Composable
internal fun ConnectionSheetDialogs(
    dialog: ConnectionSheetDialog?,
    onDismiss: () -> Unit,
    onConfirmRemove: () -> Unit,
    onConfirmBlock: () -> Unit,
    onConfirmReport: (String) -> Unit,
    onConfirmLeaveGroup: () -> Unit,
    onConfirmDeleteGroup: () -> Unit,
) {
    when (val d = dialog) {
        null -> Unit
        ConnectionSheetDialog.Remove -> {
            GlassAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Remove Connection?") },
                text = {
                    Text(
                        "This will permanently remove this connection and all messages. This cannot be undone.",
                    )
                },
                confirmButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(onClick = { onConfirmRemove(); dismissAnimated() }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(onClick = dismissAnimated) {
                        Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                    }
                },
            )
        }
        ConnectionSheetDialog.Block -> {
            GlassAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Block User?") },
                text = {
                    Text(
                        "They won't be able to contact you and this connection will be removed. This cannot be undone.",
                    )
                },
                confirmButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(onClick = { onConfirmBlock(); dismissAnimated() }) {
                        Text("Block", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(onClick = dismissAnimated) {
                        Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                    }
                },
            )
        }
        is ConnectionSheetDialog.Report -> {
            var reportReason by remember(d) { mutableStateOf(d.reason) }
            GlassAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Report User") },
                text = {
                    Column {
                        Text(
                            "Please describe the issue:",
                            color = GlassSheetTokens.OnOledMuted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        OutlinedTextField(
                            value = reportReason,
                            onValueChange = { reportReason = it },
                            placeholder = {
                                Text(
                                    "Reason for report...",
                                    color = GlassSheetTokens.OnOledMuted.copy(alpha = 0.5f),
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GlassSheetTokens.OnOled,
                                unfocusedTextColor = GlassSheetTokens.OnOled,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = GlassSheetTokens.GlassBorder,
                                cursorColor = PrimaryBlue,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(
                        onClick = {
                            val trimmed = reportReason.trim()
                            if (trimmed.isNotBlank()) {
                                onConfirmReport(trimmed)
                                dismissAnimated()
                            }
                        },
                        enabled = reportReason.isNotBlank(),
                    ) {
                        Text("Submit", color = Color(0xFFFF8C00))
                    }
                },
                dismissButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(onClick = dismissAnimated) {
                        Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                    }
                },
            )
        }
        ConnectionSheetDialog.LeaveGroup -> {
            GlassAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Leave group?") },
                text = { Text("You will lose access to this verified click and its messages.") },
                confirmButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(onClick = { onConfirmLeaveGroup(); dismissAnimated() }) {
                        Text("Leave", color = Color(0xFFFF4444))
                    }
                },
                dismissButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(onClick = dismissAnimated) {
                        Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                    }
                },
            )
        }
        ConnectionSheetDialog.DeleteGroup -> {
            GlassAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Delete group?") },
                text = {
                    Text("Permanently deletes this verified click for everyone. This cannot be undone.")
                },
                confirmButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(onClick = { onConfirmDeleteGroup(); dismissAnimated() }) {
                        Text("Delete", color = Color(0xFFFF4444))
                    }
                },
                dismissButton = {
                    val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                    TextButton(onClick = dismissAnimated) {
                        Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                    }
                },
            )
        }
    }
}
