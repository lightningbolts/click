@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformNativeNavigationBarSwipeReveal // pragma: allowlist secret
import compose.project.click.click.ui.screens.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.utils.resolveHubGatekeeperLocation // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthState // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthViewModel // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

@Composable
internal fun AppHubChatHost(
    isIOS: Boolean,
    reduceMotion: Boolean,
    authViewModel: AuthViewModel,
    hubChatTransitionMode: NavigationTransitionMode,
    hubVerifyInProgress: Boolean,
    pendingHubTargetMessageId: String?,
    closeHubChat: (NavigationTransitionMode) -> Unit,
    resolveHubGatekeeperLocationForChat: suspend () -> LocationResult?,
    hubChatArgsState: MutableState<HubChatNavArgs?>,
    lastHubChatArgsState: MutableState<HubChatNavArgs?>,
) {
    val hubChatArgs by hubChatArgsState
    var lastHubChatArgs by lastHubChatArgsState
    var hubChatRightToLeftPeek by remember {
        mutableStateOf<InteractiveSwipeBackRightToLeftPeek?>(null)
    }
    val hubSwipeDragPx = remember { mutableFloatStateOf(0f) }
    PlatformNativeNavigationBarSwipeReveal(hubSwipeDragPx)
    LaunchedEffect(hubChatArgs) {
        if (hubChatArgs != null) {
            lastHubChatArgs = hubChatArgs
        } else {
            hubChatRightToLeftPeek = null
            hubSwipeDragPx.floatValue = 0f
        }
    }

    // ── Hub Chat Overlay (mirrors ConnectionsScreen iOS chat overlay) ──
    val hubSlideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
    val hubFadeSpec = tween<Float>(220, easing = LinearOutSlowInEasing)
    androidx.compose.animation.AnimatedVisibility(
        visible = hubChatArgs != null,
        modifier = Modifier.fillMaxSize(),
        enter =
            if (reduceMotion) {
                fadeIn(animationSpec = tween(120))
            } else {
                slideInHorizontally(animationSpec = hubSlideSpec, initialOffsetX = { it }) +
                    fadeIn(animationSpec = hubFadeSpec)
            },
        exit =
            if (hubChatTransitionMode == NavigationTransitionMode.GestureBack) {
                ExitTransition.None
            } else if (reduceMotion) {
                fadeOut(animationSpec = tween(90))
            } else {
                slideOutHorizontally(animationSpec = hubSlideSpec, targetOffsetX = { it }) +
                    fadeOut(animationSpec = hubFadeSpec)
            },
        label = "hub_chat_overlay",
    ) {
        val activeHubArgs = lastHubChatArgs
        val hubUserId =
            when (val state = authViewModel.authState) {
                is AuthState.Success -> state.userId
                else -> ""
            }
        if (activeHubArgs != null && hubUserId.isNotEmpty()) {
            val hubKeyboardController = LocalSoftwareKeyboardController.current
            val hubFocusManager = LocalFocusManager.current
            InteractiveSwipeBackContainer(
                enabled = true,
                opaquePreviousBackground = false,
                externalDragOffsetPx = hubSwipeDragPx,
                onBehindLayersVisibleChanged = {},
                onBack = {
                    hubFocusManager.clearFocus()
                    if (!isIOS) {
                        hubKeyboardController?.hide()
                    }
                    closeHubChat(NavigationTransitionMode.GestureBack)
                },
                rightToLeftPeek = hubChatRightToLeftPeek,
                previousContent = {},
                currentContent = {
                    HubChatScreen(
                        args = activeHubArgs,
                        currentUserId = hubUserId,
                        targetMessageId = pendingHubTargetMessageId,
                        onNavigateBack = {
                            closeHubChat(NavigationTransitionMode.Tap)
                        },
                        resolveHubGatekeeperLocation = { resolveHubGatekeeperLocationForChat() },
                        integrateTimestampPeekWithSwipeBackContainer = true,
                        onRegisterSwipeBackRightToLeftPeek = {
                            hubChatRightToLeftPeek = it
                        },
                        parentInteractiveBackSwipePx = hubSwipeDragPx,
                    )
                },
            )
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = hubVerifyInProgress,
        enter =
            androidx.compose.animation.fadeIn(
                animationSpec = spring(stiffness = Spring.StiffnessLow),
            ),
        exit =
            androidx.compose.animation.fadeOut(
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            ),
    ) {
        val hubLoadTransition = rememberInfiniteTransition(label = "hub_verify_pulse")
        val hubPulseAlpha by hubLoadTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "hub_verify_alpha",
        )
        val hubPulseMix by hubLoadTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "hub_verify_mix",
        )
        val hubAccentColor =
            androidx.compose.ui.graphics
                .lerp(PrimaryBlue, LightBlue, hubPulseMix)
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            ChatAmbientMeshBackground(
                connection = null,
                isHubNeutral = true,
                animateMesh = true,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .graphicsLayer { alpha = hubPulseAlpha },
                        contentAlignment = Alignment.Center,
                    ) {
                        AdaptiveCircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 2.5.dp,
                            color = hubAccentColor,
                        )
                    }
                    Text(
                        text = "Joining hub…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
