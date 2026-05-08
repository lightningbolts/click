package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import compose.project.click.click.ui.components.BentoGlassOptionRow // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassModalBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import kotlinx.coroutines.launch

/**
 * Bottom sheet for connection-level actions (nudge, archive, remove,
 * report, block; group-specific: leave, delete). Used both from the
 * Connections-list overflow menu and from within an open chat. All
 * actions are full callbacks so this composable stays
 * ViewModel-agnostic.
 *
 * Every destructive primary option funnels through a second
 * "openFinalConfirm" dialog before invoking its callback.
 *
 * Extracted verbatim from ConnectionsScreen.kt; no behavior change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionActionSheet(
    chatDetails: ChatWithDetails?,
    currentUserId: String?,
    isArchived: Boolean = false,
    isServerLifecycleArchived: Boolean = false,
    onDismiss: () -> Unit,
    onNudge: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onArchive: () -> Unit = {},
    onUnarchive: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReport: (String) -> Unit = {},
    onBlock: () -> Unit = {},
    onLeaveGroup: () -> Unit = {},
    onDeleteGroup: () -> Unit = {},
) {
    @Suppress("UNUSED_PARAMETER") val unusedOpenChat = onOpenChat
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val isGroup = chatDetails?.groupClique != null
    val uid = currentUserId.orEmpty()
    val isGroupCreator = isGroup && uid.isNotBlank() && chatDetails?.groupClique?.createdByUserId == uid
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showUnarchiveConfirm by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var showFinalConfirm by remember { mutableStateOf(false) }
    var finalConfirmTitle by remember { mutableStateOf("") }
    var finalConfirmBody by remember { mutableStateOf("") }
    var finalConfirmButtonLabel by remember { mutableStateOf("") }
    var finalConfirmButtonColor by remember { mutableStateOf(PrimaryBlue) }
    var finalConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun openFinalConfirm(
        title: String,
        body: String,
        buttonLabel: String,
        buttonColor: Color,
        action: () -> Unit,
    ) {
        finalConfirmTitle = title
        finalConfirmBody = body
        finalConfirmButtonLabel = buttonLabel
        finalConfirmButtonColor = buttonColor
        finalConfirmAction = action
        showFinalConfirm = true
    }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(GlassSheetTokens.OledBlack)
                .padding(bottom = 32.dp),
        ) {
            chatDetails?.let { details ->
                val title = if (isGroup) {
                    details.groupClique?.name?.trim()?.ifBlank { null } ?: "Verified click"
                } else {
                    details.otherUser.name ?: "Connection"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassSheetTokens.OnOled,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .align(Alignment.CenterHorizontally),
                )
                HorizontalDivider(color = GlassSheetTokens.GlassBorder.copy(alpha = 0.5f))
            }

            if (!isGroup) {
                BentoGlassOptionRow(
                    title = "Nudge 👋",
                    subtitle = "Send a quick ping",
                    onClick = {
                        onNudge()
                        dismiss()
                    },
                )

                if (isArchived) {
                    BentoGlassOptionRow(
                        title = "Unarchive",
                        subtitle = if (isServerLifecycleArchived) {
                            "Remove from your Archived tab (server-archived connections stay read-only)"
                        } else {
                            "Move this connection back to Active"
                        },
                        onClick = { showUnarchiveConfirm = true },
                        leading = {
                            Icon(
                                Icons.Default.Unarchive,
                                contentDescription = null,
                                tint = GlassSheetTokens.OnOledMuted,
                            )
                        },
                    )
                } else if (!isServerLifecycleArchived) {
                    BentoGlassOptionRow(
                        title = "Archive",
                        subtitle = "Hide this connection (recoverable)",
                        onClick = { showArchiveConfirm = true },
                        leading = {
                            Icon(
                                Icons.Default.Archive,
                                contentDescription = null,
                                tint = GlassSheetTokens.OnOledMuted,
                            )
                        },
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = GlassSheetTokens.GlassBorder.copy(alpha = 0.35f),
                )

                BentoGlassOptionRow(
                    title = "Remove Connection",
                    subtitle = "Permanently remove this chat",
                    onClick = { showDeleteConfirm = true },
                    destructive = true,
                    leading = {
                        Icon(
                            Icons.Default.PersonRemove,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                        )
                    },
                )

                BentoGlassOptionRow(
                    title = "Report",
                    subtitle = "Flag for review",
                    onClick = { showReportDialog = true },
                    titleColor = Color(0xFFFF8C00),
                    leading = {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color(0xFFFF8C00),
                        )
                    },
                )

                BentoGlassOptionRow(
                    title = "Block",
                    subtitle = "They can no longer reach you",
                    onClick = { showBlockConfirm = true },
                    destructive = true,
                    leading = {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                        )
                    },
                )
            } else {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = GlassSheetTokens.GlassBorder.copy(alpha = 0.35f),
                )
                BentoGlassOptionRow(
                    title = "Leave Group",
                    subtitle = "Lose access to this verified click",
                    onClick = {
                        openFinalConfirm(
                            title = "Leave group?",
                            body = "You will lose access to this verified click and its messages.",
                            buttonLabel = "Leave",
                            buttonColor = Color(0xFFFF4444),
                        ) {
                            onLeaveGroup()
                            dismiss()
                        }
                    },
                    leading = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = GlassSheetTokens.OnOledMuted,
                        )
                    },
                )
                if (isGroupCreator) {
                    BentoGlassOptionRow(
                        title = "Delete Group",
                        subtitle = "Remove for everyone",
                        onClick = {
                            openFinalConfirm(
                                title = "Delete group?",
                                body = "Permanently deletes this verified click for everyone. This cannot be undone.",
                                buttonLabel = "Delete",
                                buttonColor = Color(0xFFFF4444),
                            ) {
                                onDeleteGroup()
                                dismiss()
                            }
                        },
                        destructive = true,
                        leading = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                            )
                        },
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
            )
        }
    }

    if (showUnarchiveConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showUnarchiveConfirm = false },
            title = { Text("Unarchive Connection?") },
            text = { Text("This connection will return to your Active list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnarchiveConfirm = false
                        openFinalConfirm(
                            title = "Confirm Unarchive",
                            body = "Move this connection back to Active now?",
                            buttonLabel = "Yes, Unarchive",
                            buttonColor = PrimaryBlue,
                        ) {
                            onUnarchive()
                        }
                    },
                ) {
                    Text("Unarchive", color = GlassSheetTokens.OnOled)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveConfirm = false }) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                }
            },
        )
    }

    if (showArchiveConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive Connection?") },
            text = { Text("This connection will be hidden from your list. You can recover it later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveConfirm = false
                        openFinalConfirm(
                            title = "Confirm Archive",
                            body = "Archive this connection now? You can unarchive it later.",
                            buttonLabel = "Yes, Archive",
                            buttonColor = PrimaryBlue,
                        ) {
                            onArchive()
                        }
                    },
                ) {
                    Text("Archive", color = GlassSheetTokens.OnOled)
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                }
            },
        )
    }

    if (showBlockConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block User?") },
            text = {
                Text("They won't be able to contact you and this connection will be removed. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockConfirm = false
                        openFinalConfirm(
                            title = "Confirm Block",
                            body = "Block this user and remove this connection? This cannot be undone.",
                            buttonLabel = "Yes, Block",
                            buttonColor = Color(0xFFFF4444),
                        ) {
                            onBlock()
                        }
                    },
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                }
            },
        )
    }

    if (showDeleteConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Connection?") },
            text = {
                Text("This will permanently remove this connection and all messages. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        openFinalConfirm(
                            title = "Confirm Remove",
                            body = "Permanently remove this connection and all messages? This cannot be undone.",
                            buttonLabel = "Yes, Remove",
                            buttonColor = Color(0xFFFF4444),
                        ) {
                            onDelete()
                        }
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                }
            },
        )
    }

    if (showReportDialog) {
        GlassAlertDialog(
            onDismissRequest = { showReportDialog = false },
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
                TextButton(
                    onClick = {
                        if (reportReason.isNotBlank()) {
                            showReportDialog = false
                            val reasonToSubmit = reportReason.trim()
                            openFinalConfirm(
                                title = "Confirm Report",
                                body = "Submit this report for review?",
                                buttonLabel = "Yes, Report",
                                buttonColor = Color(0xFFFF8C00),
                            ) {
                                onReport(reasonToSubmit)
                            }
                        }
                    },
                ) {
                    Text("Submit", color = Color(0xFFFF8C00))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                }
            },
        )
    }

    if (showFinalConfirm) {
        GlassAlertDialog(
            onDismissRequest = {
                showFinalConfirm = false
                finalConfirmAction = null
            },
            title = { Text(finalConfirmTitle) },
            text = { Text(finalConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        finalConfirmAction?.invoke()
                        showFinalConfirm = false
                        finalConfirmAction = null
                        dismiss()
                    },
                ) {
                    Text(finalConfirmButtonLabel, color = finalConfirmButtonColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFinalConfirm = false
                        finalConfirmAction = null
                    },
                ) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                }
            },
        )
    }
}
