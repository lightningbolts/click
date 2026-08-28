@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit // pragma: allowlist secret
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.ActiveHubEntry // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.ui.chat.connectionRowPressGestures // pragma: allowlist secret
import compose.project.click.click.ui.chat.connectionRowPressHighlight // pragma: allowlist secret
import compose.project.click.click.ui.components.BentoGlassOptionRow // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickActionBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickInsetDivider // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickListRowShimmer // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickPlatformListRowHeight // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalGlassAlertAnimatedDismiss // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedPopupFormDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.platformPressScale // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetPageBackground // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HubActionSheet(
    hub: ActiveHubEntry,
    currentUserId: String?,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var details by remember(hub.hubId) {
        mutableStateOf<ChatApiClient.HubDetailsDto?>(null)
    }
    var detailsLoadAttempted by remember(hub.hubId) { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var hubSheetDismissedForDialog by remember { mutableStateOf(false) }
    var confirmTitle by remember { mutableStateOf("") }
    var confirmBody by remember { mutableStateOf("") }
    var confirmButton by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editNameDraft by remember(hub.hubId) { mutableStateOf(hub.name) }
    var editCategoryDraft by remember(hub.hubId) { mutableStateOf(hub.category) }

    val resolvedCreatorId = details?.creatorId ?: hub.creatorId
    val isCreator = !currentUserId.isNullOrBlank() && currentUserId == resolvedCreatorId

    LaunchedEffect(hub.hubId) {
        viewModel.fetchActiveHubDetails(hub.hubId) { result ->
            scope.launch {
                detailsLoadAttempted = true
                result.onSuccess { loaded ->
                    details = loaded
                    editNameDraft = loaded.name
                    editCategoryDraft = loaded.category
                }
            }
        }
    }

    fun openConfirm(
        title: String,
        body: String,
        button: String,
        action: () -> Unit,
    ) {
        hubSheetDismissedForDialog = true
        confirmTitle = title
        confirmBody = body
        confirmButton = button
        confirmAction = action
        showConfirm = true
    }

    if (!hubSheetDismissedForDialog && !showEditDialog) {
        ClickActionBottomSheet(
            onDismissRequest = onDismiss,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(sheetPageBackground())
                        .padding(bottom = 32.dp),
            ) {
                val title = details?.name ?: hub.name
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassSheetTokens.OnOled(),
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .align(Alignment.CenterHorizontally),
                )
                HorizontalDivider(color = GlassSheetTokens.GlassBorder())

                BentoGlassOptionRow(
                    showBorder = false,
                    title = "Leave Hub",
                    subtitle = "Remove this hub from your list",
                    onClick = {
                        openConfirm(
                            title = "Leave hub?",
                            body = "You will leave this community hub and lose quick access from your Groups list.",
                            button = "Leave",
                        ) {
                            viewModel.leaveActiveHub(hub.hubId) { ok ->
                                if (ok) scope.launch { onDismiss() }
                            }
                        }
                    },
                    destructive = true,
                    leading = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                        )
                    },
                )

                if (isCreator) {
                    BentoGlassOptionRow(
                        showBorder = false,
                        title = "Edit Hub",
                        subtitle = "Update name and category",
                        onClick = {
                            editNameDraft = details?.name ?: hub.name
                            editCategoryDraft = details?.category ?: hub.category
                            hubSheetDismissedForDialog = true
                            showEditDialog = true
                        },
                        leading = {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = GlassSheetTokens.OnOledMuted(),
                            )
                        },
                    )

                    BentoGlassOptionRow(
                        showBorder = false,
                        title = "Delete Hub",
                        subtitle = "Kick all users and delete history",
                        onClick = {
                            openConfirm(
                                title = "Delete hub?",
                                body = "Are you sure? This will kick all users and delete the history.",
                                button = "Delete",
                            ) {
                                viewModel.deleteActiveHub(hub.hubId) { ok ->
                                    if (ok) scope.launch { onDismiss() }
                                }
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
                } else if (!detailsLoadAttempted) {
                    Text(
                        text = "Loading hub options…",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassSheetTokens.OnOledMuted(),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                Spacer(
                    modifier =
                        Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth(),
                )
            }
        }
    }

    if (showEditDialog && isCreator) {
        UnifiedPopupFormDialog(
            visible = showEditDialog,
            onDismissRequest = {
                showEditDialog = false
                onDismiss()
            },
            title = "Edit Hub",
            confirmLabel = "Save",
            onConfirm = {
                viewModel.updateActiveHub(
                    hubId = hub.hubId,
                    name = editNameDraft,
                    category = editCategoryDraft,
                ) { ok ->
                    if (ok) {
                        scope.launch {
                            details =
                                details?.copy(
                                    name = editNameDraft.trim(),
                                    category = editCategoryDraft.trim(),
                                )
                            showEditDialog = false
                        }
                    }
                }
            },
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ClickOutlinedTextField(
                        value = editNameDraft,
                        onValueChange = { editNameDraft = it.take(80) },
                        singleLine = true,
                        label = { Text("Hub name", color = GlassSheetTokens.OnOledMuted()) },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GlassSheetTokens.OnOled(),
                                unfocusedTextColor = GlassSheetTokens.OnOled(),
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = GlassSheetTokens.GlassBorder(),
                                cursorColor = PrimaryBlue,
                                focusedLabelColor = GlassSheetTokens.OnOledMuted(),
                                unfocusedLabelColor = GlassSheetTokens.OnOledMuted(),
                            ),
                    )
                    ClickOutlinedTextField(
                        value = editCategoryDraft,
                        onValueChange = { editCategoryDraft = it.take(40) },
                        singleLine = true,
                        label = { Text("Category", color = GlassSheetTokens.OnOledMuted()) },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GlassSheetTokens.OnOled(),
                                unfocusedTextColor = GlassSheetTokens.OnOled(),
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = GlassSheetTokens.GlassBorder(),
                                cursorColor = PrimaryBlue,
                                focusedLabelColor = GlassSheetTokens.OnOledMuted(),
                                unfocusedLabelColor = GlassSheetTokens.OnOledMuted(),
                            ),
                    )
                }
            },
        )
    }

    if (showConfirm) {
        GlassAlertDialog(
            onDismissRequest = {
                showConfirm = false
                onDismiss()
            },
            title = { Text(confirmTitle) },
            text = { Text(confirmBody) },
            confirmButton = {
                val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                TextButton(
                    onClick = {
                        confirmAction?.invoke()
                        dismissAnimated()
                    },
                ) {
                    Text(confirmButton, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                TextButton(onClick = dismissAnimated) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted())
                }
            },
        )
    }
}

@Composable
internal fun ActiveHubFeedRow(
    hub: ActiveHubEntry,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit,
    onLongPress: () -> Unit,
) {
    val rowInteraction = remember { MutableInteractionSource() }
    val unresolved = hub.name.isBlank() || hub.name == hub.hubId

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val rowShape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner)
        if (unresolved) {
            ClickListRowShimmer(
                modifier =
                    Modifier
                        .clip(rowShape)
                        .platformPressScale(rowInteraction)
                        .connectionRowPressHighlight(rowInteraction)
                        .connectionRowPressGestures(
                            interactionSource = rowInteraction,
                            onClick = onClick,
                            onLongPress = onLongPress,
                        ),
            )
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .height(ClickPlatformListRowHeight)
                        .platformPressScale(rowInteraction)
                        .connectionRowPressHighlight(rowInteraction)
                        .connectionRowPressGestures(
                            interactionSource = rowInteraction,
                            onClick = onClick,
                            onLongPress = onLongPress,
                        ).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hub.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${hub.occupantCount} ${if (hub.occupantCount == 1) "person" else "people"} • Community Hub",
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Hub options",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ClickInsetDivider()
    }
}
