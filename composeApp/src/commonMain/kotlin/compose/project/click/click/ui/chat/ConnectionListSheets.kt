package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.LightBlue // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickActionBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret

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
                text = "Tap someone to open their profile.",
                style = MaterialTheme.typography.bodySmall,
                color = onVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            )
            members.forEach { user ->
                val label = user.name?.trim()?.ifBlank { null } ?: "Member"
                ListItem(
                    headlineContent = {
                        Text(label, color = onSurface, style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!user.image.isNullOrBlank()) {
                                AsyncImage(
                                    model = user.image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Text(
                                    text = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onVariant,
                                )
                            }
                        }
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
