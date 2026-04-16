package compose.project.click.click.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import compose.project.click.click.data.models.User
import compose.project.click.click.qr.buildOfflineQrPayload
import compose.project.click.click.qr.parseQrCode
import compose.project.click.click.qr.QrParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compose UI smoke tests for the QR surface (R2 Phase 2 audit — T4).
 *
 * What this protects:
 *   - `UserQrCode` must render its QR bitmap on first composition even when the
 *     network/API is unreachable: the composable ships a synchronous fallback via
 *     [buildOfflineQrPayload] so users never see a blank or indefinite spinner.
 *   - The fallback payload must round-trip through [parseQrCode] back to the
 *     same `userId` so a scanner that reads it still connects.
 *
 * We can't hit the real web API from tests, so the Compose harness here asserts
 * the offline path. The fetch loop is covered at the JSON/contract level by
 * other suites; this test intentionally focuses on first-paint resilience.
 */
@OptIn(ExperimentalTestApi::class)
class QrCodeViewUiTest {

    private val fixtureUser = User(
        id = "11111111-2222-3333-4444-555555555555",
        name = "Test User",
        image = null,
        createdAt = 0L,
    )

    @Test
    fun offlineFallbackPayload_roundTripsThroughParser() {
        val payload = buildOfflineQrPayload(fixtureUser.id, fixtureUser.name)
        assertTrue(payload.isNotBlank(), "offline payload must never be blank")

        val parsed = parseQrCode(payload)
        val legacy = assertIs<QrParseResult.Legacy>(parsed, "offline fallback should parse as Legacy")
        assertEquals(fixtureUser.id, legacy.userId)
    }

    @Test
    fun offlineFallbackPayload_rejectsBlankUserId() {
        // Guard against regressing buildOfflineQrPayload into producing parseable
        // payloads with empty userIds — parseQrCode would happily return Legacy("").
        val payload = buildOfflineQrPayload("", null)
        val parsed = parseQrCode(payload)
        assertIs<QrParseResult.Invalid>(
            parsed,
            "empty userId must not round-trip as a valid Legacy payload",
        )
    }

    @Test
    fun userQrCode_rendersShareButton_immediately() = runComposeUiTest {
        // Renders the composable and asserts the CTA appears synchronously — this
        // indirectly validates that the offline fallback branch is taken when
        // `locationService = null` and network fetch (launched inside
        // LaunchedEffect) hasn't resolved yet.
        setContent {
            UserQrCode(user = fixtureUser, locationService = null)
        }
        onNodeWithText("Share QR Code").assertExists()
    }

    @Test
    fun userQrCode_rendersQrContentDescription_onFirstPaint() = runComposeUiTest {
        setContent {
            UserQrCode(user = fixtureUser, locationService = null)
        }
        // The offline fallback payload is synchronous, so the Image (with a11y
        // "QR Code") must exist without waiting for any network IO.
        val node = onNodeWithContentDescription("QR Code")
        assertNotNull(node, "QR Code image node should be in the tree on first paint")
        node.assertExists()
    }
}
