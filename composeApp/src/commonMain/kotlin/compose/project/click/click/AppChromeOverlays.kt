@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.calls.ActiveCallOverlay // pragma: allowlist secret
import compose.project.click.click.calls.CallInvite // pragma: allowlist secret
import compose.project.click.click.calls.CallOverlayState // pragma: allowlist secret
import compose.project.click.click.calls.CallOverlayTransitionPolicy // pragma: allowlist secret
import compose.project.click.click.calls.CallPreviewOverlay // pragma: allowlist secret
import compose.project.click.click.calls.CallSessionManager // pragma: allowlist secret
import compose.project.click.click.calls.CallState // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
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
import org.jetbrains.compose.ui.tooling.preview.Preview

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
internal fun AppCallOverlays(
    globalCallOverlayState: CallOverlayState,
    globalCallState: CallState,
    activeCallUiState: CallState,
    activeInvite: CallInvite?,
    appDataUser: User?,
    reduceMotion: Boolean,
    lastActiveCallPresentedState: MutableState<CallState>,
    lastPreviewOverlayPresentedState: MutableState<CallOverlayState>,
    suppressEndedPreviewAfterActiveCallState: MutableState<Boolean>,
) {
    var suppressEndedPreviewAfterActiveCall by suppressEndedPreviewAfterActiveCallState
    val overlayState = globalCallOverlayState
    LaunchedEffect(overlayState, globalCallState) {
        if (
            overlayState is CallOverlayState.Ended &&
            (
                lastActiveCallPresentedState.value is CallState.Connected ||
                    lastActiveCallPresentedState.value is CallState.Ended
            )
        ) {
            suppressEndedPreviewAfterActiveCall = true
        } else if (overlayState is CallOverlayState.Idle && globalCallState is CallState.Idle) {
            suppressEndedPreviewAfterActiveCall = false
        }
    }
    val suppressEndedForPresentation =
        suppressEndedPreviewAfterActiveCall ||
            (
                overlayState is CallOverlayState.Ended &&
                    (
                        lastActiveCallPresentedState.value is CallState.Connected ||
                            lastActiveCallPresentedState.value is CallState.Ended
                    )
            )
    val callPresentation =
        CallOverlayTransitionPolicy.presentationFor(
            overlayState = overlayState,
            callState = globalCallState,
            suppressEndedPreviewAfterActiveCall = suppressEndedForPresentation,
        )
    val activeCallVisible =
        callPresentation == CallOverlayTransitionPolicy.Presentation.Active
    val callPreviewVisible =
        callPresentation == CallOverlayTransitionPolicy.Presentation.Preview
    val previewOverlayUiState =
        if (overlayState !is CallOverlayState.Idle) {
            overlayState
        } else {
            lastPreviewOverlayPresentedState.value
        }
    val callPreviewAlpha by animateFloatAsState(
        targetValue = if (callPreviewVisible) 1f else 0f,
        animationSpec =
            if (reduceMotion) {
                tween(100)
            } else if (callPreviewVisible) {
                MotionTokens.softEnterSpec()
            } else {
                MotionTokens.softExitSpec()
            },
        label = "callPreviewOverlayAlpha",
    )
    val activeCallAlpha by animateFloatAsState(
        targetValue = if (activeCallVisible) 1f else 0f,
        animationSpec =
            if (reduceMotion) {
                tween(100)
            } else if (activeCallVisible) {
                MotionTokens.softEnterSpec()
            } else {
                MotionTokens.softExitSpec()
            },
        label = "activeCallOverlayAlpha",
    )
    LaunchedEffect(
        overlayState,
        activeCallVisible,
        activeCallAlpha,
        suppressEndedPreviewAfterActiveCall,
    ) {
        if (
            suppressEndedPreviewAfterActiveCall &&
            overlayState is CallOverlayState.Ended &&
            !activeCallVisible &&
            activeCallAlpha <= 0.01f
        ) {
            suppressEndedPreviewAfterActiveCall = false
            CallSessionManager.dismissEndedCall()
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(11_000f),
    ) {
        if (
            (callPreviewVisible || callPreviewAlpha > 0.01f) &&
            previewOverlayUiState !is CallOverlayState.Idle
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = callPreviewAlpha
                        },
            ) {
                CallPreviewOverlay(
                    overlayState = previewOverlayUiState,
                    currentUserId = appDataUser?.id,
                    onAccept = { CallSessionManager.acceptIncomingCall() },
                    onDecline = { CallSessionManager.declineIncomingCall() },
                    onCancel = { CallSessionManager.cancelCurrentCall() },
                    onDismissEnded = { CallSessionManager.dismissEndedCall() },
                )
            }
        }

        if (activeCallVisible || activeCallAlpha > 0.01f) {
            // Do not put LiveKit TextureViewRenderer under graphicsLayer alpha —
            // Android TextureView composites black through alpha layers while audio still works.
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                ActiveCallOverlay(
                    callManager = CallSessionManager.callManager,
                    otherUserName = activeInvite?.counterpartName(appDataUser?.id) ?: "Connection",
                    state = activeCallUiState,
                    onEndCall = { CallSessionManager.endActiveCall() },
                    chromeAlpha = activeCallAlpha,
                    connectedAtMs = CallSessionManager.connectedAtMs,
                )
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
