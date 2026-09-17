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
import compose.project.click.click.ui.components.PlatformOverlayAbovePresentedSheets // pragma: allowlist secret
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
    var keepEventHubPortalMounted by remember { mutableStateOf(false) }
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
            !isIOS -> keepEventHubPortalMounted = false
            activeArgs?.isEventHub == true -> keepEventHubPortalMounted = true
            activeArgs != null -> keepEventHubPortalMounted = false
            !keepEventHubPortalMounted -> Unit
            hubChatTransitionMode == NavigationTransitionMode.GestureBack -> {
                // closeHubChat keeps the route alive through the gesture's final frame before
                // clearing hubChatArgs. At that point the foreground is already fully off-screen,
                // so the transparent portal can detach without touching the live sheet stack below.
                keepEventHubPortalMounted = false
            }
            else -> {
                // Tap-back still animates inside this portal. Keep the portal mounted until the
                // outgoing route has completed so it never jumps underneath the Event/Nearby sheet.
                delay(if (reduceMotion) 110L else 320L)
                if (hubChatArgsState.value == null) {
                    keepEventHubPortalMounted = false
                }
            }
        }
    }

    // Keep the actual Event/Nearby UISheetPresentationController stack mounted for the entire Hub
    // chat session. Dismissing/re-presenting that stack introduced a visible opening stall and made
    // interactive Back reveal an empty/dimmed presenter instead of the real destination. The
    // over-full-screen portal participates in UIKit safe areas/native chrome, while its transparent
    // host lets the live sheet stack show through as the foreground follows the user's finger.
    val shouldPortalEventHub =
        isIOS &&
            (hubChatArgs?.isEventHub == true || keepEventHubPortalMounted)
    PlatformOverlayAbovePresentedSheets(
        liftAbovePresentedSheets = shouldPortalEventHub,
        revealUnderlyingPresentation = shouldPortalEventHub,
    ) {
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
                    // Event Hub's real destination is the retained native Event/Nearby sheet below
                    // this portal, not a Compose previousContent. The normal Compose back scrim was
                    // therefore painting a black veil over the sheet stack during the gesture.
                    dimPreviousLayer = !(isIOS && activeHubArgs.isEventHub),
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