package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ConnectionArchiveNotice // pragma: allowlist secret
import compose.project.click.click.ui.theme.GlassBorder
import compose.project.click.click.ui.theme.GlassWhite
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.NeonPurple
import compose.project.click.click.ui.theme.PrimaryBlue

/** Matches gradient accent used on [PollPairCard]. */
private val WebAccentBlue = Color(0xFF3A86FF)

/**
 * Glass + gradient border card matching [PollPairCard]; archive / idle window reminder with actions.
 */
@Composable
fun ConnectionArchiveWarningBanner(
    notice: ConnectionArchiveNotice,
    onOpenChat: () -> Unit,
    onSendIcebreaker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary =
        "${notice.headline}. ${notice.body}. Open chat. Send icebreaker."
    val outerShape = RoundedCornerShape(24.dp)
    val innerShape = RoundedCornerShape(23.dp)
    val borderGradient = Brush.linearGradient(
        colors = listOf(
            PrimaryBlue.copy(alpha = if (notice.urgent) 0.85f else 0.65f),
            WebAccentBlue.copy(alpha = if (notice.urgent) 0.85f else 0.65f),
        ),
    )
    val pStyle = LocalPlatformStyle.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = summary
            }
            .then(
                if (pStyle.useShadowElevation) {
                    Modifier.shadow(20.dp, outerShape, spotColor = PrimaryBlue.copy(alpha = 0.35f))
                } else {
                    Modifier
                },
            )
            .clip(outerShape)
            .background(borderGradient)
            .padding(if (pStyle.isIOS) 0.5.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(GlassWhite)
                .border(1.dp, GlassBorder, innerShape)
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    PrimaryBlue.copy(alpha = 0.35f),
                                    WebAccentBlue.copy(alpha = 0.2f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notice.headline,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonPurple,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Check in with ${notice.chatLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notice.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open chat", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onSendIcebreaker,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = WebAccentBlue,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Icebreaker",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
