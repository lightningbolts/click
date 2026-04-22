package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.OnboardingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure state machine for the Phase 2 (B2) onboarding flow.
 *
 * Target order:   **Loading → Welcome → Interests → Avatar → Complete**
 *
 * Design decisions:
 *   * Permissions are **no longer** part of the onboarding graph — they are requested contextually
 *     from the feature that needs them (map, proximity, chat composer, …). See B2 plan.
 *   * The `PermissionsOnboardingScreen` is not deleted; it is relocated to a Settings
 *     "Permissions Hub" in a later commit (C9) so users who denied a permission can review it.
 *   * The state holder is deliberately **not** a `ViewModel` subclass and has **no Compose or
 *     coroutine imports** beyond `StateFlow`. This keeps it trivially testable in `commonTest`
 *     with no dependency on `viewModelScope`, `Dispatchers`, or Android lifecycle.
 *   * Persistence is driven by the **caller** (via [OnSyncPersist]) so the VM stays deterministic
 *     and atomic — a test can assert "exactly one persist call happened for the Welcome step".
 *
 * Regression / migration (see B2 in the refactor plan):
 *   If [OnboardingState.interestsCompleted] is already true *and* the caller reports that the user
 *   has an avatar URL, we fast-forward past Avatar so existing accounts do not re-onboard.
 *   Welcome is shown for accounts with `welcomeSeen = false` regardless of legacy completion —
 *   it is a short copy screen and gives us a place to announce B2.
 */
class OnboardingViewModel(
    initialState: OnboardingState = OnboardingState(),
    private val userHasAvatar: () -> Boolean = { false },
    private val onPersist: OnSyncPersist = OnSyncPersist { /* no-op in tests */ },
    private val clockMillis: () -> Long = { 0L },
) {
    /** Collaborator that persists a new [OnboardingState] (typically backed by `TokenStorage`). */
    fun interface OnSyncPersist {
        fun persist(next: OnboardingState)
    }

    /**
     * Discrete onboarding steps. Ordered: [Loading] → [Welcome] → [Interests] → [Avatar] → [Complete].
     * [Loading] is shown while initial data (user + persisted state + remote interest resolution)
     * is being fetched.
     */
    enum class Step { Loading, Welcome, Interests, Avatar, Complete }

    private val _state: MutableStateFlow<OnboardingState> = MutableStateFlow(initialState)

    /** Persisted onboarding state. Observers render from [step]; this is exposed for debugging. */
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _step: MutableStateFlow<Step> = MutableStateFlow(computeStep(initialState))

    /** Current step the UI should render. */
    val step: StateFlow<Step> = _step.asStateFlow()

    /**
     * Install a new [OnboardingState] (e.g. after hydrating from disk or after a remote interest
     * lookup). Recomputes [step] but does **not** persist — assumes the caller just loaded it.
     */
    fun hydrate(next: OnboardingState) {
        _state.value = next
        _step.value = computeStep(next)
    }

    /** Transition Loading → Welcome once we have enough data to render it. */
    fun onDataLoaded() {
        if (_step.value == Step.Loading) {
            _step.value = computeStep(_state.value)
        }
    }

    /** User acknowledged the Welcome screen. */
    fun onWelcomeAcknowledged() = updateAnd {
        copy(welcomeSeen = true)
    }

    /** User selected ≥ 5 interests and saved them remotely. */
    fun onInterestsSaved() = updateAnd {
        copy(interestsCompleted = true)
    }

    /** User either picked an avatar or tapped "skip for now". */
    fun onAvatarSetOrSkipped() = updateAnd {
        val base = copy(avatarSetOrSkipped = true)
        if (base.interestsCompleted && base.welcomeSeen) {
            base.copy(completedAt = clockMillis().takeIf { it > 0L } ?: base.completedAt)
        } else {
            base
        }
    }

    /**
     * Force an advance without carrying a side effect (useful for error-recovery paths). Walks one
     * step forward from the current computed step, no-op at [Step.Complete].
     */
    fun advance() {
        when (_step.value) {
            Step.Welcome -> onWelcomeAcknowledged()
            Step.Interests -> onInterestsSaved()
            Step.Avatar -> onAvatarSetOrSkipped()
            Step.Loading, Step.Complete -> Unit
        }
    }

    private inline fun updateAnd(crossinline mutator: OnboardingState.() -> OnboardingState) {
        _state.update { it.mutator() }
        _step.value = computeStep(_state.value)
        onPersist.persist(_state.value)
    }

    /**
     * Pure function: derive the current [Step] from a stored [OnboardingState] plus the "user has
     * avatar" collaborator. Exposed `internal` so tests can exercise the computation directly.
     */
    internal fun computeStep(s: OnboardingState): Step {
        // Legacy fast-forward: an account that completed the Phase 1 flow and has an avatar is
        // already done — no re-onboarding.
        val avatarPresent = userHasAvatar()
        val legacyComplete = s.interestsCompleted && (s.avatarSetOrSkipped || avatarPresent)

        return when {
            !s.welcomeSeen -> Step.Welcome
            !s.interestsCompleted -> Step.Interests
            !s.avatarSetOrSkipped && !avatarPresent -> Step.Avatar
            legacyComplete -> Step.Complete
            else -> Step.Complete
        }
    }

    /**
     * Returns true iff the account needs to re-run onboarding under the Phase 2 rules.
     * Used by App.kt to decide whether to render the onboarding graph.
     */
    fun needsPhase2Onboarding(): Boolean = _step.value != Step.Complete
}
