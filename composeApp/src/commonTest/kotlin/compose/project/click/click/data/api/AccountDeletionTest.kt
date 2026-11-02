package compose.project.click.click.data.api // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountDeletionTest {
    @Test
    fun failureCopyKeepsServerReasonButNeverHtml() {
        assertEquals(
            "Your account wasn't deleted. Unauthorized",
            accountDeletionFailureMessage(ClickWebRequestException(401, "Unauthorized")),
        )
        assertEquals("Your account wasn't deleted. Try again.", accountDeletionFailureMessage(null))
        assertEquals("Your account wasn't deleted. Try again.", accountDeletionFailureMessage(Exception("<html>500</html>")))
        assertEquals("Your account wasn't deleted. Try again.", accountDeletionFailureMessage(Exception("  ")))
    }

    @Test
    fun longReasonsAreTrimmed() {
        val message = accountDeletionFailureMessage(Exception("x".repeat(400)))
        assertEquals("Your account wasn't deleted. ".length + 160, message.length)
    }
}
