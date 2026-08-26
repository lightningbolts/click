@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.navigation.NavigationItem // pragma: allowlist secret
import compose.project.click.click.navigation.bottomNavItems // pragma: allowlist secret
import compose.project.click.click.ui.components.GlobalTetherOverlay // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformBottomBar // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastState // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberBottomChromePadding // pragma: allowlist secret
import compose.project.click.click.ui.screens.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthState // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthViewModel // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Job

@Composable
internal fun BoxScope.AppSyncStatusCard(
    isInitialLoading: Boolean,
    pendingConnectionsCount: Int,
    appError: String?,
) {
    if (!isInitialLoading && (pendingConnectionsCount > 0 || appError != null)) {
        Card(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(
                        top = 8.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ).fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (pendingConnectionsCount > 0) {
                    Text(
                        text = "$pendingConnectionsCount connection${if (pendingConnectionsCount == 1) "" else "s"} queued for sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!appError.isNullOrBlank()) {
                    Text(
                        text = appError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.AppBottomChrome(
    currentRoute: String,
    hideMainBottomBar: Boolean,
    navigateTo: (String) -> Unit,
    focusManager: FocusManager,
    toastState: UnifiedToastState,
    authViewModel: AuthViewModel,
    hubChatCloseJobState: MutableState<Job?>,
    hubChatArgsState: MutableState<HubChatNavArgs?>,
    showMyQRCodeState: MutableState<Boolean>,
    showQRScannerState: MutableState<Boolean>,
    showNfcScreenState: MutableState<Boolean>,
    showUnifiedSearchSheetState: MutableState<Boolean>,
    pendingHubTargetMessageIdState: MutableState<String?>,
    pendingChatIdState: MutableState<String?>,
    pendingTargetMessageIdState: MutableState<String?>,
    pendingBeaconIdState: MutableState<String?>,
) {
    var hubChatCloseJob by hubChatCloseJobState
    var hubChatArgs by hubChatArgsState
    var showMyQRCode by showMyQRCodeState
    var showQRScanner by showQRScannerState
    var showNfcScreen by showNfcScreenState
    var showUnifiedSearchSheet by showUnifiedSearchSheetState
    var pendingHubTargetMessageId by pendingHubTargetMessageIdState
    var pendingChatId by pendingChatIdState
    var pendingTargetMessageId by pendingTargetMessageIdState
    var pendingBeaconId by pendingBeaconIdState
    // Overlay (not Scaffold bottomBar) so tab content scrolls under a translucent bar.
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(5f),
    ) {
        PlatformBottomBar(
            items = bottomNavItems,
            currentRoute = currentRoute,
            visible = !hideMainBottomBar,
            onItemSelected = { item ->
                navigateTo(item.route)
                hubChatCloseJob?.cancel()
                hubChatCloseJob = null
                hubChatArgs = null
                showMyQRCode = false
                showQRScanner = false
                showNfcScreen = false
                focusManager.clearFocus()
            },
        )
    }
    UnifiedToastHost(
        state = toastState,
        opaque = true,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = rememberBottomChromePadding() + 8.dp)
                .zIndex(6f),
    )
    if (showUnifiedSearchSheet) {
        val searchUserId =
            when (val state = authViewModel.authState) {
                is AuthState.Success -> state.userId
                else -> ""
            }
        if (searchUserId.isNotEmpty()) {
            UnifiedSearchSheet(
                onDismissRequest = { showUnifiedSearchSheet = false },
                userId = searchUserId,
                onNavigateToChat = { target ->
                    showUnifiedSearchSheet = false
                    if (target.isHub && !target.hubId.isNullOrBlank()) {
                        pendingHubTargetMessageId = target.targetMessageId
                        hubChatArgs =
                            HubChatNavArgs(
                                hubId = target.hubId,
                                realtimeChannel =
                                    target.hubRealtimeChannel
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "hub:${target.hubId}",
                                hubTitle = target.hubTitle?.ifBlank { "Hub" } ?: "Hub",
                                creatorId = target.hubCreatorId,
                                hubCategory = target.hubCategory,
                            )
                    } else {
                        pendingChatId = target.connectionId
                        pendingTargetMessageId = target.targetMessageId
                        navigateTo(NavigationItem.Connections.route)
                    }
                },
                onNavigateToMap = {
                    showUnifiedSearchSheet = false
                    navigateTo(NavigationItem.Map.route)
                },
                onNavigateToBeacon = { beaconId ->
                    showUnifiedSearchSheet = false
                    pendingBeaconId = beaconId
                    navigateTo(NavigationItem.Map.route)
                },
                onNavigateToSettings = {
                    showUnifiedSearchSheet = false
                    navigateTo(NavigationItem.Settings.route)
                },
            )
        }
    }
    GlobalTetherOverlay(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(70f),
    )
}
