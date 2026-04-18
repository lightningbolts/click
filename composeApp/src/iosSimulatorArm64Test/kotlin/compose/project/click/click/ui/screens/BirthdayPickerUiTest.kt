package compose.project.click.click.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.uikit.LocalUIViewController
import kotlin.test.Test
import platform.UIKit.UIViewController

/**
 * iOS simulator regression coverage for onboarding birthday picker interactions.
 */
@OptIn(ExperimentalTestApi::class)
class BirthdayPickerUiTest {

    @Test
    fun signUpBirthdayField_allowsKeyboardInputInIosHarness() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalUIViewController provides UIViewController()) {
                SignUpScreen(
                    onSignUpSuccess = {},
                    onLoginClick = {},
                    onEmailSignUp = { _, _, _, _, _, _, _ -> },
                )
            }
        }

        onNodeWithTag("signup-birthday-field")
            .assert(hasSetTextAction())
            .performTextInput("2000-04-17")
    }

    @Test
    fun profileGateBirthdayField_allowsKeyboardInputInIosHarness() = runComposeUiTest {
        setContent {
            ProfileBasicsGateScreen(
                userId = "test-user",
                initialFirstName = "Test",
                initialLastName = "User",
                onCompleted = {},
            )
        }

        onNodeWithTag("profile-gate-birthday-field")
            .assert(hasSetTextAction())
            .performTextInput("2000-04-17")
    }
}
