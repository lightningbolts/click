@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.InboxNudge // pragma: allowlist secret
import compose.project.click.click.data.models.NudgeKind // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderStroke // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret

private fun NudgeKind.icon(): ImageVector =
    when (this) {
        NudgeKind.SHARED_UPCOMING_EVENT -> Icons.Filled.Event
        NudgeKind.RECONNECT_LULL -> Icons.Filled.Schedule
        NudgeKind.ANNIVERSARY -> Icons.Filled.Cake
        NudgeKind.MEMORY_PROMPT -> Icons.Filled.EditNote
        NudgeKind.GROUP_REVIVAL -> Icons.Filled.Groups
        NudgeKind.WAVE -> Icons.Filled.WavingHand
        NudgeKind.HANGOUT_CONFIRM -> Icons.Filled.People
    }

private fun NudgeKind.primaryIcon(): ImageVector =
    when (this) {
        NudgeKind.SHARED_UPCOMING_EVENT -> Icons.Filled.Event
        NudgeKind.WAVE -> Icons.Filled.WavingHand
        NudgeKind.HANGOUT_CONFIRM -> Icons.Filled.Check
        NudgeKind.MEMORY_PROMPT -> Icons.Filled.EditNote
        NudgeKind.GROUP_REVIVAL -> Icons.Filled.Groups
        NudgeKind.RECONNECT_LULL, NudgeKind.ANNIVERSARY -> Icons.Filled.Chat
    }

/**
 * Dismissible relationship-moment row (reconnect, shared event, anniversary, memory, group revival,
 * wave, hangout confirmation). Same chrome as [ConnectionArchiveWarningBanner].
 * For hangout confirmations the secondary button is "Not us" (decline) rather than Dismiss.
 */
@Composable
fun InboxNudgeBanner(
    nudge: InboxNudge,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = nudge.primaryActionTitle
    val dismissLabel = nudge.secondaryActionTitle
    val summary = "${nudge.headline}. ${nudge.body}. $openLabel. $dismissLabel."
    val outerShape = RoundedCornerShape(16.dp)
    val icon = nudge.kind.icon()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = summary
                }.clip(outerShape)
                .background(clickCardSurface())
                .border(clickBorderWidth(), clickBorderColor(), outerShape)
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nudge.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = nudge.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onOpen,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(
                    nudge.kind.primaryIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(openLabel, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = clickBorderStroke(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(dismissLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
