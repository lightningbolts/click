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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.ui.screens.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthState // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthViewModel // pragma: allowlist secret

@Composable
internal fun AppHubChatHost(
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
    val hubBackHost = HubChatInteractiveBackBridge.state
    val hubSwipeDragPx = hubBackHost.dragOffsetPx

    LaunchedEffect(hubChatArgs) {
        if (hubChatArgs != null) {
            lastHubChatArgs = hubChatArgs
        } else {
            hubChatRightToLeftPeek = null
            hubBackHost.reset()
        }
    }
    DisposableEffect(Unit) {
        onDispose { hubBackHost.reset() }
    }

    // Connections/search Hubs are a true pushed route. Keep the primary tab tree mounted below and
    // mirror this foreground drag onto it through HubChatInteractiveBackBridge, exactly like normal
    // Connections chat. Event/Nearby Hubs use their independent modal presentation instead.
    val hubSlideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
    val hubFadeSpec = tween<Float>(220, easing = LinearOutSlowInEasing)
    AnimatedVisibility(
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
            val hubOverlayViewModelOwner =
                remember(activeHubArgs.realtimeChannel, hubUserId) {
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }
                }
            DisposableEffect(hubOverlayViewModelOwner) {
                onDispose {
                    hubOverlayViewModelOwner.viewModelStore.clear()
                }
            }
            val hubKeyboardController = LocalSoftwareKeyboardController.current
            val hubFocusManager = LocalFocusManager.current
            InteractiveSwipeBackContainer(
                enabled = true,
                opaquePreviousBackground = false,
                externalDragOffsetPx = hubSwipeDragPx,
                onBehindLayersVisibleChanged = { revealing ->
                    hubBackHost.behindLayersVisible = revealing
                },
                onBack = {
                    hubFocusManager.clearFocus()
                    hubKeyboardController?.hide()
                    closeHubChat(NavigationTransitionMode.GestureBack)
                },
                rightToLeftPeek = hubChatRightToLeftPeek,
                previousContent = {},
                currentContent = {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides hubOverlayViewModelOwner) {
                        ConnectionsHubChatScreen(
                            args = activeHubArgs,
                            currentUserId = hubUserId,
                            targetMessageId = pendingHubTargetMessageId,
                            onNavigateBack = {
                                closeHubChat(NavigationTransitionMode.Tap)
                            },
                            resolveHubGatekeeperLocation = { resolveHubGatekeeperLocationForChat() },
                            onRegisterSwipeBackRightToLeftPeek = {
                                hubChatRightToLeftPeek = it
                            },
                            parentInteractiveBackSwipePx = hubSwipeDragPx,
                        )
                    }
                },
            )
        }
    }

    AnimatedVisibility(
        visible = hubVerifyInProgress,
        enter = if (reduceMotion) MotionTokens.reduceMotionEnter() else MotionTokens.contentFadeIn(),
        exit = if (reduceMotion) MotionTokens.reduceMotionExit() else MotionTokens.contentFadeOut(),
    ) {
        val (hubPulseAlpha, hubPulseMix) =
            if (reduceMotion) {
                1f to 0.5f
            } else {
                val hubLoadTransition = rememberInfiniteTransition(label = "hub_verify_pulse")
                val alpha by hubLoadTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = MotionTokens.Pulse.Gentle, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "hub_verify_alpha",
                )
                val mix by hubLoadTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = MotionTokens.Pulse.Gentle, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "hub_verify_mix",
                )
                alpha to mix
            }
        val hubAccentColor =
            androidx.compose.ui.graphics
                .lerp(PrimaryBlue, LightBlue, hubPulseMix)
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            ChatAmbientMeshBackground(
                connection = null,
                isHubNeutral = true,
                animateMesh = !reduceMotion,
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
