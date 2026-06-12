package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import compose.project.click.click.ui.components.ClickFormBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.LightBlue // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickActionBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret

private val PickerSelectionPurple = Color(0xFF9D4EDD)
private val PickerSelectionRingWidth = 2.5.dp
private val PickerSearchBarHeight = 48.dp
private val PickerAvatarSize = 40.dp
private val PickerAvatarOuterSize = PickerAvatarSize + PickerSelectionRingWidth * 2
private val PickerSelectedStripHeight = 76.dp

internal fun matchesConnectionPickerSearch(user: User, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val name = user.name?.trim()?.lowercase().orEmpty()
    val email = user.email?.trim()?.lowercase().orEmpty()
    return name.contains(q) || email.contains(q)
}

@Composable
internal fun ConnectionPickerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(PickerSearchBarHeight),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f),
    ) {
        TextField(
            modifier = Modifier.fillMaxSize(),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = {
                Text(
                    placeholder,
                    color = GlassSheetTokens.OnOledMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = GlassSheetTokens.OnOledMuted,
                    modifier = Modifier.size(20.dp),
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = GlassSheetTokens.OnOled,
                unfocusedTextColor = GlassSheetTokens.OnOled,
                cursorColor = PrimaryBlue,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
    }
}

@Composable
internal fun ConnectionPickerListAvatar(
    displayName: String?,
    email: String?,
    avatarUrl: String?,
    userId: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    avatarSize: Dp = PickerAvatarSize,
) {
    Box(
        modifier = Modifier.size(PickerAvatarOuterSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .border(
                    width = if (selected) PickerSelectionRingWidth else 0.dp,
                    color = if (selected) PickerSelectionPurple else Color.Transparent,
                    shape = CircleShape,
                )
                .clip(CircleShape)
                .alpha(if (enabled) 1f else 0.45f),
        ) {
            ConnectionListUserAvatarFace(
                displayName = displayName,
                email = email,
                avatarUrl = avatarUrl,
                userId = userId,
                modifier = Modifier.fillMaxSize(),
                useCompactTypography = true,
            )
        }
    }
}

@Composable
private fun ConnectionPickerSelectedStrip(
    selectedUsers: List<User>,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PickerSelectedStripHeight)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        selectedUsers.forEach { user ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .clickable {
                        PlatformHapticsPolicy.lightImpact()
                        onRemove(user.id)
                    },
            ) {
                ConnectionPickerListAvatar(
                    displayName = user.name,
                    email = user.email,
                    avatarUrl = user.image,
                    userId = user.id,
                    selected = true,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = user.name?.trim()?.ifBlank { null } ?: "Friend",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassSheetTokens.OnOledMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConnectionPickerUserRow(
    user: User,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val label = user.name?.trim()?.ifBlank { null } ?: "Connection"
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = if (enabled || selected) GlassSheetTokens.OnOled else GlassSheetTokens.OnOledMuted,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            ConnectionPickerListAvatar(
                displayName = user.name,
                email = user.email,
                avatarUrl = user.image,
                userId = user.id,
                selected = selected,
                enabled = enabled,
            )
        },
        modifier = Modifier.clickable(enabled = enabled || selected, onClick = onToggle),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * Unified multi-select connection picker for creating a verified click or adding members to one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionMemberPickerSheet(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String,
    candidates: List<User>,
    selectedIds: Set<String>,
    onSelectedIdsChange: (Set<String>) -> Unit,
    primaryButtonLabel: String,
    primaryEnabled: Boolean,
    onPrimaryClick: () -> Unit,
    eligibilityMask: Map<String, Boolean> = emptyMap(),
    eligibilityReady: Boolean = true,
    eligibilityCheckingLabel: String? = null,
    errorMessage: String? = null,
    onSelectionBlocked: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCandidates = remember(candidates, searchQuery) {
        candidates.filter { matchesConnectionPickerSearch(it, searchQuery) }
    }
    val selectedUsers = remember(candidates, selectedIds) {
        candidates.filter { it.id in selectedIds }
    }
    val onSurface = GlassSheetTokens.OnOled
    val onVariant = GlassSheetTokens.OnOledMuted

    fun toggleUser(userId: String) {
        if (userId in selectedIds) {
            PlatformHapticsPolicy.lightImpact()
            onSelectedIdsChange(selectedIds - userId)
            return
        }
        val canSelect = eligibilityReady && (eligibilityMask.isEmpty() || eligibilityMask[userId] == true)
        if (canSelect) {
            PlatformHapticsPolicy.lightImpact()
            onSelectedIdsChange(selectedIds + userId)
        } else {
            onSelectionBlocked?.invoke()
        }
    }

    ClickFormBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(GlassSheetTokens.OledBlack)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = onVariant,
            )
            headerContent()
            ConnectionPickerSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search connections",
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PickerSelectedStripHeight),
            ) {
                if (selectedUsers.isNotEmpty()) {
                    ConnectionPickerSelectedStrip(
                        selectedUsers = selectedUsers,
                        onRemove = { userId -> onSelectedIdsChange(selectedIds - userId) },
                    )
                }
            }
            if (!eligibilityCheckingLabel.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = eligibilityCheckingLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alpha(if (!eligibilityReady && candidates.isNotEmpty()) 1f else 0f),
                    )
                }
            }
            errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            )
            if (candidates.isEmpty()) {
                Text(
                    text = "No eligible connections yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else if (filteredCandidates.isEmpty()) {
                Text(
                    text = "No matches for \"$searchQuery\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState()),
                ) {
                    filteredCandidates.forEach { user ->
                        val selected = user.id in selectedIds
                        val enabled = selected ||
                            (eligibilityReady && (eligibilityMask.isEmpty() || eligibilityMask[user.id] == true))
                        ConnectionPickerUserRow(
                            user = user,
                            selected = selected,
                            enabled = enabled,
                            onToggle = { toggleUser(user.id) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel", color = onVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        PlatformHapticsPolicy.heavyImpact()
                        onPrimaryClick()
                    },
                    enabled = primaryEnabled,
                ) {
                    Text(primaryButtonLabel)
                }
            }
        }
    }
}

