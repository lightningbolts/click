package compose.project.click.click.viewmodel

import compose.project.click.click.auth.AuthBootFastPath
import compose.project.click.click.data.storage.FakeTokenStorage
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies offline-first boot: a valid local session must admit the user without network I/O.
 *
 * [AuthViewModel] uses the same [AuthBootFastPath] resolver at cold start; this test exercises
 * that fast path directly so it can run on commonTest without Android lifecycle / Supabase.
 */
class OfflineBootTest {

    @Test
    fun offlineBoot_emitsLoggedInStateFromLocalCache() = runTest {
        val storage = FakeTokenStorage(
            jwt = testJwt(
                userId = USER_ID,
                email = EMAIL,
                name = NAME,
                expEpochSec = FAR_FUTURE_EXP_SEC,
            ),
            refreshToken = REFRESH_TOKEN,
            expiresAtEpochMs = FAR_FUTURE_EXP_SEC * 1_000L,
        )

        val bootState = AuthBootFastPath.resolveLoggedInState(storage)

        assertNotNull(bootState)
        assertEquals(USER_ID, bootState.userId)
        assertEquals(EMAIL, bootState.email)
        assertEquals(NAME, bootState.name)
    }

    @Test
    fun offlineBoot_allowsExpiredAccessTokenWhenRefreshTokenPresent() = runTest {
        val storage = FakeTokenStorage(
            jwt = testJwt(
                userId = USER_ID,
                email = EMAIL,
                name = NAME,
                expEpochSec = 1L,
            ),
            refreshToken = REFRESH_TOKEN,
            expiresAtEpochMs = 1_000L,
        )

        val bootState = AuthBootFastPath.resolveLoggedInState(storage)

        assertNotNull(bootState)
        assertEquals(USER_ID, bootState.userId)
    }

    @Test
    fun offlineBoot_requiresRefreshToken() = runTest {
        val storage = FakeTokenStorage(
            jwt = testJwt(
                userId = USER_ID,
                email = EMAIL,
                name = NAME,
                expEpochSec = FAR_FUTURE_EXP_SEC,
            ),
            refreshToken = null,
            expiresAtEpochMs = FAR_FUTURE_EXP_SEC * 1_000L,
        )

        val bootState = AuthBootFastPath.resolveLoggedInState(storage)

        assertNull(bootState)
    }

    @Test
    fun authViewModelProbe_matchesOfflineBootFastPath() = runTest {
        val storage = FakeTokenStorage(
            jwt = testJwt(
                userId = USER_ID,
                email = EMAIL,
                name = NAME,
                expEpochSec = FAR_FUTURE_EXP_SEC,
            ),
            refreshToken = REFRESH_TOKEN,
            expiresAtEpochMs = FAR_FUTURE_EXP_SEC * 1_000L,
        )

        val fastPath = AuthBootFastPath.resolveLoggedInState(storage)
        assertNotNull(fastPath)
        assertTrue(fastPath.userId.isNotBlank())
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun testJwt(
        userId: String,
        email: String,
        name: String,
        expEpochSec: Long,
    ): String {
        fun b64(value: String): String =
            Base64.encode(value.encodeToByteArray()).trimEnd('=').replace('+', '-').replace('/', '_')

        val header = b64("""{"alg":"HS256","typ":"JWT"}""")
        val payload = b64(
            """{"sub":"$userId","email":"$email","name":"$name","exp":$expEpochSec}""",
        )
        return "$header.$payload.fake-signature"
    }

    private companion object {
        const val USER_ID = "offline-user-abc"
        const val EMAIL = "offline@click.test"
        const val NAME = "Offline User"
        const val REFRESH_TOKEN = "offline-refresh-token"
        const val FAR_FUTURE_EXP_SEC = 4_102_444_800L // year 2100
    }
}
