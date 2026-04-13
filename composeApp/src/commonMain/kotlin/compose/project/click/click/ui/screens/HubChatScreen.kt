package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.ui.chat.rememberChatMediaPickers
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.utils.LocationResult
import compose.project.click.click.viewmodel.HubChatViewModel

data class HubChatNavArgs(
    val hubId: String,
    val realtimeChannel: String,
    val hubTitle: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubChatScreen(
    args: HubChatNavArgs,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    resolveHubGatekeeperLocation: suspend () -> LocationResult? = { null },
) {
    val viewModel: HubChatViewModel = viewModel(key = args.realtimeChannel) {
        HubChatViewModel(
            hubId = args.hubId,
            realtimeChannelName = args.realtimeChannel,
            hubTitle = args.hubTitle,
            currentUserId = currentUserId,
            hubLocationResolver = resolveHubGatekeeperLocation,
        )
    }

    val messages by viewModel.messages.collectAsState()
    val occupantCount by viewModel.occupantCount.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val isSending by viewModel.isSending.collectAsState()

    val mediaPickers = rememberChatMediaPickers(
        onImagePicked = { bytes, mime -> viewModel.sendHubImageFromPicker(bytes, mime) },
        onAudioPicked = { _, _, _ -> },
        onMediaAccessBlocked = { },
    )

    val hubIdForSecureMedia = remember(args.hubId) { args.hubId }

    val inLobby = occupantCount < 3

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (inLobby) {
                                "$occupantCount ${if (occupantCount == 1) "person" else "people"} here"
                            } else {
                                "$occupantCount people in this hub"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryBlue,
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = "See someone interesting? Go tap phones to make a permanent connection.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

            if (inLobby) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "You're the first one here! We'll ping you when others join.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.message.id }) { mwu ->
                    ChatMessageBubble(
                        messageWithUser = mwu,
                        currentUserId = currentUserId,
                        reactions = emptyList(),
                        onToggleReaction = {},
                        onForward = {},
                        onLongPress = {},
                        onSwipeReply = {},
                        showPeerAvatarInGroup = true,
                        secureMediaHost = viewModel,
                        activeChatId = hubIdForSecureMedia,
                    )
                }
            }

            sendError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = viewModel::updateDraft,
                    modifier = Modifier.weight(1f),
                    enabled = !inLobby && !isSending,
                    placeholder = {
                        Text(
                            if (inLobby) "Chat unlocks when 3+ people are here"
                            else "Message the hub…",
                        )
                    },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                )
                IconButton(
                    onClick = { mediaPickers.openPhotoLibrary() },
                    enabled = !inLobby && !isSending,
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = "Attach image",
                        tint = if (!inLobby && !isSending) {
                            PrimaryBlue
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
                IconButton(
                    onClick = { viewModel.sendMessage() },
                    enabled = !inLobby && draft.isNotBlank() && !isSending,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (!inLobby && draft.isNotBlank() && !isSending) {
                            PrimaryBlue
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
    }
}
