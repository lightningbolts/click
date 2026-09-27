@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ScheduleSendTiming // pragma: allowlist secret
import compose.project.click.click.data.models.ScheduledMessage // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickDateTimePickerDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetPrimaryButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetSecondaryButton // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import compose.project.click.click.util.formatClockTime // pragma: allowlist secret
import compose.project.click.click.util.formatRelativeDay // pragma: allowlist secret
import compose.project.click.click.util.formatShortDateTime // pragma: allowlist secret
import kotlinx.datetime.Clock

/*
 * Send Later UI: iOS ScheduleSendSheet, ScheduledMessagesBar and ScheduledMessagesSheet.
 */

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** "Scheduled for Oct 4, 7:00 PM": the toast after scheduling. */
internal fun scheduledToastText(sendAtEpochMs: Long): String = "Scheduled for ${formatShortDateTime(sendAtEpochMs)}"

/** "Tomorrow 9:00 AM" style label for the next scheduled send. */
internal fun scheduledWhenLabel(
    sendAtEpochMs: Long,
    nowEpochMs: Long,
): String = "${formatRelativeDay(sendAtEpochMs, nowEpochMs)} ${formatClockTime(sendAtEpochMs)}"

/** Picks when the composer text goes out. Text only; the caller hides the entry point otherwise. */
@Composable
internal fun ScheduleSendSheet(
    text: String,
    onDismiss: () -> Unit,
    onSchedule: (sendAtEpochMs: Long) -> Unit,
) {
    val openedAt = remember { nowMs() }
    var sendAt by remember { mutableLongStateOf(ScheduleSendTiming.defaultSendAt(openedAt)) }
    var showPicker by remember { mutableStateOf(false) }

    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        ClickSheetChrome(
            title = "Send Later",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(clickCardSurface(), RoundedCornerShape(12.dp))
                        .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(12.dp))
                        .padding(12.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showPicker = true }
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Send at", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatShortDateTime(sendAt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { showPicker = true }) { Text("Change") }
            }
            Spacer(Modifier.height(16.dp))
            ClickSheetPrimaryButton(
                text = "Schedule",
                enabled = ScheduleSendTiming.isValid(sendAt, nowMs()),
                onClick = { onSchedule(sendAt) },
            )
            Spacer(Modifier.height(10.dp))
            ClickSheetSecondaryButton(text = "Cancel", onClick = onDismiss)
            Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
        }
    }

    if (showPicker) {
        val now = nowMs()
        ClickDateTimePickerDialog(
            initialEpochMs = sendAt,
            minEpochMs = now + ScheduleSendTiming.MIN_LEAD_MS,
            maxEpochMs = now + ScheduleSendTiming.MAX_AHEAD_MS,
            onPicked = {
                sendAt = it
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Capsule above the composer: "2 scheduled messages · next Tomorrow 9:00 AM". */
@Composable
internal fun ScheduledMessagesBar(
    messages: List<ScheduledMessage>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = messages.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val next = messages.minByOrNull { it.sendAtEpochMs }
        val count = messages.size
        val label =
            buildString {
                append(if (count == 1) "1 scheduled message" else "$count scheduled messages")
                if (next != null) append(" · next ${scheduledWhenLabel(next.sendAtEpochMs, nowMs())}")
            }
        Row(
            modifier =
                Modifier
                    .padding(bottom = 6.dp)
                    .background(clickCardSurface(), RoundedCornerShape(50))
                    .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(50))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { contentDescription = "$label. Show scheduled messages." },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pending scheduled messages, soonest first; each can be cancelled (no edit or send-now, as on iOS). */
@Composable
internal fun ScheduledMessagesSheet(
    messages: List<ScheduledMessage>,
    onCancelMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        ClickSheetChrome(
            title = "Scheduled",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            if (messages.isEmpty()) {
                Text(
                    "Nothing scheduled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                messages.sortedBy { it.sendAtEpochMs }.forEachIndexed { index, message ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                formatShortDateTime(message.sendAtEpochMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onCancelMessage(message.id) }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
        }
    }
}
