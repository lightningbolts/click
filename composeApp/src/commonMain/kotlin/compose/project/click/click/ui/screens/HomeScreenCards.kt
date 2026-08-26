@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.api.CommunityHubNearbyDto // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionInsights // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.ReconnectReminder // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.events.HomeEventReminder // pragma: allowlist secret
import compose.project.click.click.events.eventReminderBody // pragma: allowlist secret
import compose.project.click.click.events.eventReminderTitle // pragma: allowlist secret
import compose.project.click.click.events.isActiveForDiscoveryFeed // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButtonVariant // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.components.GroupAvatar // pragma: allowlist secret
import compose.project.click.click.ui.components.HomeExploreTile // pragma: allowlist secret
import compose.project.click.click.ui.components.SectionHeader // pragma: allowlist secret
import compose.project.click.click.ui.components.cardVisualBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberCardVisual // pragma: allowlist secret
import compose.project.click.click.ui.components.toHomeExploreTile // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

internal fun buildHomeExploreTiles(
    beacons: List<compose.project.click.click.data.models.MapBeacon>, // pragma: allowlist secret
    hubs: List<compose.project.click.click.data.api.CommunityHubNearbyDto>, // pragma: allowlist secret
): List<HomeExploreTile> {
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val activeBeacons = beacons.filter { it.isActiveForDiscoveryFeed(nowMs) }
    val kindTiles =
        activeBeacons
            .groupBy { it.kind }
            .entries
            .sortedBy { it.key.ordinal }
            .map { (kind, group) -> kind.toHomeExploreTile(group.size) }
    val hubCount = hubs.size
    val hubTile =
        if (hubCount > 0) {
            HomeExploreTile(
                id = "hubs",
                label = "Hub",
                count = hubCount,
                layerFilter = MapLayerFilter.COMMUNITY_HUBS,
                icon = Icons.Filled.Groups,
            )
        } else {
            null
        }
    return buildList {
        addAll(kindTiles)
        hubTile?.let { add(it) }
    }
}

/**
 * Stat card — Functional Clarity bordered surface.
 */
