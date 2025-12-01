package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveButton
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.PageHeader
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars

@Composable
fun AddClickScreen(
    currentUserId: String = "",
    currentUsername: String? = null,
    onNavigateToNfc: () -> Unit = {},
    onShowMyQRCode: () -> Unit = {},
    onScanQRCode: () -> Unit = {},
    onStartChatting: () -> Unit = {}
) {
    var isClicked by remember { mutableStateOf(false) }
    var clickedUserName by remember { mutableStateOf("") }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerTop = if (topInset > 32.dp) topInset - 32.dp else 0.dp

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = headerTop, end = 20.dp)) {
                PageHeader(title = "Add Click", subtitle = "Scan QR or use NFC to connect")
            }
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isClicked) {
                            AddClickContent(
                                onClickSuccess = { userName ->
                                    isClicked = true
                                    clickedUserName = userName
                                },
                                onNavigateToNfc = onNavigateToNfc,
                                onShowMyQRCode = onShowMyQRCode,
                                onScanQRCode = onScanQRCode
                            )
                        } else {
                            ClickedSuccessContent(
                                userName = clickedUserName,
                                onStartChatting = onStartChatting
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddClickContent(
    onClickSuccess: (String) -> Unit,
    onNavigateToNfc: () -> Unit,
    onShowMyQRCode: () -> Unit,
    onScanQRCode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {


        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Scan QR code or tap NFC to connect",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // QR Code Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AdaptiveCard(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp),
                onClick = onShowMyQRCode
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.Check, // Should use QR icon if available
                        contentDescription = "My QR Code",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("My Code", fontWeight = FontWeight.Bold)
                }
            }

            AdaptiveCard(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp),
                onClick = onScanQRCode
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.Search, // Should use Camera/Scan icon
                        contentDescription = "Scan QR",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Scan Code", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // NFC Section - Clickable card
        AdaptiveCard(
            modifier = Modifier
                .fillMaxWidth(),
            onClick = onNavigateToNfc
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Nfc,
                    contentDescription = "NFC",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Tap to use NFC",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AdaptiveButton(
            onClick = { onClickSuccess("Alice") },
        ) {
            Text("Demo: Simulate Click", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ClickedSuccessContent(
    userName: String,
    onStartChatting: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success Animation Placeholder with Material You
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(60.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Success",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Clicked with $userName!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "You're now connected and can start chatting.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        AdaptiveButton(onClick = onStartChatting) {
            Text("Start Chatting")
        }
    }
}
