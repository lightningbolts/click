@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.api.ConnectionEventRecommendationDto // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret

@Composable
fun ConnectionEventRecommendationCard(
    recommendation: ConnectionEventRecommendationDto,
    onRsvp: () -> Unit,
    onDismiss: () -> Unit,
    rsvpInProgress: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = clickCardSurface(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Go together?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${recommendation.peerName} is going to ${recommendation.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val whenWhere =
                listOfNotNull(
                    recommendation.eventStartAt?.take(16)?.replace('T', ' '),
                    recommendation.locationName
                        ?.trim()
                        ?.takeUnless { it.isEmpty() || it.equals("Current location", ignoreCase = true) },
                ).joinToString(" · ")
            if (whenWhere.isNotBlank()) {
                Text(
                    text = whenWhere,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRsvp,
                    enabled = !rsvpInProgress,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (rsvpInProgress) "RSVPing…" else "RSVP")
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !rsvpInProgress,
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}
