@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.rememberReduceMotionEnabled // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenWithFloatingHeader // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickContentCard // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickListRow // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickScreenSpacing // pragma: allowlist secret
import compose.project.click.click.ui.components.CreateHubModal // pragma: allowlist secret
import compose.project.click.click.ui.components.JoinCommunityHubSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.SuccessBeat // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret

@Composable
fun AddClickScreen(
    currentUserId: String = "",
    currentUsername: String? = null,
    locationService: LocationService,
    onNavigateToNfc: () -> Unit = {},
    onShowMyQRCode: () -> Unit = {},
    onScanQRCode: () -> Unit = {},
    /** Hub slug from venue (e.g. local_point); runs proximity check then opens hub chat. */
    onJoinCommunityHub: (hubId: String) -> Unit = {},
    /** After POST `/api/hub/create`, verify geofence and open hub chat. */
    onCommunityHubCreated: (hubId: String) -> Unit = {},
    onStartChatting: () -> Unit = {},
    onHubCreateError: (String) -> Unit = {},
) {
    var isClicked by remember { mutableStateOf(false) }
    var clickedUserName by remember { mutableStateOf("") }

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        AppScreenWithFloatingHeader(
            title = "Add Click",
            subtitle = "Connect with QR or Tap to Connect, or join a venue community hub",
        ) { contentModifier ->
            if (!isClicked) {
                AddClickContent(
                    modifier = contentModifier.fillMaxWidth(),
                    onClickSuccess = { userName ->
                        isClicked = true
                        clickedUserName = userName
                    },
                    onNavigateToNfc = onNavigateToNfc,
                    onShowMyQRCode = onShowMyQRCode,
                    onScanQRCode = onScanQRCode,
                    onJoinCommunityHub = onJoinCommunityHub,
                    locationService = locationService,
                    onCommunityHubCreated = onCommunityHubCreated,
                    onHubCreateError = onHubCreateError,
                )
            } else {
                ClickedSuccessContent(
                    modifier = contentModifier.fillMaxWidth(),
                    userName = clickedUserName,
                    onStartChatting = onStartChatting,
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
    locationService: LocationService,
    onCommunityHubCreated: (hubId: String) -> Unit = {},
    onHubCreateError: (String) -> Unit = {},
) {
    var showJoinHubSheet by remember { mutableStateOf(false) }
    var showCreateHubModal by remember { mutableStateOf(false) }

    CreateHubModal(
        visible = showCreateHubModal,
        onDismiss = { showCreateHubModal = false },
        onHubCreated = { hubId ->
            showCreateHubModal = false
            onCommunityHubCreated(hubId)
        },
        locationService = locationService,
        onError = { msg -> onHubCreateError(msg) },
    )

    JoinCommunityHubSheet(
        visible = showJoinHubSheet,
        onDismiss = { showJoinHubSheet = false },
        onJoinHub = onJoinCommunityHub,
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ClickScreenSpacing.Section),
    ) {
        val reduceMotion = rememberReduceMotionEnabled()
        val pulse = rememberInfiniteTransition(label = "tap_to_connect_pulse")
        val pulseAlpha by pulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "tap_to_connect_pulse_alpha",
        )
        ClickContentCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToNfc,
            showBorder = false,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentPadding = ClickScreenSpacing.Section,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.BluetoothSearching,
                    contentDescription = "Tap to Connect",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .graphicsLayer { alpha = if (reduceMotion) 1f else pulseAlpha },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Tap to Connect",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(ClickScreenSpacing.Compact))
                Text(
                    "Nearby handshake with Bluetooth and audio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            AddClickSecondaryRow(
                title = "My QR",
                subtitle = "Share your code",
                icon = Icons.Filled.QrCode,
                onClick = onShowMyQRCode,
            )
            AddClickSecondaryRow(
                title = "Scan QR",
                subtitle = "Friend or hub code",
                icon = Icons.Filled.QrCodeScanner,
                onClick = onScanQRCode,
            )
            AddClickSecondaryRow(
                title = "Create Community Hub",
                subtitle = "Host a venue for nearby Clicks",
                icon = Icons.Filled.Campaign,
                onClick = { showCreateHubModal = true },
            )
            AddClickSecondaryRow(
                title = "Join Community Hub",
                subtitle = "Enter a venue code",
                icon = Icons.Filled.GroupAdd,
                onClick = { showJoinHubSheet = true },
                showDivider = false,
            )
        }
    }
}

@Composable
private fun AddClickSecondaryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    ClickListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        showDivider = showDivider,
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        },
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}

@Composable
fun ClickedSuccessContent(
    modifier: Modifier = Modifier,
    userName: String,
    onStartChatting: () -> Unit,
) {
    SuccessBeat(
        trigger = userName,
        modifier = modifier,
        hapticsEnabled = false,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(clickBorderWidth(), clickBorderColor(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Success",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Clicked with $userName!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "You're now connected and can start chatting.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            ClickButton(onClick = onStartChatting, modifier = Modifier.fillMaxWidth()) {
                Text("Start Chatting")
            }
        }
    }
}
