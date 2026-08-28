package compose.project.click.click.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnsureFreshAccessTokenTest {

    @Test
    fun jwtExpEpochMs_parsesStandardPayload() {
        // {"exp":1700000000} base64url
        val payload = "eyJleHAiOjE3MDAwMDAwMDB9"
        val jwt = "hdr.$payload.sig"
        val exp = EnsureFreshAccessToken.jwtExpEpochMs(jwt)
        assertEquals(1_700_000_000_000L, exp)
    }

    @Test
    fun jwtExpEpochMs_rejectsMalformed() {
        assertNull(EnsureFreshAccessToken.jwtExpEpochMs(null))
        assertNull(EnsureFreshAccessToken.jwtExpEpochMs(""))
        assertNull(EnsureFreshAccessToken.jwtExpEpochMs("not-a-jwt"))
    }

    @Test
    fun isAccessTokenFresh_rejectsExpired() {
        val payload = "eyJleHAiOjF9" // {"exp":1}
        val jwt = "hdr.$payload.sig"
        assertFalse(EnsureFreshAccessToken.isAccessTokenFresh(jwt, nowMs = 2_000L))
    }

    @Test
    fun isAccessTokenFresh_acceptsFutureExp() {
        val payload = "eyJleHAiOjE3MDAwMDAwMDB9" // {"exp":1700000000}
        val jwt = "hdr.$payload.sig"
        assertTrue(EnsureFreshAccessToken.isAccessTokenFresh(jwt, nowMs = 1_699_999_000_000L))
    }

    @Test
    fun refreshSkew_isPositive() {
        assertTrue(EnsureFreshAccessToken.REFRESH_SKEW_MS > 0L)
        assertNotNull(EnsureFreshAccessToken.REFRESH_SKEW_MS)
    }
}
