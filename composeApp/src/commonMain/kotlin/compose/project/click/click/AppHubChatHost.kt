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
import androidx.compose.runtime.mutableFloatStateOf
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
import compose.project.click.click.ui.components.PlatformNativeNavigationBarSwipeReveal // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformPresentedSheetSuspension // pragma: allowlist secret
import compose.project.click.click.ui.screens.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthState // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthViewModel // pragma: allowlist secret
import kotlinx.coroutines.delay

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
    var keepEventHubSheetsSuspended by remember { mutableStateOf(false) }
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

    LaunchedEffect(isIOS, hubChatArgs, hubChatTransitionMode, reduceMotion) {
        val activeArgs = hubChatArgs
        when {
            !isIOS -> keepEventHubSheetsSuspended = false
            activeArgs?.isEventHub == true -> keepEventHubSheetsSuspended = true
            activeArgs != null -> keepEventHubSheetsSuspended = false
            !keepEventHubSheetsSuspended -> Unit
            hubChatTransitionMode == NavigationTransitionMode.GestureBack -> {
                // InteractiveSwipeBackContainer invokes onBack at the committed end position, then
                // intentionally holds its settling state for 34 ms so the foreground can disappear
                // without snapping back. Restoring native sheets inside that guard races UIKit's
                // presentation layers and can leave the retained sheet stack black/dimmed.
                delay(50L)
                if (hubChatArgsState.value == null) {
                    keepEventHubSheetsSuspended = false
                }
            }
            else -> {
                // Tap-back exits inside the root host. Keep the Event/Nearby controllers suspended
                // until the outgoing route has finished so the restored sheet stack cannot cover its
                // final frames or steal native chrome ownership early.
                delay(if (reduceMotion) 110L else 320L)
                if (hubChatArgsState.value == null) {
                    keepEventHubSheetsSuspended = false
                }
            }
        }
    }

    // Event detail/Nearby are native UISheetPresentationController layers. Portaling Event Hub into
    // a second ComposeUIViewController gave it a different UIKit/safe-area/chrome host from ordinary
    // chat, which is why its header and tab bar could never have exact 1:1/group parity. Temporarily
    // suspend those sheet controllers instead, keep their Compose selection state intact, and render
    // Event Hub here in the root host with the same persistent IosNavChrome + UITabBar as chat.
    val shouldSuspendEventSheets =
        isIOS &&
            (hubChatArgs?.isEventHub == true || keepEventHubSheetsSuspended)
    val eventSheetStackReady = PlatformPresentedSheetSuspension(shouldSuspendEventSheets)

    val hubSlideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
    val hubFadeSpec = tween<Float>(220, easing = LinearOutSlowInEasing)
    AnimatedVisibility(
        visible =
            hubChatArgs != null &&
                (hubChatArgs?.isEventHub != true || eventSheetStackReady),
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
                    CompositionLocalProvider(LocalViewModelStoreOwner provides hubOverlayViewModelOwner) {
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
