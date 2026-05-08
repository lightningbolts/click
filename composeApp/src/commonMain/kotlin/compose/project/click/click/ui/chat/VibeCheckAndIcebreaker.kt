package compose.project.click.click.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.IcebreakerPrompt
import compose.project.click.click.ui.theme.PrimaryBlue

/**
 * Vibe Check banner + mutual-keep dialog, and the Icebreaker prompts
 * panel shown above the chat composer.
 *
 * Extracted verbatim from ConnectionsScreen.kt; no behavior change.
 */

/**
 * Shows the vibe-check countdown timer, context tag, and keep button.
 * Displays different states based on timer status and user decisions
 * (pending, active, warning-red, both-kept celebration, or
 * time's-up with modal dialog).
 */
@Composable
fun VibeCheckBanner(
    connection: Connection,
    remainingMs: Long,
    currentUserHasKept: Boolean,
    otherUserHasKept: Boolean,
    vibeCheckExpired: Boolean,
    connectionKept: Boolean,
    onKeepClick: () -> Unit,
    onExpiredDismiss: () -> Unit,
) {
    val isTimerActive = remainingMs > 0 && !connectionKept
    val isWarning = remainingMs in 1..300_000

    val bannerColor = when {
        connectionKept -> PrimaryBlue.copy(alpha = 0.15f)
        vibeCheckExpired && !connectionKept -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        else -> PrimaryBlue.copy(alpha = 0.1f)
    }

    val bannerBorderColor = when {
        connectionKept -> PrimaryBlue
        vibeCheckExpired && !connectionKept -> MaterialTheme.colorScheme.error
        isWarning -> MaterialTheme.colorScheme.error
        else -> PrimaryBlue.copy(alpha = 0.5f)
    }

    if (vibeCheckExpired && !connectionKept) {
        GlassAlertDialog(
            onDismissRequest = { },
            icon = {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp),
                )
            },
            title = {
                Text(
                    "Vibe Check Complete",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (currentUserHasKept && !otherUserHasKept) {
                            "Unfortunately, the other person didn't choose to keep this connection. The chat will be deleted."
                        } else if (!currentUserHasKept && otherUserHasKept) {
                            "You didn't choose to keep this connection. The chat will be deleted."
                        } else {
                            "Neither of you chose to keep this connection. The chat will be deleted."
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassSheetTokens.OnOledMuted,
                    )
                }
            },
            dismissButton = null,
            confirmButton = {
                Button(
                    onClick = onExpiredDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("OK")
                }
            },
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = bannerColor,
        border = BorderStroke(1.dp, bannerBorderColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            connection.context_tag?.let { tag ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            when {
                connectionKept -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Connection Kept!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                        )
                    }
                    Text(
                        "You both chose to continue this connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                isTimerActive -> {
                    val isPending = connection.isPending()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isPending) Icons.Filled.EmojiPeople else Icons.Filled.Timer,
                                contentDescription = null,
                                tint = if (isWarning) MaterialTheme.colorScheme.error else PrimaryBlue,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    if (isPending) "Say Hi" else "Vibe Check",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    formatVibeCheckTime(remainingMs),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (otherUserHasKept) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PrimaryBlue.copy(alpha = 0.1f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = PrimaryBlue,
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "They want to keep!",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PrimaryBlue,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = onKeepClick,
                                enabled = !currentUserHasKept,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentUserHasKept) PrimaryBlue.copy(alpha = 0.5f) else PrimaryBlue,
                                    disabledContainerColor = PrimaryBlue.copy(alpha = 0.3f),
                                ),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                if (currentUserHasKept) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kept!")
                                } else {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Keep")
                                }
                            }
                        }
                    }

                    Text(
                        if (currentUserHasKept) {
                            "Waiting for ${if (otherUserHasKept) "mutual confirmation" else "them to decide"}..."
                        } else {
                            "Click 'Keep' if you want to continue this connection"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                else -> {
                    Text(
                        "Time's up!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Shows conversation-starter prompts to help break the ice above the
 * chat composer. Displays tappable prompts that users can click to
 * auto-fill the message input, plus refresh and dismiss controls.
 */
@Composable
fun IcebreakerPanel(
    prompts: List<IcebreakerPrompt>,
    onPromptClick: (IcebreakerPrompt) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    /** Seconds left before "new prompts" refresh is allowed again (mirrors [ChatViewModel.icebreakerCooldownRemainingSec]). */
    cooldownRemainingSec: Int = 0,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = PrimaryBlue.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = PrimaryBlue,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Conversation Starters",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val refreshLocked = cooldownRemainingSec > 0
                    IconButton(
                        onClick = onRefresh,
                        enabled = !refreshLocked,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Get new prompts",
                            modifier = Modifier
                                .size(18.dp)
                                .alpha(if (refreshLocked) 0.35f else 1f),
                            tint = PrimaryBlue.copy(alpha = if (refreshLocked) 0.35f else 0.7f),
                        )
                    }
                    Box(
                        modifier = Modifier.widthIn(min = 34.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (refreshLocked) {
                            Text(
                                text = "${cooldownRemainingSec}s",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                prompts.forEach { prompt ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PrimaryBlue.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .clickable { onPromptClick(prompt) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(PrimaryBlue.copy(alpha = 0.6f)),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                prompt.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Tap a prompt to use it",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryBlue.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
