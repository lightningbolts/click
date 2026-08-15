@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.heroImageUrl // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayTypeTitle // pragma: allowlist secret

private data class PendingShareAction(
    val chatIds: List<String>,
    val openChatConnectionId: String?,
)

/** Softer than default popup motion so share-to-chat doesn't snap open/closed. */
private val ShareToChatMotion =
    UnifiedPopupMotion(
        fadeInMillis = 340,
        fadeOutMillis = 280,
        scaleInInitial = 0.94f,
        scaleOutTarget = 0.96f,
        slideEnterFraction = 0.08f,
        slideExitFraction = 0.06f,
    )

/**
 * Multiselect share-to-chat picker for map beacons.
 * Uses [UnifiedPopupOverlay] (window Popup) so it stacks above ModalBottomSheet /
 * iOS page-sheet content instead of appearing behind the event sheet.
 *
 * Open/close always run through the overlay's animated dismiss path — Cancel and Share
 * must not tear down composition by calling [onDismissRequest] / [onShare] synchronously.
 */
@Composable
fun BeaconShareToChatDialog(
    beacon: MapBeacon,
    chats: List<ChatWithDetails>,
    onDismissRequest: () -> Unit,
    onShare: (selectedChatIds: List<String>, openChatConnectionId: String?) -> Unit,
) {
    var selectedIds by remember {
        mutableStateOf(emptySet<String>())
    }
    var pendingShare by remember { mutableStateOf<PendingShareAction?>(null) }
    val border = clickBorderColor()
    val surface = clickCardSurface()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val eligible =
        remember(chats) {
            chats.filter { row ->
                !row.chat.id.isNullOrBlank()
            }
        }

    UnifiedPopupOverlay(
        visible = true,
        onDismissRequest = {
            val share = pendingShare
            pendingShare = null
            if (share != null) {
                onShare(share.chatIds, share.openChatConnectionId)
            } else {
                onDismissRequest()
            }
        },
        modifier = Modifier.fillMaxSize(),
        scrimAlpha = GlassSheetTokens.ScrimBaseAlpha,
        motion = ShareToChatMotion,
    ) {
        val requestAnimatedDismiss = LocalUnifiedPopupAnimatedDismiss.current
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surface,
            border = BorderStroke(clickBorderWidth(), border),
            modifier =
                Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.78f),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Share to chat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                )
                val visual = rememberCardVisual(beacon.id, beacon.kind, beacon.sourceBeaconType)
                CardVisualHero(
                    id = beacon.id,
                    visual = visual,
                    imageUrl = beacon.metadata.heroImageUrl(),
                    chipLabel = beacon.displayTypeTitle(),
                    contentAlignment = Alignment.BottomStart,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(clickBorderWidth(), border, RoundedCornerShape(12.dp)),
                ) {
                    Column(
                        // Leaves room for the category chip the hero draws at the top-start.
                        modifier = Modifier.padding(start = 12.dp, top = 44.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = beacon.displayDynamicTitle(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = visual.onContent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        beacon.metadata.locationName
                            ?.takeUnless { it.equals("Current location", ignoreCase = true) }
                            ?.let { loc ->
                                Text(
                                    text = loc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = visual.onContent.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                    }
                }

                Text(
                    text = "Select one or more chats",
                    style = MaterialTheme.typography.bodySmall,
                    color = onVariant,
                )

                if (eligible.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No chats yet. Connect with someone first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(eligible, key = { it.chat.id.orEmpty() }) { row ->
                            val chatId = row.chat.id!!.trim()
                            val selected = chatId in selectedIds
                            val isGroup = row.groupClique != null
                            val label =
                                row.groupClique
                                    ?.name
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                                    ?: row.otherUser.name
                                        ?.trim()
                                        ?.takeIf { it.isNotEmpty() }
                                    ?: if (isGroup) "Group chat" else "Chat"
                            val subtitle =
                                if (isGroup) {
                                    val n =
                                        row.groupMemberUsers.size.coerceAtLeast(
                                            (row.groupClique?.memberUserIds?.size ?: 1) - 1,
                                        )
                                    if (n > 0) "$n members" else "Group"
                                } else {
                                    "Direct"
                                }
                            val avatarUser =
                                if (isGroup) {
                                    row.groupMemberUsers.firstOrNull() ?: row.otherUser
                                } else {
                                    row.otherUser
                                }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .border(
                                            clickBorderWidth(),
                                            if (selected) MaterialTheme.colorScheme.primary else border,
                                            RoundedCornerShape(12.dp),
                                        ).background(surface, RoundedCornerShape(12.dp))
                                        .clickable {
                                            PlatformHapticsPolicy.lightImpact()
                                            selectedIds =
                                                if (selected) selectedIds - chatId else selectedIds + chatId
                                        }.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ConnectionListUserAvatarFace(
                                    displayName = label,
                                    email = avatarUser.email,
                                    avatarUrl = avatarUser.image,
                                    userId = avatarUser.id,
                                    modifier =
                                        Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .border(clickBorderWidth(), border, CircleShape),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = onVariant,
                                    )
                                }
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        selectedIds =
                                            if (checked) selectedIds + chatId else selectedIds - chatId
                                    },
                                )
                            }
                        }
                    }
                }

                val canShare = selectedIds.isNotEmpty()
                Button(
                    onClick = {
                        PlatformHapticsPolicy.successNotification()
                        val ordered = eligible.filter { it.chat.id?.trim() in selectedIds }
                        val openConn = ordered.firstOrNull()?.connection?.id
                        pendingShare =
                            PendingShareAction(
                                chatIds = selectedIds.toList(),
                                openChatConnectionId = openConn,
                            )
                        requestAnimatedDismiss()
                    },
                    enabled = canShare,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(clickBorderWidth(), border),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text(
                        text = if (selectedIds.size <= 1) "Share" else "Share to ${selectedIds.size} chats",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedButton(
                    onClick = { requestAnimatedDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(clickBorderWidth(), border),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold, color = onSurface)
                }
            }
        }
    }
}
