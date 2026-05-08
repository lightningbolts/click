package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
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
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
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
    /** Hub slug from venue (e.g. local_point); runs proximity check then opens hub chat. */
    onJoinCommunityHub: (hubId: String) -> Unit = {},
    onStartChatting: () -> Unit = {}
) {
    var isClicked by remember { mutableStateOf(false) }
    var clickedUserName by remember { mutableStateOf("") }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Box(modifier = Modifier.padding(top = topInset)) {
                PageHeader(
                    title = "Add Click",
                    subtitle = "Connect with QR or Tap to Connect, or join a venue community hub",
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (!isClicked) {
                AddClickContent(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    onClickSuccess = { userName ->
                        isClicked = true
                        clickedUserName = userName
                    },
                    onNavigateToNfc = onNavigateToNfc,
                    onShowMyQRCode = onShowMyQRCode,
                    onScanQRCode = onScanQRCode,
                    onJoinCommunityHub = onJoinCommunityHub,
                )
            } else {
                ClickedSuccessContent(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    userName = clickedUserName,
                    onStartChatting = onStartChatting
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClickContent(
    modifier: Modifier = Modifier,
    onClickSuccess: (String) -> Unit,
    onNavigateToNfc: () -> Unit,
    onShowMyQRCode: () -> Unit,
    onScanQRCode: () -> Unit,
    onJoinCommunityHub: (hubId: String) -> Unit = {},
) {
    var showHubCodeDialog by remember { mutableStateOf(false) }
    var hubCodeDraft by remember { mutableStateOf("") }

    if (showHubCodeDialog) {
        GlassAlertDialog(
            onDismissRequest = {
                showHubCodeDialog = false
                hubCodeDraft = ""
            },
            title = { Text("Join community hub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter the hub code shown at the venue. You must be within range for location check.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassSheetTokens.OnOledMuted,
                    )
                    OutlinedTextField(
                        value = hubCodeDraft,
                        onValueChange = { hubCodeDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. local_point", color = GlassSheetTokens.OnOledMuted.copy(alpha = 0.5f)) },
                        label = { Text("Hub code", color = GlassSheetTokens.OnOledMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GlassSheetTokens.OnOled,
                            unfocusedTextColor = GlassSheetTokens.OnOled,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = GlassSheetTokens.GlassBorder,
                            cursorColor = PrimaryBlue,
                            focusedLabelColor = GlassSheetTokens.OnOledMuted,
                            unfocusedLabelColor = GlassSheetTokens.OnOledMuted,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = hubCodeDraft.trim()
                        if (id.isNotEmpty()) {
                            onJoinCommunityHub(id)
                            showHubCodeDialog = false
                            hubCodeDraft = ""
                        }
                    },
                    enabled = hubCodeDraft.trim().isNotEmpty(),
                ) {
                    Text("Join hub", color = GlassSheetTokens.OnOled)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHubCodeDialog = false
                        hubCodeDraft = ""
                    },
                ) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                }
            },
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // QR Code Section - Two cards side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AdaptiveCard(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.85f),
                onClick = onShowMyQRCode
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.QrCode,
                        contentDescription = "My QR Code",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "My Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Share your QR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AdaptiveCard(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.85f),
                onClick = onScanQRCode
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = "Scan QR",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Scan Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Friend or hub QR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tap to Connect (BLE + audio + GPS) — full width card
        AdaptiveCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            onClick = onNavigateToNfc
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.BluetoothSearching,
                    contentDescription = "Tap to Connect",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Tap to Connect",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Nearby handshake with Bluetooth and audio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Ephemeral community hub (venue QR or code)
        AdaptiveCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            onClick = { showHubCodeDialog = true },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Groups,
                    contentDescription = "Community hub",
                    tint = MaterialTheme.colorScheme.primary,
                    // DO NOT REVERT: Fixed size per design specs
                    modifier = Modifier.size(120.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Community hub",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Enter a hub code or scan a hub QR with Scan Code",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}


@Composable
fun ClickedSuccessContent(
    modifier: Modifier = Modifier,
    userName: String,
    onStartChatting: () -> Unit
) {
    Column(
        modifier = modifier
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
