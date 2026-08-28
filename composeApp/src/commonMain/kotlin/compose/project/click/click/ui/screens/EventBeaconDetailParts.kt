@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.GuestListStatusDto // pragma: allowlist secret
import compose.project.click.click.events.EventSchedule // pragma: allowlist secret
import compose.project.click.click.events.formatEventEndDateLabel // pragma: allowlist secret
import compose.project.click.click.events.formatEventEndTimeLabel // pragma: allowlist secret
import compose.project.click.click.events.formatEventStartDateLabel // pragma: allowlist secret
import compose.project.click.click.events.formatEventStartTimeLabel // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickDropdownMenu // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickMenuItem // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import kotlinx.coroutines.launch

@Composable
internal fun EventLiveBadge() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFDC2626))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White),
        )
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
internal fun BeaconShareMenuButton(
    onShare: () -> Unit,
    onShareToChat: (() -> Unit)?,
    border: androidx.compose.ui.graphics.Color,
    contentDescription: String = "Share",
) {
    var shareMenuExpanded by remember { mutableStateOf(false) }
    Box {
        EventHeroIconButton(
            selected = shareMenuExpanded,
            border = border,
            onClick = {
                if (onShareToChat != null) {
                    shareMenuExpanded = true
                } else {
                    onShare()
                }
            },
            contentDescription = contentDescription,
            icon = Icons.Filled.Share,
        )
        ClickDropdownMenu(
            expanded = shareMenuExpanded,
            onDismissRequest = { shareMenuExpanded = false },
            items =
                buildList {
                    add(ClickMenuItem(label = "Share link", onClick = onShare))
                    if (onShareToChat != null) {
                        add(ClickMenuItem(label = "Share to chat", onClick = { onShareToChat.invoke() }))
                    }
                },
        )
    }
}