@Composable
internal fun HomeStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    GlassCard(
        modifier = modifier,
        usePrimaryBorder = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * An expandable card grouping all connections made at a single semantic location.
 * Tapping the header toggles the list of individual connections open/closed.
 */
@Composable
internal fun LocationGroupCard(
    location: String,
    connections: List<Connection>,
    isExpanded: Boolean,
    connectedUsers: Map<String, User>,
    currentUserId: String,
    onToggleExpand: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNudge: (connectionId: String, otherUserName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "chevron",
    )

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onToggleExpand,
        usePrimaryBorder = isExpanded,
    ) {
        Column {
            // Group header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val memberUsers =
                    connections.mapNotNull { conn ->
                        val otherId = conn.user_ids.firstOrNull { it != currentUserId }
                        otherId?.let { connectedUsers[it] }
                    }
                if (memberUsers.isNotEmpty()) {
                    GroupAvatar(
                        members = memberUsers,
                        avatarSize = 36.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        location,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${connections.size} connection${if (connections.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Count badge
                Box(
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        connections.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(chevronAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Expanded individual connections
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150)),
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(
                        color = clickBorderColor(),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    connections.forEach { connection ->
                        val otherUserId = connection.user_ids.firstOrNull { it != currentUserId }
                        val otherUser = otherUserId?.let { connectedUsers[it] }
                        ConnectionRowItem(
                            connection = connection,
                            otherUser = otherUser,
                            currentUserId = currentUserId,
                            onNavigate = { onNavigateToChat(connection.id) },
                            onNudge = {
                                onNudge(connection.id, otherUser?.name ?: "them")
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual connection row rendered inside an expanded LocationGroupCard.
 */
@Composable
internal fun ConnectionRowItem(
    connection: Connection,
    otherUser: User?,
    currentUserId: String,
    onNavigate: () -> Unit,
    onNudge: () -> Unit,
) {
    val duration =
        (
            kotlinx.datetime.Clock.System
                .now()
                .toEpochMilliseconds() - connection.created
        ).milliseconds
    val timeAgo =
        when {
            duration.inWholeMinutes < 1 -> "Just now"
            duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
            duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
            duration.inWholeDays < 7 -> "${duration.inWholeDays}d ago"
            else -> {
                val dt =
                    Instant
                        .fromEpochMilliseconds(connection.created)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                "${dt.month.name.take(3)} ${dt.dayOfMonth}"
            }
        }
    val displayName = otherUser?.name ?: "Connection"

    val rowStyle = LocalPlatformStyle.current
    val rowShape = RoundedCornerShape(if (rowStyle.isIOS) 14.dp else 12.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .clickable { onNavigate() }
                .background(MaterialTheme.colorScheme.surface)
                .border(clickBorderWidth(), clickBorderColor(), rowShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectionListUserAvatarFace(
            displayName = displayName,
            email = otherUser?.email,
            avatarUrl = otherUser?.image,
            userId = otherUser?.id.orEmpty(),
            modifier = Modifier.size(36.dp),
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                timeAgo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Nudge button
        IconButton(
            onClick = onNudge,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = "Nudge",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        // Chat button
        IconButton(
            onClick = onNavigate,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.Chat,
                contentDescription = "Open chat",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ConnectionCard(
    connection: Connection,
    currentUserId: String,
) {
    val otherUserId = connection.user_ids.firstOrNull { it != currentUserId }

    val instant = Instant.fromEpochMilliseconds(connection.created)
    val now =
        kotlinx.datetime.Clock.System
            .now()
    val duration = (now.toEpochMilliseconds() - connection.created).milliseconds

    val timeAgo =
        when {
            duration.inWholeMinutes < 1 -> "Just now"
            duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
            duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
            duration.inWholeDays < 7 -> "${duration.inWholeDays}d ago"
            else -> {
                val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                "${dateTime.month.name.take(3)} ${dateTime.dayOfMonth}"
            }
        }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { /* Navigate to connection details */ },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon container
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(clickBorderWidth(), clickBorderColor(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    connection.semanticLocation ?: "Connection",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        timeAgo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * In-app event reminder surfaced on Home (day-of + one hour before start).
 */
@Composable
fun HomeEventReminderCard(
    reminder: HomeEventReminder,
    onDismiss: () -> Unit,
    onViewMap: (() -> Unit)? = null,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        usePrimaryBorder = true,
        contentPadding = 14.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Identity stripe rather than a full hero: this is a notification row, but it should still
            // carry the same generated colours as the event's card, pin, and detail sheet.
            val visual = rememberCardVisual(reminder.beaconId, MapBeaconKind.EVENT)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .cardVisualBackground(visual),
            )
            Text(
                text = eventReminderTitle(reminder.kind, reminder.description),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = eventReminderBody(reminder.kind, reminder.description, reminder.title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                if (onViewMap != null) {
                    TextButton(onClick = onViewMap) {
                        Text("View on Map", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Card for displaying a reconnect reminder.
 */
@Composable
fun ReconnectReminderCard(
    reminder: ReconnectReminder,
    onReconnect: () -> Unit,
    onDismiss: () -> Unit,
    avatarUrl: String? = null,
    email: String? = null,
) {
    val actionShape = RoundedCornerShape(8.dp)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        usePrimaryBorder = true,
        contentPadding = 14.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionListUserAvatarFace(
                    displayName = reminder.userName,
                    email = email,
                    avatarUrl = avatarUrl,
                    userId = reminder.userId,
                    modifier =
                        Modifier
                            .size(44.dp)
                            .border(clickBorderWidth(), clickBorderColor(), CircleShape),
                    useCompactTypography = true,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reminder.userName ?: "Someone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${reminder.daysSinceContact} days since last chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(44.dp),
                    shape = actionShape,
                    border = BorderStroke(clickBorderWidth(), clickBorderColor()),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = onReconnect,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(44.dp),
                    shape = actionShape,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Message",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Expandable card for displaying connection insights - Glass styled
 */
@Composable
fun ConnectionInsightsCard(
    insights: ConnectionInsights,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        usePrimaryBorder = true,
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Connection Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Quick stats row (always visible)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                InsightStat(
                    value = "${insights.keepRate.toInt()}%",
                    label = "Keep Rate",
                )
                InsightStat(
                    value = insights.activeConnections.toString(),
                    label = "Active",
                )
                InsightStat(
                    value = insights.dormantConnections.toString(),
                    label = "Need Attention",
                )
            }

            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = clickBorderColor())
                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InsightRow(
                        icon = Icons.Filled.Group,
                        label = "Total Connections",
                        value = insights.totalConnections.toString(),
                    )

                    InsightRow(
                        icon = Icons.Filled.Favorite,
                        label = "Connections Kept",
                        value = insights.keptConnections.toString(),
                    )

                    if (insights.longestConnectionDays > 0) {
                        val longestName = insights.longestConnectionName?.trim()?.takeIf { it.isNotEmpty() }
                        InsightRow(
                            icon = Icons.Filled.AccessTime,
                            label = "Longest Connection",
                            value =
                                if (longestName != null) {
                                    "${insights.longestConnectionDays} days\n($longestName)"
                                } else {
                                    "${insights.longestConnectionDays} days"
                                },
                        )
                    }

                    InsightRow(
                        icon = Icons.Filled.CalendarToday,
                        label = "New This Week",
                        value = insights.connectionsThisWeek.toString(),
                    )

                    InsightRow(
                        icon = Icons.Filled.DateRange,
                        label = "New This Month",
                        value = insights.connectionsThisMonth.toString(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun InsightStat(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun InsightRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier =
                Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun HomeAvailabilityIntentsRow(
    intents: List<AvailabilityIntentRow>,
    onCreateIntent: () -> Unit,
    onEditIntent: (AvailabilityIntentRow) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = "I'm down for…")
        Spacer(modifier = Modifier.height(10.dp))
        if (intents.isEmpty()) {
            ClickButton(
                onClick = onCreateIntent,
                modifier = Modifier.fillMaxWidth(),
                variant = ClickButtonVariant.Primary,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Set what you're down for",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                intents.forEach { row ->
                    val label =
                        row.intentTag
                            ?.trim()
                            .orEmpty()
                            .ifEmpty { "Intent" }
                    val sub = row.activeUntilLabel()
                    AssistChip(
                        onClick = { onEditIntent(row) },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (sub.isNotBlank()) {
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(clickBorderWidth(), clickBorderColor()),
                        colors =
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                }
                AssistChip(
                    onClick = onCreateIntent,
                    label = {
                        Text(
                            if (intents.isEmpty()) "Set what you're down for" else "Add intent",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(clickBorderWidth(), clickBorderColor()),
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                )
            }
        }
    }
}
