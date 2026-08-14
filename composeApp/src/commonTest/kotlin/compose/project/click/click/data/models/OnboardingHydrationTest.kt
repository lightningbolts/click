package compose.project.click.click.data.models // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingHydrationTest {
    @Test
    fun resolveOnboardingInitialState_completedWithoutSavedState_seedsExistingHydrated() {
        val state =
            resolveOnboardingInitialState(
                savedState = null,
                hasCompletedOnboarding = true,
            )
        assertEquals(existingHydratedOnboardingState(), state)
    }

    @Test
    fun resolveOnboardingInitialState_noSavedStateNotCompleted_returnsNull() {
        assertNull(
            resolveOnboardingInitialState(
                savedState = null,
                hasCompletedOnboarding = false,
            ),
        )
    }

    @Test
    fun resolveOnboardingInitialState_savedStateWhenCompleted_forcesWelcomeSeen() {
        val saved = OnboardingState(interestsCompleted = true, welcomeSeen = false)
        val state =
            resolveOnboardingInitialState(
                savedState = saved,
                hasCompletedOnboarding = true,
            )
        assertTrue(state!!.welcomeSeen)
    }

    @Test
    fun resolveOnboardingAfterRemoteResolution_existingUserWithTags_skipsWelcome() {
        val state =
            resolveOnboardingAfterRemoteResolution(
                currentState = null,
                tagCount = 5,
                userFirstName = null,
                userBirthday = null,
                userAvatarUrl = null,
                minInterestTags = 5,
            )
        assertTrue(state.welcomeSeen)
        assertTrue(state.interestsCompleted)
    }

    @Test
    fun resolveOnboardingAfterRemoteResolution_brandNewUser_getsDefaultState() {
        val state =
            resolveOnboardingAfterRemoteResolution(
                currentState = null,
                tagCount = 0,
                userFirstName = null,
                userBirthday = null,
                userAvatarUrl = null,
                minInterestTags = 5,
            )
        assertEquals(OnboardingState(), state)
        assertFalse(state.welcomeSeen)
    }

    @Test
    fun resolveOnboardingAfterRemoteResolution_existingProfileWithoutTags_setsWelcomeSeen() {
        val state =
            resolveOnboardingAfterRemoteResolution(
                currentState = null,
                tagCount = 0,
                userFirstName = "Ada",
                userBirthday = "1990-01-01",
                userAvatarUrl = null,
                minInterestTags = 5,
            )
        assertTrue(state.welcomeSeen)
        assertFalse(state.interestsCompleted)
    }

    @Test
    fun isExistingAccountForOnboarding_avatarUrlCountsAsExisting() {
        assertTrue(
            isExistingAccountForOnboarding(
                tagCount = 0,
                userFirstName = null,
                userBirthday = null,
                userAvatarUrl = "https://example.com/a.jpg",
                minInterestTags = 5,
            ),
        )
    }
}
