@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.models.HangoutPlan // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.PlanResponses // pragma: allowlist secret
import compose.project.click.click.data.models.PlanRsvp // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import compose.project.click.click.util.formatClockTime // pragma: allowlist secret
import compose.project.click.click.util.formatRelativeDay // pragma: allowlist secret
import io.ktor.http.encodeURLQueryComponent

/*
 * Plan card, responses sheet and group revival banner (iOS `PlanViews.swift`).
 */

/** "Today · 7:00 PM–9:00 PM", "Tomorrow · 7:00 PM", "Saturday · …", "Sat, Oct 4 · …". */
internal fun planWhenText(
    plan: HangoutPlan,
    nowEpochMs: Long,
): String {
    val day = formatRelativeDay(plan.startsAtEpochMs, nowEpochMs)
    val start = formatClockTime(plan.startsAtEpochMs)
    val end = plan.validEndsAtEpochMs?.let { "–${formatClockTime(it)}" }.orEmpty()
    return "$day · $start$end"
}

/** Opens the place in a maps app: coordinates when known, otherwise a text search. */
internal fun planMapsUri(plan: HangoutPlan): String? {
    val place = plan.placeName?.takeIf { it.isNotBlank() }
    val lat = plan.latitude
    val lon = plan.longitude
    return when {
        lat != null && lon != null -> "geo:$lat,$lon?q=$lat,$lon(${(place ?: plan.title).encodeURLQueryComponent()})"
        place != null -> "https://www.google.com/maps/search/?api=1&query=${place.encodeURLQueryComponent()}"
        else -> null
    }
}

/** Reactions other than the RSVP pair, which the card itself represents. */
internal fun nonRsvpReactions(reactions: List<MessageReaction>): List<MessageReaction> =
    reactions.filterNot { it.reactionType == HangoutPlan.GOING || it.reactionType == HangoutPlan.DECLINED }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlanChatCard(
    plan: HangoutPlan,
    isSent: Boolean,
    responses: PlanResponses,
    currentUserId: String?,
    nowEpochMs: Long,
    /** False while sending or after a failed send: RSVP is disabled. */
    interactive: Boolean,
    onRsvp: (PlanRsvp?) -> Unit,
    onShowResponses: () -> Unit,
    onLongPress: () -> Unit,
    enableContextMenu: Boolean,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(ChatBubbleTokens.cornerMain)
    val mine = responses.rsvpOf(currentUserId)
    val over = plan.isOver(nowEpochMs)
    val summary = "Plan: ${plan.title}, ${planWhenText(plan, nowEpochMs)}"
    Column(
        modifier =
            modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (isSent) PrimaryBlue.copy(alpha = 0.18f) else clickCardSurface())
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .combinedClickable(
                    onClick = {},
                    onLongClick =
                        if (enableContextMenu) {
                            {
                                PlatformHapticsPolicy.heavyImpact()
                                onLongPress()
                            }
                        } else {
                            null
                        },
                ).padding(14.dp)
                .semantics(mergeDescendants = false) { contentDescription = summary },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryBlue)
            Spacer(Modifier.width(6.dp))
            Text("PLAN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue)
        }
        Spacer(Modifier.height(6.dp))
        Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            planWhenText(plan, nowEpochMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        plan.placeName?.let { place ->
            val uri = planMapsUri(plan)
            Row(
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .then(if (uri != null) Modifier.clickable { runCatching { uriHandler.openUri(uri) } } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    place,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uri != null) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (over) {
            val went = responses.goingUserIds.size
            Text(
                if (went > 0) "$went went" else "This plan has passed",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RsvpButton(
                    label = "Going",
                    selected = mine == PlanRsvp.GOING,
                    enabled = interactive,
                    modifier = Modifier.weight(1f),
                    onClick = { onRsvp(if (mine == PlanRsvp.GOING) null else PlanRsvp.GOING) },
                )
                RsvpButton(
                    label = "Can't",
                    selected = mine == PlanRsvp.DECLINED,
                    enabled = interactive,
                    modifier = Modifier.weight(1f),
                    onClick = { onRsvp(if (mine == PlanRsvp.DECLINED) null else PlanRsvp.DECLINED) },
                )
            }
            val going = responses.goingUserIds.size
            val declined = responses.declinedUserIds.size
            if (going + declined > 0) {
                Text(
                    buildString {
                        append("$going going")
                        if (declined > 0) append(" · $declined can't")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(top = 8.dp)
                            .clickable(onClick = onShowResponses),
                )
            }
        }
    }
}

@Composable
private fun RsvpButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color =
            when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                selected -> Color.White
                else -> MaterialTheme.colorScheme.onSurface
            },
        modifier =
            modifier
                .clip(shape)
                .background(if (selected) PrimaryBlue else Color.Transparent)
                .border(clickBorderWidth(), if (selected) PrimaryBlue else clickBorderColor(), shape)
                .clickable(enabled = enabled) {
                    PlatformHapticsPolicy.lightImpact()
                    onClick()
                }.padding(vertical = 8.dp)
                .semantics { contentDescription = if (selected) "$label, selected" else label },
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Who's going / can't, with names resolved by the caller. */
@Composable
internal fun PlanResponsesSheet(
    plan: HangoutPlan,
    responses: PlanResponses,
    nameFor: (String) -> String,
    onDismiss: () -> Unit,
) {
    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        ClickSheetChrome(
            title = plan.title,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            ResponseSection("Going", responses.goingUserIds, nameFor)
            if (responses.declinedUserIds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ResponseSection("Can't make it", responses.declinedUserIds, nameFor)
            }
            Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
        }
    }
}

@Composable
private fun ResponseSection(
    title: String,
    userIds: List<String>,
    nameFor: (String) -> String,
) {
    Text(
        "$title · ${userIds.size}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    userIds.forEachIndexed { index, id ->
        if (index > 0) HorizontalDivider()
        Text(nameFor(id), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 10.dp))
    }
    if (userIds.isEmpty()) {
        Text("No one yet", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
    }
}

/** A group quiet for 21+ days (with some history) gets a nudge to plan something. */
internal fun shouldShowGroupRevival(
    isGroupChat: Boolean,
    messageCount: Int,
    lastMessageEpochMs: Long?,
    nowEpochMs: Long,
): Boolean {
    if (!isGroupChat || messageCount < 10 || lastMessageEpochMs == null) return false
    return nowEpochMs - lastMessageEpochMs >= 21L * 24 * 60 * 60 * 1000
}

@Composable
internal fun GroupRevivalBanner(
    groupName: String,
    onPlan: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(clickCardSurface())
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("It's been a while", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Get $groupName together again?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onPlan) { Text("Plan") }
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
    }
}
