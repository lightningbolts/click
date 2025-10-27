package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.AdaptiveSurface

data class ClickConnection(
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)

@Composable
fun ConnectionsScreen() {
    var selectedConnection by remember { mutableStateOf<ClickConnection?>(null) }

    if (selectedConnection == null) {
        ConnectionsListView(
            onConnectionSelected = { connection ->
                selectedConnection = connection
            }
        )
    } else {
        ChatView(
            connection = selectedConnection!!,
            onBackPressed = { selectedConnection = null }
        )
    }
}

@Composable
fun ConnectionsListView(onConnectionSelected: (ClickConnection) -> Unit) {
    val connections = remember {
        listOf(
            ClickConnection("Alice", "See you tomorrow!", "2m ago", 2, true),
            ClickConnection("Charlie", "That was fun!", "1h ago", 0, true),
            ClickConnection("Diana", "Let's click again soon", "Yesterday", 1, false),
            ClickConnection("Eve", "Thanks for the coffee ☕", "2d ago", 0, false),
            ClickConnection("Frank", "Great meeting you!", "3d ago", 0, false),
            ClickConnection("Grace", "📍 See you at the park", "1w ago", 0, false),
        )
    }

    AdaptiveBackground(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header with adaptive surface
            AdaptiveSurface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        "Clicks",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceLight
                    )
                    Text(
                        "${connections.size} connections",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }

            // Connections List
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(connections) { connection ->
                    ConnectionItem(
                        connection = connection,
                        onClick = { onConnectionSelected(connection) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = Color.LightGray.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionItem(connection: ClickConnection, onClick: () -> Unit) {
    AdaptiveCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PrimaryBlue, LightBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        connection.name.first().toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                // Online indicator
                if (connection.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                            .padding(2.dp)
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .background(Color(0xFF4CAF50), RoundedCornerShape(6.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        connection.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceLight
                    )
                    Text(
                        connection.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        connection.lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (connection.unreadCount > 0) OnSurfaceLight else TextSecondary,
                        fontWeight = if (connection.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )

                    if (connection.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(PrimaryBlue, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                connection.unreadCount.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(connection: ClickConnection, onBackPressed: () -> Unit) {
    var messageText by remember { mutableStateOf("") }

    val messages = remember {
        listOf(
            ChatMessage("Hey! Want to grab coffee?", false, "10:30 AM"),
            ChatMessage("Sure! Where?", true, "10:32 AM"),
            ChatMessage("The usual spot?", false, "10:33 AM"),
            ChatMessage(connection.lastMessage, true, "10:35 AM"),
        )
    }

    AdaptiveBackground(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header with adaptive surface
            AdaptiveSurface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurfaceLight)
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PrimaryBlue, LightBlue)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            connection.name.first().toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            connection.name,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurfaceLight
                        )
                        if (connection.isOnline) {
                            Text(
                                "Online",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }

                    IconButton(onClick = { /* Voice call */ }) {
                        Icon(Icons.Filled.Call, contentDescription = "Voice Call", tint = PrimaryBlue)
                    }

                    IconButton(onClick = { /* Video call */ }) {
                        Icon(Icons.Filled.VideoCall, contentDescription = "Video Call", tint = PrimaryBlue)
                    }
                }
            }

            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    ChatMessageBubble(message)
                }
            }

            // Input
            AdaptiveSurface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color.LightGray
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { /* Send message */ },
                        modifier = Modifier
                            .size(48.dp)
                            .background(PrimaryBlue, RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

data class ChatMessage(val text: String, val isSent: Boolean, val time: String)

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isSent) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isSent) 16.dp else 4.dp,
                bottomEnd = if (message.isSent) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isSent) PrimaryBlue else SurfaceLight
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    message.text,
                    color = if (message.isSent) Color.White else OnSurfaceLight,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    message.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isSent) Color.White.copy(alpha = 0.7f) else TextSecondary
                )
            }
        }
    }
}
