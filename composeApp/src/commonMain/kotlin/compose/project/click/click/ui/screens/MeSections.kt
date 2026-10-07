@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickListRow // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickNavRow // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.theme.AppearanceMode // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

/** A Core person on the Me root strip. */
internal data class MeCoreItem(
    val userId: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
)

/** Core connections the viewer pinned, in pin order of the connection list, with a known peer. */
internal fun meCoreItems(
    connections: List<Connection>,
    coreConnectionIds: Set<String>,
    hiddenConnectionIds: Set<String>,
    connectedUsers: Map<String, User>,
    viewerUserId: String?,
): List<MeCoreItem> {
    if (viewerUserId.isNullOrBlank()) return emptyList()
    return connections
        .filter { it.id in coreConnectionIds && it.id !in hiddenConnectionIds }
        .mapNotNull { connection ->
            val peerId = connection.user_ids.firstOrNull { it != viewerUserId } ?: return@mapNotNull null
            val peer = connectedUsers[peerId]
            MeCoreItem(
                userId = peerId,
                displayName = peer?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Click",
                email = peer?.email,
                avatarUrl = peer?.image,
            )
        }.distinctBy { it.userId }
}

/** "1 Click" / "12 Clicks": every connection the viewer hasn't removed (iOS counts archived too). */
internal fun clicksCountLabel(
    connections: List<Connection>,
    hiddenConnectionIds: Set<String>,
): String? {
    val count = connections.count { it.id !in hiddenConnectionIds }
    if (count == 0) return null
    return if (count == 1) "1 Click" else "$count Clicks"
}

/** The pill above the avatar: "Down for coffee" from the newest active intent. */
internal fun availabilityStatusLabel(intents: List<AvailabilityIntentRow>): String? =
    intents
        .firstOrNull()
        ?.intentTag
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { "Down for ${it.lowercase()}" }

internal fun availabilitySummary(intents: List<AvailabilityIntentRow>): String =
    intents
        .mapNotNull { it.intentTag?.trim()?.takeIf { tag -> tag.isNotEmpty() } }
        .joinToString(", ")
        .ifEmpty { "None" }

@Composable
internal fun MeIdentityHeader(
    user: User?,
    bio: String?,
    statusLabel: String?,
    clicksLabel: String?,
    avatarUploading: Boolean,
    onChangePhoto: () -> Unit,
    onStatusClick: () -> Unit,
) {
    val (first, last) = namePartsForEditor(user)
    val displayName =
        listOf(first, last)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { user?.name?.trim().orEmpty() }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (statusLabel != null) {
            Surface(
                onClick = onStatusClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Box(modifier = Modifier.padding(top = 4.dp).size(120.dp)) {
            val id = user?.id.orEmpty()
            ConnectionListUserAvatarFace(
                displayName = displayName.ifBlank { null },
                email = user?.email,
                avatarUrl = user?.image,
                userId = id.ifEmpty { "me" },
                modifier =
                    Modifier
                        .size(120.dp)
                        .clickable(enabled = !avatarUploading, onClickLabel = "Change profile photo", onClick = onChangePhoto),
            )
            if (avatarUploading) {
                Box(
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue)
                        .clickable(enabled = !avatarUploading, onClick = onChangePhoto),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Change profile photo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            displayName.ifBlank { " " },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!bio.isNullOrBlank()) {
            Text(
                bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        if (clicksLabel != null) {
            Text(
                clicksLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MeCoreStrip(
    items: List<MeCoreItem>,
    onOpenProfile: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsSectionHeader("Core")
            Box(Modifier.weight(1f))
            TextButton(onClick = onViewAll) { Text("View") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items, key = { it.userId }) { item ->
                Column(
                    modifier =
                        Modifier
                            .width(70.dp)
                            .clickable(onClickLabel = "${item.displayName}, Core") { onOpenProfile(item.userId) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ConnectionListUserAvatarFace(
                        displayName = item.displayName,
                        email = item.email,
                        avatarUrl = item.avatarUrl,
                        userId = item.userId,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        item.displayName.substringBefore(' '),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A Me root row: icon, title, optional trailing value, chevron. */
@Composable
internal fun MeNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    subtitle: String? = null,
) {
    ClickNavRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leadingIcon = icon,
        leadingTint = MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!value.isNullOrBlank()) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
internal fun AppearanceSegmentedRow(
    mode: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Appearance", style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AppearanceMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == mode,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = AppearanceMode.entries.size),
                ) {
                    Text(option.label)
                }
            }
        }
    }
}

@Composable
internal fun MeExternalLinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    ClickListRow(
        title = title,
        onClick = onClick,
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        showDivider = false,
    )
}

@Composable
internal fun MeAccountRows(
    signingOut: Boolean,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Column {
        ClickListRow(
            title = "Sign out",
            onClick = if (signingOut) null else onSignOut,
            leading = {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailing = if (signingOut) ({ CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }) else null,
            showDivider = false,
        )
        SettingsDivider()
        ClickListRow(
            onClick = onDeleteAccount,
            leading = {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            showDivider = false,
        ) {
            Text("Delete account", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun MeFooter(version: String) {
    Text(
        if (version.isBlank()) "Click for Android" else "Click for Android · $version",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}
