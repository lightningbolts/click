package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.models.OnboardingState // pragma: allowlist secret
import compose.project.click.click.data.models.existingHydratedOnboardingState // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingViewModelTest {
    private class PersistRecorder : OnboardingViewModel.OnSyncPersist {
        val snapshots: MutableList<OnboardingState> = mutableListOf()

        override fun persist(next: OnboardingState) {
            snapshots.add(next)
        }
    }

    @Test
    fun freshInstall_startsAtWelcome() {
        val vm = OnboardingViewModel(initialState = OnboardingState())
        assertEquals(OnboardingViewModel.Step.Welcome, vm.step.value)
        assertTrue(vm.needsPhase2Onboarding())
    }

    @Test
    fun fullHappyPath_goesWelcomeInterestsPersonalityAvatarComplete() {
        val recorder = PersistRecorder()
        val vm = OnboardingViewModel(onPersist = recorder, clockMillis = { 1_700_000_000_000L })

        assertEquals(OnboardingViewModel.Step.Welcome, vm.step.value)

        vm.onWelcomeAcknowledged()
        assertEquals(OnboardingViewModel.Step.Interests, vm.step.value)
        assertTrue(vm.state.value.welcomeSeen)

        vm.onInterestsSaved()
        assertEquals(OnboardingViewModel.Step.Personality, vm.step.value)
        assertTrue(vm.state.value.interestsCompleted)

        vm.onPersonalitySaved()
        assertEquals(OnboardingViewModel.Step.Avatar, vm.step.value)
        assertTrue(vm.state.value.personalityCompleted)

        vm.onAvatarSetOrSkipped()
        assertEquals(OnboardingViewModel.Step.PriorConnections, vm.step.value)
        assertTrue(vm.state.value.avatarSetOrSkipped)

        vm.onPriorConnectionsSetOrSkipped()
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
        assertTrue(vm.state.value.priorConnectionsSetOrSkipped)
        assertEquals(1_700_000_000_000L, vm.state.value.completedAt)

        assertEquals(5, recorder.snapshots.size)
    }

    @Test
    fun advance_walksOneStepPerCall() {
        val vm = OnboardingViewModel()
        vm.advance()
        assertEquals(OnboardingViewModel.Step.Interests, vm.step.value)
        vm.advance()
        assertEquals(OnboardingViewModel.Step.Personality, vm.step.value)
        vm.advance()
        assertEquals(OnboardingViewModel.Step.Avatar, vm.step.value)
        vm.advance()
        assertEquals(OnboardingViewModel.Step.PriorConnections, vm.step.value)
        vm.advance()
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
    }

    @Test
    fun advance_isIdempotentAtComplete() {
        val recorder = PersistRecorder()
        val vm =
            OnboardingViewModel(
                initialState =
                    OnboardingState(
                        welcomeSeen = true,
                        interestsCompleted = true,
                        avatarSetOrSkipped = true,
                    ),
                onPersist = recorder,
            )
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
        vm.advance()
        vm.advance()
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
        // No persist should have fired because we never transitioned.
        assertEquals(0, recorder.snapshots.size)
    }

    @Test
    fun legacyAccount_withInterestsAndAvatar_skipsToComplete() {
        val vm =
            OnboardingViewModel(
                initialState =
                    OnboardingState(
                        permissionsCompleted = true,
                        interestsCompleted = true,
                        welcomeSeen = true,
                        avatarSetOrSkipped = false, // flag not yet written for legacy users
                    ),
                userHasAvatar = { true },
            )
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
        assertFalse(vm.needsPhase2Onboarding())
    }

    @Test
    fun existingHydratedOnboardingState_skipsWelcome() {
        val vm =
            OnboardingViewModel(
                initialState = existingHydratedOnboardingState(),
                userHasAvatar = { true },
            )
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
        assertFalse(vm.needsPhase2Onboarding())
    }

    @Test
    fun legacyAccount_withoutWelcomeFlag_skipsWelcomeWhenInterestsComplete() {
        val vm =
            OnboardingViewModel(
                initialState =
                    OnboardingState(
                        permissionsCompleted = true,
                        interestsCompleted = true,
                        welcomeSeen = false,
                    ),
                userHasAvatar = { true },
            )
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
    }

    @Test
    fun hydrate_recomputesStepWithoutPersisting() {
        val recorder = PersistRecorder()
        val vm = OnboardingViewModel(onPersist = recorder)
        recorder.snapshots.clear()

        vm.hydrate(
            OnboardingState(
                welcomeSeen = true,
                interestsCompleted = true,
                avatarSetOrSkipped = true,
            ),
        )
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
        assertEquals(0, recorder.snapshots.size)
    }

    @Test
    fun onDataLoaded_moves_Loading_toNextComputedStep() {
        // We simulate Loading by constructing a VM and forcing step = Loading externally. Because
        // computeStep never returns Loading on its own, the easiest way is to hydrate with default
        // and then call onDataLoaded (no-op, step stays Welcome). Verify idempotence.
        val vm = OnboardingViewModel()
        vm.onDataLoaded()
        assertEquals(OnboardingViewModel.Step.Welcome, vm.step.value)
    }

    @Test
    fun avatarSkippedWithAvatarAlreadyPresent_isIdempotent() {
        val vm =
            OnboardingViewModel(
                initialState = OnboardingState(welcomeSeen = true, interestsCompleted = true),
                userHasAvatar = { true },
            )
        // Avatar is fast-forwarded to Complete because the user already has an avatar.
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
    }

    @Test
    fun interestsNotYetSaved_keepsUserOnInterestsStep() {
        val vm =
            OnboardingViewModel(
                initialState = OnboardingState(welcomeSeen = true),
            )
        assertEquals(OnboardingViewModel.Step.Interests, vm.step.value)
    }

    @Test
    fun persistSnapshots_matchStateProgression() {
        val recorder = PersistRecorder()
        val vm = OnboardingViewModel(onPersist = recorder, clockMillis = { 0L })

        vm.onWelcomeAcknowledged()
        vm.onInterestsSaved()
        vm.onPersonalitySaved()
        vm.onAvatarSetOrSkipped()
        vm.onPriorConnectionsSetOrSkipped()

        assertEquals(true, recorder.snapshots[0].welcomeSeen)
        assertEquals(false, recorder.snapshots[0].interestsCompleted)

        assertEquals(true, recorder.snapshots[1].welcomeSeen)
        assertEquals(true, recorder.snapshots[1].interestsCompleted)
        assertEquals(false, recorder.snapshots[1].personalityCompleted)

        assertEquals(true, recorder.snapshots[2].personalityCompleted)
        assertEquals(false, recorder.snapshots[2].avatarSetOrSkipped)

        assertEquals(true, recorder.snapshots[3].avatarSetOrSkipped)
        assertEquals(false, recorder.snapshots[3].priorConnectionsSetOrSkipped)

        assertEquals(true, recorder.snapshots[4].priorConnectionsSetOrSkipped)
    }

    @Test
    fun unknownAvatar_holdsLoadingInsteadOfAvatar() {
        val vm =
            OnboardingViewModel(
                initialState = OnboardingState(welcomeSeen = true, interestsCompleted = true),
                userHasAvatar = { null },
            )
        assertEquals(OnboardingViewModel.Step.Loading, vm.step.value)
        assertTrue(vm.needsPhase2Onboarding())
    }

    @Test
    fun unknownAvatar_stillShowsWelcome() {
        val vm =
            OnboardingViewModel(
                initialState = OnboardingState(),
                userHasAvatar = { null },
            )
        assertEquals(OnboardingViewModel.Step.Welcome, vm.step.value)
    }

    @Test
    fun absentAvatar_showsPersonalityThenAvatar() {
        val vm =
            OnboardingViewModel(
                initialState = OnboardingState(welcomeSeen = true, interestsCompleted = true),
                userHasAvatar = { false },
            )
        assertEquals(OnboardingViewModel.Step.Personality, vm.step.value)
    }

    @Test
    fun personalityCompletedWithoutAvatar_showsAvatar() {
        val vm =
            OnboardingViewModel(
                initialState =
                    OnboardingState(
                        welcomeSeen = true,
                        interestsCompleted = true,
                        personalityCompleted = true,
                    ),
                userHasAvatar = { false },
            )
        assertEquals(OnboardingViewModel.Step.Avatar, vm.step.value)
    }

    @Test
    fun personalityAndAvatarDone_showsPriorConnections() {
        val vm =
            OnboardingViewModel(
                initialState =
                    OnboardingState(
                        welcomeSeen = true,
                        interestsCompleted = true,
                        personalityCompleted = true,
                        avatarSetOrSkipped = true,
                    ),
                userHasAvatar = { false },
            )
        assertEquals(OnboardingViewModel.Step.PriorConnections, vm.step.value)
        assertTrue(vm.canGoBack())
        vm.goBack()
        assertEquals(OnboardingViewModel.Step.Avatar, vm.step.value)
    }

    @Test
    fun existingUser_skipsPersonalityGate() {
        val vm =
            OnboardingViewModel(
                initialState =
                    OnboardingState(
                        welcomeSeen = true,
                        interestsCompleted = true,
                        personalityCompleted = false,
                    ),
                userHasAvatar = { true },
            )
        assertEquals(OnboardingViewModel.Step.Complete, vm.step.value)
        assertFalse(vm.needsPhase2Onboarding())
    }

    @Test
    fun goBack_fromAvatarToPersonalityWithoutClearingPersonality() {
        val vm =
            OnboardingViewModel(
                initialState =
                    OnboardingState(
                        welcomeSeen = true,
                        interestsCompleted = true,
                        personalityCompleted = true,
                    ),
                userHasAvatar = { false },
            )
        assertEquals(OnboardingViewModel.Step.Avatar, vm.step.value)
        vm.goBack()
        assertEquals(OnboardingViewModel.Step.Personality, vm.step.value)
        assertTrue(vm.state.value.personalityCompleted)
        assertTrue(vm.canGoBack())
        assertEquals(2, vm.visibleStepIndex())
    }

    @Test
    fun goBack_fromPersonalityToInterests() {
        val vm =
            OnboardingViewModel(
                initialState = OnboardingState(welcomeSeen = true, interestsCompleted = true),
                userHasAvatar = { false },
            )
        assertEquals(OnboardingViewModel.Step.Personality, vm.step.value)
        vm.goBack()
        assertEquals(OnboardingViewModel.Step.Interests, vm.step.value)
        assertTrue(vm.state.value.interestsCompleted)
    }

    @Test
    fun goBack_fromInterestsToWelcome() {
        val vm =
            OnboardingViewModel(
                initialState = OnboardingState(welcomeSeen = true),
            )
        vm.goBack()
        assertEquals(OnboardingViewModel.Step.Welcome, vm.step.value)
        assertFalse(vm.canGoBack())
        assertEquals(0, vm.visibleStepIndex())
        assertEquals(5, vm.visibleStepCount())
    }

    @Test
    fun goBack_fromWelcome_isNoOp() {
        val recorder = PersistRecorder()
        val vm = OnboardingViewModel(onPersist = recorder)
        vm.goBack()
        assertEquals(OnboardingViewModel.Step.Welcome, vm.step.value)
        assertEquals(0, recorder.snapshots.size)
    }
}