@Composable
internal fun EventHeroActions(
    bookmarked: Boolean,
    bookmarkPending: Boolean = false,
    isCreator: Boolean,
    onShare: () -> Unit,
    onShareToChat: (() -> Unit)? = null,
    onToggleBookmark: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val border = clickBorderColor()
    var menuExpanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BeaconShareMenuButton(
            onShare = onShare,
            onShareToChat = onShareToChat,
            border = border,
            contentDescription = "Share event",
        )
        EventHeroIconButton(
            selected = bookmarked,
            border = border,
            onClick = onToggleBookmark,
            enabled = !bookmarkPending,
            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark event",
            icon = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
        )
        if (isCreator) {
            Box {
                EventHeroIconButton(
                    selected = menuExpanded,
                    border = border,
                    onClick = { menuExpanded = true },
                    contentDescription = "More actions",
                    icon = Icons.Filled.MoreVert,
                )
                BeaconOwnerDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
internal fun EventHeroIconButton(
    selected: Boolean,
    border: Color,
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .size(48.dp)
                .border(clickBorderWidth(), border, CircleShape)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    },
                ).graphicsLayer { alpha = if (enabled) 1f else 0.55f },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun EventScheduleBento(
    schedule: EventSchedule,
    border: Color,
    cardSurface: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EventBentoCell(
            modifier = Modifier.weight(1f),
            label = "Start Time",
            date = formatEventStartDateLabel(schedule),
            time = formatEventStartTimeLabel(schedule),
            icon = Icons.Filled.Schedule,
            border = border,
            cardSurface = cardSurface,
        )
        EventBentoCell(
            modifier = Modifier.weight(1f),
            label = "End Time",
            date = formatEventEndDateLabel(schedule),
            time = formatEventEndTimeLabel(schedule),
            icon = Icons.Filled.EventBusy,
            border = border,
            cardSurface = cardSurface,
        )
    }
}

@Composable
internal fun EventBentoCell(
    modifier: Modifier,
    label: String,
    date: String,
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    border: Color,
    cardSurface: Color,
) {
    Column(
        modifier =
            modifier
                .border(clickBorderWidth(), border, RoundedCornerShape(12.dp))
                .background(cardSurface, RoundedCornerShape(12.dp))
                .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = date,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = time,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventCategoryChips(
    categories: List<String>,
    border: Color,
    cardSurface: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "CATEGORIES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .border(clickBorderWidth(), border, RoundedCornerShape(999.dp))
                            .background(cardSurface, RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun EventHostCard(
    displayName: String,
    userId: String,
    avatarUrl: String?,
    border: Color,
    cardSurface: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(clickBorderWidth(), border, RoundedCornerShape(12.dp))
                .background(cardSurface, RoundedCornerShape(12.dp))
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConnectionListUserAvatarFace(
            displayName = displayName,
            email = null,
            avatarUrl = avatarUrl,
            userId = userId,
            modifier =
                Modifier
                    .size(56.dp)
                    .border(clickBorderWidth(), border, CircleShape)
                    .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Host",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun EventAttendeeStack(
    attendees: List<compose.project.click.click.data.api.BeaconAttendeeDto>, // pragma: allowlist secret
    loading: Boolean,
    border: Color,
    cardSurface: Color,
) {
    val visible = attendees.take(4)
    val overflow = (attendees.size - visible.size).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "ACTIVE CLICKS (${attendees.size})",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            loading -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            attendees.isEmpty() ->
                Text(
                    text = "Be the first to RSVP.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    visible.forEachIndexed { index, attendee ->
                        ConnectionListUserAvatarFace(
                            displayName = attendee.name,
                            email = null,
                            avatarUrl = attendee.avatarUrl,
                            userId = attendee.userId,
                            modifier =
                                Modifier
                                    .offset(x = (-10 * index).dp)
                                    .zIndex((visible.size - index).toFloat())
                                    .size(48.dp)
                                    .border(clickBorderWidth(), border, CircleShape)
                                    .clip(CircleShape)
                                    .background(cardSurface),
                        )
                    }
                    if (overflow > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .offset(x = (-10 * visible.size).dp)
                                    .zIndex(0f)
                                    .size(48.dp)
                                    .border(clickBorderWidth(), border, CircleShape)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+$overflow",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EventGuestListPasteCard(
    beaconId: String,
    border: Color,
    cardSurface: Color,
) {
    val api = remember { ApiClient() }
    var paste by remember(beaconId) { mutableStateOf("") }
    var status by remember(beaconId) { mutableStateOf<GuestListStatusDto?>(null) }
    var error by remember(beaconId) { mutableStateOf<String?>(null) }
    var busy by remember(beaconId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(beaconId) {
        status = api.getBeaconGuestList(beaconId).getOrNull()
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(clickBorderWidth(), border, RoundedCornerShape(12.dp))
                .background(cardSurface, RoundedCornerShape(12.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Seed this room",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Paste emails (one per line). Click matches people who already have an account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = paste,
            onValueChange = { paste = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
            placeholder = { Text("one@email.com") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (busy || paste.isBlank()) return@Button
                    busy = true
                    error = null
                    scope.launch {
                        val result = api.postBeaconGuestList(beaconId, "manual", paste)
                        busy = false
                        result
                            .onSuccess {
                                status = it
                                paste = ""
                            }.onFailure { error = it.message ?: "Upload failed" }
                    }
                },
                enabled = !busy && paste.isNotBlank(),
            ) {
                Text(if (busy) "Working…" else "Add emails")
            }
            if ((status?.uploaded ?: 0) > 0) {
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        busy = true
                        error = null
                        scope.launch {
                            val result = api.matchBeaconGuestList(beaconId)
                            busy = false
                            result.onSuccess { status = it }.onFailure { error = it.message ?: "Match failed" }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("Rematch")
                }
            }
        }
        error?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        status?.let {
            Text(
                text = "${it.uploaded} uploaded · ${it.matched} matched · ${it.teasers} teasers",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
