@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.models.ONBOARDING_FLOW_VERSION_COMPLETE // pragma: allowlist secret
import compose.project.click.click.data.models.OnboardingState // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.ui.components.AppShimmerScreen // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformBackHandler // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberUnifiedToastState // pragma: allowlist secret
import compose.project.click.click.ui.screens.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthState // pragma: allowlist secret
import compose.project.click.click.viewmodel.AuthViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.OnboardingViewModel // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun AppAuthGate(
    authViewModel: AuthViewModel,
    authSurfaceVisible: Boolean,
    showSignUpState: MutableState<Boolean>,
) {
    var showSignUp by showSignUpState
    val authSurfaceAlpha by animateFloatAsState(
        targetValue = if (authSurfaceVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing),
        label = "auth_surface_alpha",
    )
    val authSurfaceScale by animateFloatAsState(
        targetValue = if (authSurfaceVisible) 1f else 1.01f,
        animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing),
        label = "auth_surface_scale",
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = authSurfaceAlpha
                    scaleX = authSurfaceScale
                    scaleY = authSurfaceScale
                },
    ) {
        AnimatedContent(
            targetState = showSignUp,
            transitionSpec = {
                val towardsSignUp = targetState
                val slideSpec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                val fadeSpec = tween<Float>(180, easing = LinearOutSlowInEasing)
                val enterX = if (towardsSignUp) { width: Int -> width } else { width: Int -> -width }
                val exitX = if (towardsSignUp) { width: Int -> -width } else { width: Int -> width }
                (
                    slideInHorizontally(animationSpec = slideSpec, initialOffsetX = enterX) +
                        fadeIn(animationSpec = fadeSpec)
                ).togetherWith(
                    slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = exitX) +
                        fadeOut(animationSpec = fadeSpec),
                ).using(SizeTransform(clip = true))
            },
            label = "auth_login_signup",
        ) { signUp ->
            if (signUp) {
                SignUpScreen(
                    onSignUpSuccess = {
                        // Success is handled by state change in viewModel
                    },
                    onLoginClick = {
                        showSignUp = false
                        authViewModel.resetAuthState()
                    },
                    onEmailSignUp = { firstName, lastName, birthdayIso, email, password, avatarBytes, avatarMime ->
                        authViewModel.signUpWithEmail(
                            firstName,
                            lastName,
                            birthdayIso,
                            email,
                            password,
                            avatarBytes,
                            avatarMime,
                        )
                    },
                    isLoading = authViewModel.authState is AuthState.Loading,
                    errorMessage =
                        if (authViewModel.authState is AuthState.Error) {
                            (authViewModel.authState as AuthState.Error).message
                        } else {
                            null
                        },
                )
            } else {
                LoginScreen(
                    onLoginSuccess = {
                        // Success is handled by state change in viewModel
                    },
                    onSignUpClick = {
                        showSignUp = true
                        authViewModel.resetAuthState()
                    },
                    onEmailSignIn = { email, password ->
                        authViewModel.signInWithEmail(email, password)
                    },
                    onGoogleSignIn = { authViewModel.signInWithGoogle() },
                    onAppleSignIn = { authViewModel.signInWithApple() },
                    isLoading = authViewModel.authState is AuthState.Loading,
                    errorMessage =
                        if (authViewModel.authState is AuthState.Error) {
                            (authViewModel.authState as AuthState.Error).message
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
internal fun AppOnboardingFlowHost(
    onboardingVm: OnboardingViewModel,
    onboardingScope: CoroutineScope,
    onboardingStep: String,
    appDataUser: User?,
    currentUser: User,
    avatarAuthRepo: AuthRepository,
    supabaseRepo: SupabaseRepository,
    tokenStorage: TokenStorage,
    interestsRemoteResolved: Boolean,
    isDarkMode: Boolean,
    onboardingStateState: MutableState<OnboardingState?>,
    persistOnboardingState: suspend (OnboardingState) -> Unit,
) {
    val onboardingState by onboardingStateState
    val onboardingToastState = rememberUnifiedToastState()
    LaunchedEffect(Unit) {
        AppDataManager.transientUserMessages.collect {
            onboardingToastState.show(onboardingScope, it)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        PlatformBackHandler(enabled = onboardingVm.canGoBack()) {
            onboardingVm.goBack()
        }
        AnimatedContent(
            targetState = onboardingStep,
            transitionSpec = {
                val slideSpec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                val fadeSpec = tween<Float>(180, easing = LinearOutSlowInEasing)
                (
                    slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) +
                        fadeIn(animationSpec = fadeSpec)
                ).togetherWith(
                    slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { -it }) +
                        fadeOut(animationSpec = fadeSpec),
                ).using(SizeTransform(clip = true))
            },
            label = "onboarding_transition",
        ) { step ->
            when (step) {
                "welcome" -> {
                    WelcomeScreen(
                        firstName = appDataUser?.firstName,
                        onContinue = { onboardingVm.onWelcomeAcknowledged() },
                    )
                }

                "avatar" -> {
                    AvatarScreen(
                        existingAvatarUrl = appDataUser?.image,
                        onUploadBytes = { bytes, mimeType ->
                            avatarAuthRepo.uploadProfilePicture(bytes, mimeType)
                        },
                        onUploaded = { _ ->
                            onboardingScope.launch {
                                AppDataManager.refresh(force = true)
                                onboardingVm.onAvatarSetOrSkipped()
                            }
                        },
                        onSkip = { onboardingVm.onAvatarSetOrSkipped() },
                    )
                }

                "prior_connections" -> {
                    PriorConnectionsScreen(
                        onSkip = { onboardingVm.onPriorConnectionsSetOrSkipped() },
                    )
                }

                "personality" -> {
                    PersonalityTaggingScreen(
                        initialTags = appDataUser?.personalityTags.orEmpty(),
                        onTagsSelected = { tags ->
                            onboardingScope.launch {
                                val saveResult =
                                    ApiClient().patchUserProfile(
                                        currentUser.id,
                                        personalityTags = tags,
                                    )
                                if (saveResult.isSuccess) {
                                    AppDataManager.applyPersonalityTags(tags)
                                    onboardingVm.onPersonalitySaved()
                                    val base = onboardingState ?: OnboardingState()
                                    persistOnboardingState(
                                        base.copy(
                                            welcomeSeen = true,
                                            interestsCompleted = true,
                                            personalityCompleted = true,
                                        ),
                                    )
                                } else {
                                    val msg =
                                        saveResult
                                            .exceptionOrNull()
                                            ?.message
                                            ?.trim()
                                            .orEmpty()
                                            .ifBlank {
                                                "Couldn't save personality. Check your connection and try again."
                                            }
                                    AppDataManager.postTransientUserMessage(msg)
                                }
                            }
                        },
                    )
                }

                else -> {
                    if (!interestsRemoteResolved) {
                        AppShimmerScreen(isDarkMode = isDarkMode)
                    } else {
                        InterestTaggingScreen(
                            onTagsSelected = { tags ->
                                onboardingScope.launch {
                                    val saveResult = supabaseRepo.updateUserInterests(currentUser.id, tags)
                                    if (saveResult.isSuccess) {
                                        tokenStorage.saveTagsInitialized(true)
                                        // Advance the in-memory step immediately, then persist
                                        // flowVersion so cold start recognizes Phase 2 completion.
                                        onboardingVm.onInterestsSaved()
                                        val base = onboardingState ?: OnboardingState()
                                        persistOnboardingState(
                                            base.copy(
                                                interestsCompleted = true,
                                                flowVersion = ONBOARDING_FLOW_VERSION_COMPLETE,
                                            ),
                                        )
                                        AppDataManager.refresh(force = true)
                                    } else {
                                        val msg =
                                            saveResult
                                                .exceptionOrNull()
                                                ?.message
                                                ?.trim()
                                                .orEmpty()
                                                .ifBlank {
                                                    "Couldn't save interests. Check your connection and try again."
                                                }
                                        AppDataManager.postTransientUserMessage(msg)
                                    }
                                }
                            },
                            canSkip = false,
                        )
                    }
                }
            }
        }
        OnboardingShellChrome(
            stepIndex = onboardingVm.visibleStepIndex(),
            stepCount = onboardingVm.visibleStepCount(),
            canGoBack = onboardingVm.canGoBack(),
            onBack = { onboardingVm.goBack() },
        )
        UnifiedToastHost(
            state = onboardingToastState,
            opaque = true,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
        )
    }
}
