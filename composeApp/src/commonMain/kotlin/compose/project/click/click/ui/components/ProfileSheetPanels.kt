@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.GroupSharedInterest // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelineJournalEntry // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

@Composable
internal fun ProfileSheetHeader(
    displayName: String,
    subtitle: String?,
    avatarUrl: String?,
    userId: String?,
    email: String?,
    statusBadge: ProfileSheetBadge?,
    onAvatarClick: (() -> Unit)? = null,
    avatarUploading: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(78.dp)
                    .clickable(enabled = onAvatarClick != null && !avatarUploading) {
                        onAvatarClick?.invoke()
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(68.dp)
                        .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ConnectionListUserAvatarFace(
                    displayName = displayName,
                    email = email,
                    avatarUrl = avatarUrl,
                    userId = userId?.takeIf { it.isNotBlank() } ?: displayName,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (avatarUploading) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(GlassSheetTokens.OledBlack().copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (onAvatarClick != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "Change group avatar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = GlassSheetTokens.OnOled(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassSheetTokens.OnOledMuted(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (statusBadge != null) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBadge.tint.copy(alpha = 0.14f),
                ) {
                    Text(
                        statusBadge.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusBadge.tint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileActionGrid(
    showNudge: Boolean,
    showDisposableRoll: Boolean,
    onMessage: () -> Unit,
    onNudge: () -> Unit,
    onOpenDisposableRoll: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileActionCard(
                label = "Message",
                icon = Icons.Outlined.Message,
                onClick = onMessage,
                usePrimaryBorder = true,
                modifier = Modifier.weight(1f),
            )
            if (showNudge) {
                ProfileActionCard(
                    label = "Nudge",
                    icon = Icons.Outlined.NotificationsActive,
                    onClick = onNudge,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        if (showDisposableRoll && onOpenDisposableRoll != null) {
            ProfileActionCard(
                label = "Click Drops",
                icon = Icons.Filled.PhotoCamera,
                onClick = onOpenDisposableRoll,
                usePrimaryBorder = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun ProfileActionCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    usePrimaryBorder: Boolean = false,
) {
    GlassCard(
        modifier = modifier.heightIn(min = 52.dp),
        onClick = onClick,
        usePrimaryBorder = usePrimaryBorder,
        contentPadding = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (usePrimaryBorder) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun TimelinePanel(
    scrollState: ScrollState,
    items: List<ProfileSheetTimelineItem>,
    legacyProfile: UserPublicProfile?,
    legacyLoading: Boolean,
    legacyError: String?,
    showLegacy: Boolean,
    isGroup: Boolean,
    sharedInterests: List<GroupSharedInterest>,
    journalEntries: List<ProfileTimelineJournalEntry>,
    journalText: String,
    onJournalTextChange: (String) -> Unit,
    journalVisibility: String,
    onJournalVisibilityChange: (String) -> Unit,
    journalPosting: Boolean,
    journalError: String?,
    onSubmitJournalEntry: () -> Unit,
    viewerUserId: String?,
    editingJournalId: String?,
    editingJournalText: String,
    onEditingJournalTextChange: (String) -> Unit,
    editingJournalVisibility: String,
    onEditingJournalVisibilityChange: (String) -> Unit,
    mutatingJournalIds: Set<String>,
    onStartEditJournalEntry: (ProfileTimelineJournalEntry) -> Unit,
    onCancelEditJournalEntry: () -> Unit,
    onSaveEditJournalEntry: (String) -> Unit,
    onDeleteJournalEntry: (String) -> Unit,
) {
    val hasTimelineItems = items.isNotEmpty()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxSize()
                // Always Compose-scroll inside the pager page (UIKit host sheetBodyScroll is a
                // no-op; fillMaxSize + verticalScroll keeps tabs scrollable and Metal-safe).
                .verticalScroll(scrollState)
                .padding(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        JournalComposerCard(
            text = journalText,
            onTextChange = onJournalTextChange,
            visibility = journalVisibility,
            onVisibilityChange = onJournalVisibilityChange,
            posting = journalPosting,
            error = journalError,
            onSubmit = onSubmitJournalEntry,
        )
        if (isGroup && sharedInterests.isNotEmpty()) {
            SharedInterestsTimelineSection(sharedInterests)
        }
        if (journalEntries.isNotEmpty()) {
            Text(
                text = "Journal",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
            journalEntries.forEach { entry ->
                JournalTimelineRow(
                    entry = entry,
                    canEdit = entry.authorUserId == viewerUserId,
                    isEditing = editingJournalId == entry.id,
                    editText = editingJournalText,
                    onEditTextChange = onEditingJournalTextChange,
                    editVisibility = editingJournalVisibility,
                    onEditVisibilityChange = onEditingJournalVisibilityChange,
                    isMutating = entry.id in mutatingJournalIds,
                    onStartEdit = { onStartEditJournalEntry(entry) },
                    onCancelEdit = onCancelEditJournalEntry,
                    onSaveEdit = { onSaveEditJournalEntry(entry.id) },
                    onDelete = { onDeleteJournalEntry(entry.id) },
                )
            }
        }
        if (hasTimelineItems) {
            items.forEach { TimelineRow(item = it) }
        }
        if (showLegacy) {
            if (hasTimelineItems || journalEntries.isNotEmpty() || sharedInterests.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(Modifier.height(6.dp))
            }
            ProfileLegacyTimelineContent(
                profile = legacyProfile,
                loading = legacyLoading,
                error = legacyError,
            )
        }
        if (!showLegacy && !hasTimelineItems && journalEntries.isEmpty() && sharedInterests.isEmpty()) {
            EmptyTabState(
                icon = Icons.Outlined.History,
                title = "No timeline yet",
                body = "Add a journal entry or come back after shared moments appear.",
            )
        }
    }
}

@Composable
internal fun JournalComposerCard(
    text: String,
    onTextChange: (String) -> Unit,
    visibility: String,
    onVisibilityChange: (String) -> Unit,
    posting: Boolean,
    error: String?,
    onSubmit: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, PrimaryBlue.copy(alpha = 0.28f), shape)
                .background(GlassSheetTokens.GlassSurface())
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Add to timeline",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ClickOutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            placeholder = {
                Text("Write a quick memory, note, or plan...")
            },
            enabled = !posting,
            shape = RoundedCornerShape(14.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileVisibilityPill(
                label = "Private",
                selected = visibility != "shared",
                onClick = { onVisibilityChange("private") },
            )
            ProfileVisibilityPill(
                label = "Everyone",
                selected = visibility == "shared",
                onClick = { onVisibilityChange("shared") },
            )
            Spacer(Modifier.weight(1f))
            val addEnabled = text.trim().isNotEmpty() && !posting
            val addShape = RoundedCornerShape(999.dp)
            Surface(
                onClick = onSubmit,
                enabled = addEnabled,
                shape = addShape,
                color = if (addEnabled) PrimaryBlue.copy(alpha = 0.18f) else Color.Transparent,
                border =
                    BorderStroke(
                        1.dp,
                        if (addEnabled) PrimaryBlue.copy(alpha = 0.7f) else GlassSheetTokens.GlassBorder(),
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (posting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = PrimaryBlue,
                        )
                    }
                    Text(
                        text = if (posting) "Adding…" else "Add",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (addEnabled) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
            }
        }
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun ProfileVisibilityPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) PrimaryBlue.copy(alpha = 0.18f) else Color.Transparent,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) PrimaryBlue.copy(alpha = 0.7f) else GlassSheetTokens.GlassBorder(),
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
internal fun SharedInterestsTimelineSection(items: List<GroupSharedInterest>) {
    val grouped =
        items
            .groupBy { it.count }
            .entries
            .sortedByDescending { entry -> entry.key }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Common ground",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp),
        )
        grouped.forEach { entry ->
            val count = entry.key
            val tags = entry.value
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, GlassSheetTokens.GlassBorder(), shape)
                        .background(GlassSheetTokens.GlassSurface())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text =
                        if (count == tags.firstOrNull()?.userIds?.size && count > 2) {
                            "$count members share"
                        } else {
                            "$count people share"
                        },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                )
                tags.forEach { item ->
                    Text(
                        text = item.tag,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.memberNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun JournalTimelineRow(
    entry: ProfileTimelineJournalEntry,
    canEdit: Boolean,
    isEditing: Boolean,
    editText: String,
    onEditTextChange: (String) -> Unit,
    editVisibility: String,
    onEditVisibilityChange: (String) -> Unit,
    isMutating: Boolean,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, GlassSheetTokens.GlassBorder(), shape)
                .background(GlassSheetTokens.GlassSurface())
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.authorName?.takeIf { it.isNotBlank() } ?: "You",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (entry.visibility == "shared") "Everyone" else "Private",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.visibility == "shared") PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isEditing) {
            ClickOutlinedTextField(
                value = editText,
                onValueChange = onEditTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                enabled = !isMutating,
                shape = RoundedCornerShape(14.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileVisibilityPill(
                    label = "Private",
                    selected = editVisibility != "shared",
                    onClick = { onEditVisibilityChange("private") },
                )
                ProfileVisibilityPill(
                    label = "Everyone",
                    selected = editVisibility == "shared",
                    onClick = { onEditVisibilityChange("shared") },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancelEdit, enabled = !isMutating) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = onSaveEdit,
                    enabled = editText.trim().isNotEmpty() && !isMutating,
                ) {
                    Text("Save")
                }
            }
        } else {
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = formatProfileTimelineIso(entry.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
        if (canEdit && !isEditing) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onStartEdit, enabled = !isMutating) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete, enabled = !isMutating) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
internal fun TimelineRow(item: ProfileSheetTimelineItem) {
    val rowShape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .border(1.dp, GlassSheetTokens.GlassBorder(), rowShape)
                .background(GlassSheetTokens.GlassSurface())
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .width(32.dp)
                    .padding(top = 6.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .border(2.dp, PrimaryBlue.copy(alpha = 0.35f), CircleShape)
                        .background(PrimaryBlue),
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}
