package compose.project.click.click.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.isActiveForUser
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.viewmodel.ChatListState

/**
 * Chat-screen loading states and the cross-chat forward dialog.
 *
 * Extracted from ConnectionsScreen.kt so chat loading / forward UI
 * doesn't share a file with the screen shell. Bodies moved verbatim.
 */

/**
 * Shown when [ChatMessagesState.Loading] but the chat list already has
 * a row for this thread, so we avoid a blank full-screen spinner
 * while the ViewModel resolves details.
 */
@Composable
internal fun ChatWarmLoadingView(
    topInset: Dp,
    onBackPressed: () -> Unit,
    chatRow: ChatWithDetails,
) {
    val title = chatRow.groupClique?.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: chatRow.otherUser.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Chat"
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = topInset, end = 20.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AdaptiveCircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
                color = PrimaryBlue,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading conversation…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
) {
    val transition = rememberInfiniteTransition(label = "chat_loading_spinner")
    val spinnerScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chat_loading_scale",
    )
    val spinnerMix by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chat_loading_mix",
    )
    val spinnerColor = lerp(PrimaryBlue, LightBlue, spinnerMix)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset + 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            AdaptiveCircularProgressIndicator(
                modifier = Modifier.size((34f * spinnerScale).dp),
                strokeWidth = 3.dp,
                color = spinnerColor,
            )
        }
    }
}

/**
 * Alert dialog offering a list of other chats to forward a message to.
 * Active chats only, most-recent-first, with graceful empty/loading/
 * error states.
 */
@Composable
internal fun ForwardDialog(
    chatListState: ChatListState,
    currentChatId: String,
    archivedConnectionIds: Set<String>,
    hiddenConnectionIds: Set<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Forward to...") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
            ) {
                when (chatListState) {
                    is ChatListState.Success -> {
                        val options = chatListState.chats
                            .filter {
                                it.connection.id != currentChatId &&
                                    it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                            }
                            .sortedByDescending { connectionListActivityTs(it) }
                        if (options.isEmpty()) {
                            Text("No other chats available")
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                items(options, key = { it.connection.id }) { item ->
                                    ListItem(
                                        headlineContent = { Text(item.otherUser.name ?: "Unknown") },
                                        supportingContent = { Text(item.otherUser.email ?: "") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(8.dp)
                                            .clickable {
                                                onSelect(item.connection.id)
                                            },
                                    )
                                }
                            }
                        }
                    }
                    is ChatListState.Loading -> {
                        AdaptiveCircularProgressIndicator()
                    }
                    is ChatListState.Error -> {
                        Text("Failed to load chats")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
