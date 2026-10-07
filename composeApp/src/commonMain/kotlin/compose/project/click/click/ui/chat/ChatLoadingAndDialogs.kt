@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickLogoPulse
import compose.project.click.click.ui.components.platformNativeHeaderClearance

/**
 * Chat-screen loading states and the cross-chat forward dialog.
 *
 * Extracted from ConnectionsScreen.kt so chat loading / forward UI
 * doesn't share a file with the screen shell. Bodies moved verbatim.
 *
 * [ChatDetailsLoadingHeader] is shown when [ChatMessagesState.Loading] but the chat
 * list already has a row for this thread, so we avoid a blank full-screen spinner
 * while the ViewModel resolves details.
 */
@Composable
internal fun ChatWarmLoadingView(
    topInset: Dp,
    onBackPressed: () -> Unit,
    chatRow: ChatWithDetails,
    composeHeader: Boolean = true,
) {
    val title =
        chatRow.groupClique
            ?.name
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: chatRow.otherUser.name
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: "Chat"
    Column(modifier = Modifier.fillMaxSize()) {
        if (composeHeader) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = topInset, end = 20.dp)
                        .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBackPressed,
                    showBorder = true,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            val stacked = chatRow.groupClique == null
            Spacer(
                modifier =
                    Modifier.fillMaxWidth().height(
                        platformNativeHeaderClearance(
                            statusBarTop = topInset,
                            collapseFraction = 1f,
                            hasSubtitle = stacked,
                            stackSubtitle = stacked,
                        ),
                    ),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            ClickLogoPulse(logoSize = 72.dp)
        }
    }
}

/**
 * Full-screen loading state shown while the chat channel subscribes;
 * pulses the spinner between Primary and Light theme blues.
 */
@Composable
internal fun ChatChannelLoadingView(
    topInset: Dp,
    onBackPressed: () -> Unit,
    composeHeader: Boolean = true,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (composeHeader) {
            Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatHeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBackPressed,
                        showBorder = true,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.fillMaxWidth().height(platformNativeHeaderClearance(topInset)))
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = if (composeHeader) topInset + 56.dp else platformNativeHeaderClearance(topInset)),
            contentAlignment = Alignment.Center,
        ) {
            ClickLogoPulse(logoSize = 72.dp)
        }
    }
}