/** Thin wrapper — prefer [ConnectionMemberPickerSheet] with [onAddMembers]. */
@Composable
internal fun GroupAddMemberPickerSheet(
    candidates: List<User>,
    onDismiss: () -> Unit,
    onAddMembers: (List<String>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    ConnectionMemberPickerSheet(
        onDismissRequest = onDismiss,
        title = "Add to group",
        subtitle = "Choose verified connections to invite.",
        candidates = candidates,
        selectedIds = selectedIds,
        onSelectedIdsChange = { selectedIds = it },
        primaryButtonLabel = if (selectedIds.size <= 1) "Add" else "Add ${selectedIds.size}",
        primaryEnabled = selectedIds.isNotEmpty(),
        onPrimaryClick = {
            onAddMembers(selectedIds.toList())
            onDismiss()
        },
    )
}

/**
 * Inline row shown under a 1:1 connection when GPS is missing — matches [ConnectionItem] card chrome.
 */
@Composable
internal fun LocationGapNudge(
    otherName: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardBorderAlpha by animateFloatAsState(
        targetValue = if (isPressed) GlassSheetTokens.GlassBorderPressed.alpha else GlassSheetTokens.GlassBorder.alpha,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "location_nudge_border",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner))
            .border(
                width = 1.dp,
                color = GlassSheetTokens.GlassBorder.copy(alpha = cardBorderAlpha),
                shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner),
            )
            .background(GlassSheetTokens.GlassSurface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = PrimaryBlue,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Remember where you met",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Enable location for $otherName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Enable",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = LightBlue,
        )
    }
}

/**
 * Produces the sorted member list (self included) used to populate
 * the group-members picker sheet. Fills in a placeholder `User` for
 * any member id we don't have a profile for yet so the sheet still
 * renders without gaps.
 */
internal fun orderedGroupMembersForPicker(chatDetails: ChatWithDetails): List<User> {
    val gc = chatDetails.groupClique ?: return emptyList()
    val self = AppDataManager.currentUser.value
    val byId = (chatDetails.groupMemberUsers + listOfNotNull(self))
        .distinctBy { it.id }
        .associateBy { it.id }
    return gc.memberUserIds.sorted().map { id ->
        byId[id] ?: User(id = id, name = "Member", createdAt = 0L)
    }
}

/** Carries group admin context so member add/remove works without an open chat session. */
data class GroupMembersPickerContext(
    val members: List<User>,
    val groupId: String,
    val createdByUserId: String,
    val memberUserIds: List<String>,
)

internal fun groupMembersPickerContextFrom(chatDetails: ChatWithDetails): GroupMembersPickerContext? {
    val gc = chatDetails.groupClique ?: return null
    return GroupMembersPickerContext(
        members = orderedGroupMembersForPicker(chatDetails),
        groupId = gc.groupId,
        createdByUserId = gc.createdByUserId,
        memberUserIds = gc.memberUserIds,
    )
}

/**
 * Modal bottom sheet listing the members of a group click, each with
 * avatar and name. Tapping a member invokes [onMemberClick] with
 * their user id and animates the sheet closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupMembersPickerSheet(
    members: List<User>,
    onDismiss: () -> Unit,
    onMemberClick: (String) -> Unit,
    isGroupAdmin: Boolean = false,
    currentUserId: String? = null,
    onAddMember: (() -> Unit)? = null,
    onRemoveMember: ((String) -> Unit)? = null,
) {
    fun dismissSheet() {
        onDismiss()
    }
    val scroll = rememberScrollState()
    val surface = GlassSheetTokens.OledBlack
    val onSurface = GlassSheetTokens.OnOled
    val onVariant = GlassSheetTokens.OnOledMuted
    ClickActionBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .verticalScroll(scroll)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "People in this click",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(
                text = if (isGroupAdmin) {
                    "Tap someone to open their profile. Anyone can add members; only the creator can remove."
                } else {
                    "Tap someone to open their profile. You can invite verified connections to this click."
                },
                style = MaterialTheme.typography.bodySmall,
                color = onVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp),
            )
            if (onAddMember != null) {
                TextButton(
                    onClick = {
                        PlatformHapticsPolicy.lightImpact()
                        onAddMember.invoke()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = PrimaryBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Add member", color = PrimaryBlue)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            )
            members.forEach { user ->
                val label = user.name?.trim()?.ifBlank { null } ?: "Member"
                val canKick = isGroupAdmin &&
                    onRemoveMember != null &&
                    !currentUserId.isNullOrBlank() &&
                    user.id != currentUserId
                ListItem(
                    headlineContent = {
                        Text(label, color = onSurface, style = MaterialTheme.typography.bodyLarge)
                    },
                    trailingContent = if (canKick) {
                        {
                            IconButton(onClick = { onRemoveMember.invoke(user.id) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove member",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    leadingContent = {
                        ConnectionPickerListAvatar(
                            displayName = user.name,
                            email = user.email,
                            avatarUrl = user.image,
                            userId = user.id,
                        )
                    },
                    modifier = Modifier.clickable {
                        onMemberClick(user.id)
                        dismissSheet()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
